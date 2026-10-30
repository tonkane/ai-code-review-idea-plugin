package com.aicodereview.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable text snapshot of one changed file.
 *
 * @param path       path relative to the project root (new path)
 * @param oldPath    old path for renames, otherwise equal to path
 * @param kind       change type
 * @param beforeText content before the change, null for a new file
 * @param afterText  content after the change, null for a deleted file
 */
public record FileSnapshot(
        @NotNull String path,
        @NotNull String oldPath,
        @NotNull ChangeKind kind,
        @Nullable String beforeText,
        @Nullable String afterText
) {
}
