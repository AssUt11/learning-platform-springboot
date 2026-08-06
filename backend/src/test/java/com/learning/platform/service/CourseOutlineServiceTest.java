package com.learning.platform.service;

import com.learning.platform.vo.CourseOutlineView;
import com.learning.platform.vo.CourseContentValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseOutlineServiceTest {
    private JdbcTemplate jdbcTemplate;
    private CourseContentValidationService validationService;
    private CourseOutlineService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        validationService = mock(CourseContentValidationService.class);
        CourseContentValidationResult validation = mock(CourseContentValidationResult.class);
        when(validation.isContentComplete()).thenReturn(true);
        when(validationService.validatePublic(7L)).thenReturn(validation);
        service = new CourseOutlineService(jdbcTemplate, validationService);
        when(jdbcTemplate.queryForList(contains("left join courses c"), eq(7L)))
                .thenReturn(rows(row("courseId", 7L, "title", "Java 入门", "teacherId", 3L,
                        "status", 1, "reviewStatus", "approved", "internalCourseId", 24L)));
        when(jdbcTemplate.queryForList(contains("from course_chapters ch where"), eq(24L)))
                .thenReturn(rows(row("id", 11L, "title", "第 1 章", "description", "基础",
                        "sortOrder", 1, "status", "published")));
        when(jdbcTemplate.queryForList(contains("from course_lessons l"), eq(24L)))
                .thenReturn(rows(row("id", 101L, "chapterId", 11L, "title", "开发环境",
                        "lessonType", "video", "durationSeconds", 600, "freePreview", 0,
                        "sortOrder", 1, "status", "published")));
        when(jdbcTemplate.queryForList(contains("from lesson_assets a"), eq(24L)))
                .thenReturn(rows(row("id", 1001L, "lessonId", 101L, "assetType", "video",
                        "fileUrl", "/uploads/java.mp4", "fileName", "java.mp4", "fileSize", 1024L,
                        "mimeType", "video/mp4", "transcodingStatus", "ready")));
    }

    @Test
    void anonymousOutlineContainsMetadataButNotPlaybackUrl() {
        CourseOutlineView outline = service.outline(null, 7L);

        assertEquals(1, outline.getChapterCount());
        assertEquals(1, outline.getLessonCount());
        assertEquals(600, outline.getTotalDurationSeconds());
        assertNull(outline.getChapters().get(0).getLessons().get(0).getPlaybackUrl());
    }

    @Test
    void signedInSchoolUserStillReceivesNoVideoUrlFromOutline() {
        Map<String, Object> student = row("id", 8L, "role", "student");

        CourseOutlineView outline = service.outline(student, 7L);

        assertNull(outline.getChapters().get(0).getLessons().get(0).getPlaybackUrl());
    }

    @SafeVarargs
    private final List<Map<String, Object>> rows(Map<String, Object>... values) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> value : values) result.add(value);
        return result;
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
