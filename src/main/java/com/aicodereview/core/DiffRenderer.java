package com.aicodereview.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds deterministic unified diffs with a line-based LCS. The output is plain
 * Java (no IDE APIs) so it can be unit tested.
 */
public final class DiffRenderer {

    private static final int CONTEXT_LINES = 3;
    private static final long MAX_DP_CELLS = 4_000_000L;

    private DiffRenderer() {
    }

    public static DiffResult render(List<FileSnapshot> files, int maxChars) {
        StringBuilder out = new StringBuilder();
        List<String> reviewed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        boolean truncated = false;
        int used = 0;
        int limit = Math.max(1000, maxChars);

        for (FileSnapshot file : files) {
            String section;
            try {
                section = renderFile(file);
            } catch (RuntimeException e) {
                skipped.add(file.path() + "（生成 diff 失败：" + safeMessage(e) + "）");
                continue;
            }
            if (section == null) {
                skipped.add(file.path() + "（无文本变更）");
                continue;
            }
            if (used + section.length() > limit) {
                if (used == 0) {
                    int room = Math.max(0, limit - 80);
                    out.append(section, 0, Math.min(section.length(), room));
                    out.append("\n\n> 注意：单个 diff 过长，已截断。\n");
                    reviewed.add(file.path());
                    truncated = true;
                    used = out.length();
                } else {
                    skipped.add(file.path() + "（超出 max diff 字符数限制）");
                    truncated = true;
                }
                continue;
            }
            out.append(section).append('\n');
            reviewed.add(file.path());
            used += section.length() + 1;
        }

        return new DiffResult(out.toString(), reviewed, skipped, truncated);
    }

    /**
     * Returns null when the file has no text changes.
     */
    static String renderFile(FileSnapshot file) {
        List<String> before = splitLines(file.beforeText());
        List<String> after = splitLines(file.afterText());
        List<LineOp> ops = buildOps(file.kind(), before, after);
        if (ops.stream().allMatch(op -> op.kind == Kind.EQUAL)) {
            return null;
        }

        StringBuilder section = new StringBuilder();
        section.append("### `").append(file.path()).append("`（").append(file.kind().label()).append("）\n");
        if (!file.oldPath().equals(file.path())) {
            section.append("\n原路径：`").append(file.oldPath()).append("`\n");
        }
        appendChangedLineNumbers(section, ops);
        section.append("```diff\n");
        section.append("diff --git a/").append(file.oldPath()).append(" b/").append(file.path()).append('\n');

        switch (file.kind()) {
            case ADDED -> section.append("new file mode 100644\n");
            case DELETED -> section.append("deleted file mode 100644\n");
            case MOVED -> section.append("rename from ").append(file.oldPath()).append('\n')
                    .append("rename to ").append(file.path()).append('\n');
            default -> {
            }
        }

        section.append(file.kind() == ChangeKind.ADDED ? "--- /dev/null" : "--- a/" + file.oldPath())
                .append('\n');
        section.append(file.kind() == ChangeKind.DELETED ? "+++ /dev/null" : "+++ b/" + file.path())
                .append('\n');

        List<int[]> ranges = hunkRanges(ops);
        for (int[] range : ranges) {
            int start = range[0];
            int end = range[1];
            int oldStart = 0;
            int newStart = 0;
            int oldCount = 0;
            int newCount = 0;
            for (int i = start; i < end; i++) {
                LineOp op = ops.get(i);
                if (op.oldLine > 0) {
                    if (oldStart == 0) {
                        oldStart = op.oldLine;
                    }
                    oldCount++;
                }
                if (op.newLine > 0) {
                    if (newStart == 0) {
                        newStart = op.newLine;
                    }
                    newCount++;
                }
            }
            section.append("@@ -").append(oldStart).append(',').append(oldCount)
                    .append(" +").append(newStart).append(',').append(newCount).append(" @@\n");
            for (int i = start; i < end; i++) {
                LineOp op = ops.get(i);
                section.append(op.kind.prefix).append(op.text).append('\n');
            }
        }

        section.append("```");
        return section.toString();
    }

    private static void appendChangedLineNumbers(StringBuilder section, List<LineOp> ops) {
        List<Integer> added = new ArrayList<>();
        List<Integer> deleted = new ArrayList<>();
        for (LineOp op : ops) {
            if (op.kind == Kind.INSERT && op.newLine > 0) {
                added.add(op.newLine);
            } else if (op.kind == Kind.DELETE && op.oldLine > 0) {
                deleted.add(op.oldLine);
            }
        }
        if (!added.isEmpty()) {
            section.append("变更行号（新文件）：").append(formatLineNumbers(added)).append('\n');
        }
        if (!deleted.isEmpty()) {
            section.append("删除行号（旧文件）：").append(formatLineNumbers(deleted)).append('\n');
        }
    }

    private static String formatLineNumbers(List<Integer> lines) {
        StringBuilder result = new StringBuilder();
        int limit = Math.min(lines.size(), 40);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append('L').append(lines.get(i));
        }
        if (lines.size() > limit) {
            result.append(" …（共 ").append(lines.size()).append(" 行）");
        }
        return result.toString();
    }

    private static List<int[]> hunkRanges(List<LineOp> ops) {
        List<Integer> changes = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).kind != Kind.EQUAL) {
                changes.add(i);
            }
        }
        List<int[]> ranges = new ArrayList<>();
        if (changes.isEmpty()) {
            return ranges;
        }

        int rangeStart = Math.max(0, changes.get(0) - CONTEXT_LINES);
        int rangeEnd = Math.min(ops.size(), changes.get(0) + CONTEXT_LINES + 1);
        for (int index = 1; index < changes.size(); index++) {
            int change = changes.get(index);
            int nextStart = Math.max(0, change - CONTEXT_LINES);
            int nextEnd = Math.min(ops.size(), change + CONTEXT_LINES + 1);
            if (nextStart <= rangeEnd) {
                rangeEnd = Math.max(rangeEnd, nextEnd);
            } else {
                ranges.add(new int[]{rangeStart, rangeEnd});
                rangeStart = nextStart;
                rangeEnd = nextEnd;
            }
        }
        ranges.add(new int[]{rangeStart, rangeEnd});
        return ranges;
    }

    private static List<LineOp> buildOps(ChangeKind kind, List<String> before, List<String> after) {
        if (kind == ChangeKind.ADDED || before == null) {
            List<RawOp> raw = new ArrayList<>();
            for (String line : after) {
                raw.add(new RawOp(Kind.INSERT, line));
            }
            return numberLines(raw);
        }
        if (kind == ChangeKind.DELETED || after == null) {
            List<RawOp> raw = new ArrayList<>();
            for (String line : before) {
                raw.add(new RawOp(Kind.DELETE, line));
            }
            return numberLines(raw);
        }

        int prefix = 0;
        int maxPrefix = Math.min(before.size(), after.size());
        while (prefix < maxPrefix && before.get(prefix).equals(after.get(prefix))) {
            prefix++;
        }

        int suffix = 0;
        while (suffix < Math.min(before.size(), after.size()) - prefix
                && before.get(before.size() - 1 - suffix).equals(after.get(after.size() - 1 - suffix))) {
            suffix++;
        }

        int beforeMiddleEnd = before.size() - suffix;
        int afterMiddleEnd = after.size() - suffix;
        int beforeMiddleSize = beforeMiddleEnd - prefix;
        int afterMiddleSize = afterMiddleEnd - prefix;

        List<RawOp> raw = new ArrayList<>(before.size() + after.size());
        for (int i = 0; i < prefix; i++) {
            raw.add(new RawOp(Kind.EQUAL, before.get(i)));
        }

        if ((long) beforeMiddleSize * afterMiddleSize <= MAX_DP_CELLS) {
            appendLcs(raw, before, prefix, beforeMiddleEnd, after, prefix, afterMiddleEnd);
        } else {
            // Pathological or huge files: fall back to a deterministic replace block.
            for (int i = prefix; i < beforeMiddleEnd; i++) {
                raw.add(new RawOp(Kind.DELETE, before.get(i)));
            }
            for (int j = prefix; j < afterMiddleEnd; j++) {
                raw.add(new RawOp(Kind.INSERT, after.get(j)));
            }
        }

        for (int i = beforeMiddleEnd; i < before.size(); i++) {
            raw.add(new RawOp(Kind.EQUAL, before.get(i)));
        }

        return numberLines(raw);
    }

    private static void appendLcs(List<RawOp> raw,
                                  List<String> before, int beforeStart, int beforeEnd,
                                  List<String> after, int afterStart, int afterEnd) {
        int n = beforeEnd - beforeStart;
        int m = afterEnd - afterStart;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (before.get(beforeStart + i).equals(after.get(afterStart + j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (before.get(beforeStart + i).equals(after.get(afterStart + j))) {
                raw.add(new RawOp(Kind.EQUAL, before.get(beforeStart + i)));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                raw.add(new RawOp(Kind.DELETE, before.get(beforeStart + i)));
                i++;
            } else {
                raw.add(new RawOp(Kind.INSERT, after.get(afterStart + j)));
                j++;
            }
        }
        while (i < n) {
            raw.add(new RawOp(Kind.DELETE, before.get(beforeStart + i)));
            i++;
        }
        while (j < m) {
            raw.add(new RawOp(Kind.INSERT, after.get(afterStart + j)));
            j++;
        }
    }

    private static List<LineOp> numberLines(List<RawOp> raw) {
        List<LineOp> result = new ArrayList<>(raw.size());
        int oldLine = 1;
        int newLine = 1;
        for (RawOp op : raw) {
            int oldNumber = 0;
            int newNumber = 0;
            if (op.kind == Kind.EQUAL) {
                oldNumber = oldLine++;
                newNumber = newLine++;
            } else if (op.kind == Kind.DELETE) {
                oldNumber = oldLine++;
            } else {
                newNumber = newLine++;
            }
            result.add(new LineOp(op.kind, op.text, oldNumber, newNumber));
        }
        return result;
    }

    static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>(Arrays.asList(normalized.split("\n", -1)));
        if (normalized.endsWith("\n") && !lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String safeMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private enum Kind {
        EQUAL(" "), DELETE("-"), INSERT("+");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }

    private record RawOp(Kind kind, String text) {
    }

    private record LineOp(Kind kind, String text, int oldLine, int newLine) {
    }
}
