package com.learning.platform.ai.domain;

public class AiUsage {
    private final int promptTokens;
    private final int outputTokens;
    private final long loadDurationMs;
    private final long totalDurationMs;
    private final double tokensPerSecond;

    public AiUsage(int promptTokens, int outputTokens, long loadDurationMs,
                   long totalDurationMs, double tokensPerSecond) {
        this.promptTokens = promptTokens;
        this.outputTokens = outputTokens;
        this.loadDurationMs = loadDurationMs;
        this.totalDurationMs = totalDurationMs;
        this.tokensPerSecond = tokensPerSecond;
    }

    public int getPromptTokens() { return promptTokens; }
    public int getOutputTokens() { return outputTokens; }
    public long getLoadDurationMs() { return loadDurationMs; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public double getTokensPerSecond() { return tokensPerSecond; }
}
