package com.learning.platform.ai.controller;

import com.learning.platform.ai.application.AiTutorApplicationService;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.service.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiHealthControllerTest {
    @Test
    void cachesRemoteHealthChecksForFifteenSeconds() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        AiModelProvider provider = mock(AiModelProvider.class);
        Neo4jLearningContextService neo4jService = mock(Neo4jLearningContextService.class);
        AiTutorApplicationService tutorService = mock(AiTutorApplicationService.class);
        when(currentUserService.requireLogin("Bearer token"))
                .thenReturn(Collections.<String, Object>singletonMap("role", "admin"));
        Map<String, Object> available = new LinkedHashMap<String, Object>();
        available.put("available", true);
        when(provider.health()).thenReturn(available);
        when(neo4jService.health()).thenReturn(available);
        when(provider.providerCode()).thenReturn("ollama");
        when(provider.modelCode()).thenReturn("qwen3:8b");
        AiHealthController controller = new AiHealthController(
                currentUserService, provider, neo4jService, tutorService);

        controller.health("Bearer token");
        controller.health("Bearer token");

        verify(provider, times(1)).health();
        verify(neo4jService, times(1)).health();
        verify(tutorService, times(2)).activeCount();
        verify(tutorService, times(2)).queueSize();
    }
}
