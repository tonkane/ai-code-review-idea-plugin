package com.aicodereview.action;

import com.aicodereview.config.AiReviewConfigurable;
import com.aicodereview.config.AiReviewSettings;
import com.aicodereview.config.ApiKeyStore;
import com.aicodereview.config.I18n;
import com.aicodereview.core.ReviewRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.vcs.commit.CommitWorkflowUi;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reviews the changes checked in the commit tool window. If the action is
 * invoked outside the commit context, it falls back to selected changes or a
 * file picker.
 */
public final class ReviewChangesAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        event.getPresentation().setEnabled(project != null && !DumbService.isDumb(project));
        event.getPresentation().setText(I18n.t("AI 审查勾选的更改", "Review Checked Changes"));
        event.getPresentation().setDescription(I18n.t(
                "用 AI 审查 Git 提交区中勾选的更改，并生成 Markdown 报告",
                "Review the changes checked in the Commit tool window with AI"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        if (DumbService.isDumb(project)) {
            Messages.showInfoMessage(project,
                    I18n.t("IDEA 正在建立索引，请稍后再试。", "IDEA is indexing. Please try again shortly."),
                    I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }
        if (ReviewRunner.getInstance(project).isRunning()) {
            Messages.showInfoMessage(project,
                    I18n.t("已经有一次 AI 审查正在进行，请等待完成。",
                            "An AI review is already running. Please wait for it to finish."),
                    I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }
        if (!ensureConfigured(project)) {
            return;
        }

        List<Change> changes = new ArrayList<>();
        List<FilePath> unversionedFiles = new ArrayList<>();

        // The modern commit tool window exposes the exact checked changes.
        CommitWorkflowUi commitUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI);
        if (commitUi != null) {
            changes.addAll(commitUi.getIncludedChanges());
            unversionedFiles.addAll(commitUi.getIncludedUnversionedFiles());
            if (!changes.isEmpty() || !unversionedFiles.isEmpty()) {
                ReviewRunner.getInstance(project).start(changes, unversionedFiles);
                return;
            }

            // A modal commit dialog does not always expose an active workflow UI;
            // fall through to the selected-changes data key before giving up.
            Change[] selectedInCommit = event.getData(VcsDataKeys.SELECTED_CHANGES);
            if (selectedInCommit != null && selectedInCommit.length > 0) {
                changes.addAll(Arrays.asList(selectedInCommit));
                ReviewRunner.getInstance(project).start(changes, unversionedFiles);
                return;
            }

            Messages.showInfoMessage(project,
                    I18n.t("请先在提交区勾选要审查的文件，再执行此操作。",
                            "Check the files to review in the Commit tool window first."),
                    I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }

        Change[] selected = event.getData(VcsDataKeys.SELECTED_CHANGES);
        if (selected == null || selected.length == 0) {
            selected = event.getData(VcsDataKeys.CHANGES);
        }
        if (selected != null && selected.length > 0) {
            changes.addAll(Arrays.asList(selected));
            ReviewRunner.getInstance(project).start(changes, unversionedFiles);
            return;
        }

        List<Change> allChanges = new ArrayList<>(ChangeListManager.getInstance(project).getAllChanges());
        if (allChanges.isEmpty()) {
            Messages.showInfoMessage(project,
                    I18n.t("当前项目没有待提交的 Git 变更。", "This project has no pending Git changes."),
                    I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }

        ChangeSelectionDialog dialog = new ChangeSelectionDialog(project, allChanges);
        if (!dialog.showAndGet()) {
            return;
        }
        changes.addAll(dialog.getSelectedChanges());
        if (changes.isEmpty()) {
            return;
        }
        ReviewRunner.getInstance(project).start(changes, unversionedFiles);
    }

    private boolean ensureConfigured(Project project) {
        AiReviewSettings settings = AiReviewSettings.getInstance();
        if (settings.isConfigured()
                && (settings.getState().noApiKey
                || ApiKeyStore.hasKey()
                || isLocalEndpoint(settings.getState().endpoint))) {
            return true;
        }
        int result = Messages.showYesNoDialog(project,
                I18n.t("还没有配置 API Key（或接口地址/模型）。现在打开设置吗？\n"
                                + "本地 Ollama 等无需鉴权的接口可以直接留空 Key。",
                        "No API key (or endpoint/model) is configured. Open settings now?\n"
                                + "Local Ollama and similar endpoints can leave the key empty."),
                "AI Code Review",
                I18n.t("打开设置", "Open settings"), I18n.t("取消", "Cancel"),
                Messages.getWarningIcon());
        if (result == Messages.YES) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AiReviewConfigurable.class);
        }
        return settings.isConfigured();
    }

    private static boolean isLocalEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            String host = new URI(endpoint).getHost();
            if (host == null) {
                return false;
            }
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || host.endsWith(".local");
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
