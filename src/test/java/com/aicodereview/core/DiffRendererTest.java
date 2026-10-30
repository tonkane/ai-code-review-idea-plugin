package com.aicodereview.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffRendererTest {

    @Test
    void rendersAddedFile() {
        FileSnapshot file = new FileSnapshot("src/New.java", "src/New.java", ChangeKind.ADDED,
                null, "package a;\n\nclass New {}\n");
        String diff = DiffRenderer.renderFile(file);

        assertTrue(diff.contains("new file mode 100644"));
        assertTrue(diff.contains("--- /dev/null"));
        assertTrue(diff.contains("+++ b/src/New.java"));
        assertTrue(diff.contains("@@ -0,0 +1,3 @@"));
        assertTrue(diff.contains("+package a;"));
    }

    @Test
    void rendersDeletedFile() {
        FileSnapshot file = new FileSnapshot("src/Old.java", "src/Old.java", ChangeKind.DELETED,
                "a\nb\n", null);
        String diff = DiffRenderer.renderFile(file);

        assertTrue(diff.contains("deleted file mode 100644"));
        assertTrue(diff.contains("--- a/src/Old.java"));
        assertTrue(diff.contains("+++ /dev/null"));
        assertTrue(diff.contains("@@ -1,2 +0,0 @@"));
        assertTrue(diff.contains("-a"));
        assertTrue(diff.contains("-b"));
    }

    @Test
    void rendersModificationWithContext() {
        FileSnapshot file = new FileSnapshot("src/A.java", "src/A.java", ChangeKind.MODIFIED,
                "a\nb\nc\nd\n", "a\nb\nX\nd\n");
        String diff = DiffRenderer.renderFile(file);

        assertTrue(diff.contains("@@ -1,4 +1,4 @@"));
        assertTrue(diff.contains(" b\n"));
        assertTrue(diff.contains("-c\n"));
        assertTrue(diff.contains("+X\n"));
        assertTrue(diff.contains(" d\n"));
        assertTrue(diff.contains("变更行号（新文件）：L3"));
        assertTrue(diff.contains("删除行号（旧文件）：L3"));
    }

    @Test
    void handlesMovedFileWithModification() {
        FileSnapshot file = new FileSnapshot("new/Name.java", "old/Name.java", ChangeKind.MOVED,
                "a\n", "b\n");
        String diff = DiffRenderer.renderFile(file);

        assertTrue(diff.contains("rename from old/Name.java"));
        assertTrue(diff.contains("rename to new/Name.java"));
        assertTrue(diff.contains("--- a/old/Name.java"));
        assertTrue(diff.contains("+++ b/new/Name.java"));
    }

    @Test
    void skipsPureRename() {
        FileSnapshot file = new FileSnapshot("new/Name.java", "old/Name.java", ChangeKind.MOVED,
                "same\n", "same\n");
        assertEquals(null, DiffRenderer.renderFile(file));
    }

    @Test
    void respectsCharacterBudget() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            before.append("old line ").append(i).append('\n');
            after.append("new line ").append(i).append('\n');
        }
        FileSnapshot file = new FileSnapshot("big.txt", "big.txt", ChangeKind.MODIFIED,
                before.toString(), after.toString());
        DiffResult result = DiffRenderer.render(List.of(file), 1200);

        assertTrue(result.truncated());
        assertEquals(List.of("big.txt"), result.reviewedFiles());
        assertTrue(result.text().length() <= 1200);
        assertTrue(result.text().contains("已截断"));
    }

    @Test
    void emptyDiffIsSkipped() {
        FileSnapshot file = new FileSnapshot("a.txt", "a.txt", ChangeKind.MODIFIED,
                "same\n", "same\n");
        DiffResult result = DiffRenderer.render(List.of(file), 10000);

        assertFalse(result.truncated());
        assertTrue(result.reviewedFiles().isEmpty());
        assertTrue(result.skippedFiles().get(0).contains("无文本变更"));
    }
}
