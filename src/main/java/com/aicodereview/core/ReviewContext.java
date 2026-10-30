package com.aicodereview.core;

import com.aicodereview.config.I18n;
import org.jetbrains.annotations.Nullable;

/**
 * What is being reviewed: the pending working-tree changes or one Git commit.
 */
public record ReviewContext(String kind, @Nullable String commitHash, @Nullable String subject) {

    public static final String WORKING = "working";
    public static final String COMMIT = "commit";

    public static ReviewContext workingChanges() {
        return new ReviewContext(WORKING, null, null);
    }

    public static ReviewContext commit(String hash, @Nullable String subject) {
        return new ReviewContext(COMMIT, hash, subject == null ? "" : subject);
    }

    public boolean isCommit() {
        return COMMIT.equals(kind);
    }

    public String shortHash() {
        if (commitHash == null || commitHash.isBlank()) {
            return "";
        }
        return commitHash.length() <= 8 ? commitHash : commitHash.substring(0, 8);
    }

    public String display() {
        if (!isCommit()) {
            return I18n.t("Git 待提交变更", "Uncommitted Git changes");
        }
        String subjectText = subject == null ? "" : subject;
        return I18n.t("提交 " + shortHash() + "：" + subjectText,
                "Commit " + shortHash() + ": " + subjectText);
    }
}
