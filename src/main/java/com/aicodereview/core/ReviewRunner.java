package com.aicodereview.core;

import com.aicodereview.config.AiReviewSettings;
import com.aicodereview.config.ApiKeyStore;
import com.aicodereview.config.DefaultPrompt;
import com.aicodereview.config.I18n;
import com.aicodereview.history.ReviewHistoryService;
import com.aicodereview.llm.AiClient;
import com.aicodereview.llm.AiCompletion;
import com.aicodereview.llm.AiConnection;
import com.aicodereview.llm.AiException;
import com.aicodereview.output.ReportWriter;
import com.aicodereview.review.ReviewFindingsDialog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReviewRunner {

    private static final Logger LOG = Logger.getInstance(ReviewRunner.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SYSTEM_PROMPT = """
            你是一名严谨、务实的资深代码审查专家。
            你必须严格遵守用户给出的审查规则和输出模板，只输出 Markdown 报告正文。
            不要输出前言、过程说明、思考过程、结束语，也不要用代码块围栏包裹整篇报告。
            只审查用户提供的 diff，不臆测未展示的代码；不确定时标注“待确认”。
            """;

    private static final String SYSTEM_PROMPT_EN = """
            You are a rigorous, pragmatic senior code reviewer.
            Follow the user's review rules and output template exactly, and output only the Markdown report body.
            Do not output a preface, process explanation, chain of thought, or closing remarks, and do not wrap
            the whole report in a code fence. Review only the supplied diff; never invent unseen code.
            Mark uncertain findings as "needs confirmation".
            """;

    private final Project project;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ReviewRunner(@NotNull Project project) {
        this.project = project;
    }

    public static ReviewRunner getInstance(@NotNull Project project) {
        return project.getService(ReviewRunner.class);
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start(@NotNull List<Change> changes, @NotNull List<FilePath> unversionedFiles) {
        start(changes, unversionedFiles, ReviewContext.workingChanges());
    }

    public void start(@NotNull List<Change> changes,
                      @NotNull List<FilePath> unversionedFiles,
                      @NotNull ReviewContext context) {
        if (!running.compareAndSet(false, true)) {
            Messages.showInfoMessage(project,
                    I18n.t("已经有一次 AI 审查正在进行，请等待完成。",
                            "An AI review is already running. Please wait for it to finish."),
                    I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }

        List<Change> changesCopy = List.copyOf(changes);
        List<FilePath> unversionedCopy = List.copyOf(unversionedFiles);

        new Task.Backgroundable(project,
                I18n.t("AI 代码审查", "AI Code Review"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    doReview(changesCopy, unversionedCopy, context, indicator);
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (Throwable e) {
                    LOG.warn("AI code review failed", e);
                    showFailure(e);
                }
            }

            @Override
            public void onFinished() {
                running.set(false);
            }
        }.queue();
    }

    private void doReview(List<Change> changes, List<FilePath> unversionedFiles,
                          ReviewContext context, ProgressIndicator indicator) throws Exception {
        AiReviewSettings settings = AiReviewSettings.getInstance();
        AiReviewSettings.State state = settings.copy();
        String outputFileName = settings.outputFileName();

        indicator.setText(I18n.t("正在读取待提交代码…", "Reading the selected changes…"));
        indicator.setIndeterminate(true);

        SnapshotResult snapshot = ReadAction.compute(
                () -> ChangeSnapshotter.snapshot(project, changes, unversionedFiles, outputFileName));
        // Keep the persisted state consistent with the sanitized name used for output.
        state.outputFile = outputFileName;
        indicator.checkCanceled();

        if (snapshot.files().isEmpty()) {
            showInfo(I18n.t("没有可审查的文本变更。", "No reviewable text changes.")
                    + warningSuffix(snapshot), I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }

        indicator.setText(I18n.t("正在生成 diff…", "Building the unified diff…"));
        DiffResult diff = DiffRenderer.render(snapshot.files(), state.maxDiffChars);
        if (diff.reviewedFiles().isEmpty()) {
            showInfo(I18n.t("没有可审查的文本变更（可能都是二进制文件或空 diff）。",
                            "No reviewable text changes (all files may be binary or the diff is empty).")
                            + warningSuffix(snapshot),
                    I18n.t("AI 代码审查", "AI Code Review"));
            return;
        }
        indicator.checkCanceled();

        String promptTemplate = state.promptTemplate;
        if (DefaultPrompt.ZH_TEMPLATE.equals(promptTemplate)
                || DefaultPrompt.EN_TEMPLATE.equals(promptTemplate)) {
            promptTemplate = DefaultPrompt.forLanguage(state.uiLanguage);
        }
        String prompt = PromptRenderer.render(
                promptTemplate,
                project.getName(),
                context,
                diff.reviewedFiles(),
                diff.text(),
                state.language,
                LocalDateTime.now().format(DATE_FORMAT));
        if (!diff.skippedFiles().isEmpty()) {
            StringBuilder note = new StringBuilder(I18n.t(
                    "\n\n> 注意：以下选中文件未纳入本次 diff：\n",
                    "\n\n> Note: these selected files were not included in the diff:\n"));
            int limit = Math.min(20, diff.skippedFiles().size());
            for (int i = 0; i < limit; i++) {
                note.append("> - ").append(diff.skippedFiles().get(i)).append('\n');
            }
            if (diff.skippedFiles().size() > limit) {
                note.append("> - …\n");
            }
            prompt += note;
        }

        String apiKey = state.noApiKey ? null : ApiKeyStore.get();
        AiConnection connection = AiConnection.from(state, apiKey);
        String systemPrompt = "en".equalsIgnoreCase(state.uiLanguage) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT;
        AiCompletion completion = new AiClient().complete(connection, systemPrompt, prompt, indicator);
        indicator.checkCanceled();

        indicator.setText(I18n.t("正在写入审查报告…", "Writing the review report…"));
        String report = ReviewReport.build(project.getName(), state, context, snapshot, diff, completion);
        Path reportPath;
        try {
            reportPath = ReportWriter.write(project, outputFileName, report);
        } catch (IOException e) {
            throw new AiException(I18n.t("报告写入失败：", "Failed to write the report: ")
                    + safeMessage(e), e);
        }

        try {
            ReviewHistoryService.getInstance(project).save(
                    context,
                    completion.model() == null ? state.model : completion.model(),
                    snapshot.files().size(),
                    report);
        } catch (Exception e) {
            LOG.warn("Could not save AI review history", e);
        }

        boolean openReport = state.openReport;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            if (openReport) {
                ReportWriter.openInEditor(project, reportPath);
            } else {
                ReportWriter.refresh(project, reportPath);
            }
            NotificationGroup group = NotificationGroupManager.getInstance()
                    .getNotificationGroup("AiCodeReview.Notifications");
            if (group != null) {
                Notification notification = group.createNotification(
                        I18n.t("AI 代码审查完成", "AI review complete"),
                        I18n.t("报告已写入项目根目录：" + outputFileName,
                                "Report written to the project root: " + outputFileName),
                        NotificationType.INFORMATION);
                notification.addAction(NotificationAction.createSimpleExpiring(
                        I18n.t("打开报告", "Open report"),
                        () -> ReportWriter.openInEditor(project, reportPath)));
                notification.addAction(NotificationAction.createSimpleExpiring(
                        I18n.t("定位问题", "Findings"),
                        () -> openFindings(reportPath)));
                Notifications.Bus.notify(notification, project);
            }
        });
    }

    private void openFindings(Path reportPath) {
        try {
            String report = Files.readString(reportPath, StandardCharsets.UTF_8);
            new ReviewFindingsDialog(project, report, reportPath).show();
        } catch (IOException e) {
            showError(I18n.t("读取报告失败：" + safeMessage(e),
                    "Failed to read the report: " + safeMessage(e)));
        }
    }

    private void showFailure(Throwable error) {
        String message;
        if (error instanceof AiException) {
            message = error.getMessage();
        } else if (error instanceof IOException) {
            message = I18n.t("读取/写入文件失败：", "File I/O failed: ") + safeMessage(error);
        } else {
            message = I18n.t("AI 代码审查失败：", "AI review failed: ") + safeMessage(error);
        }
        showError(message);
    }

    private void showInfo(String message, String title) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            Messages.showInfoMessage(project, message, title);
        });
    }

    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            Messages.showErrorDialog(project, message, "AI 代码审查失败");
        });
    }

    private static String warningSuffix(SnapshotResult snapshot) {
        if (snapshot.warnings().isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder("\n\n");
        int limit = Math.min(8, snapshot.warnings().size());
        for (int i = 0; i < limit; i++) {
            result.append("• ").append(snapshot.warnings().get(i)).append('\n');
        }
        if (snapshot.warnings().size() > limit) {
            result.append("• …");
        }
        return result.toString();
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
