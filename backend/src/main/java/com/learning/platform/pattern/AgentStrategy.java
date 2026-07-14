package com.learning.platform.pattern;

public interface AgentStrategy {
    String type();

    String execute(RecommendationContext context);
}
