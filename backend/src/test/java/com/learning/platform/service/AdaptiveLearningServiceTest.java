package com.learning.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdaptiveLearningServiceTest {
    private Map<String, Object> lesson(long id, int progress, Integer score) {
        Map<String, Object> row = new HashMap<>();
        row.put("lessonId", id); row.put("progress", progress); row.put("completed", progress == 100 ? 1 : 0);
        row.put("score", score); return row;
    }
    @Test void readingCompletionDoesNotImplyMastery() {
        Map<String,Object> node = new HashMap<>();
        AdaptiveLearningService.applyFeedback(node, List.of(lesson(1,100,null)));
        assertEquals("建议自测", node.get("learningState"));
        assertNull(node.get("quizScore"));
    }
    @Test void lowScorePrioritizesReviewEvenAfterReadingIsComplete() {
        Map<String,Object> node = new HashMap<>();
        AdaptiveLearningService.applyFeedback(node, List.of(lesson(1,100,100), lesson(2,100,0)));
        assertEquals("优先复习", node.get("learningState")); assertEquals(2L, node.get("nextLessonId"));
    }
    @Test void passingOneLessonCannotMaskWeaknessInAnother() {
        Map<String,Object> node = new HashMap<>();
        AdaptiveLearningService.applyFeedback(node, List.of(lesson(1,100,100), lesson(2,100,70)));
        assertEquals("建议自测", node.get("learningState"));
    }
    @Test void passedTestsDoNotMarkUnfinishedLessonsAsComplete() {
        Map<String,Object> node = new HashMap<>();
        AdaptiveLearningService.applyFeedback(node, List.of(lesson(1,0,100)));
        assertEquals("待学习", node.get("learningState"));
    }
    @Test void pathSkipsCompletedStepsButKeepsOriginalLinksAndSavedSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(7L), eq(7L), eq(11L))).thenReturn(List.of(lesson(1,100,100)));
        when(jdbc.queryForList(anyString(), eq(7L), eq(7L), eq(22L))).thenReturn(List.of(lesson(2,20,null)));
        List<Map<String,Object>> original = List.of(Map.of("id","a","courseId",11L,"title","基础"), Map.of("id","b","courseId",22L,"title","进阶"));
        Map<String,Object> snapshot = new HashMap<>(Map.of("nodes",original,"links",List.of(Map.of("source","a","target","b"))));
        Map<String,Object> result = new AdaptiveLearningService(jdbc).enrich(7L,snapshot);
        assertEquals("b", ((Map<?,?>) result.get("nextAction")).get("nodeId"));
        assertEquals(List.of(Map.of("source","a","target","b")),result.get("links"));
        assertFalse(original.get(0).containsKey("learningState"));
    }
    @Test void withdrawnCourseCannotBeRecommended() {
        Map<String,Object> node = new HashMap<>();
        AdaptiveLearningService.applyFeedback(node,List.of());
        assertEquals("课程已下架",node.get("learningState"));
    }
}
