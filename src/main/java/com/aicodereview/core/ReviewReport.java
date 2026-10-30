package com.aicodereview.core;

import com.aicodereview.config.AiReviewSettings;
import com.aicodereview.llm.AiCompletion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Adds a deterministic title/scope header around the model's Markdown body.
 */
public final class ReviewReport {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_FILES_IN_HEADER = 12;

    private ReviewReport() {
    }

    public static String build(String projectName,
                               AiReviewSettings.State settings,
                               ReviewContext scope,
                               SnapshotResult snapshot,
                               DiffResult diff,
                               AiCompletion completion) {
        boolean en = "en".equalsIgnoreCase(settings.uiLanguage);
        StringBuilder report = new StringBuilder();
        report.append("# AI Code Review\n\n");
        report.append(en ? "> Project: " : "> 项目：").append(projectName).append("  \n");
        report.append(en ? "> Time: " : "> 时间：")
                .append(LocalDateTime.now().format(TIME_FORMAT)).append("  \n");
        report.append(en ? "> Model: " : "> 模型：")
                .append(completion.model() == null ? settings.model : completion.model()).append("  \n");
        report.append(en ? "> Scope: " : "> 范围：")
                .append(scope == null ? "" : scope.display())
                .append(en ? " (" + snapshot.files().size() + " files selected, "
                        + diff.reviewedFiles().size() + " in diff)"
                        : "（选中 " + snapshot.files().size() + " 个文件，纳入 diff "
                        + diff.reviewedFiles().size() + " 个）")
                .append("  \n");
        report.append(en ? "> Language: " : "> 语言：").append(settings.language).append("  \n");
        report.append(en ? "> Files: " : "> 文件：").append(fileSummary(snapshot.files(), en))
                .append("\n\n");
        report.append("---\n\n");

        if (completion.truncated()) {
            report.append(en
                    ? "> ⚠️ Model output reached the max token limit; the report may be incomplete.\n\n"
                    : "> ⚠️ 模型输出达到最大 token 限制，报告可能不完整；可调大“最大输出 tokens”后重试。\n\n");
        }
        if (diff.truncated()) {
            report.append(en
                    ? "> ⚠️ Input exceeded the max diff characters; some files or hunks were not reviewed.\n\n"
                    : "> ⚠️ 输入超过 max diff 字符数，部分文件或内容未纳入本次审查。\n\n");
        }

        StringBuilder notes = new StringBuilder();
        for (String warning : snapshot.warnings()) {
            notes.append("- ").append(warning).append('\n');
        }
        for (String skipped : diff.skippedFiles()) {
            notes.append(en ? "- Not reviewed: " : "- 未审查：").append(skipped).append('\n');
        }
        if (!notes.isEmpty()) {
            report.append(en
                            ? "<details>\n<summary>Excluded / skipped files</summary>\n\n"
                            : "<details>\n<summary>未纳入/已跳过文件</summary>\n\n")
                    .append(notes)
                    .append("\n</details>\n\n");
        }

        report.append(stripOuterFence(completion.content()).strip()).append('\n');
        return report.toString();
    }

    private static String fileSummary(List<FileSnapshot> files, boolean en) {
        if (files.isEmpty()) {
            return en ? "(none)" : "（无）";
        }
        StringBuilder summary = new StringBuilder();
        int limit = Math.min(files.size(), MAX_FILES_IN_HEADER);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                summary.append(en ? ", " : "、");
            }
            summary.append('`').append(files.get(i).path()).append('`');
        }
        if (files.size() > limit) {
            summary.append(en ? " and " + files.size() + " total" : " 等 " + files.size() + " 个");
        }
        return summary.toString();
    }

    static String stripOuterFence(String content) {
        String text = content == null ? "" : content.strip();
        if (!text.startsWith("```") || !text.endsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0 || text.length() < 8) {
            return text;
        }
        String opening = text.substring(0, firstNewline).strip().toLowerCase(java.util.Locale.ROOT);
        if (!opening.equals("```") && !opening.equals("```markdown") && !opening.equals("```md")) {
            return text;
        }
        // The closing fence must be on its own line; otherwise this is just
        // Markdown content that happens to end with a code block.
        if (text.charAt(text.length() - 4) != '\n') {
            return text;
        }
        return text.substring(firstNewline + 1, text.length() - 3).strip();
    }
}
