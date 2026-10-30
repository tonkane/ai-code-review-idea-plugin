package com.aicodereview.action;

import com.aicodereview.config.I18n;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class ChangePaths {

    private ChangePaths() {
    }

    public static @NotNull String relativePath(@NotNull Project project, @NotNull Change change) {
        FilePath path = change.getAfterRevision() != null
                ? change.getAfterRevision().getFile()
                : change.getBeforeRevision() != null ? change.getBeforeRevision().getFile() : null;
        if (path == null) {
            return "(未知文件)";
        }

        VirtualFile file = path.getVirtualFile();
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        if (file != null && base != null) {
            String relative = VfsUtilCore.getRelativePath(file, base, '/');
            if (relative != null && !relative.isBlank()) {
                return relative;
            }
        }
        String basePath = project.getBasePath();
        if (basePath != null) {
            String relative = FileUtil.getRelativePath(basePath, path.getPath(), '/');
            if (relative != null && !relative.isBlank()) {
                return relative;
            }
        }
        return path.getPath();
    }

    public static @NotNull String describe(@NotNull Project project, @NotNull Change change) {
        return relativePath(project, change) + "  [" + typeLabel(change.getType()) + "]";
    }

    public static @NotNull String typeLabel(Change.Type type) {
        if (type == null) {
            return I18n.t("变更", "Changed");
        }
        return switch (type) {
            case NEW -> I18n.t("新增", "Added");
            case DELETED -> I18n.t("删除", "Deleted");
            case MOVED -> I18n.t("移动/重命名", "Moved/Renamed");
            default -> I18n.t("修改", "Modified");
        };
    }
}
