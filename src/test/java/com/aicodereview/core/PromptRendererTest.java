package com.aicodereview.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptRendererTest {

    @Test
    void replacesAllPlaceholders() {
        String prompt = PromptRenderer.render(
                "project={{PROJECT_NAME}}\nscope={{SCOPE}}\nfiles=\n{{FILE_LIST}}\nlang={{LANGUAGE}}\ndate={{DATE}}\n{{DIFF}}",
                "demo", ReviewContext.workingChanges(), List.of("a.java", "b.java"),
                "DIFF-CONTENT", "中文", "2026-01-01");

        assertTrue(prompt.contains("project=demo"));
        assertTrue(prompt.contains("scope="));
        assertTrue(prompt.contains("- `a.java`"));
        assertTrue(prompt.contains("- `b.java`"));
        assertTrue(prompt.contains("lang=中文"));
        assertTrue(prompt.contains("date=2026-01-01"));
        assertTrue(prompt.contains("DIFF-CONTENT"));
        assertFalse(prompt.contains("{{"));
    }

    @Test
    void appendsDiffWhenPlaceholderMissing() {
        String prompt = PromptRenderer.render("Review this.", "demo", ReviewContext.workingChanges(),
                List.of("a.java"), "DIFF-CONTENT", "中文", "2026-01-01");

        assertTrue(prompt.startsWith("Review this."));
        assertTrue(prompt.contains("## 待审查代码 / Code Changes"));
        assertTrue(prompt.contains("DIFF-CONTENT"));
    }
}
