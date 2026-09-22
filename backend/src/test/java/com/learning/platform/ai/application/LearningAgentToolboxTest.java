package com.learning.platform.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import com.learning.platform.pattern.RecommendationContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningAgentToolboxTest {
    private final Neo4jLearningContextService graphService = mock(Neo4jLearningContextService.class);
    private final LearningAgentToolbox toolbox = new LearningAgentToolbox(graphService);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pathRequiresGraphEvidence() {
        assertEquals(List.of("get_learning_profile", "get_learning_resources", "get_graph_prerequisites"), toolbox.requiredTools("path"));
        assertEquals(4, toolbox.definitions("path").size());
    }

    @Test
    void resourceToolOnlyReturnsPrevalidatedContextResources() {
        RecommendationContext context = context(Collections.singletonList(
                Map.of("id", 17L, "title", "Spring Boot 接口实战")));

        LearningAgentToolbox.ToolResult result = toolbox.execute(
                "get_learning_resources", mapper.createObjectNode(), context);

        assertEquals(Collections.singletonList("resource:17"), result.getSources());
        assertEquals(context.getResources(), result.getData().get("resources"));
        assertFalse(result.isGraphUsed());
    }

    @Test
    void unavailableGraphIsReportedWithoutPretendingItWasUsed() {
        when(graphService.findPrerequisites("Spring Boot")).thenReturn(
                Neo4jLearningContextService.PrerequisiteContext.unavailable(
                        "Spring Boot", "NEO4J_UNAVAILABLE"));

        LearningAgentToolbox.ToolResult result = toolbox.execute("get_graph_prerequisites",
                mapper.createObjectNode().put("topic", "Spring Boot"), context(Collections.emptyList()));

        assertFalse(result.isGraphUsed());
        assertEquals("NEO4J_UNAVAILABLE", result.getData().get("reason"));
        assertEquals(Collections.emptyList(), result.getSources());
    }

    private RecommendationContext context(List<Map<String, Object>> resources) {
        return new RecommendationContext.Builder()
                .studentId(1L)
                .studentName("学生")
                .subject("Java")
                .recordCount(1)
                .recentRecordCount(1)
                .averageProgress(50)
                .resources(resources)
                .weakNodes(Collections.emptyList())
                .recentRecords(Collections.emptyList())
                .favoriteResources(Collections.emptyList())
                .build();
    }
}
