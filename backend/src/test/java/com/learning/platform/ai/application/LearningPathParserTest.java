package com.learning.platform.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LearningPathParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RecommendationContext context = new RecommendationContext.Builder().resources(List.of(Map.of("id", 12L))).build();

    @Test void usesTheSameOrderedStepsForTextAndGraph() throws Exception {
        String json = mapper.writeValueAsString(Map.of("title", "Java路径", "summary", "先基础后实践", "steps", List.of(
                Map.of("title", "基础", "goal", "理解语法", "completionCriteria", "完成练习", "courseId", 12),
                Map.of("title", "实践", "goal", "完成项目", "completionCriteria", "通过测试"))));
        Map<String,Object> result = LearningPathParser.parse("```json\n" + json + "\n```", mapper, context);
        assertEquals(List.of(Map.of("source", "step-1", "target", "step-2", "type", "NEXT_STEP")), result.get("links"));
        assertTrue(LearningPathParser.describe(result).contains("2. 实践"));
    }
    @Test void rejectsUnapprovedCourseReferences() throws Exception {
        String json = mapper.writeValueAsString(Map.of("title", "路径", "summary", "说明", "steps", List.of(
                Map.of("title", "基础", "goal", "学习", "completionCriteria", "练习", "courseId", 999))));
        assertThrows(BusinessException.class, () -> LearningPathParser.parse(json, mapper, context));
    }
    @Test void rejectsMissingStepsAndPlainTextInsteadOfFabricatingAGraph() {
        assertThrows(BusinessException.class, () -> LearningPathParser.parse("文字回答", mapper, context));
        assertThrows(BusinessException.class, () -> LearningPathParser.parse("{\"steps\":[]}", mapper, context));
    }
}
