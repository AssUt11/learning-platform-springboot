package com.learning.platform.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class LearningStageService {
    private final JdbcTemplate jdbc;
    public LearningStageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<Long, String> overrides() {
        Map<Long, String> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select resource_id,stage_code from course_learning_stages"))
            result.put(((Number) row.get("resource_id")).longValue(), String.valueOf(row.get("stage_code")));
        return result;
    }
    public static void enrich(Map<String, Object> course, Map<Long, String> overrides) {
        long id = course.get("id") instanceof Number ? ((Number)course.get("id")).longValue() : 0;
        String stage = overrides.getOrDefault(id, LearningStages.infer(course.get("title"), course.get("level")));
        course.put("learningStage", stage); course.put("stageLabel", LearningStages.label(stage));
    }
}
