package com.aicodereview.review;

import com.aicodereview.config.AiReviewSettings;
import com.aicodereview.config.I18n;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class ReviewFindingsFromReportAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean isReport = project != null && file != null
                && file.getName().equals(AiReviewSettings.getInstance().outputFileName());
        event.getPresentation().setEnabledAndVisible(isReport);
        event.getPresentation().setText(I18n.t("AI 审查问题定位", "AI Review Findings"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || file == null) {
            return;
        }
        Document document = editor.getDocument();
        new ReviewFindingsDialog(project, document.getText(),
                java.nio.file.Path.of(file.getPath())).show();
    }
}
