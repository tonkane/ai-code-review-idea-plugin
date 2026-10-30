package com.aicodereview.core;

import java.util.List;

public final class PromptRenderer {

    private PromptRenderer() {
    }

    public static String render(String template,
                                String projectName,
                                ReviewContext scope,
                                List<String> files,
                                String diff,
                                String language,
                                String date) {
        StringBuilder fileList = new StringBuilder();
        for (String file : files) {
            fileList.append("- `").append(file).append("`\n");
        }
        if (files.isEmpty()) {
            fileList.append("- （无文件）\n");
        }

        String prompt = template == null ? "" : template;
        prompt = prompt.replace("{{PROJECT_NAME}}", projectName);
        prompt = prompt.replace("{{SCOPE}}", scope == null ? "" : scope.display());
        prompt = prompt.replace("{{FILE_LIST}}", fileList.toString().stripTrailing());
        prompt = prompt.replace("{{LANGUAGE}}", language);
        prompt = prompt.replace("{{DATE}}", date);
        prompt = prompt.replace("{{DIFF}}", diff);

        if (template == null || !template.contains("{{SCOPE}}")) {
            prompt = prompt.stripTrailing() + "\n\nScope: " + (scope == null ? "" : scope.display());
        }
        if (template == null || !template.contains("{{DIFF}}")) {
            prompt = prompt.stripTrailing() + "\n\n## 待审查代码 / Code Changes\n" + diff;
        }
        return prompt;
    }
}
