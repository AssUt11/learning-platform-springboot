package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.CourseContentValidationResult;
import com.learning.platform.vo.LessonPlaybackView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
import static org.mockito.Mockito.when;

class LessonPlaybackServiceTest {
    private JdbcTemplate jdbcTemplate;
    private CourseContentValidationService validationService;
    private LessonPlaybackService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        validationService = mock(CourseContentValidationService.class);
        CourseContentValidationResult valid = mock(CourseContentValidationResult.class);
        when(valid.isContentComplete()).thenReturn(true);
        when(validationService.validatePublic(7L)).thenReturn(valid);
        service = new LessonPlaybackService(jdbcTemplate, validationService);
    }

    @Test
    void differentLessonsReturnTheirOwnVideos() {
        stubLesson(7L, 101L, "published", "published", 14L);
        stubLesson(7L, 102L, "published", "published", 14L);
        when(jdbcTemplate.queryForList(contains("from lesson_assets"), eq(101L)))
                .thenReturn(rows(row("id", 1001L, "fileUrl", "/uploads/a.mp4", "transcodingStatus", "ready")));
        when(jdbcTemplate.queryForList(contains("from lesson_assets"), eq(102L)))
                .thenReturn(rows(row("id", 1002L, "fileUrl", "/uploads/b.mp4", "transcodingStatus", "ready")));

        LessonPlaybackView first = service.playback(student(), 7L, 101L);
        LessonPlaybackView second = service.playback(student(), 7L, 102L);

        assertEquals("/uploads/a.mp4", first.getVideoUrl());
        assertEquals("/uploads/b.mp4", second.getVideoUrl());
    }

    @Test
    void lessonWithoutAssetReturnsExplicitEmptyState() {
        stubLesson(7L, 103L, "published", "published", 14L);
        when(jdbcTemplate.queryForList(contains("from lesson_assets"), eq(103L))).thenReturn(rows());

        LessonPlaybackView result = service.playback(student(), 7L, 103L);

        assertFalse(result.isPlayable());
        assertEquals("NO_PLAYABLE_VIDEO", result.getReasonCode());
    }

    @Test
    void studentCannotAccessDraftLesson() {
        stubLesson(7L, 104L, "draft", "published", 14L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.playback(student(), 7L, 104L));

        assertEquals(403, exception.getCode());
    }

    @Test
    void ownerCanPreviewDraftLessonWithoutVideo() {
        stubLesson(7L, 105L, "draft", "published", 14L);
        when(jdbcTemplate.queryForList(contains("from lesson_assets"), eq(105L))).thenReturn(rows());

        LessonPlaybackView result = service.playback(row("id", 14L, "role", "teacher"), 7L, 105L);

        assertFalse(result.isPlayable());
        assertEquals("NO_PLAYABLE_VIDEO", result.getReasonCode());
    }

    @Test
    void lessonFromAnotherCourseIsRejected() {
        when(jdbcTemplate.queryForList(contains("from course_lessons l"), eq(7L), eq(999L)))
                .thenReturn(rows());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.playback(student(), 7L, 999L));

        assertEquals(404, exception.getCode());
    }

    private void stubLesson(long courseId, long lessonId, String lessonStatus, String chapterStatus, long teacherId) {
        when(jdbcTemplate.queryForList(contains("from course_lessons l"), eq(courseId), eq(lessonId)))
                .thenReturn(rows(row("courseId", courseId, "teacherId", teacherId, "courseStatus", 1,
                        "reviewStatus", "approved", "chapterStatus", chapterStatus, "lessonId", lessonId,
                        "lessonType", "video", "durationSeconds", 120, "lessonStatus", lessonStatus)));
    }

    private Map<String, Object> student() {
        return row("id", 15L, "role", "student");
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
