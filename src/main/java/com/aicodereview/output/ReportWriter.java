package com.aicodereview.output;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ReportWriter {

    private ReportWriter() {
    }

    public static Path write(@NotNull Project project, @NotNull String fileName, @NotNull String markdown)
            throws IOException {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new IOException("无法确定项目根目录。");
        }
        Path base = Path.of(basePath).toAbsolutePath().normalize();
        String safeName = fileName.trim();
        if (safeName.contains("/") || safeName.contains("\\") || safeName.equals(".")
                || safeName.equals("..")) {
            throw new IOException("输出文件名不合法：" + fileName);
        }
        Path target = base.resolve(safeName).normalize();
        if (!base.equals(target.getParent())) {
            throw new IOException("输出文件必须位于项目根目录。");
        }

        Path temp = base.resolve("." + safeName + ".tmp");
        Files.writeString(temp, markdown, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return target;
    }

    /**
     * Refreshes the VFS entry on the EDT so the file appears in the project tree.
     */
    public static void refresh(@NotNull Project project, @NotNull Path path) {
        ApplicationManager.getApplication().invokeLater(
                () -> refreshAndReload(project, path), ModalityState.any());
    }

    public static void openInEditor(@NotNull Project project, @NotNull Path path) {
        ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile file = refreshAndReload(project, path);
            if (file != null && file.isValid()) {
                FileEditorManager.getInstance(project).openFile(file, true);
            }
        }, ModalityState.any());
    }

    /**
     * Refresh the VFS entry and reload the already-open document. Without the
     * explicit document reload, an open editor can keep showing the previous
     * report even though the file on disk has been replaced.
     */
    private static VirtualFile refreshAndReload(Project project, Path path) {
        VirtualFile file = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(path.toString().replace('\\', '/'));
        if (file == null || !file.isValid()) {
            return null;
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            // The report is a generated file and is intentionally overwritten;
            // discarding stale editor content is the expected behavior here.
            FileDocumentManager.getInstance().reloadFromDisk(document, project);
        }
        return file;
    }
}
