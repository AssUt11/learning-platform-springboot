package com.learning.platform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private boolean enabled = false;
    private String provider = "ollama";
    private LlamaFactory llamaFactory = new LlamaFactory();
    private int maxConcurrent = 1;
    private int maxQueue = 5;
    private int messageMaxChars = 1000;
    private int contextMaxChars = 3500;
    private int rateLimitPerMinute = 10;
    private long streamTimeoutMs = 150000L;
    private Ollama ollama = new Ollama();

    public boolean isEnabled() { return enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public LlamaFactory getLlamaFactory() { return llamaFactory; }
    public void setLlamaFactory(LlamaFactory value) { this.llamaFactory = value == null ? new LlamaFactory() : value; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    public int getMaxQueue() { return maxQueue; }
    public void setMaxQueue(int maxQueue) { this.maxQueue = maxQueue; }
    public int getMessageMaxChars() { return messageMaxChars; }
    public void setMessageMaxChars(int messageMaxChars) { this.messageMaxChars = messageMaxChars; }
    public int getContextMaxChars() { return contextMaxChars; }
    public void setContextMaxChars(int contextMaxChars) { this.contextMaxChars = contextMaxChars; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    public long getStreamTimeoutMs() { return streamTimeoutMs; }
    public void setStreamTimeoutMs(long streamTimeoutMs) { this.streamTimeoutMs = streamTimeoutMs; }
    public Ollama getOllama() { return ollama; }
    public void setOllama(Ollama ollama) { this.ollama = ollama == null ? new Ollama() : ollama; }

    public static class LlamaFactory {
        private boolean toolCallingEnabled = false;
        private String baseUrl = "http://127.0.0.1:8000/v1";
        private String model = "zhidu-qwen3-4b-lora";
        private String apiKey = "";
        private long connectTimeoutMs = 3000L;
        private long responseTimeoutMs = 120000L;
        private int maxTokens = 600;
        private int contextWindow = 4096;

        public String getBaseUrl() { return baseUrl; }
        public boolean isToolCallingEnabled() { return toolCallingEnabled; }
        public void setToolCallingEnabled(boolean value) { toolCallingEnabled = value; }
        public void setBaseUrl(String value) { baseUrl = value; }
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public long getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(long value) { connectTimeoutMs = value; }
        public long getResponseTimeoutMs() { return responseTimeoutMs; }
        public void setResponseTimeoutMs(long value) { responseTimeoutMs = value; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int value) { maxTokens = value; }
        public int getContextWindow() { return contextWindow; }
        public void setContextWindow(int value) { contextWindow = value; }
    }

    public static class Ollama {
        private String baseUrl = "http://127.0.0.1:11434";
        private String model = "qwen3:8b-q4_K_M";
        private long connectTimeoutMs = 3000L;
        private long responseTimeoutMs = 120000L;
        private String keepAlive = "15m";
        private boolean think = false;
        private int numCtx = 4096;
        private int numPredict = 600;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public long getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public long getResponseTimeoutMs() { return responseTimeoutMs; }
        public void setResponseTimeoutMs(long responseTimeoutMs) { this.responseTimeoutMs = responseTimeoutMs; }
        public String getKeepAlive() { return keepAlive; }
        public void setKeepAlive(String keepAlive) { this.keepAlive = keepAlive; }
        public boolean isThink() { return think; }
        public void setThink(boolean think) { this.think = think; }
        public int getNumCtx() { return numCtx; }
        public void setNumCtx(int numCtx) { this.numCtx = numCtx; }
        public int getNumPredict() { return numPredict; }
        public void setNumPredict(int numPredict) { this.numPredict = numPredict; }
    }
}
