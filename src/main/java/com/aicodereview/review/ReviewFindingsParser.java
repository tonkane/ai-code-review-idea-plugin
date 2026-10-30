package com.aicodereview.review;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReviewFindingsParser {

    private static final Pattern LOCATION = Pattern.compile("`?([^\\s|`]+):(\\d+)`?");

    private ReviewFindingsParser() {
    }

    public static List<ReviewFinding> parse(String markdown) {
        List<ReviewFinding> findings = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return findings;
        }
        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine.trim();
            if (!line.startsWith("|")) {
                continue;
            }
            String[] rawCells = line.split("\\|", -1);
            List<String> cells = new ArrayList<>();
            for (String cell : rawCells) {
                String value = cell.trim();
                if (!value.isEmpty()) {
                    cells.add(value);
                }
            }
            if (cells.size() < 6) {
                continue;
            }
            String first = cells.get(0);
            if ("#".equals(first) || "序号".equals(first) || first.startsWith("---")
                    || first.startsWith(":")) {
                continue;
            }
            for (int i = 0; i < cells.size(); i++) {
                Matcher matcher = LOCATION.matcher(cells.get(i));
                if (!matcher.find()) {
                    continue;
                }
                String path = matcher.group(1).replace('\\', '/')
                        .replace("**", "").replace("`", "");
                while (path.startsWith("./")) {
                    path = path.substring(2);
                }
                while (path.startsWith("(") || path.startsWith("[") || path.startsWith("`")) {
                    path = path.substring(1);
                }
                int lineNumber;
                try {
                    lineNumber = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException e) {
                    continue;
                }
                String severity = index(cells, 1);
                String dimension = index(cells, 2);
                String description = index(cells, 4);
                String suggestion = index(cells, 5);
                findings.add(new ReviewFinding(severity, dimension, cells.get(i),
                        description, suggestion, path, lineNumber));
                break;
            }
        }
        return findings;
    }

    private static String index(List<String> cells, int index) {
        return index < cells.size() ? cells.get(index) : "";
    }
}
