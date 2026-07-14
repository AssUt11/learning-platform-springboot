package com.learning.platform.pattern;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ResourceRecommendAgentStrategy implements AgentStrategy {
    @Override
    public String type() {
        return "recommend";
    }

    @Override
    public String execute(RecommendationContext context) {
        StringBuilder builder = new StringBuilder();
        if (context.getResources() != null) {
            for (Map<String, Object> row : context.getResources()) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(row.get("title"));
            }
        }
        if (builder.length() == 0) {
            builder.append(context.getSubject()).append(" foundation video, exercise material, project case");
        }
        return "Resource recommendation agent: " + builder
                + ". Reason: matched with subject preference, favorite history, weak nodes, and average progress "
                + context.getAverageProgress() + "%.";
    }
}
