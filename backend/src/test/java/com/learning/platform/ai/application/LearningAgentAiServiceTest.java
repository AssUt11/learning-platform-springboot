package com.learning.platform.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.ai.domain.AiUsage;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class LearningAgentAiServiceTest {
    @Test
    void returnsVisibleOllamaAnswerWithoutThinkingContent() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        AiModelProvider provider = mock(AiModelProvider.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("<think>internal</think>课程诊断结果");
            return new AiUsage(10, 5, 1L, 2L, 3D);
        }).when(provider).chatStream(any(), any(), any());
        LearningAgentAiService service = new LearningAgentAiService(properties, provider, new ObjectMapper());

        String result = service.generate("guide", context());

        assertEquals("课程诊断结果", result);
    }

    @Test
    void refusesCallsWhenAiIsDisabled() {
        LearningAgentAiService service = new LearningAgentAiService(
                new AiProperties(), mock(AiModelProvider.class), new ObjectMapper());

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
