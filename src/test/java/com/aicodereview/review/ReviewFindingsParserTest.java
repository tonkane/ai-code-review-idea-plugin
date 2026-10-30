package com.aicodereview.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewFindingsParserTest {

    @Test
    void parsesChineseFindingsTable() {
        String report = """
                | 序号 | 严重级别 | 维度 | 位置 | 问题描述 | 修改建议 |
                | --- | --- | --- | --- | --- | --- |
                | 1 | 严重 | 安全 | `src/main/A.java:42` | 缺少鉴权 | 增加权限校验 |
                | 2 | 建议 | 性能 | src/B.java:7（删除行） | 循环查询 | 改为批量查询 |
                """;

        List<ReviewFinding> findings = ReviewFindingsParser.parse(report);

        assertEquals(2, findings.size());
        assertEquals("src/main/A.java", findings.get(0).path());
        assertEquals(42, findings.get(0).line());
        assertEquals("严重", findings.get(0).severity());
        assertEquals("src/B.java", findings.get(1).path());
        assertEquals(7, findings.get(1).line());
    }

    @Test
    void parsesEnglishFindingsTable() {
        String report = """
                | # | Severity | Dimension | Location | Issue | Recommendation |
                | --- | --- | --- | --- | --- | --- |
                | 1 | Critical | Security | src/A.kt:10 | Missing check | Add a check |
                """;

        List<ReviewFinding> findings = ReviewFindingsParser.parse(report);

        assertEquals(1, findings.size());
        assertEquals("Critical", findings.get(0).severity());
        assertEquals("src/A.kt", findings.get(0).path());
        assertEquals(10, findings.get(0).line());
    }
}
