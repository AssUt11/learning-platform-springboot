package com.learning.platform.ai.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.safety.ThinkingContentFilter;
import com.learning.platform.exception.BusinessException;
import java.util.*;

/** 模型只提供题目；答案格式、题量和计分规则由服务端校验。 */
public final class PracticeQuestionParser {

    private PracticeQuestionParser() {}

    public static List<Map<String, Object>> parse(String text, ObjectMapper mapper) {
        try {
            ThinkingContentFilter filter = new ThinkingContentFilter();
            String json = (filter.accept(text) + filter.finish())
                .trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
            // A known delimiter typo puts ':' inside a field-name quote. Repair keys only,
            // never alter question text, choices or answer values.
            json = json.replaceAll("(?<=[{,])\\s*\"(stem|explanation|reference):\"", "\"$1\":\"");
            JsonNode questions = mapper.readTree(removeSurplusClosers(json)).path("questions");
            if (
                !questions.isArray() || questions.isEmpty() || questions.size() > 2
            ) throw new IllegalArgumentException();
            List<Map<String, Object>> result = new ArrayList<>();
            Set<String> stems = new HashSet<>();
            for (JsonNode question : questions) {
                String stem = required(question, "stem", 300);
                if (!stems.add(stem)) throw new IllegalArgumentException();
                JsonNode options = question.path("options");
                if (!options.isArray() || options.size() != 4) throw new IllegalArgumentException();
                List<String> choices = new ArrayList<>();
                for (JsonNode option : options) {
                    if (
                        !option.isTextual() ||
                        option.asText().isBlank() ||
                        option.asText().length() > 160
                    ) throw new IllegalArgumentException();
                    choices.add(option.asText().trim());
                }
                if (
                    new HashSet<>(choices).size() != 4 ||
                    !question.path("answer").isInt() ||
                    question.get("answer").intValue() < 0 ||
                    question.get("answer").intValue() > 3
                ) throw new IllegalArgumentException();
                result.add(
                    Map.of(
                        "stem",
                        stem,
                        "options",
                        choices,
                        "answer",
                        question.get("answer").intValue(),
                        "explanation",
                        required(question, "explanation", 500),
                        "reference",
                        required(question, "reference", 500)
                    )
                );
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
            String correct = answerPhrase(String.valueOf(
                ((List<?>) question.get("options")).get(
                    ((Number) question.get("answer")).intValue()
                )
            ));
            if (
                !source.contains(reference) || !reference.contains(correct)
            ) throw new BusinessException(502, "练习题的原文依据未通过校验，请重新生成。");
        }
    }

    /** Attach a verified source sentence ourselves; never accept an answer phrase absent from the material. */
    public static List<Map<String, Object>> citeEvidence(List<Map<String, Object>> questions, String evidence) {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] sentences = evidence.split("(?<=[。！？!?])|\\n+");
        for (var question : questions) {
            String correct = answerPhrase(String.valueOf(((List<?>) question.get("options")).get(((Number) question.get("answer")).intValue())));
            String reference = Arrays.stream(sentences)
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank() && sentence.length() <= 500 && sentence.replaceAll("\\s+", "").contains(correct))
                .min(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new BusinessException(502, "练习题的正确选项未在讲义中找到，请重新生成。"));
            Map<String, Object> grounded = new LinkedHashMap<>(question);
            grounded.put("reference", reference);
            result.add(grounded);
        }
        requireGrounding(result, evidence);
        return result;
    }

    private static String answerPhrase(String text) {
        // A choice may end with a full stop where the textbook continues with a comma.
        // Preserve every word and internal punctuation; never accept a paraphrased answer.
        String phrase = text.replaceAll("\\s+", "").replaceFirst("[。；;.!！?？]+$", "");
        if (phrase.isBlank()) throw new BusinessException(502, "练习题的正确选项缺少有效内容，请重新生成。");
        return phrase;
    }

    /** Small local models sometimes emit one extra closing brace. Remove only surplus closers outside strings.
     * Missing structure, keys, choices and answer values are never invented or repaired here. */
    private static String removeSurplusClosers(String json) {
        StringBuilder result = new StringBuilder();
        Deque<Character> stack = new ArrayDeque<>();
        boolean quoted = false, escaped = false;
        for (char current : json.toCharArray()) {
            if (quoted) {
                result.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '{' || current == '[') stack.push(current);
            else if (current == '}' || current == ']') {
                char expected = current == '}' ? '{' : '[';
                if (stack.isEmpty() || stack.peek() != expected) continue;
                stack.pop();
            }
            result.append(current);
        }
        return result.toString();
    }

    private static String required(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (
            !value.isTextual() || value.asText().isBlank() || value.asText().length() > max
        ) throw new IllegalArgumentException();
        return value.asText().trim();
    }
}
