package com.aicodereview.history;

import com.aicodereview.core.ReviewContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Stores review reports locally, outside the project tree. The latest report is
 * still written to CODE_REVIEW.md; history keeps one Markdown file per run.
 */
public final class ReviewHistoryService {

    private static final int MAX_ENTRIES = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<List<ReviewHistoryEntry>>() {
    }.getType();
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Project project;
    private final Path directory;
    private final Path indexFile;

    public ReviewHistoryService(@NotNull Project project) {
        this.project = project;
        this.directory = historyRoot().resolve(projectKey(project));
        this.indexFile = directory.resolve("index.json");
    }

    public static ReviewHistoryService getInstance(@NotNull Project project) {
        return project.getService(ReviewHistoryService.class);
    }

    public Path getDirectory() {
        return directory;
    }

    public synchronized List<ReviewHistoryEntry> list() {
        ensureDirectory();
        if (!Files.isRegularFile(indexFile)) {
            return new ArrayList<>();
        }
        try {
            String text = Files.readString(indexFile, StandardCharsets.UTF_8);
            List<ReviewHistoryEntry> entries = GSON.fromJson(text, INDEX_TYPE);
            if (entries == null) {
                return new ArrayList<>();
            }
            entries.sort(Comparator.comparingLong((ReviewHistoryEntry e) -> e.timestamp).reversed());
            return entries;
        } catch (IOException | RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    public synchronized Path save(@NotNull ReviewContext context,
                                  @NotNull String model,
                                  int fileCount,
                                  @NotNull String report) throws IOException {
        ensureDirectory();
        String id = UUID.randomUUID().toString();
        String stamp = LocalDateTime.now().format(FILE_TIME);
        String fileName = stamp + "-" + id.substring(0, 8) + ".md";
        Path reportFile = directory.resolve(fileName);
        Files.writeString(reportFile, report, StandardCharsets.UTF_8);

        List<ReviewHistoryEntry> entries = list();
        entries.add(0, new ReviewHistoryEntry(
                id, System.currentTimeMillis(), context.display(), model, fileCount, fileName));
        while (entries.size() > MAX_ENTRIES) {
            ReviewHistoryEntry removed = entries.remove(entries.size() - 1);
            deleteReportFile(removed);
        }
        writeIndex(entries);
        return reportFile;
    }

    public synchronized String read(@NotNull ReviewHistoryEntry entry) throws IOException {
        Path file = resolveReport(entry);
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("Review report not found: " + entry.reportFile);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public synchronized Path reportPath(@NotNull ReviewHistoryEntry entry) {
        return resolveReport(entry);
    }

    public synchronized void delete(@NotNull ReviewHistoryEntry entry) throws IOException {
        List<ReviewHistoryEntry> entries = list();
        entries.removeIf(item -> entry.id != null && entry.id.equals(item.id));
        deleteReportFile(entry);
        writeIndex(entries);
    }

    public synchronized void clear() throws IOException {
        List<ReviewHistoryEntry> entries = list();
        for (ReviewHistoryEntry entry : entries) {
            deleteReportFile(entry);
        }
        Files.deleteIfExists(indexFile);
    }

    private Path resolveReport(ReviewHistoryEntry entry) {
        if (entry == null || entry.reportFile == null || entry.reportFile.contains("/")
                || entry.reportFile.contains("\\")) {
            return null;
        }
        Path resolved = directory.resolve(entry.reportFile).normalize();
        return resolved.startsWith(directory) ? resolved : null;
    }

    private void deleteReportFile(ReviewHistoryEntry entry) {
        Path file = resolveReport(entry);
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Best effort cleanup.
            }
        }
    }

    private void writeIndex(List<ReviewHistoryEntry> entries) throws IOException {
        ensureDirectory();
        Files.writeString(indexFile, GSON.toJson(entries), StandardCharsets.UTF_8);
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException ignored) {
            // save() will fail with a meaningful error if the directory is unavailable.
        }
    }

    private static Path historyRoot() {
        String systemPath = PathManager.getSystemPath();
        if (systemPath == null || systemPath.isBlank()) {
            systemPath = System.getProperty("java.io.tmpdir");
        }
        return Path.of(systemPath, "ai-git-code-review", "history");
    }

    private static String projectKey(Project project) {
        String basePath = project.getBasePath();
        String source = basePath == null ? project.getName() : basePath;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                result.append(String.format("%02x", hash[i]));
            }
            return result.toString();
        } catch (Exception e) {
            return Integer.toHexString(source.hashCode());
        }
    }
}
