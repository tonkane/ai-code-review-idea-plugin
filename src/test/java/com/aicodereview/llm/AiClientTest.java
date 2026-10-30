package com.aicodereview.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void resolvesBareHostAndBaseUrls() {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                AiClient.resolveEndpoint("https://api.deepseek.com"));
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                AiClient.resolveEndpoint("https://api.deepseek.com/v1/"));
        assertEquals("https://api.openai.com/v1/chat/completions",
                AiClient.resolveEndpoint("https://api.openai.com/v1/chat/completions"));
        assertEquals("https://example.com/azure?api-version=1",
                AiClient.resolveEndpoint("https://example.com/azure?api-version=1"));
    }

    @Test
    void parsesStringAndArrayContent() throws Exception {
        AiCompletion completion = AiClient.parseResponse("""
                {"model":"m","choices":[{"message":{"content":"# report"},"finish_reason":"stop"}]}
                """, "fallback", 12);
        assertEquals("# report", completion.content());
        assertEquals("m", completion.model());
        assertEquals(false, completion.truncated());

        AiCompletion array = AiClient.parseResponse("""
                {"choices":[{"message":{"content":[{"type":"text","text":"part1"},{"type":"text","text":"part2"}]},"finish_reason":"length"}]}
                """, "fallback", 1);
        assertEquals("part1part2", array.content());
        assertTrue(array.truncated());
        assertEquals("fallback", array.model());
    }

    @Test
    void sendsAuthAndParsesSuccessfulResponse() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        startServer(exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"model":"test-model","choices":[{"message":{"content":"OK"},"finish_reason":"stop"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        AiConnection connection = new AiConnection(endpoint(), "test-key", "Authorization",
                "Bearer ", "test-model", "", 30, 1024, 0.2);
        AiCompletion completion = new AiClient().complete(connection, "system", "user", null);

        assertEquals("Bearer test-key", auth.get());
        assertTrue(body.get().contains("\"model\":\"test-model\""));
        assertTrue(body.get().contains("\"stream\":false"));
        assertTrue(body.get().contains("\"max_tokens\":1024"));
        assertEquals("OK", completion.content());
    }

    @Test
    void reportsHttpErrorWithoutLeakingKey() throws Exception {
        startServer(exchange -> {
            byte[] response = "{\"error\":{\"message\":\"invalid key\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        AiConnection connection = new AiConnection(endpoint(), "super-secret-key", "Authorization",
                "Bearer ", "test-model", "", 30, 1024, 0.0);
        AiException error = assertThrows(AiException.class,
                () -> new AiClient().complete(connection, "system", "user", null));

        assertTrue(error.getMessage().contains("401"));
        assertTrue(error.getMessage().contains("invalid key"));
        assertTrue(!error.getMessage().contains("super-secret-key"));
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", handler);
        server.start();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
    }
}
