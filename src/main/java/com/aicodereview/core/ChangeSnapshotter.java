package com.aicodereview.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the before/after text of the changes selected in the commit area.
 * Must be called inside a read action.
 */
public final class ChangeSnapshotter {

    private static final int MAX_TEXT_CHARS = 1_000_000;
    private static final int MAX_FILES = 300;
    private static final long MAX_TOTAL_CHARS = 12_000_000L;

    private ChangeSnapshotter() {
    }

    public static SnapshotResult snapshot(@NotNull Project project,
                                          @NotNull List<Change> changes,
                                          @NotNull List<FilePath> unversionedFiles,
                                          @NotNull String outputFileName) {
        List<FileSnapshot> files = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long totalChars = 0;

        for (Change change : changes) {
            if (files.size() >= MAX_FILES) {
                warnings.add("文件数量超过 " + MAX_FILES + " 个，其余文件已跳过。");
                break;
            }
            if (totalChars >= MAX_TOTAL_CHARS) {
                warnings.add("待审查文本总量超过限制，其余文件已跳过。");
                break;
            }
            try {
                FileSnapshot snapshot = snapshotChange(project, change, outputFileName);
                if (snapshot == null) {
                    continue;
                }
                int chars = textLength(snapshot.beforeText()) + textLength(snapshot.afterText());
                if (totalChars + chars > MAX_TOTAL_CHARS) {
                    warnings.add("待审查文本总量超过限制，其余文件已跳过。");
                    break;
                }
                if (seen.add(snapshot.path())) {
                    files.add(snapshot);
                    totalChars += chars;
                }
            } catch (Exception e) {
                String path = safePath(project, change);
                warnings.add("已跳过：" + path + "（" + safeMessage(e) + "）");
            }
        }

        for (FilePath filePath : unversionedFiles) {
            if (files.size() >= MAX_FILES) {
                warnings.add("文件数量超过 " + MAX_FILES + " 个，其余文件已跳过。");
                break;
            }
            if (totalChars >= MAX_TOTAL_CHARS) {
                warnings.add("待审查文本总量超过限制，其余文件已跳过。");
                break;
            }
            try {
                FileSnapshot snapshot = snapshotUnversioned(project, filePath, outputFileName);
                if (snapshot == null) {
                    continue;
                }
                int chars = textLength(snapshot.afterText());
                if (totalChars + chars > MAX_TOTAL_CHARS) {
                    warnings.add("待审查文本总量超过限制，其余文件已跳过。");
                    break;
                }
                if (seen.add(snapshot.path())) {
                    files.add(snapshot);
                    totalChars += chars;
                }
            } catch (Exception e) {
                warnings.add("已跳过：" + filePath.getPath() + "（" + safeMessage(e) + "）");
            }
        }

        files.sort(Comparator.comparing(FileSnapshot::path));
        return new SnapshotResult(files, warnings);
    }

    private static @Nullable FileSnapshot snapshotChange(Project project, Change change, String outputFileName) {
        FilePath beforePath = change.getBeforeRevision() == null ? null : change.getBeforeRevision().getFile();
        FilePath afterPath = change.getAfterRevision() == null ? null : change.getAfterRevision().getFile();
        FilePath primaryPath = afterPath != null ? afterPath : beforePath;
        if (primaryPath == null) {
            return null;
        }

        String path = relativePath(project, primaryPath);
        if (path == null || path.isBlank() || isOutputFile(path, outputFileName)) {
            return null;
        }
        String oldPath = beforePath == null ? path : relativePath(project, beforePath);
        if (oldPath == null || oldPath.isBlank()) {
            oldPath = path;
        }

        VirtualFile afterFile = afterPath == null ? null : afterPath.getVirtualFile();
        VirtualFile beforeFile = beforePath == null ? null : beforePath.getVirtualFile();
        if (afterFile != null && afterFile.isDirectory()) {
            return null;
        }
        if (beforeFile != null && beforeFile.isDirectory()) {
            return null;
        }

        if (isOversized(afterFile) || isOversized(beforeFile)) {
            // The warning is generated by the caller through an exception message.
            throw new IllegalStateException("文件过大（超过约 1 MB）");
        }

        String beforeText = readRevision(change.getBeforeRevision());
        String afterText = readRevision(change.getAfterRevision());
        if (beforeText == null && afterText == null) {
            return null;
        }
        if (looksBinary(beforeText) || looksBinary(afterText)
                || isBinaryFileType(afterFile) || isBinaryFileType(beforeFile)) {
            throw new IllegalStateException("二进制文件");
        }
        if (tooLong(beforeText) || tooLong(afterText)) {
            throw new IllegalStateException("文本超过 100 万字符");
        }

        ChangeKind kind = switch (change.getType()) {
            case NEW -> ChangeKind.ADDED;
            case DELETED -> ChangeKind.DELETED;
            case MOVED -> ChangeKind.MOVED;
            default -> ChangeKind.MODIFIED;
        };

        return new FileSnapshot(path, oldPath, kind, beforeText, afterText);
    }

    private static @Nullable FileSnapshot snapshotUnversioned(Project project, FilePath filePath,
                                                               String outputFileName) {
        VirtualFile file = filePath.getVirtualFile();
        if (file == null || !file.isValid() || file.isDirectory()) {
            return null;
        }
        String path = relativePath(project, filePath);
        if (path == null || path.isBlank() || isOutputFile(path, outputFileName)) {
            return null;
        }
        if (file.getLength() > MAX_TEXT_CHARS) {
            throw new IllegalStateException("文件过大（超过约 1 MB）");
        }
        if (isBinaryFileType(file)) {
            throw new IllegalStateException("二进制文件");
        }
        String text;
        try {
            text = VfsUtilCore.loadText(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取文件失败：" + safeMessage(e), e);
        }
        if (looksBinary(text)) {
            throw new IllegalStateException("二进制文件");
        }
        if (tooLong(text)) {
            throw new IllegalStateException("文本超过 100 万字符");
        }
        return new FileSnapshot(path, path, ChangeKind.ADDED, null, text);
    }

    private static @Nullable String readRevision(@Nullable ContentRevision revision) {
        if (revision == null) {
            return null;
        }
        try {
            String content = revision.getContent();
            if (content != null) {
                return content;
            }
            VirtualFile file = revision.getFile().getVirtualFile();
            if (file != null && file.isValid() && !file.isDirectory()) {
                try {
                    return VfsUtilCore.loadText(file);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("读取文件失败：" + safeMessage(e), e);
                }
            }
            return null;
        } catch (VcsException e) {
            throw new IllegalStateException("VCS 读取失败：" + safeMessage(e), e);
        }
    }

    private static boolean isOversized(@Nullable VirtualFile file) {
        return file != null && file.isValid() && file.getLength() > MAX_TEXT_CHARS;
    }

    private static boolean isBinaryFileType(@Nullable VirtualFile file) {
        return file != null && file.isValid() && file.getFileType().isBinary();
    }

    private static boolean looksBinary(@Nullable String text) {
        if (text == null) {
            return false;
        }
        int limit = Math.min(text.length(), 8000);
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\0') {
                return true;
            }
        }
        return false;
    }

    private static boolean tooLong(@Nullable String text) {
        return text != null && text.length() > MAX_TEXT_CHARS;
    }

    private static int textLength(@Nullable String text) {
        return text == null ? 0 : text.length();
    }

    private static boolean isOutputFile(String relativePath, String outputFileName) {
        return relativePath.equals(outputFileName)
                || relativePath.endsWith("/" + outputFileName)
                || relativePath.endsWith("\\" + outputFileName);
    }

    private static @Nullable String relativePath(Project project, FilePath filePath) {
        String basePath = project.getBasePath();
        String path = filePath.getPath();
        if (basePath == null) {
            return path;
        }
        String relative = FileUtil.getRelativePath(basePath, path, '/');
        return relative == null ? path : relative;
    }

    private static @Nullable String relativePath(Project project, VirtualFile file) {
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        if (base == null) {
            return file.getPath();
        }
        String relative = VfsUtilCore.getRelativePath(file, base, '/');
        return relative == null ? file.getPath() : relative;
    }

    private static String safePath(Project project, Change change) {
        try {
            String path = null;
            if (change.getAfterRevision() != null) {
                path = relativePath(project, change.getAfterRevision().getFile());
            }
            if ((path == null || path.isBlank()) && change.getBeforeRevision() != null) {
                path = relativePath(project, change.getBeforeRevision().getFile());
            }
            if (path != null && !path.isBlank()) {
                return path;
            }
        } catch (RuntimeException ignored) {
            // Fall through.
        }
        return "(未知文件)";
    }

    private static String safeMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

}
