package com.learning.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.CourseContentValidationResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeCourseTargetServiceTest {

    private static final String TOPIC = "期望与方差：平均值和波动";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourseContentValidationService validation = mock(CourseContentValidationService.class);
    private final KnowledgeCourseTargetService service = new KnowledgeCourseTargetService(jdbc, validation);

    @Test
    void resolvesTheExactLessonWithCategoryAndDirectionScope() {
        Map<String, Object> target = target(21, 302);
        when(jdbc.queryForList(anyString(), anyString(), anyString(), anyString())).thenReturn(List.of(target));
        visible(21, true);

        assertEquals(List.of(target), service.targets("考研备考", "考研数学", TOPIC));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq("考研备考"), eq("考研数学"), eq(TOPIC));
        assertTrue(sql.getValue().contains("coalesce(cat.name,r.subject)=target.categoryName"));
        assertTrue(sql.getValue().contains("coalesce(d.name,r.subject)=target.directionName and l.title=target.topicTitle"));
        assertTrue(sql.getValue().contains("kt.category_name=target.categoryName and kt.direction_name=target.directionName"));
        assertFalse(sql.getValue().toLowerCase().contains("like"));
        assertTrue(sql.getValue().contains("r.id courseId"), "routes use the public resource id");
        assertTrue(sql.getValue().contains("r.review_status='approved'"));
        assertTrue(sql.getValue().contains("r.status=1"));
        assertTrue(sql.getValue().contains("r.lifecycle_status='active'"));
        assertTrue(sql.getValue().contains("ch.status='published' and l.status='published'"));
        assertTrue(sql.getValue().contains("a.transcoding_status='ready'"));
    }

    @Test
    void returnsAllExactMatchesRatherThanRankingAndChoosingOne() {
        List<Map<String, Object>> candidates = List.of(target(21, 302), target(22, 401));
        when(jdbc.queryForList(anyString(), anyString(), anyString(), anyString())).thenReturn(candidates);
        visible(21, true);
        visible(22, true);

        assertEquals(candidates, service.targets("考研备考", "考研数学", TOPIC));
    }

    @Test
    void resolvesExplicitTopicMappingWhenTheLessonHasADifferentTitle() {
        Map<String, Object> target = Map.of("courseId", 7L, "lessonId", 90L, "lessonTitle", "封装、继承与对象不变量", "topicTitle", "面向对象", "matchType", "mapped");
        when(jdbc.queryForList(anyString(), eq("软件工程"), eq("Java"), eq("面向对象"))).thenReturn(List.of(target));
        visible(7, true);
        assertEquals(List.of(target), service.targets("软件工程", "Java", "面向对象"));
    }

    @Test
    void doesNotReturnAnUnpublishableCourseAndChecksEachCourseOnlyOnce() {
        when(jdbc.queryForList(anyString(), anyString(), anyString(), anyString())).thenReturn(
            List.of(target(21, 302), target(21, 303), target(22, 401))
        );
        visible(21, true);
        visible(22, false);

        List<Map<String, Object>> result = service.targets("考研备考", "考研数学", TOPIC);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(item -> item.get("courseId").equals(21L)));
        verify(validation, times(1)).validatePublic(21L);
    }

    @Test
    void anUnmatchedTopicDoesNotFallBackToUnrelatedDirectionCourses() {
        when(jdbc.queryForList(anyString(), anyString(), anyString(), anyString())).thenReturn(List.of());

        assertTrue(service.targets("考研备考", "考研数学", TOPIC).isEmpty());
        verify(jdbc, times(1)).queryForList(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(validation);
    }

    @Test
    void requiresACompleteBoundedScopeBeforeQuerying() {
        BusinessException failure = assertThrows(
            BusinessException.class,
            () -> service.targets("", "考研数学", TOPIC)
        );
        assertEquals(400, failure.getCode());
        assertThrows(BusinessException.class, () -> service.targets("考研备考", "考研数学", "x".repeat(251)));
        verifyNoInteractions(jdbc, validation);
    }

    private void visible(long courseId, boolean complete) {
        CourseContentValidationResult result = mock(CourseContentValidationResult.class);
        when(result.isContentComplete()).thenReturn(complete);
        when(validation.validatePublic(courseId)).thenReturn(result);
    }

    private Map<String, Object> target(long courseId, long lessonId) {
        return Map.of(
            "courseId", courseId,
            "courseTitle", "考研概率统计：条件概率与随机变量",
            "lessonId", lessonId,
            "lessonTitle", TOPIC
        );
    }
}
