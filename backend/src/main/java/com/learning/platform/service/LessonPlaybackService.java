package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.LessonPlaybackView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LessonPlaybackService {
    private final JdbcTemplate jdbcTemplate;
    private final CourseContentValidationService validationService;

    public LessonPlaybackService(JdbcTemplate jdbcTemplate, CourseContentValidationService validationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.validationService = validationService;
    }

    public LessonPlaybackView playback(Map<String, Object> viewer, long publicCourseId, long lessonId) {
        if (viewer == null) throw new BusinessException(401, "请先登录学校账号。");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.id courseId,r.teacher_id teacherId,r.status courseStatus,r.review_status reviewStatus,"
                        + "ch.status chapterStatus,l.id lessonId,l.lesson_type lessonType,l.duration_seconds durationSeconds,"
                        + "l.status lessonStatus from course_lessons l "
                        + "join course_chapters ch on ch.id=l.chapter_id join courses c on c.id=ch.course_id "
                        + "join resources r on r.id=c.legacy_resource_id where r.id=? and l.id=?",
                publicCourseId, lessonId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "课时不存在或不属于当前课程。");
        }
        Map<String, Object> lesson = rows.get(0);
        boolean privileged = "admin".equals(text(viewer.get("role")))
                || ("teacher".equals(text(viewer.get("role"))) && lesson.get("teacherId") != null
                && number(viewer.get("id")) == number(lesson.get("teacherId")));
        boolean publicCourse = number(lesson.get("courseStatus")) == 1L
                && "approved".equals(text(lesson.get("reviewStatus")))
                && validationService.validatePublic(publicCourseId).isContentComplete();
        boolean publicLesson = "published".equals(text(lesson.get("chapterStatus")))
                && "published".equals(text(lesson.get("lessonStatus")));
        if (!(privileged || (publicCourse && publicLesson))) {
            throw new BusinessException(403, "当前课时尚未发布或无权预览。");
        }

        String lessonType = text(lesson.get("lessonType"));
        int durationSeconds = (int) number(lesson.get("durationSeconds"));
        if (!"video".equals(lessonType)) {
            return unavailable(publicCourseId, lessonId, lessonType, durationSeconds, "NOT_VIDEO_LESSON");
        }
        List<Map<String, Object>> assets = jdbcTemplate.queryForList(
                "select id,file_url fileUrl,transcoding_status transcodingStatus from lesson_assets "
                        + "where lesson_id=? and asset_type='video' and status=1 order by id",
                lessonId);
        if (assets.isEmpty()) {
            return unavailable(publicCourseId, lessonId, lessonType, durationSeconds, "NO_PLAYABLE_VIDEO");
        }
        if (assets.size() > 1) {
            return unavailable(publicCourseId, lessonId, lessonType, durationSeconds, "DUPLICATE_ACTIVE_VIDEO_ASSET");
        }
        Map<String, Object> asset = assets.get(0);
        if (!"ready".equals(text(asset.get("transcodingStatus"))) || text(asset.get("fileUrl")).isEmpty()) {
            return unavailable(publicCourseId, lessonId, lessonType, durationSeconds, "ASSET_NOT_READY");
        }
        return new LessonPlaybackView(publicCourseId, lessonId, lessonType, true,
                number(asset.get("id")), text(asset.get("fileUrl")), durationSeconds, null);
    }

    private LessonPlaybackView unavailable(long courseId, long lessonId, String lessonType,
                                           int durationSeconds, String reasonCode) {
        return new LessonPlaybackView(courseId, lessonId, lessonType, false,
                null, null, durationSeconds, reasonCode);
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
