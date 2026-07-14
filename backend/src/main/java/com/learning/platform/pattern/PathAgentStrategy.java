package com.learning.platform.pattern;

import org.springframework.stereotype.Component;

@Component
public class PathAgentStrategy implements AgentStrategy {
    @Override
    public String type() {
        return "path";
    }

    @Override
    public String execute(RecommendationContext context) {
        String weakNode = firstValue(context.getWeakNodes(), "node", context.getSubject() + " foundation");
        String nextResource = firstValue(context.getResources(), "title", "a matching practice resource");
        if (context.getAverageProgress() < 40) {
            return "Learning path: restart from " + weakNode
                    + " -> complete guided examples -> finish " + nextResource
                    + " -> take a stage check after progress reaches 60%.";
        }
        return "Learning path: consolidate " + weakNode
                + " -> continue with " + nextResource
                + " -> project practice -> stage assessment.";
    }

    private String firstValue(java.util.List<java.util.Map<String, Object>> rows, String key, String fallback) {
        if (rows == null || rows.isEmpty() || rows.get(0).get(key) == null) {
            return fallback;
        }
        return String.valueOf(rows.get(0).get(key));
    }
}
