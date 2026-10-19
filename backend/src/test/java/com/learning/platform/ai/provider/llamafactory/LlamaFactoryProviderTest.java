package com.learning.platform.ai.provider.llamafactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.*;
import com.learning.platform.ai.provider.ollama.OllamaProvider;
import com.learning.platform.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LlamaFactoryProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> received = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;
    private LlamaFactoryProvider provider;

    @AfterEach void close() {
        if (provider != null) provider.close();
        if (server != null) server.stop(0);
        executor.shutdownNow();
    }

    @Test void selectsExactlyOneProviderAndKeepsOllamaAsDefault() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(AiProperties.class, AiProperties::new).withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(OllamaProvider.class, LlamaFactoryProvider.class);
        runner.run(context -> assertEquals(OllamaProvider.class, context.getBean(AiModelProvider.class).getClass()));
        runner.withPropertyValues("app.ai.provider=llamafactory").run(context ->
                assertEquals(LlamaFactoryProvider.class, context.getBean(AiModelProvider.class).getClass()));
    }

    @Test void consumesSseAndDoesNotExposeReasoningOrInventUsage() throws Exception {
        start(200, ": keepalive\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"你好\",\"reasoning_content\":\"secret\"}}]}\r\n\r\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n");
        StringBuilder output = new StringBuilder();
        AiUsage usage = provider.chatStream(request(), output::append, new AiCancellation());
        assertEquals("你好", output.toString());
        assertEquals(-1, usage.getOutputTokens());
        assertTrue(received.get().path("stream").asBoolean());
        assertEquals(600, received.get().path("max_tokens").asInt());
        assertEquals("Bearer test-key", authorization.get());
    }

    @Test void readsUsageEventAfterFinishFrame() throws Exception {
        start(200, "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":3}}\n\n"
                + "data: [DONE]\n\n");
        AiUsage usage = provider.chatStream(request(), s -> {}, new AiCancellation());
        assertEquals(10, usage.getPromptTokens());
        assertEquals(3, usage.getOutputTokens());
    }

    @Test void rejectsPrematureEofAndLengthLimitedCompletion() throws Exception {
        start(200, "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n");
        assertThrows(IllegalStateException.class, () -> provider.chatStream(request(), s -> {}, new AiCancellation()));
        server.stop(0); provider.close();
        start(200, "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n");
        assertThrows(IllegalStateException.class, () -> provider.chatStream(request(), s -> {}, new AiCancellation()));
    }

    @Test void preservesToolCallIdsAndStringArguments() throws Exception {
        start(200, "{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_learning_profile\",\"arguments\":\"{}\"}}]}}]}");
        JsonNode response = provider.chatTurn(List.of(Map.of("role", "user", "content", "诊断")),
                List.of(Map.of("type", "function", "function", Map.of("name", "get_learning_profile"))), false, new AiCancellation());
        assertEquals("call_1", response.at("/message/tool_calls/0/id").asText());
        assertEquals("{}", response.at("/message/tool_calls/0/function/arguments").asText());
        assertTrue(received.get().has("tools"));
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "诊断"));
        messages.add(mapper.convertValue(response.path("message"), Map.class));
        messages.add(Map.of("role", "tool", "tool_call_id", "call_1", "tool_name", "get_learning_profile", "content", "{}"));
        provider.chatTurn(messages, List.of(), false, new AiCancellation());
        assertEquals("call_1", received.get().at("/messages/2/tool_call_id").asText());
        assertFalse(received.get().at("/messages/2").has("tool_name"));
    }

    @Test void normalizesTruncatedHistoryWithoutMutatingCaller() throws Exception {
        start(200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");
        List<Map<String, Object>> messages = List.of(Map.of("role", "system", "content", "规则"),
                Map.of("role", "assistant", "content", "orphan"), Map.of("role", "user", "content", "旧问题"),
                Map.of("role", "user", "content", "新问题"));
        provider.chatTurn(messages, List.of(), false, new AiCancellation());
        assertEquals(2, received.get().path("messages").size());
        assertEquals("旧问题\n新问题", received.get().at("/messages/1/content").asText());
        assertEquals("旧问题", messages.get(2).get("content"));
    }

    @Test void reportsModelMismatchAndNeverLeaksUpstreamErrors() throws Exception {
        start(200, "{\"data\":[{\"id\":\"other-model\"}]}");
        assertEquals("AI_MODEL_NOT_FOUND", provider.health().get("reason"));
        server.stop(0); provider.close();
        start(401, "private-prompt-and-secret");
        BusinessException error = assertThrows(BusinessException.class,
                () -> provider.chatStream(request(), s -> {}, new AiCancellation()));
        assertEquals(503, error.getCode());
        assertFalse(error.getMessage().contains("private"));
    }

    @Test void deadlineClosesBlockedStreamAndReleasesCapacity() throws Exception {
        blockedServer();
        provider = new LlamaFactoryProvider(properties(250), mapper);
        assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
                assertThrows(HttpTimeoutException.class, () -> provider.chatStream(request(), s -> {}, new AiCancellation())));
        // A second request must reach the server instead of finding a leaked permit/capacity.
        assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
                assertThrows(HttpTimeoutException.class, () -> provider.chatStream(request(), s -> {}, new AiCancellation())));
    }

    @Test void userCancellationClosesBlockedStream() throws Exception {
        blockedServer();
        provider = new LlamaFactoryProvider(properties(10000), mapper);
        AiCancellation cancellation = new AiCancellation();
        Future<?> task = executor.submit(() -> {
            try { provider.chatStream(request(), s -> {}, cancellation); fail("Cancelled stream completed"); }
            catch (Exception expected) { assertTrue(cancellation.isCancelled()); }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (received.get() == null && System.nanoTime() < deadline) Thread.sleep(10);
        assertNotNull(received.get());
        cancellation.cancel();
        task.get(3, TimeUnit.SECONDS);
    }

    private void blockedServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> {
            received.set(mapper.readTree(exchange.getRequestBody()));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(": ready\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { Thread.sleep(10000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
    }

    private void start(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/v1/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod())) received.set(mapper.readTree(exchange.getRequestBody()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        provider = new LlamaFactoryProvider(properties(3000), mapper);
    }

    private AiProperties properties(long timeout) {
        AiProperties p = new AiProperties(); p.setEnabled(true); p.setMaxConcurrent(1); p.setMaxQueue(0);
        p.getLlamaFactory().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        p.getLlamaFactory().setApiKey("test-key"); p.getLlamaFactory().setResponseTimeoutMs(timeout);
        return p;
    }
    private AiProviderRequest request() { return new AiProviderRequest(List.of(new AiProviderMessage("user", "问题"))); }
}
