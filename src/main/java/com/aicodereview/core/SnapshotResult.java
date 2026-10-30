package com.aicodereview.core;

import java.util.List;

public record SnapshotResult(List<FileSnapshot> files, List<String> warnings) {
}
