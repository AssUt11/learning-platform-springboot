package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.vo.ChapterView;
import com.learning.platform.vo.CourseOutlineView;
import com.learning.platform.vo.LessonAssetView;
import com.learning.platform.vo.LessonView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseOutlineService {
    private final JdbcTemplate jdbcTemplate;
    private final CourseContentValidationService validationService;

    public CourseOutlineService(JdbcTemplate jdbcTemplate, CourseContentValidationService validationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.validationService = validationService;
    }

    public CourseOutlineView outline(Map<String, Object> viewer, long publicCourseId) {
        if (publicCourseId <= 0) {
            throw new BusinessException(400, "courseId 必须是正整数。");
        }
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(
                "select r.id courseId,r.title,r.teacher_id teacherId,r.status,r.review_status reviewStatus,"
                        + "c.id internalCourseId from resources r "
                        + "left join courses c on c.legacy_resource_id=r.id "
                        + "where r.id=?",
                publicCourseId);
        if (courses.isEmpty()) {
            throw new BusinessException(404, "课程不存在。");
        }
        Map<String, Object> course = courses.get(0);
        if (course.get("internalCourseId") == null) {
            throw new BusinessException(409, "课程目录尚未初始化，请稍后重试。");
        }
        boolean publicCourse = number(course.get("status")) == 1L
                && "approved".equals(text(course.get("reviewStatus")))
                && validationService.validatePublic(publicCourseId).isContentComplete();
        boolean privileged = isAdmin(viewer) || isOwner(viewer, course.get("teacherId"));
        if (!publicCourse && !privileged) {
            throw new BusinessException(404, "课程不存在或尚未发布。");
        }
        boolean includePrivateContent = viewer != null && (publicCourse || privileged);
        long internalCourseId = number(course.get("internalCourseId"));
        String chapterVisibility = privileged ? "ch.status<>'disabled'" : "ch.status='published'";
        String lessonVisibility = privileged ? "l.status<>'disabled'" : "l.status='published'";

        List<Map<String, Object>> chapterRows = jdbcTemplate.queryForList(
                "select ch.id,ch.title,ch.description,ch.sort_order sortOrder,ch.status "
                        + "from course_chapters ch where ch.course_id=? and " + chapterVisibility
                        + " order by ch.sort_order,ch.id",
                internalCourseId);
        List<Map<String, Object>> lessonRows = jdbcTemplate.queryForList(
                "select l.id,l.chapter_id chapterId,l.title,l.lesson_type lessonType,"
                        + "l.duration_seconds durationSeconds,l.free_preview freePreview,"
                        + "l.sort_order sortOrder,l.status from course_lessons l "
                        + "join course_chapters ch on ch.id=l.chapter_id "
                        + "where ch.course_id=? and " + chapterVisibility + " and " + lessonVisibility
                        + " order by ch.sort_order,ch.id,l.sort_order,l.id",
                internalCourseId);
        List<Map<String, Object>> assetRows = jdbcTemplate.queryForList(
                "select a.id,a.lesson_id lessonId,a.asset_type assetType,a.file_url fileUrl,"
                        + "a.file_name fileName,a.file_size fileSize,a.mime_type mimeType,a.cover_url coverUrl,"
                        + "a.content_text contentText,a.transcoding_status transcodingStatus "
                        + "from lesson_assets a join course_lessons l on l.id=a.lesson_id "
                        + "join course_chapters ch on ch.id=l.chapter_id "
                        + "where ch.course_id=? and a.status=1 and " + chapterVisibility + " and " + lessonVisibility
                        + " order by a.lesson_id,a.id",
                internalCourseId);

        Map<Long, List<LessonAssetView>> assetsByLesson = new LinkedHashMap<Long, List<LessonAssetView>>();
        for (Map<String, Object> asset : assetRows) {
            long lessonId = number(asset.get("lessonId"));
            String ready = text(asset.get("transcodingStatus"));
            String assetType = text(asset.get("assetType"));
            String playbackUrl = includePrivateContent && "ready".equals(ready) && !"video".equals(assetType)
                    ? nullableText(asset.get("fileUrl")) : null;
            assetsByLesson.computeIfAbsent(lessonId, ignored -> new ArrayList<LessonAssetView>()).add(
                    new LessonAssetView(number(asset.get("id")), assetType,
                            nullableText(asset.get("fileName")), nullableLong(asset.get("fileSize")),
                            nullableText(asset.get("mimeType")), nullableText(asset.get("coverUrl")),
                            includePrivateContent && "ready".equals(ready) ? nullableText(asset.get("contentText")) : null,
                            ready,
                            playbackUrl));
        }

        Map<Long, List<LessonView>> lessonsByChapter = new LinkedHashMap<Long, List<LessonView>>();
        int totalDurationSeconds = 0;
        for (Map<String, Object> lesson : lessonRows) {
            long lessonId = number(lesson.get("id"));
            List<LessonAssetView> assets = assetsByLesson.getOrDefault(lessonId, new ArrayList<LessonAssetView>());
            LessonAssetView primary = firstReady(assets);
            int durationSeconds = (int) number(lesson.get("durationSeconds"));
            totalDurationSeconds += durationSeconds;
            LessonView view = new LessonView(lessonId, text(lesson.get("title")), text(lesson.get("lessonType")),
                    durationSeconds, number(lesson.get("freePreview")) == 1L,
                    (int) number(lesson.get("sortOrder")), text(lesson.get("status")), primary != null,
                    primary == null ? null : primary.getPlaybackUrl(),
                    primary == null ? null : primary.getContentText(), assets);
            lessonsByChapter.computeIfAbsent(number(lesson.get("chapterId")), ignored -> new ArrayList<LessonView>())
                    .add(view);
        }

        List<ChapterView> chapters = new ArrayList<ChapterView>();
        for (Map<String, Object> chapter : chapterRows) {
            long chapterId = number(chapter.get("id"));
            chapters.add(new ChapterView(chapterId, text(chapter.get("title")),
                    nullableText(chapter.get("description")), (int) number(chapter.get("sortOrder")),
                    text(chapter.get("status")),
                    lessonsByChapter.getOrDefault(chapterId, new ArrayList<LessonView>())));
        }
        return new CourseOutlineView(publicCourseId, text(course.get("title")), chapters.size(), lessonRows.size(),
                totalDurationSeconds, chapters);
    }

    private LessonAssetView firstReady(List<LessonAssetView> assets) {
        for (LessonAssetView asset : assets) {
            if ("ready".equals(asset.getTranscodingStatus())) {
                return asset;
            }
        }
        return null;
    }

    private boolean isAdmin(Map<String, Object> viewer) {
        return viewer != null && "admin".equals(viewer.get("role"));
    }

    private boolean isOwner(Map<String, Object> viewer, Object teacherId) {
        return viewer != null && "teacher".equals(viewer.get("role")) && teacherId != null
                && number(viewer.get("id")) == number(teacherId);
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        return value == null ? null : Long.valueOf(number(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String result = text(value);
        return result.isEmpty() ? null : result;
    }
}
