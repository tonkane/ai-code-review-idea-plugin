package com.aicodereview.history;

import com.aicodereview.config.I18n;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ReviewHistoryAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        event.getPresentation().setEnabled(project != null);
        event.getPresentation().setText(I18n.t("AI 审查历史", "AI Review History"));
        event.getPresentation().setDescription(I18n.t(
                "查看本地留存的 AI 审查记录并定位问题",
                "Browse locally retained AI review reports and navigate to findings"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        new ReviewHistoryDialog(project).show();
    }
}
