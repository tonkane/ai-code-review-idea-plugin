package com.aicodereview.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewReportTest {

    @Test
    void stripsWholeReportFence() {
        String content = "```markdown\n## 一、审查概览\n内容\n```";
        String stripped = ReviewReport.stripOuterFence(content);

        assertTrue(stripped.startsWith("## 一、审查概览"));
        assertFalse(stripped.endsWith("```"));
    }

    @Test
    void keepsInnerFence() {
        String content = "## 一、审查概览\n```java\nint a = 1;\n```\n## 二、问题清单";
        assertEquals(content, ReviewReport.stripOuterFence(content));
    }
}
