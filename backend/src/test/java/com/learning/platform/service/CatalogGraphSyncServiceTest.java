package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogGraphSyncServiceTest {
    @Test void failedGraphWriteRemainsRetryableAndOnlySuccessfulWritesSaveTheHash() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        KnowledgeTaxonomyService taxonomy=mock(KnowledgeTaxonomyService.class);
        Neo4jLearningContextService graph=mock(Neo4jLearningContextService.class);
        when(jdbc.queryForMap(anyString())).thenReturn(Map.of("status","failed","payload_hash","old"));
        when(taxonomy.flattened()).thenReturn(List.of()); when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(graph.syncCourseCatalog(anyMap())).thenReturn(false,true);
        var service=new CatalogGraphSyncService(jdbc,new ObjectMapper(),taxonomy,graph,mock(CourseContentValidationService.class),true);
        service.sync(false);
        verify(jdbc).update(contains("status='failed'"));
        verify(jdbc,never()).update(contains("payload_hash=?"),any(),any(),any());
        service.sync(false);
        verify(graph,times(2)).syncCourseCatalog(anyMap());
        verify(jdbc).update(contains("payload_hash=?"),matches("[0-9a-f]{64}"),eq(0),eq(0));
    }
}
