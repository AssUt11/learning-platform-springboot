package com.learning.platform.ai.domain;

import java.util.Collections;
import java.util.List;

public class AiProviderRequest {
    private final List<AiProviderMessage> messages;

    public AiProviderRequest(List<AiProviderMessage> messages) {
        this.messages = messages == null ? Collections.emptyList() : messages;
    }

    public List<AiProviderMessage> getMessages() { return messages; }
}
