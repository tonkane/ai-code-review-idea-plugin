package com.aicodereview.action;

import com.aicodereview.config.I18n;
import com.aicodereview.core.ReviewContext;
import com.aicodereview.core.ReviewRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Reviews exactly one commit selected in the Git Log tool window.
 */
public final class ReviewCommitAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VcsLogCommitSelection selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
        boolean singleCommit = selection != null && selection.getCommits().size() == 1;
        event.getPresentation().setEnabled(project != null && !DumbService.isDumb(project) && singleCommit);
        event.getPresentation().setText(I18n.t("AI 审查此次提交", "Review This Commit with AI"));
        event.getPresentation().setDescription(I18n.t(
                "用 AI 审查当前选中的 Git 提交",
                "Review the selected Git commit with AI"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        VcsLogCommitSelection selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
        if (selection == null || selection.getCommits().size() != 1) {
            Messages.showInfoMessage(project,
                    I18n.t("请只选择一条提交记录。", "Select exactly one commit."),
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

        List<VcsFullCommitDetails> cached = selection.getCachedFullDetails();
        if (cached.size() == 1) {
            start(project, cached.get(0));
            return;
        }
        selection.requestFullDetails(details -> {
            if (project.isDisposed()) {
                return;
            }
            if (details.size() != 1) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!project.isDisposed()) {
                        Messages.showInfoMessage(project,
                                I18n.t("无法加载该提交的详细信息。", "Could not load details for this commit."),
                                I18n.t("AI 代码审查", "AI Code Review"));
                    }
                });
                return;
            }
            VcsFullCommitDetails commit = details.get(0);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed()) {
                    start(project, commit);
                }
            });
        });
    }

    private void start(Project project, VcsFullCommitDetails commit) {
        if (commit.getChanges().isEmpty()) {
            Messages.showInfoMessage(project,
                    I18n.t("该提交没有可审查的文本变更。", "This commit has no reviewable text changes."),
                    I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }
        Hash id = commit.getId();
        String hash = id == null ? "" : id.asString();
        ReviewContext context = ReviewContext.commit(hash, commit.getSubject());
        ReviewRunner.getInstance(project).start(
                List.copyOf(commit.getChanges()), Collections.emptyList(), context);
    }
}
