package com.aicodereview.llm;

/**
 * @param content   the assistant message
 * @param truncated true when the provider reports finish_reason=length
 * @param model     the model reported by the response, or the requested model
 * @param elapsedMs wall-clock latency of the request
 */
public record AiCompletion(String content, boolean truncated, String model, long elapsedMs) {
}
