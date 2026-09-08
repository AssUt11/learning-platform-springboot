package com.learning.platform.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.ai.domain.LearningAgentResult;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningAgentAiServiceTest {
    @Test
    void returnsVisibleOllamaAnswerWithoutThinkingContent() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        LearningAgentToolbox toolbox = mock(LearningAgentToolbox.class);
        ObjectMapper mapper = new ObjectMapper();
        when(toolbox.definitions("guide")).thenReturn(Collections.singletonList(Collections.emptyMap()));
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
        when(toolbox.definitions("path")).thenReturn(Collections.singletonList(Collections.emptyMap()));
        when(toolbox.requiredTools("path")).thenReturn(Collections.singletonList("get_graph_prerequisites"));
        when(toolbox.execute(any(), any(), any())).thenReturn(new LearningAgentToolbox.ToolResult(
                Collections.singletonMap("available", false), Collections.emptyList(), false));
        when(provider.chatTurn(any(), any(), anyBoolean(), any()))
                .thenReturn(mapper.readTree("{\"message\":{\"role\":\"assistant\",\"content\":\"直接回答\"}}"),
                        mapper.readTree("{\"message\":{\"role\":\"assistant\",\"content\":\"\","
                                + "\"tool_calls\":[{\"function\":{\"name\":\"get_graph_prerequisites\","
                                + "\"arguments\":{\"topic\":\"Java\"}}}]}}"),
                        mapper.readTree("{\"message\":{\"role\":\"assistant\",\"content\":\"图谱不可用，按学习记录给出暂行路径\"}}"));
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper, toolbox);

        LearningAgentResult result = service.execute("path", context());

        assertEquals("知识图谱当前不可用，或没有匹配到明确的先修关系；"
                + "以下路径仅基于学习记录与已审核资源生成，尚未完成图谱校验。\n"
                + "图谱不可用，按学习记录给出暂行路径", result.getContent());
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
