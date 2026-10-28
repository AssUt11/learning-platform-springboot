package com.learning.platform.ai.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.safety.ThinkingContentFilter;
import com.learning.platform.exception.BusinessException;
import java.util.*;

/** 模型只提供题目；答案格式、题量和计分规则由服务端校验。 */
public final class PracticeQuestionParser {
    private PracticeQuestionParser() { }

    public static List<Map<String, Object>> parse(String text, ObjectMapper mapper) {
        try {
            ThinkingContentFilter filter = new ThinkingContentFilter();
            String json = (filter.accept(text) + filter.finish()).trim()
                    .replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            JsonNode questions = mapper.readTree(json).path("questions");
            if (!questions.isArray() || questions.isEmpty() || questions.size() > 2) throw new IllegalArgumentException();
            List<Map<String, Object>> result = new ArrayList<>();
            Set<String> stems = new HashSet<>();
            for (JsonNode question : questions) {
                String stem = required(question, "stem", 300);
                if (!stems.add(stem)) throw new IllegalArgumentException();
                JsonNode options = question.path("options");
                if (!options.isArray() || options.size() != 4) throw new IllegalArgumentException();
                List<String> choices = new ArrayList<>();
                for (JsonNode option : options) {
                    if (!option.isTextual() || option.asText().isBlank() || option.asText().length() > 160)
                        throw new IllegalArgumentException();
                    choices.add(option.asText().trim());
                }
                if (new HashSet<>(choices).size() != 4 || !question.path("answer").isInt()
                        || question.get("answer").intValue() < 0 || question.get("answer").intValue() > 3)
                    throw new IllegalArgumentException();
                result.add(Map.of("stem", stem, "options", choices, "answer", question.get("answer").intValue(),
                        "explanation", required(question, "explanation", 500), "reference", required(question, "reference", 500)));
            }
            return result;
        } catch (Exception invalid) {
            throw new BusinessException(502, "模型未返回完整、有效的练习题，请重新生成。");
        }
    }

    /** 原文依据必须来自当前资料，正确选项也必须能在该依据中直接找到。 */
    public static void requireGrounding(List<Map<String, Object>> questions, String evidence) {
        String source = evidence.replaceAll("\\s+", "");
        for (Map<String, Object> question : questions) {
            String reference = String.valueOf(question.get("reference")).replaceAll("\\s+", "");
            String correct = String.valueOf(((List<?>) question.get("options")).get(((Number) question.get("answer")).intValue())).replaceAll("\\s+", "");
            if (!source.contains(reference) || !reference.contains(correct))
                throw new BusinessException(502, "练习题的原文依据未通过校验，请重新生成。");
        }
    }

    private static String required(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max)
            throw new IllegalArgumentException();
        return value.asText().trim();
    }
}
