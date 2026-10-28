package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import com.learning.platform.ai.domain.LearningAgentResult;
import com.learning.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentLearningPathServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Neo4jLearningContextService neo4j = mock(Neo4jLearningContextService.class);
    private final StudentLearningPathService service = new StudentLearningPathService(jdbc, new ObjectMapper(), neo4j, new AdaptiveLearningService(jdbc));

    @Test void cannotReadOrSyncAnotherStudentsPath() {
        when(jdbc.queryForList(anyString(), eq("someone-else"), eq(7L))).thenReturn(Collections.emptyList());
        assertEquals(404, assertThrows(BusinessException.class, () -> service.get(7L, "someone-else")).getCode());
        assertThrows(BusinessException.class, () -> service.retrySync(7L, "someone-else"));
        verifyNoInteractions(neo4j);
    }
    @Test void keepsTheGeneratedPathWhenNeo4jIsUnavailable() {
        Map<String,Object> path = Map.of("title", "路径", "nodes", List.of(Map.of("id", "step-1")), "links", List.of());
        Map<String,Object> saved = service.save(7L, new LearningAgentResult("path-a", "分析", List.of(), List.of(), false, path));
        assertEquals(false, saved.get("neo4jSynced"));
        assertEquals("snapshot", saved.get("source"));
        assertEquals("step-1", ((List<Map<String,Object>>) saved.get("nodes")).get(0).get("id"));
        assertEquals(List.of(Map.of("id", "step-1")), path.get("nodes"));
        verify(jdbc).update(startsWith("insert into student_learning_paths"), eq("path-a"), eq(7L), anyString());
    }
    @Test void loadsNeo4jSnapshotOnlyForTheAuthenticatedOwner() {
        when(jdbc.queryForList(anyString(), eq("path-a"), eq(7L))).thenReturn(List.of(Map.of("snapshot_json", "{}", "neo4j_synced", 1)));
        when(neo4j.readStudentPath(7L, "path-a")).thenReturn("{\"title\":\"保存的路径\",\"nodes\":[]}");
        Map<String,Object> result = service.get(7L, "path-a");
        assertEquals("neo4j", result.get("source"));
        assertEquals("保存的路径", result.get("title"));
        verify(neo4j).readStudentPath(7L, "path-a");
    }
}
