package com.learning.platform.ai.chat;

public class AgentChatRequest {
    private Long conversationId;
    private String message;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
