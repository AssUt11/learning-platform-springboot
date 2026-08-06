package com.learning.platform.service;

import com.learning.platform.dto.LessonProgressRequest;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.CourseContentValidationResult;
import com.learning.platform.vo.CourseLearningProgressView;
import com.learning.platform.vo.LessonProgressView;
import com.learning.platform.vo.ResumeLearningView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonProgressServiceTest {
    private JdbcTemplate jdbcTemplate;
    private CourseContentValidationService validationService;
    private LessonProgressService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        validationService = mock(CourseContentValidationService.class);
        CourseContentValidationResult complete = mock(CourseContentValidationResult.class);
        when(complete.isContentComplete()).thenReturn(true);
        when(validationService.validatePublic(7L)).thenReturn(complete);
        service = new LessonProgressService(jdbcTemplate, validationService);
        when(jdbcTemplate.queryForList(contains("from resources where id=?"), eq(7L)))
                .thenReturn(rows(row("id", 7L, "title", "示例课程", "status", 1, "reviewStatus", "approved")));
    }

    @Test
    void videoProgressIsCalculatedOnServerAndNotCompletedTooEarly() {
        stubLesson(101L, "video", 100);
        stubSaved(101L, 40, 100, 40, 0);
        LessonProgressRequest request = request(7L, 40, 100, "heartbeat");

        LessonProgressView result = service.save(15L, 101L, request);

        assertEquals(40, result.getProgressPercent());
        assertFalse(result.isCompleted());
        verify(jdbcTemplate).update(contains("insert into lesson_progress"),
                eq(15L), eq(101L), eq(40), eq(100), eq(40), eq(0));
    }

    @Test
    void nearEndVideoPositionMarksLessonComplete() {
        stubLesson(102L, "video", 100);
        stubSaved(102L, 92, 100, 100, 1);

        LessonProgressView result = service.save(15L, 102L, request(7L, 92, 9999, "pause"));

        assertTrue(result.isCompleted());
        assertEquals(100, result.getProgressPercent());
        verify(jdbcTemplate).update(contains("insert into lesson_progress"),
                eq(15L), eq(102L), eq(92), eq(100), eq(100), eq(1));
    }

    @Test
    void positionIsClampedToAuthoritativeLessonDuration() {
        stubLesson(103L, "video", 120);
        stubSaved(103L, 120, 120, 100, 1);

        service.save(15L, 103L, request(7L, 800, 800, "ended"));

        verify(jdbcTemplate).update(contains("insert into lesson_progress"),
                eq(15L), eq(103L), eq(120), eq(120), eq(100), eq(1));
    }

    @Test
    void articleCompletionRequiresExplicitCompletedEvent() {
        stubLesson(104L, "article", 0);
        stubSaved(104L, 1, 1, 100, 1);

        LessonProgressView result = service.save(15L, 104L, request(7L, 0, 0, "completed"));

        assertTrue(result.isCompleted());
        verify(jdbcTemplate).update(contains("insert into lesson_progress"),
                eq(15L), eq(104L), eq(1), eq(1), eq(100), eq(1));
    }

    @Test
    void lessonOutsideCourseIsRejected() {
        when(jdbcTemplate.queryForList(contains("from course_lessons l join"), eq(7L), eq(999L)))
                .thenReturn(rows());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(15L, 999L, request(7L, 20, 100, "heartbeat")));

        assertEquals(404, error.getCode());
    }

    @Test
    void courseProgressAggregatesAllPublishedLessons() {
        Timestamp older = Timestamp.valueOf("2026-08-04 10:00:00");
        Timestamp latest = Timestamp.valueOf("2026-08-04 11:00:00");
        when(jdbcTemplate.queryForList(contains("left join lesson_progress"), eq(15L), eq(7L)))
                .thenReturn(rows(
                        row("lessonId", 101L, "lessonDuration", 100, "lastPositionSeconds", 100,
                                "savedDurationSeconds", 100, "progressPercent", 100, "completed", 1,
                                "lastStudiedAt", older),
                        row("lessonId", 102L, "lessonDuration", 200, "lastPositionSeconds", 100,
                                "savedDurationSeconds", 200, "progressPercent", 50, "completed", 0,
                                "lastStudiedAt", latest)));

        CourseLearningProgressView result = service.courseProgress(15L, 7L);

        assertEquals(2, result.getTotalLessons());
        assertEquals(1, result.getCompletedLessons());
        assertEquals(75, result.getProgressPercent());
        assertEquals(102L, result.getLastLessonId());
        assertEquals(200, result.getWatchedSeconds());
    }

    @Test
    void resumeReturnsMostRecentPublishedLesson() {
        Timestamp studiedAt = Timestamp.valueOf("2026-08-04 11:30:00");
        when(jdbcTemplate.queryForList(contains("from lesson_progress lp"), eq(15L)))
                .thenReturn(rows(row("courseId", 7L, "courseTitle", "示例课程", "lessonId", 102L,
                        "lessonTitle", "第二节", "lastPositionSeconds", 61, "progressPercent", 31,
                        "lastStudiedAt", studiedAt)));

        ResumeLearningView result = service.resume(15L);

        assertTrue(result.isAvailable());
        assertEquals(7L, result.getCourseId());
        assertEquals(102L, result.getLessonId());
        assertEquals(61, result.getLastPositionSeconds());
    }

    private void stubLesson(long lessonId, String lessonType, int duration) {
        when(jdbcTemplate.queryForList(contains("from course_lessons l join"), eq(7L), eq(lessonId)))
                .thenReturn(rows(row("lessonId", lessonId, "lessonType", lessonType, "durationSeconds", duration)));
    }

    private void stubSaved(long lessonId, int position, int duration, int progress, int completed) {
        when(jdbcTemplate.queryForList(contains("from lesson_progress where"), eq(15L), eq(lessonId)))
                .thenReturn(rows(row("lastPositionSeconds", position, "durationSeconds", duration,
                        "progressPercent", progress, "completed", completed,
                        "lastStudiedAt", Timestamp.valueOf("2026-08-04 10:00:00"))));
    }

    private LessonProgressRequest request(long courseId, int position, int duration, String eventType) {
        LessonProgressRequest request = new LessonProgressRequest();
        request.setCourseId(courseId);
        request.setPositionSeconds(position);
        request.setDurationSeconds(duration);
        request.setEventType(eventType);
        return request;
    }

    @SafeVarargs
    private final List<Map<String, Object>> rows(Map<String, Object>... values) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> value : values) result.add(value);
        return result;
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
