package com.aicodereview.review;

public record ReviewFinding(
        String severity,
        String dimension,
        String location,
        String description,
        String suggestion,
        String path,
        int line
) {
}
