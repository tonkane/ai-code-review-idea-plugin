package com.aicodereview.llm;

import com.aicodereview.config.AiReviewSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable connection settings used for one request.
 */
public record AiConnection(
        String endpoint,
        @Nullable String apiKey,
        String apiKeyHeader,
        String apiKeyPrefix,
        String model,
        String extraHeaders,
        int timeoutSeconds,
        int maxOutputTokens,
        double temperature
) {

    public static AiConnection from(AiReviewSettings.State state, @Nullable String apiKey) {
        return new AiConnection(
                state.endpoint,
                apiKey,
                state.apiKeyHeader,
                state.apiKeyPrefix,
                state.model,
                state.extraHeaders,
                state.timeoutSeconds,
                state.maxOutputTokens,
                state.temperature);
    }
}
