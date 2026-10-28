package com.learning.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeTaxonomyServiceTest {
    @Test void databaseCoursesAppearInTheTreeWithoutChangingExistingJavaKeys() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenAnswer(call -> {
            String sql=call.getArgument(0);
            if(sql.contains("from categories")) return List.of(Map.of("id",7L,"name","考研备考","description","基础课"),Map.of("id",1L,"name","软件工程"));
            if(sql.contains("from directions")) return List.of(Map.of("id",26L,"category_id",7L,"name","考研数学"),Map.of("id",1L,"category_id",1L,"name","Java"));
            return List.of(Map.of("subject","考研数学","title","定积分","description","累积量"),Map.of("subject","Java","title","Java基础"));
        });
        var service=new KnowledgeTaxonomyService(jdbc);
        var flattened=service.flattened();
        assertEquals(1,flattened.stream().filter(row -> "software_engineering/java/java基础".equals(row.get("key"))).count());
        var topic=flattened.stream().filter(row -> "考研备考 > 考研数学 > 定积分".equals(row.get("path"))).findFirst().orElseThrow();
        assertEquals(3,topic.get("level")); assertFalse(String.valueOf(topic.get("parentKey")).isBlank());
        assertEquals(flattened.size(),flattened.stream().map(row -> row.get("key")).distinct().count());
    }
}
