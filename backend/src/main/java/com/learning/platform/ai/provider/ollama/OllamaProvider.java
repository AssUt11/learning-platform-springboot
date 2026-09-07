package com.learning.platform.ai.provider.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.AiCancellation;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.ai.domain.AiProviderMessage;
import com.learning.platform.ai.domain.AiProviderRequest;
import com.learning.platform.ai.domain.AiUsage;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OllamaProvider implements AiModelProvider {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaProvider(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(positive(properties.getOllama().getConnectTimeoutMs(), 3000L)))
                .build();
    }

    @Override
    public String providerCode() { return "ollama"; }

    @Override
    public String modelCode() { return properties.getOllama().getModel(); }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("provider", providerCode());
        result.put("model", modelCode());
        if (!properties.isEnabled()) {
            result.put("available", false);
            result.put("modelInstalled", false);
            result.put("reason", "AI_DISABLED");
            return result;
        }
        try {
            JsonNode version = getJson("/api/version");
            JsonNode tags = getJson("/api/tags");
            boolean installed = false;
            for (JsonNode model : tags.path("models")) {
                if (modelCode().equals(model.path("name").asText())) {
                    installed = true;
                    break;
                }
            }
            result.put("available", installed);
            result.put("modelInstalled", installed);
            result.put("version", version.path("version").asText("unknown"));
            if (!installed) result.put("reason", "AI_MODEL_NOT_FOUND");
        } catch (Exception exception) {
            result.put("available", false);
            result.put("modelInstalled", false);
            result.put("reason", "AI_SERVICE_OFFLINE");
        }
        return result;
    }

    @Override
    public AiUsage chatStream(AiProviderRequest request, Consumer<String> chunkConsumer,
                              AiCancellation cancellation) throws Exception {
        cancellation.throwIfCancelled();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", modelCode());
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        for (AiProviderMessage message : request.getMessages()) {
            Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            messages.add(item);
        }
        body.put("messages", messages);
        body.put("stream", true);
        body.put("think", properties.getOllama().isThink());
        body.put("keep_alive", properties.getOllama().getKeepAlive());
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("num_ctx", properties.getOllama().getNumCtx());
        options.put("num_predict", properties.getOllama().getNumPredict());
        body.put("options", options);

        HttpRequest httpRequest = HttpRequest.newBuilder(uri("/api/chat"))
                .timeout(Duration.ofMillis(positive(properties.getOllama().getResponseTimeoutMs(), 120000L)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        InputStream input = response.body();
        cancellation.register(input);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String error = new String(input.readNBytes(2048), StandardCharsets.UTF_8);
            throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + error);
        }

        AiUsage usage = new AiUsage(0, 0, 0L, 0L, 0D);
        boolean completed = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cancellation.throwIfCancelled();
                if (line.trim().isEmpty()) continue;
                JsonNode json = objectMapper.readTree(line);
                String content = json.path("message").path("content").asText("");
                // message.thinking is deliberately never read or forwarded.
                if (!content.isEmpty()) chunkConsumer.accept(content);
                if (json.path("done").asBoolean(false)) {
                    usage = usage(json);
                    completed = true;
                }
            }
        }
        cancellation.throwIfCancelled();
        if (!completed) {
            throw new IllegalStateException("Ollama stream ended without a done=true completion frame");
        }
        return usage;
    }

    private JsonNode getJson(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofMillis(Math.max(1000L, positive(properties.getOllama().getConnectTimeoutMs(), 3000L))))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Ollama health endpoint returned " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private AiUsage usage(JsonNode json) {
        int promptTokens = json.path("prompt_eval_count").asInt(0);
        int outputTokens = json.path("eval_count").asInt(0);
        long loadNanos = json.path("load_duration").asLong(0L);
        long totalNanos = json.path("total_duration").asLong(0L);
        long evalNanos = json.path("eval_duration").asLong(0L);
        double tokensPerSecond = evalNanos <= 0L ? 0D : outputTokens * 1_000_000_000D / evalNanos;
        return new AiUsage(promptTokens, outputTokens, loadNanos / 1_000_000L,
                totalNanos / 1_000_000L, tokensPerSecond);
    }

    private URI uri(String path) {
        String base = properties.getOllama().getBaseUrl();
        if (base == null || base.trim().isEmpty()) throw new IllegalStateException("OLLAMA_BASE_URL is empty");
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        URI uri = URI.create(base + path);
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalStateException("OLLAMA_BASE_URL must use HTTP or HTTPS");
        }
        return uri;
    }

    private long positive(long configured, long fallback) {
        return configured > 0L ? configured : fallback;
    }
}
