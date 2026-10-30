package com.aicodereview.history;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ReviewHistoryEntry {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public String id;
    public long timestamp;
    public String scope;
    public String model;
    public int fileCount;
    public String reportFile;

    public ReviewHistoryEntry() {
    }

    public ReviewHistoryEntry(String id, long timestamp, String scope, String model,
                              int fileCount, String reportFile) {
        this.id = id;
        this.timestamp = timestamp;
        this.scope = scope;
        this.model = model;
        this.fileCount = fileCount;
        this.reportFile = reportFile;
    }

    public String displayTime() {
        return DISPLAY_TIME.format(Instant.ofEpochMilli(timestamp));
    }
}
