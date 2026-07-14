package com.learning.platform.pattern;

import org.springframework.stereotype.Component;

@Component
public class GuideAgentStrategy implements AgentStrategy {
    @Override
    public String type() {
        return "guide";
    }

    @Override
    public String execute(RecommendationContext context) {
        String weakNode = firstValue(context.getWeakNodes(), "node", "foundation nodes");
        String activity = context.getRecentRecordCount() == 0
                ? "You have no learning activity in the last 7 days"
                : "You completed " + context.getRecentRecordCount() + " learning actions in the last 7 days";
        return "Course guide agent: focus on " + context.getSubject() + ". "
                + activity + ", with an average progress of " + context.getAverageProgress() + "%. "
                + "Priority: review " + weakNode + " first, then use examples and exercises to raise progress above 60%.";
    }

    private String firstValue(java.util.List<java.util.Map<String, Object>> rows, String key, String fallback) {
        if (rows == null || rows.isEmpty() || rows.get(0).get(key) == null) {
            return fallback;
        }
        return String.valueOf(rows.get(0).get(key));
    }
}
