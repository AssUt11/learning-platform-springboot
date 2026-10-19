package com.learning.platform.ai.provider.llamafactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.application.LearningAgentAiService;
import com.learning.platform.ai.application.LearningAgentToolbox;
import com.learning.platform.ai.config.AiProperties;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import com.learning.platform.ai.domain.*;
import com.learning.platform.pattern.RecommendationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/** Opt-in real inference smoke test. Uses synthetic data only; no application database. */
@EnabledIfEnvironmentVariable(named = "LLAMAFACTORY_SMOKE", matches = "true")
class LlamaFactoryLiveTest {
    @Test void realModelSupportsTutorStreamingAndAllFourEvidenceBasedScenes() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true); properties.setProvider("llamafactory");
        properties.getLlamaFactory().setBaseUrl(System.getenv().getOrDefault("LLAMAFACTORY_BASE_URL", "http://127.0.0.1:8000/v1"));
        properties.getLlamaFactory().setModel(System.getenv().getOrDefault("LLAMAFACTORY_MODEL", "zhidu-qwen3-4b-lora"));
        properties.getLlamaFactory().setApiKey(System.getenv().getOrDefault("LLAMAFACTORY_API_KEY", ""));
        ObjectMapper mapper = new ObjectMapper();
        LlamaFactoryProvider provider = new LlamaFactoryProvider(properties, mapper);
        try {
            assertEquals(true, provider.health().get("available"));
            StringBuilder answer = new StringBuilder();
            provider.chatStream(new AiProviderRequest(List.of(new AiProviderMessage("system", "你是知度课堂AI伴学，简短回答。"),
                    new AiProviderMessage("user", "用两句话解释Java依赖注入。"))), answer::append, new AiCancellation());
            assertFalse(answer.toString().isBlank());
            Neo4jLearningContextService graph = mock(Neo4jLearningContextService.class);
            when(graph.findPrerequisites(anyString())).thenReturn(new Neo4jLearningContextService.PrerequisiteContext(
                    false, false, "Java", List.of(), "TEST_GRAPH_UNAVAILABLE"));
            LearningAgentAiService service = new LearningAgentAiService(properties, provider, mapper,
                    new LearningAgentToolbox(graph));
            RecommendationContext context = new RecommendationContext.Builder()
                    .studentId(999999L).studentName("接口测试学生").subject("Java").recordCount(3).recentRecordCount(1)
                    .averageProgress(40).resources(List.of(Map.of("id", 101L, "title", "Java基础", "subject", "Java")))
                    .weakNodes(List.of(Map.of("node", "依赖注入", "avgProgress", 40)))
                    .recentRecords(List.of()).favoriteResources(List.of()).build();
            assertAll(List.of("guide", "recommend", "path", "analysis").stream().map(scene -> () -> {
                LearningAgentResult result = service.execute(scene, context);
                assertTrue(result.getToolsUsed().contains("get_learning_profile"));
                assertFalse(result.getContent().isBlank());
                if ("path".equals(scene)) assertFalse(result.getLearningPath().isEmpty());
                System.out.println("Live scene=" + scene + " mode=" + service.executionMode() + " tools=" + result.getToolsUsed());
            }));
            assertEquals("prefetched-evidence", service.executionMode());
        } finally { provider.close(); }
    }
}
