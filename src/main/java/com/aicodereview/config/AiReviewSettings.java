package com.aicodereview.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AiGitCodeReview", storages = @Storage("ai-git-code-review.xml"))
public final class AiReviewSettings implements PersistentStateComponent<AiReviewSettings.State> {

    public static final String DEFAULT_OUTPUT_FILE = "CODE_REVIEW.md";
    public static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    private State state = new State();

    public static AiReviewSettings getInstance() {
        return ApplicationManager.getApplication().getService(AiReviewSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        normalize(this.state);
    }

    public State copy() {
        State copy = new State();
        copy.endpoint = state.endpoint;
        copy.model = state.model;
        copy.apiKeyHeader = state.apiKeyHeader;
        copy.apiKeyPrefix = state.apiKeyPrefix;
        copy.extraHeaders = state.extraHeaders;
        copy.noApiKey = state.noApiKey;
        copy.uiLanguage = state.uiLanguage;
        copy.outputFile = state.outputFile;
        copy.language = state.language;
        copy.temperature = state.temperature;
        copy.maxOutputTokens = state.maxOutputTokens;
        copy.timeoutSeconds = state.timeoutSeconds;
        copy.maxDiffChars = state.maxDiffChars;
        copy.openReport = state.openReport;
        copy.promptTemplate = state.promptTemplate;
        return copy;
    }

    public void applyFrom(State source) {
        State next = new State();
        next.endpoint = source.endpoint;
        next.model = source.model;
        next.apiKeyHeader = source.apiKeyHeader;
        next.apiKeyPrefix = source.apiKeyPrefix;
        next.extraHeaders = source.extraHeaders;
        next.noApiKey = source.noApiKey;
        next.uiLanguage = source.uiLanguage;
        next.outputFile = source.outputFile;
        next.language = source.language;
        next.temperature = source.temperature;
        next.maxOutputTokens = source.maxOutputTokens;
        next.timeoutSeconds = source.timeoutSeconds;
        next.maxDiffChars = source.maxDiffChars;
        next.openReport = source.openReport;
        next.promptTemplate = source.promptTemplate;
        normalize(next);
        state = next;
    }

    public static void normalize(State value) {
        value.endpoint = trimToDefault(value.endpoint, DEFAULT_ENDPOINT);
        value.model = trimToDefault(value.model, DEFAULT_MODEL);
        value.apiKeyHeader = trimToDefault(value.apiKeyHeader, "Authorization");
        // Do not trim: the conventional prefix is "Bearer " with a trailing space.
        value.apiKeyPrefix = value.apiKeyPrefix == null ? "Bearer " : value.apiKeyPrefix;
        value.extraHeaders = value.extraHeaders == null ? "" : value.extraHeaders.trim();
        value.uiLanguage = normalizeUiLanguage(value.uiLanguage);
        value.outputFile = sanitizeFileName(value.outputFile);
        value.language = trimToDefault(value.language, "中文");
        if (Double.isNaN(value.temperature) || value.temperature < 0 || value.temperature > 2) {
            value.temperature = 0.1;
        }
        value.maxOutputTokens = clamp(value.maxOutputTokens, 256, 32768, 4096);
        value.timeoutSeconds = clamp(value.timeoutSeconds, 10, 600, 180);
        value.maxDiffChars = clamp(value.maxDiffChars, 4000, 400000, 60000);
        if (value.promptTemplate == null || value.promptTemplate.isBlank()) {
            value.promptTemplate = DefaultPrompt.forLanguage(value.uiLanguage);
        }
    }

    private static String trimToDefault(@Nullable String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The report must live directly in the project root. Strip any directory
     * component and characters that are illegal in Windows file names.
     */
    public static String normalizeUiLanguage(@Nullable String language) {
        return I18n.EN.equalsIgnoreCase(language) ? I18n.EN : I18n.ZH;
    }

    public static String sanitizeFileName(@Nullable String raw) {
        String name = raw == null ? "" : raw.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}:*?\"<>|]", "_");
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return DEFAULT_OUTPUT_FILE;
        }
        if (!name.contains(".")) {
            name = name + ".md";
        }
        return name;
    }

    public String outputFileName() {
        return sanitizeFileName(state.outputFile);
    }

    public boolean isConfigured() {
        return state.endpoint != null && !state.endpoint.isBlank()
                && state.model != null && !state.model.isBlank();
    }

    public static final class State {
        public String endpoint = DEFAULT_ENDPOINT;
        public String model = DEFAULT_MODEL;
        public String apiKeyHeader = "Authorization";
        public String apiKeyPrefix = "Bearer ";
        public String extraHeaders = "";
        public boolean noApiKey = false;
        public String uiLanguage = I18n.ZH;
        public String outputFile = DEFAULT_OUTPUT_FILE;
        public String language = "中文";
        public double temperature = 0.1;
        public int maxOutputTokens = 4096;
        public int timeoutSeconds = 180;
        public int maxDiffChars = 60000;
        public boolean openReport = true;
        public String promptTemplate = DefaultPrompt.TEMPLATE;
    }
}
