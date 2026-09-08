package com.learning.platform.ai.provider.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.AiCancellation;
import com.learning.platform.ai.domain.AiProviderMessage;
import com.learning.platform.ai.domain.AiProviderRequest;
import com.learning.platform.ai.domain.AiUsage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OllamaProviderTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void rejectsStreamThatEndsWithoutDoneFrame() throws Exception {
        startServer("{\"message\":{\"content\":\"partial\"},\"done\":false}\n");
        OllamaProvider provider = provider();
        StringBuilder chunks = new StringBuilder();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.chatStream(request(), chunks::append, new AiCancellation()));

        assertEquals("partial", chunks.toString());
        assertEquals("Ollama stream ended without a done=true completion frame", error.getMessage());
    }

    @Test
    void acceptsStreamWithDoneFrameAndReturnsUsage() throws Exception {
        startServer("{\"message\":{\"content\":\"answer\"},\"done\":false}\n"
                + "{\"message\":{\"content\":\"\"},\"done\":true,\"prompt_eval_count\":2,"
                + "\"eval_count\":3,\"load_duration\":1000000,\"total_duration\":4000000,"
                + "\"eval_duration\":3000000000}\n");
        OllamaProvider provider = provider();
        StringBuilder chunks = new StringBuilder();

        AiUsage usage = provider.chatStream(request(), chunks::append, new AiCancellation());

        assertEquals("answer", chunks.toString());
        assertEquals(2, usage.getPromptTokens());
        assertEquals(3, usage.getOutputTokens());
        assertEquals(1, usage.getLoadDurationMs());
        assertEquals(4, usage.getTotalDurationMs());
        assertEquals(1D, usage.getTokensPerSecond());
    }

    @Test
    void supportsNonStreamingToolTurns() throws Exception {
        startServer("{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"\","
                + "\"tool_calls\":[{\"function\":{\"name\":\"get_learning_profile\",\"arguments\":{}}}]}}");
        OllamaProvider provider = provider();
        java.util.List<java.util.Map<String, Object>> messages = new ArrayList<java.util.Map<String, Object>>();
        messages.add(new java.util.LinkedHashMap<String, Object>(java.util.Map.of("role", "user", "content", "分析")));

        com.fasterxml.jackson.databind.JsonNode result = provider.chatTurn(
                messages, Collections.singletonList(Collections.emptyMap()), false, new AiCancellation());

        assertEquals("get_learning_profile",
                result.path("message").path("tool_calls").get(0).path("function").path("name").asText());
    }

    private void startServer(String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<String>(response);
        server.createContext("/api/chat", exchange -> respond(exchange, body.get()));
        server.start();
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private OllamaProvider provider() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.getOllama().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getOllama().setResponseTimeoutMs(3000L);
        return new OllamaProvider(properties, new ObjectMapper());
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(Collections.singletonList(new AiProviderMessage("user", "question")));
    }
}
