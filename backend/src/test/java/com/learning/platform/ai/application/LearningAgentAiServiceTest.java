package com.learning.platform.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.ai.domain.LearningAgentResult;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

class LearningAgentAiServiceTest {
    @Test
    void repairsMalformedEvidenceBasedPathOnce() throws Exception {
        AiProperties properties = new AiProperties(); properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        ObjectMapper mapper = new ObjectMapper();
        when(provider.requiresPrefetchedEvidence()).thenReturn(true);
        String corrected = mapper.writeValueAsString(Map.of("title", "Java路径", "summary", "按证据学习",
                "steps", List.of(Map.of("title", "基础", "goal", "理解变量", "completionCriteria", "完成练习"))));
        when(provider.chatTurn(any(), any(), anyBoolean(), any())).thenReturn(
                mapper.valueToTree(Map.of("message", Map.of("role", "assistant", "content", "非JSON路径"))),
                mapper.valueToTree(Map.of("message", Map.of("role", "assistant", "content", corrected))));
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper,
                mock(LearningAgentToolbox.class));
        assertEquals(1, ((List<?>) service.execute("path", context()).getLearningPath().get("nodes")).size());
        verify(provider, org.mockito.Mockito.times(2)).chatTurn(any(), any(), anyBoolean(), any());
    }

    @Test
    void stillRejectsInvalidCourseAfterSinglePathRepair() throws Exception {
        AiProperties properties = new AiProperties(); properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        ObjectMapper mapper = new ObjectMapper();
        when(provider.requiresPrefetchedEvidence()).thenReturn(true);
        String invalid = mapper.writeValueAsString(Map.of("title", "Java路径", "summary", "学习计划",
                "steps", List.of(Map.of("title", "基础", "goal", "理解变量", "completionCriteria", "完成练习", "courseId", 999))));
        when(provider.chatTurn(any(), any(), anyBoolean(), any())).thenReturn(
                mapper.valueToTree(Map.of("message", Map.of("role", "assistant", "content", invalid))));
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper,
                mock(LearningAgentToolbox.class));
        assertEquals(502, assertThrows(BusinessException.class, () -> service.execute("path", context())).getCode());
        verify(provider, org.mockito.Mockito.times(2)).chatTurn(any(), any(), anyBoolean(), any());
    }

    @Test
    void prefetchesAuthorizedEvidenceAndDoesNotSendToolDefinitionsToModel() throws Exception {
        AiProperties properties = new AiProperties(); properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        LearningAgentToolbox toolbox = mock(LearningAgentToolbox.class);
        ObjectMapper mapper = new ObjectMapper();
        when(provider.requiresPrefetchedEvidence()).thenReturn(true);
        when(toolbox.definitions("guide")).thenReturn(List.of(Map.of("function", Map.of("name", "get_learning_profile"))));
        when(toolbox.requiredTools("guide")).thenReturn(List.of("get_learning_profile"));
        when(toolbox.execute(eq("get_learning_profile"), any(), any())).thenReturn(new LearningAgentToolbox.ToolResult(
                Map.of("recordCount", 3), List.of("learning_records"), false));
        when(provider.chatTurn(any(), eq(Collections.emptyList()), anyBoolean(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> messages = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertTrue(messages.get(1).get("content").toString().contains("\"recordCount\":3"));
            return mapper.readTree("{\"message\":{\"role\":\"assistant\",\"content\":\"基于三条记录的诊断\"}}");
        });
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper, toolbox);
        LearningAgentResult result = service.execute("guide", context());
        assertEquals(List.of("get_learning_profile"), result.getToolsUsed());
        assertEquals("prefetched-evidence", service.executionMode());
        verify(toolbox).execute(eq("get_learning_profile"), any(), any());
    }

    @Test
    void rejectsToolNotOfferedInCurrentScene() throws Exception {
        AiProperties properties = new AiProperties(); properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        LearningAgentToolbox toolbox = mock(LearningAgentToolbox.class);
        ObjectMapper mapper = new ObjectMapper();
        when(toolbox.definitions("guide")).thenReturn(List.of(Map.of("function", Map.of("name", "get_learning_profile"))));
        when(provider.chatTurn(any(), any(), anyBoolean(), any())).thenReturn(mapper.readTree(
                "{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"function\":{\"name\":\"get_graph_prerequisites\",\"arguments\":{}}}]}}"));
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper, toolbox);
        assertThrows(BusinessException.class, () -> service.execute("guide", context()));
        org.mockito.Mockito.verify(toolbox, org.mockito.Mockito.never()).execute(any(), any(), any());
    }

    @Test
    void returnsVisibleOllamaAnswerWithoutThinkingContent() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        LearningAgentToolbox toolbox = mock(LearningAgentToolbox.class);
        ObjectMapper mapper = new ObjectMapper();
        when(toolbox.definitions("guide")).thenReturn(List.of(Map.of("function", Map.of("name", "get_learning_profile"))));
        when(toolbox.requiredTools("guide")).thenReturn(Collections.singletonList("get_learning_profile"));
        when(toolbox.execute(any(), any(), any())).thenReturn(new LearningAgentToolbox.ToolResult(
                Collections.singletonMap("recordCount", 3), Collections.singletonList("learning_records"), false));
        when(provider.chatTurn(any(), any(), anyBoolean(), any()))
                .thenReturn(mapper.readTree("{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"\","
                                + "\"tool_calls\":[{\"function\":{\"name\":\"get_learning_profile\",\"arguments\":{}}}]}}"),
                        mapper.readTree("{\"done\":true,\"message\":{\"role\":\"assistant\","
                                + "\"content\":\"<think>internal</think>课程诊断结果\"}}"));
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper, toolbox);

        LearningAgentResult result = service.execute("guide", context());

        assertEquals("课程诊断结果", result.getContent());
        assertEquals(Collections.singletonList("get_learning_profile"), result.getToolsUsed());
        assertEquals(Collections.singletonList("learning_records"), result.getSources());
    }

    @Test
    void asksAgainWhenRequiredToolWasNotCalled() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        LearningAgentToolbox toolbox = mock(LearningAgentToolbox.class);
        ObjectMapper mapper = new ObjectMapper();
        when(toolbox.definitions("path")).thenReturn(List.of(Map.of("function", Map.of("name", "get_graph_prerequisites"))));
        when(toolbox.requiredTools("path")).thenReturn(Collections.singletonList("get_graph_prerequisites"));
        when(toolbox.execute(any(), any(), any())).thenReturn(new LearningAgentToolbox.ToolResult(
                Collections.singletonMap("available", false), Collections.emptyList(), false));
        when(provider.chatTurn(any(), any(), anyBoolean(), any()))
                .thenReturn(mapper.readTree("{\"message\":{\"role\":\"assistant\",\"content\":\"直接回答\"}}"),
                        mapper.readTree("{\"message\":{\"role\":\"assistant\",\"content\":\"\","
                                + "\"tool_calls\":[{\"function\":{\"name\":\"get_graph_prerequisites\","
                                + "\"arguments\":{\"topic\":\"Java\"}}}]}}"),
                        mapper.valueToTree(Map.of("message", Map.of("role", "assistant", "content", mapper.writeValueAsString(Map.of(
                                "title", "Java学习路线", "summary", "图谱不可用，按学习记录给出暂行路径", "steps", List.of(Map.of(
                                        "title", "Java基础", "goal", "理解变量", "completionCriteria", "完成练习"))))))));
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper, toolbox);

        LearningAgentResult result = service.execute("path", context());

        assertEquals("知识图谱当前不可用，或没有匹配到明确的先修关系；"
                + "以下路径仅基于学习记录与已审核资源生成，尚未完成图谱校验。\n"
                + "图谱不可用，按学习记录给出暂行路径\n\n1. Java基础\n目标：理解变量\n完成标准：完成练习", result.getContent());
        assertEquals(1, ((List<?>) result.getLearningPath().get("nodes")).size());
        assertEquals(Collections.singletonList("get_graph_prerequisites"), result.getToolsUsed());
    }

    @Test
    void refusesCallsWhenAiIsDisabled() {
        LearningAgentAiService service = new LearningAgentAiService(
                new AiProperties(), mock(AiModelProvider.class), new ObjectMapper(), mock(LearningAgentToolbox.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generate("path", context()));

        assertEquals(503, error.getCode());
    }

    private RecommendationContext context() {
        return new RecommendationContext.Builder()
                .studentId(1L)
                .studentName("学生")
                .subject("Java")
                .recordCount(3)
                .recentRecordCount(1)
                .averageProgress(40)
                .resources(Collections.emptyList())
                .weakNodes(Collections.emptyList())
                .recentRecords(Collections.emptyList())
                .favoriteResources(Collections.emptyList())
                .build();
    }
}
