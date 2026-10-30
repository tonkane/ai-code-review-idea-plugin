package com.aicodereview.core;

import java.util.List;

public record DiffResult(
        String text,
        List<String> reviewedFiles,
        List<String> skippedFiles,
        boolean truncated
) {
}
