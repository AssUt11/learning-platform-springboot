package com.learning.platform.service;

import com.learning.platform.vo.CourseContentValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseContentValidationServiceTest {
    private JdbcTemplate jdbcTemplate;
    private CourseContentValidationService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new CourseContentValidationService(jdbcTemplate);
    }

    @Test
    void videoCourseWithoutChapterFails() {
        stub(1L, rows(), rows(), rows());

        CourseContentValidationResult result = service.validatePublic(1L);

        assertFalse(result.isContentComplete());
        assertTrue(result.getReasonCodes().contains("NO_CHAPTER"));
        assertTrue(result.getReasonCodes().contains("NO_PUBLISHED_LESSON"));
    }

    @Test
    void publishedVideoLessonWithoutAssetFails() {
        stub(2L,
                rows(row("id", 21L, "status", "published")),
                rows(videoLesson(201L, "published", "published")),
                rows());

        CourseContentValidationResult result = service.validatePublic(2L);

        assertFalse(result.isContentComplete());
        assertTrue(result.getReasonCodes().contains("VIDEO_LESSON_WITHOUT_PLAYABLE_ASSET"));
        assertTrue(result.getReasonCodes().contains("NO_PLAYABLE_VIDEO"));
    }

    @Test
    void duplicateActiveVideoAssetsFail() {
        stub(3L,
                rows(row("id", 31L, "status", "published")),
                rows(videoLesson(301L, "published", "published")),
                rows(videoAsset(3001L, 301L, "/uploads/a.mp4"), videoAsset(3002L, 301L, "/uploads/b.mp4")));

        CourseContentValidationResult result = service.validatePublic(3L);

        assertFalse(result.isContentComplete());
        assertTrue(result.getReasonCodes().contains("DUPLICATE_ACTIVE_VIDEO_ASSET"));
    }

    @Test
    void completeVideoCoursePasses() {
        stub(4L,
                rows(row("id", 41L, "status", "published")),
                rows(videoLesson(401L, "published", "published")),
                rows(videoAsset(4001L, 401L, "/uploads/a.mp4")));

        CourseContentValidationResult result = service.validatePublic(4L);

        assertTrue(result.isContentComplete());
        assertTrue(result.isPublishable());
        assertTrue(result.getReasonCodes().isEmpty());
    }

    @Test
    void missingTeacherIsReportedWithoutHidingLegacyCompleteCourse() {
        stub(5L, null,
                rows(row("id", 51L, "status", "published")),
                rows(videoLesson(501L, "published", "published")),
                rows(videoAsset(5001L, 501L, "/uploads/a.mp4")));

        CourseContentValidationResult result = service.validatePublic(5L);

        assertTrue(result.isContentComplete());
        assertFalse(result.isPublishable());
        assertTrue(result.getReasonCodes().contains("TEACHER_MISSING"));
    }

    private void stub(long courseId, List<Map<String, Object>> chapters,
                      List<Map<String, Object>> lessons, List<Map<String, Object>> assets) {
        stub(courseId, 14L, chapters, lessons, assets);
    }

    private void stub(long courseId, Long teacherId, List<Map<String, Object>> chapters,
                      List<Map<String, Object>> lessons, List<Map<String, Object>> assets) {
        when(jdbcTemplate.queryForList(contains("from resources r left join courses"), eq(courseId)))
                .thenReturn(rows(row("id", courseId, "title", "示例课程", "type", "video",
                        "teacherId", teacherId, "status", 1, "reviewStatus", "approved", "internalCourseId", 99L)));
        when(jdbcTemplate.queryForList(contains("from course_chapters where"), eq(99L))).thenReturn(chapters);
        when(jdbcTemplate.queryForList(contains("from course_lessons l join"), eq(99L))).thenReturn(lessons);
        when(jdbcTemplate.queryForList(contains("from lesson_assets a join"), eq(99L))).thenReturn(assets);
    }

    private Map<String, Object> videoLesson(long id, String lessonStatus, String chapterStatus) {
        return row("id", id, "lessonType", "video", "durationSeconds", 120,
                "status", lessonStatus, "chapterStatus", chapterStatus);
    }

    private Map<String, Object> videoAsset(long id, long lessonId, String url) {
        return row("id", id, "lessonId", lessonId, "assetType", "video", "fileUrl", url,
                "transcodingStatus", "ready");
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
