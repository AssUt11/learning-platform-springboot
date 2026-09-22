package com.learning.platform.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import java.util.*;

/** Converts the model's structured plan into the same steps used by text and graph views. */
public final class LearningPathParser {
    private LearningPathParser() { }

    public static Map<String, Object> parse(String content, ObjectMapper mapper, RecommendationContext context) {
        try {
            String json = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            JsonNode root = mapper.readTree(json);
            JsonNode steps = root.path("steps");
            if (!steps.isArray() || steps.isEmpty() || steps.size() > 5) throw new IllegalArgumentException();
            List<Map<String, Object>> nodes = new ArrayList<>();
            Set<Long> courseIds = new HashSet<>();
            if (context.getResources() != null) context.getResources().stream().limit(8).forEach(course -> {
                if (course.get("id") instanceof Number) courseIds.add(((Number) course.get("id")).longValue());
            });
            for (JsonNode step : steps) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", "step-" + (nodes.size() + 1));
                node.put("order", nodes.size() + 1);
                node.put("title", required(step, "title", 120));
                node.put("goal", required(step, "goal", 800));
                node.put("completionCriteria", required(step, "completionCriteria", 800));
                if (step.hasNonNull("courseId")) {
                    if (!step.get("courseId").isIntegralNumber() || !courseIds.contains(step.get("courseId").longValue())) {
                        throw new IllegalArgumentException();
                    }
                    node.put("courseId", step.get("courseId").longValue());
                }
                nodes.add(node);
            }
            List<Map<String, Object>> links = new ArrayList<>();
            for (int i = 1; i < nodes.size(); i++) {
                links.add(Map.of("source", nodes.get(i - 1).get("id"), "target", nodes.get(i).get("id"), "type", "NEXT_STEP"));
            }
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("title", required(root, "title", 160));
            plan.put("summary", required(root, "summary", 1500));
            plan.put("nodes", nodes);
            plan.put("links", links);
            return plan;
        } catch (Exception error) {
            throw new BusinessException(502, "AI 返回的路径结构不完整或引用了无效课程，请重新生成学习路径。");
        }
    }

    @SuppressWarnings("unchecked")
    public static String describe(Map<String, Object> plan) {
        StringBuilder result = new StringBuilder(String.valueOf(plan.get("summary")));
        for (Map<String, Object> step : (List<Map<String, Object>>) plan.get("nodes")) {
            result.append("\n\n").append(step.get("order")).append(". ").append(step.get("title"))
                    .append("\n目标：").append(step.get("goal")).append("\n完成标准：").append(step.get("completionCriteria"));
        }
        return result.toString();
    }

    private static String required(JsonNode node, String field, int max) {
        if (!node.path(field).isTextual()) throw new IllegalArgumentException();
        String value = node.get(field).asText().trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException();
        return value;
    }
}
