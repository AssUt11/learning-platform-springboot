package com.learning.platform.ai.provider.llamafactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.*;
import com.learning.platform.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** LLaMA-Factory's OpenAI-style API; never loads model weights in the Java process. */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "llamafactory")
public class LlamaFactoryProvider implements AiModelProvider {
    private final AiProperties properties;
    private final AiProperties.LlamaFactory config;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Semaphore permits;
    private final Semaphore capacity;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "llamafactory-deadlines");
        thread.setDaemon(true);
        return thread;
    });

    public LlamaFactoryProvider(AiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.config = properties.getLlamaFactory();
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(positive(config.getConnectTimeoutMs()))).build();
        this.permits = new Semaphore(Math.max(1, properties.getMaxConcurrent()), true);
        this.capacity = new Semaphore(Math.max(1, properties.getMaxConcurrent()) + Math.max(0, properties.getMaxQueue()), true);
    }

    @Override public String providerCode() { return "llamafactory"; }
    @Override public String modelCode() { return config.getModel(); }
    @Override public boolean requiresPrefetchedEvidence() { return !config.isToolCallingEnabled(); }

    @Override public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", providerCode());
        result.put("model", modelCode());
        result.put("agentMode", requiresPrefetchedEvidence() ? "prefetched-evidence" : "native-tools");
        result.put("available", false);
        result.put("modelInstalled", false);
        if (!properties.isEnabled()) { result.put("reason", "AI_DISABLED"); return result; }
        try {
            AiCancellation cancellation = new AiCancellation();
            JsonNode data = withDeadline(cancellation, positive(config.getConnectTimeoutMs()), () -> {
                HttpResponse<InputStream> response = send(builder("/models").GET().build(), cancellation);
                return readJson(response, cancellation);
            });
            boolean found = false;
            for (JsonNode model : data.path("data")) if (modelCode().equals(model.path("id").asText())) found = true;
            result.put("available", found);
            result.put("modelInstalled", found);
            if (!found) result.put("reason", "AI_MODEL_NOT_FOUND");
        } catch (Exception error) {
            result.put("reason", error instanceof BusinessException && ((BusinessException) error).getCode() == 503
                    ? "AI_UPSTREAM_REJECTED" : "AI_SERVICE_OFFLINE");
        }
        return result;
    }

    @Override public AiUsage chatStream(AiProviderRequest request, Consumer<String> consumer,
                                        AiCancellation cancellation) throws Exception {
        return guarded(cancellation, () -> {
            long started = System.nanoTime();
            List<Map<String, Object>> messages = new ArrayList<>();
            for (AiProviderMessage message : request.getMessages()) {
                messages.add(Map.of("role", message.getRole(), "content", message.getContent()));
            }
            Map<String, Object> body = body(messages, true);
            HttpResponse<InputStream> response = send(post(body), cancellation);
            boolean completed = false;
            String finishReason = "";
            int promptTokens = -1, outputTokens = -1;
            try (InputStream input = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                requireSuccess(response.statusCode());
                String line;
                StringBuilder event = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    cancellation.throwIfCancelled();
                    if (line.startsWith("data:")) {
                        if (event.length() > 0) event.append('\n');
                        event.append(line.substring(5).stripLeading());
                        if (event.length() > 131072) throw new IllegalStateException("AI stream frame too large");
                    } else if (line.isEmpty() && event.length() > 0) {
                        String payload = event.toString();
                        event.setLength(0);
                        if ("[DONE]".equals(payload)) { completed = true; break; }
                        JsonNode chunk = mapper.readTree(payload);
                        if (chunk.hasNonNull("error")) throw new IllegalStateException("AI stream error");
                        JsonNode choice = chunk.path("choices").path(0);
                        if (choice.path("delta").hasNonNull("tool_calls")) {
                            throw new IllegalStateException("Unexpected tool calls in tutor stream");
                        }
                        String content = choice.path("delta").path("content").asText("");
                        // reasoning_content is intentionally not exposed.
                        if (!content.isEmpty()) consumer.accept(content);
                        if (choice.hasNonNull("finish_reason")) finishReason = choice.path("finish_reason").asText();
                        if (chunk.path("usage").isObject()) {
                            promptTokens = chunk.path("usage").path("prompt_tokens").asInt(-1);
                            outputTokens = chunk.path("usage").path("completion_tokens").asInt(-1);
                        }
                    }
                }
            }
            cancellation.throwIfCancelled();
            if (!completed || !"stop".equals(finishReason)) throw new IllegalStateException("Incomplete AI stream");
            long elapsed = Math.max(1, (System.nanoTime() - started) / 1_000_000);
            return new AiUsage(promptTokens, outputTokens, 0L, elapsed,
                    outputTokens < 0 ? 0D : outputTokens * 1000D / elapsed);
        });
    }

    @Override public JsonNode chatTurn(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                       boolean jsonOutput, AiCancellation cancellation) throws Exception {
        return guarded(cancellation, () -> {
            Map<String, Object> body = body(messages, false);
            if (!tools.isEmpty()) body.put("tools", tools);
            // LLaMA-Factory does not implement response_format. Business JSON is validated by the caller.
            JsonNode response = readJson(send(post(body), cancellation), cancellation);
            JsonNode choice = response.path("choices").path(0);
            String reason = choice.path("finish_reason").asText();
            JsonNode message = choice.path("message");
            if (!("stop".equals(reason) || "tool_calls".equals(reason)) || !message.isObject()
                    || !"assistant".equals(message.path("role").asText())) {
                throw new IllegalStateException("Incomplete AI response: finish_reason=" + reason);
            }
            return mapper.createObjectNode().put("done", true).set("message", message);
        });
    }

    private Map<String, Object> body(List<Map<String, Object>> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelCode());
        body.put("messages", normalize(messages));
        body.put("stream", stream);
        body.put("max_tokens", Math.max(1, config.getMaxTokens()));
        body.put("do_sample", false);
        return body;
    }

    /** This API requires user/assistant alternation, including one observation for parallel tools. */
    private List<Map<String, Object>> normalize(List<Map<String, Object>> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> original : messages) {
            Map<String, Object> item = new LinkedHashMap<>(original);
            String role = String.valueOf(item.get("role"));
            item.remove("tool_name");
            if ("assistant".equals(role) && result.stream().noneMatch(m -> "user".equals(m.get("role")))) continue;
            if (!result.isEmpty()) {
                Map<String, Object> previous = result.get(result.size() - 1);
                if (("user".equals(role) || "tool".equals(role)) && role.equals(previous.get("role"))) {
                    previous.put("content", Objects.toString(previous.get("content"), "") + "\n" + Objects.toString(item.get("content"), ""));
                    continue;
                }
            }
            result.add(item);
        }
        return result;
    }

    private HttpRequest post(Map<String, Object> body) throws Exception {
        return builder("/chat/completions").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();
    }

    private HttpRequest.Builder builder(String path) {
        String base = Objects.toString(config.getBaseUrl(), "").trim().replaceAll("/+$", "");
        URI uri = URI.create(base + path);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("LLAMAFACTORY_BASE_URL must be an HTTP(S) API base URL ending in /v1");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(positive(config.getResponseTimeoutMs())));
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) builder.header("Authorization", "Bearer " + config.getApiKey().trim());
        return builder;
    }

    private HttpResponse<InputStream> send(HttpRequest request, AiCancellation cancellation) throws Exception {
        cancellation.throwIfCancelled();
        CompletableFuture<HttpResponse<InputStream>> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        cancellation.register(() -> future.cancel(true));
        try {
            HttpResponse<InputStream> response = future.get();
            cancellation.register(response.body());
            cancellation.throwIfCancelled();
            return response;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof Exception) throw (Exception) error.getCause();
            throw error;
        }
    }

    private JsonNode readJson(HttpResponse<InputStream> response, AiCancellation cancellation) throws Exception {
        try (InputStream input = response.body()) {
            requireSuccess(response.statusCode());
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) throw new IllegalStateException("AI response too large");
            cancellation.throwIfCancelled();
            return mapper.readTree(bytes);
        }
    }

    private void requireSuccess(int status) {
        // Never return raw upstream payloads, which may contain prompts or credentials.
        if (status == 401 || status == 403) throw new BusinessException(503, "模型服务鉴权失败，请检查服务端 API 密钥配置。");
        if (status == 429) throw new BusinessException(429, "模型服务繁忙，请稍后重试。");
        if (status < 200 || status >= 300) throw new BusinessException(502, "模型服务返回错误（HTTP " + status + "）。");
    }

    private <T> T guarded(AiCancellation cancellation, Callable<T> operation) throws Exception {
        cancellation.throwIfCancelled();
        if (!capacity.tryAcquire()) throw new BusinessException(429, "AI 请求队列已满，请稍后重试。");
        try {
            return withDeadline(cancellation, positive(config.getResponseTimeoutMs()), () -> {
                boolean acquired = false;
                try {
                    while (!(acquired = permits.tryAcquire(100, TimeUnit.MILLISECONDS))) cancellation.throwIfCancelled();
                    cancellation.throwIfCancelled();
                    return operation.call();
                } finally { if (acquired) permits.release(); }
            });
        } finally { capacity.release(); }
    }

    private <T> T withDeadline(AiCancellation cancellation, long timeout, Callable<T> operation) throws Exception {
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> deadline = deadlines.schedule(() -> { timedOut.set(true); cancellation.cancel(); }, timeout, TimeUnit.MILLISECONDS);
        try { return operation.call(); }
        finally {
            deadline.cancel(false);
            if (timedOut.get()) throw new HttpTimeoutException("LLaMA-Factory response deadline exceeded");
        }
    }

    private long positive(long value) { return value > 0 ? value : 3000L; }
    @PreDestroy public void close() { deadlines.shutdownNow(); }
}
