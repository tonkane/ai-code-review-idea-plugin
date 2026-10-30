package com.aicodereview.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * A small OpenAI-compatible chat-completions client. It intentionally supports
 * only the broadly implemented subset: /chat/completions, Bearer auth, and
 * non-streaming JSON responses.
 */
public final class AiClient {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_ERROR_BODY = 1200;

    public AiClient() {
    }

    public AiCompletion complete(@NotNull AiConnection connection,
                                 @NotNull String systemPrompt,
                                 @NotNull String userPrompt,
                                 @Nullable ProgressIndicator indicator) throws AiException {
        return request(connection, systemPrompt, userPrompt,
                connection.maxOutputTokens(), connection.temperature(), indicator);
    }

    /**
     * Sends a deliberately tiny request to verify URL, credentials and model.
     */
    public AiCompletion testConnection(@NotNull AiConnection connection) throws AiException {
        return request(connection,
                "You are a connectivity checker. Follow the user's instruction exactly.",
                "Reply with exactly: OK",
                16, 0.0, null);
    }

    private AiCompletion request(@NotNull AiConnection connection,
                                 @NotNull String systemPrompt,
                                 @NotNull String userPrompt,
                                 int maxOutputTokens,
                                 double temperature,
                                 @Nullable ProgressIndicator indicator) throws AiException {
        String endpoint = connection.endpoint() == null ? "" : connection.endpoint().trim();
        if (endpoint.isEmpty()) {
            throw new AiException("接口地址为空，请先在设置中配置 API 地址。");
        }
        if (connection.model() == null || connection.model().isBlank()) {
            throw new AiException("模型名称为空，请先在设置中配置模型。");
        }

        URI uri = toUri(resolveEndpoint(endpoint));
        checkCanceled(indicator);

        JsonObject body = new JsonObject();
        body.addProperty("model", connection.model());
        body.addProperty("temperature", temperature);
        body.addProperty("stream", false);
        if (maxOutputTokens > 0) {
            body.addProperty("max_tokens", maxOutputTokens);
        }

        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(5, connection.timeoutSeconds())))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));

        String apiKey = connection.apiKey();
        String keyHeader = connection.apiKeyHeader() == null ? "" : connection.apiKeyHeader().trim();
        if (apiKey != null && !apiKey.isBlank() && !keyHeader.isEmpty()) {
            String prefix = connection.apiKeyPrefix() == null ? "" : connection.apiKeyPrefix();
            builder.header(keyHeader, prefix + apiKey);
        }

        applyExtraHeaders(builder, connection.extraHeaders());

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(Math.min(20, Math.max(5, connection.timeoutSeconds()))))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        if (indicator != null) {
            indicator.setText("正在请求 AI 接口…");
            indicator.setText2(uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
            indicator.setIndeterminate(true);
        }

        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new AiException("请求超时（" + connection.timeoutSeconds()
                    + " 秒）。可以调大超时时间，或检查网络/代理。", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException(e);
        } catch (IOException e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new AiException("无法连接 AI 接口：" + detail
                    + "\n请检查接口地址、端口、网络和代理设置。", e);
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        checkCanceled(indicator);

        int status = response.statusCode();
        String responseBody = response.body() == null ? "" : response.body();
        if (status < 200 || status >= 300) {
            throw new AiException(buildHttpError(status, responseBody, connection.apiKey()));
        }

        return parseResponse(responseBody, connection.model(), elapsedMs);
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static void applyExtraHeaders(HttpRequest.Builder builder, @Nullable String extraHeaders)
            throws AiException {
        if (extraHeaders == null || extraHeaders.isBlank()) {
            return;
        }
        for (String rawLine : extraHeaders.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new AiException("额外请求头格式错误：“" + line + "”，应为 Header: Value。");
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (name.equalsIgnoreCase("Content-Length") || name.equalsIgnoreCase("Host")) {
                throw new AiException("额外请求头不允许设置 " + name + "。");
            }
            try {
                builder.header(name, value);
            } catch (IllegalArgumentException e) {
                throw new AiException("额外请求头不合法：" + name);
            }
        }
    }

    private static URI toUri(String endpoint) throws AiException {
        try {
            URI uri = new URI(endpoint);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                throw new AiException("接口地址必须是完整的 http:// 或 https:// URL。");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new AiException("接口地址格式不正确：" + endpoint);
        }
    }

    /**
     * Accepts a full endpoint, a base URL ending in /v1, or a bare host/base path.
     * A URL with a query string is assumed to be complete and is left untouched.
     */
    static String resolveEndpoint(String raw) {
        String endpoint = raw.trim();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (endpoint.contains("?")) {
            return endpoint;
        }
        String lower = endpoint.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/chat/completions")) {
            return endpoint;
        }
        if (lower.matches(".*/v\\d+$")) {
            return endpoint + "/chat/completions";
        }
        return endpoint + "/v1/chat/completions";
    }

    static AiCompletion parseResponse(String body, String fallbackModel, long elapsedMs)
            throws AiException {
        if (body.isBlank()) {
            throw new AiException("AI 接口返回了空响应。");
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                throw new AiException("AI 接口返回错误：" + errorMessage(root.get("error")));
            }

            JsonArray choices = root.has("choices") && root.get("choices").isJsonArray()
                    ? root.getAsJsonArray("choices") : null;
            if (choices == null || choices.size() == 0) {
                throw new AiException("AI 接口响应中没有 choices，无法读取报告内容。");
            }

            JsonObject first = choices.get(0).getAsJsonObject();
            String content = "";
            if (first.has("message") && first.get("message").isJsonObject()) {
                JsonObject message = first.getAsJsonObject("message");
                content = contentAsText(message.get("content"));
                if (content.isBlank() && message.has("refusal")) {
                    content = contentAsText(message.get("refusal"));
                }
            }
            if (content.isBlank() && first.has("text")) {
                content = contentAsText(first.get("text"));
            }
            if (content.isBlank() && root.has("output_text")) {
                content = contentAsText(root.get("output_text"));
            }
            if (content.isBlank()) {
                throw new AiException("AI 接口返回的内容为空。可能是模型不支持该接口，或触发了内容过滤。");
            }

            String finishReason = first.has("finish_reason") && !first.get("finish_reason").isJsonNull()
                    ? first.get("finish_reason").getAsString() : "";
            boolean truncated = "length".equalsIgnoreCase(finishReason);
            String model = root.has("model") && !root.get("model").isJsonNull()
                    ? root.get("model").getAsString() : fallbackModel;
            return new AiCompletion(content.trim(), truncated, model, elapsedMs);
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            String snippet = body.length() > 300 ? body.substring(0, 300) + "…" : body;
            throw new AiException("无法解析 AI 接口响应：" + snippet, e);
        }
    }

    private static String contentAsText(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonElement part : element.getAsJsonArray()) {
                if (part == null || part.isJsonNull()) {
                    continue;
                }
                if (part.isJsonPrimitive()) {
                    result.append(part.getAsString());
                } else if (part.isJsonObject()) {
                    JsonObject object = part.getAsJsonObject();
                    if (object.has("text")) {
                        result.append(contentAsText(object.get("text")));
                    } else if (object.has("content")) {
                        result.append(contentAsText(object.get("content")));
                    }
                }
            }
            return result.toString();
        }
        return element.toString();
    }

    private static String buildHttpError(int status, String body, @Nullable String apiKey) {
        String reason;
        if (status == 400) {
            reason = "请求参数或上下文长度有误";
        } else if (status == 401) {
            reason = "API Key 无效或未授权";
        } else if (status == 403) {
            reason = "没有访问该模型/接口的权限";
        } else if (status == 404) {
            reason = "接口地址不存在，或模型名称不正确";
        } else if (status == 408 || status == 504) {
            reason = "服务端处理超时";
        } else if (status == 413) {
            reason = "请求体过大，请调小最大 diff 字符数";
        } else if (status == 429) {
            reason = "请求过于频繁或额度不足";
        } else if (status >= 500) {
            reason = "AI 服务端错误";
        } else {
            reason = "请求失败";
        }

        String detail = extractErrorDetail(body);
        if (apiKey != null && !apiKey.isBlank()) {
            detail = detail.replace(apiKey, "***");
        }
        if (detail.isBlank()) {
            return "HTTP " + status + "：" + reason + "。";
        }
        return "HTTP " + status + "：" + reason + "。\n服务端返回：" + detail;
    }

    private static String extractErrorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                return truncate(errorMessage(root.get("error")));
            }
            if (root.has("message")) {
                return truncate(root.get("message").getAsString());
            }
        } catch (RuntimeException ignored) {
            // Fall through to the raw body.
        }
        return truncate(body);
    }

    private static String errorMessage(JsonElement error) {
        if (error == null || error.isJsonNull()) {
            return "";
        }
        if (error.isJsonObject()) {
            JsonObject object = error.getAsJsonObject();
            if (object.has("message")) {
                return contentAsText(object.get("message"));
            }
            return object.toString();
        }
        return contentAsText(error);
    }

    private static String truncate(String text) {
        String normalized = text.strip();
        if (normalized.length() > MAX_ERROR_BODY) {
            return normalized.substring(0, MAX_ERROR_BODY) + "…";
        }
        return normalized;
    }

    private static void checkCanceled(@Nullable ProgressIndicator indicator) {
        if (indicator != null && indicator.isCanceled()) {
            throw new ProcessCanceledException();
        }
    }
}
