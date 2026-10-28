package com.learning.platform.service;

import com.learning.platform.dto.LessonProgressRequest;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.CourseLearningProgressView;
import com.learning.platform.vo.LessonProgressView;
import com.learning.platform.vo.ResumeLearningView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LessonProgressService {
    private static final int MAX_REPORTED_DURATION_SECONDS = 24 * 60 * 60;

    private final JdbcTemplate jdbcTemplate;
    private final CourseContentValidationService validationService;

    public LessonProgressService(JdbcTemplate jdbcTemplate,
                                 CourseContentValidationService validationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.validationService = validationService;
    }

    public LessonProgressView save(long userId, long lessonId, LessonProgressRequest request) {
        if (userId <= 0 || lessonId <= 0 || request == null || request.getCourseId() == null
                || request.getCourseId() <= 0) {
            throw new BusinessException(400, "课程、课时和学习进度参数不能为空。");
        }
        long courseId = request.getCourseId();
        Map<String, Object> lesson = requirePublishedLesson(courseId, lessonId);
        String lessonType = text(lesson.get("lessonType"));
        int lessonDuration = nonNegativeInt(lesson.get("durationSeconds"));
        int reportedDuration = clamp(request.getDurationSeconds() == null ? 0 : request.getDurationSeconds(),
                0, MAX_REPORTED_DURATION_SECONDS);
        int duration = "video".equals(lessonType)
                ? (lessonDuration > 0 ? lessonDuration : reportedDuration)
                : 1;
        int position = clamp(request.getPositionSeconds() == null ? 0 : request.getPositionSeconds(),
                0, duration > 0 ? duration : MAX_REPORTED_DURATION_SECONDS);
        String eventType = text(request.getEventType()).toLowerCase();

        boolean completed;
        if ("video".equals(lessonType)) {
            completed = duration > 0 && position >= duration;
        } else {
            completed = "completed".equals(eventType);
            position = completed ? 1 : 0;
        }
        int progress = duration <= 0 ? 0 : Math.min(100, (int) Math.floor(position * 100.0 / duration));
        if ("video".equals(lessonType) && position > 0 && duration > 0) progress = Math.max(1, progress);
        if (!"video".equals(lessonType) && "reading".equals(eventType)) {
            progress = clamp(request.getReadingProgressPercent() == null ? 1 : request.getReadingProgressPercent(), 1, 99);
        }
        if (completed) progress = 100;

        jdbcTemplate.update(
                "insert into lesson_progress(user_id,lesson_id,last_position_seconds,duration_seconds,progress_percent,completed) "
                        + "values(?,?,?,?,?,?) on duplicate key update "
                        + "last_position_seconds=greatest(last_position_seconds,values(last_position_seconds)),"
                        + "duration_seconds=greatest(duration_seconds,values(duration_seconds)),"
                        + "progress_percent=greatest(progress_percent,values(progress_percent)),"
                        + "completed=greatest(completed,values(completed)),last_studied_at=current_timestamp",
                userId, lessonId, position, duration, progress, completed ? 1 : 0);
        return readProgress(userId, courseId, lessonId);
    }

    public CourseLearningProgressView courseProgress(long userId, long courseId) {
        Map<String, Object> course = requirePublicCourse(courseId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select l.id lessonId,l.duration_seconds lessonDuration,"
                        + "coalesce(lp.last_position_seconds,0) lastPositionSeconds,"
                        + "coalesce(lp.duration_seconds,0) savedDurationSeconds,"
                        + "coalesce(lp.progress_percent,0) progressPercent,coalesce(lp.completed,0) completed,"
                        + "lp.last_studied_at lastStudiedAt from course_lessons l "
                        + "join course_chapters ch on ch.id=l.chapter_id "
                        + "join courses c on c.id=ch.course_id "
                        + "left join lesson_progress lp on lp.lesson_id=l.id and lp.user_id=? "
                        + "where c.legacy_resource_id=? and ch.status='published' and l.status='published' "
                        + "order by ch.sort_order,ch.id,l.sort_order,l.id",
                userId, courseId);
        List<LessonProgressView> lessons = new ArrayList<LessonProgressView>();
        int completedLessons = 0;
        int totalProgress = 0;
        int totalDuration = 0;
        int watchedSeconds = 0;
        Long lastLessonId = null;
        Timestamp latest = null;
        for (Map<String, Object> row : rows) {
            long lessonId = number(row.get("lessonId"));
            int lessonDuration = nonNegativeInt(row.get("lessonDuration"));
            int savedDuration = nonNegativeInt(row.get("savedDurationSeconds"));
            int duration = lessonDuration > 0 ? lessonDuration : savedDuration;
            int position = clamp(nonNegativeInt(row.get("lastPositionSeconds")), 0,
                    duration > 0 ? duration : MAX_REPORTED_DURATION_SECONDS);
            int progress = clamp(nonNegativeInt(row.get("progressPercent")), 0, 100);
            boolean completed = number(row.get("completed")) == 1L;
            Timestamp studiedAt = timestamp(row.get("lastStudiedAt"));
            lessons.add(new LessonProgressView(courseId, lessonId, position, duration, progress, completed, studiedAt));
            totalProgress += progress;
            totalDuration += duration;
            watchedSeconds += position;
            if (completed) completedLessons++;
            if (studiedAt != null && (latest == null || studiedAt.after(latest))) {
                latest = studiedAt;
                lastLessonId = lessonId;
            }
        }
        if (lastLessonId == null && !lessons.isEmpty()) lastLessonId = lessons.get(0).getLessonId();
        int progressPercent = lessons.isEmpty() ? 0 : completedLessons == lessons.size() ? 100
                : Math.min(99, Math.round(totalProgress / (float) lessons.size()));
        return new CourseLearningProgressView(courseId, text(course.get("title")), lessons.size(),
                completedLessons, progressPercent, totalDuration, watchedSeconds, lastLessonId, lessons);
    }

    public ResumeLearningView resume(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.id courseId,r.title courseTitle,l.id lessonId,l.title lessonTitle,"
                        + "lp.last_position_seconds lastPositionSeconds,lp.progress_percent progressPercent,"
                        + "lp.last_studied_at lastStudiedAt from lesson_progress lp "
                        + "join course_lessons l on l.id=lp.lesson_id "
                        + "join course_chapters ch on ch.id=l.chapter_id "
                        + "join courses c on c.id=ch.course_id "
                        + "join resources r on r.id=c.legacy_resource_id "
                        + "where lp.user_id=? and r.status=1 and r.review_status='approved' "
                        + "and ch.status='published' and l.status='published' "
                        + "order by lp.last_studied_at desc limit 20",
                userId);
        for (Map<String, Object> row : rows) {
            long courseId = number(row.get("courseId"));
            if (!validationService.validatePublic(courseId).isContentComplete()) continue;
            return new ResumeLearningView(true, courseId, text(row.get("courseTitle")),
                    number(row.get("lessonId")), text(row.get("lessonTitle")),
                    nonNegativeInt(row.get("lastPositionSeconds")),
                    clamp(nonNegativeInt(row.get("progressPercent")), 0, 100),
                    timestamp(row.get("lastStudiedAt")));
        }
        return ResumeLearningView.unavailable();
    }

    private LessonProgressView readProgress(long userId, long courseId, long lessonId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select last_position_seconds lastPositionSeconds,duration_seconds durationSeconds,"
                        + "progress_percent progressPercent,completed,last_studied_at lastStudiedAt "
                        + "from lesson_progress where user_id=? and lesson_id=?",
                userId, lessonId);
        if (rows.isEmpty()) throw new BusinessException(500, "课时进度保存失败，请稍后重试。");
        Map<String, Object> row = rows.get(0);
        return new LessonProgressView(courseId, lessonId,
                nonNegativeInt(row.get("lastPositionSeconds")), nonNegativeInt(row.get("durationSeconds")),
                clamp(nonNegativeInt(row.get("progressPercent")), 0, 100),
                number(row.get("completed")) == 1L, timestamp(row.get("lastStudiedAt")));
    }

    private Map<String, Object> requirePublishedLesson(long courseId, long lessonId) {
        Map<String, Object> course = requirePublicCourse(courseId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select l.id lessonId,l.lesson_type lessonType,l.duration_seconds durationSeconds "
                        + "from course_lessons l join course_chapters ch on ch.id=l.chapter_id "
                        + "join courses c on c.id=ch.course_id "
                        + "where c.legacy_resource_id=? and l.id=? and ch.status='published' and l.status='published'",
                courseId, lessonId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "课时不存在、未发布或不属于当前课程。");
        }
        Map<String, Object> lesson = rows.get(0);
        lesson.put("courseTitle", course.get("title"));
        return lesson;
    }

    private Map<String, Object> requirePublicCourse(long courseId) {
        if (courseId <= 0) throw new BusinessException(400, "courseId 必须是正整数。");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id,title,status,review_status reviewStatus from resources where id=?",
                courseId);
        if (rows.isEmpty()) throw new BusinessException(404, "课程不存在或尚未发布。");
        Map<String, Object> course = rows.get(0);
        boolean visible = number(course.get("status")) == 1L
                && "approved".equals(text(course.get("reviewStatus")))
                && validationService.validatePublic(courseId).isContentComplete();
        if (!visible) throw new BusinessException(404, "课程不存在或尚未发布。");
        return course;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private int nonNegativeInt(Object value) {
        return value == null ? 0 : (int) Math.max(0, number(value));
    }

    private long number(Object value) {
        if (value == null) return 0L;
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Timestamp timestamp(Object value) {
        return value instanceof Timestamp ? (Timestamp) value : null;
    }
}
