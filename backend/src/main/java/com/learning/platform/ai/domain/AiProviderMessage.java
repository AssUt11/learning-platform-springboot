package com.learning.platform.ai.domain;

public class AiProviderMessage {
    private final String role;
    private final String content;

    public AiProviderMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
}
