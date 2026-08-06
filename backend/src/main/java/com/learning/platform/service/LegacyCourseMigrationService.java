package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LegacyCourseMigrationService {
    private final JdbcTemplate jdbcTemplate;

    public LegacyCourseMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void migrateAll() {
        List<Long> resourceIds = jdbcTemplate.query(
                "select id from resources order by id",
                (resultSet, rowNumber) -> resultSet.getLong("id"));
        for (Long resourceId : resourceIds) {
            ensureStructure(resourceId.longValue());
        }
    }

    @Transactional
    public ProvisionedCourse ensureStructure(long resourceId) {
        List<Map<String, Object>> resources = jdbcTemplate.queryForList(
                "select id,teacher_id,course_id,title,subject,description,type,file_url,content,"
                        + "estimated_minutes,status,review_status,created_at,updated_at "
                        + "from resources where id=? for update",
                resourceId);
        if (resources.isEmpty()) {
            throw new BusinessException(404, "课程不存在。");
        }
        Map<String, Object> resource = resources.get(0);
        Long previousCourseId = nullableNumber(resource.get("course_id"));
        long courseId = resolveCourse(resource);
        jdbcTemplate.update("update resources set course_id=? where id=? and (course_id is null or course_id<>?)",
                courseId, resourceId, courseId);
        syncCourse(resourceId, courseId);

        List<Map<String, Object>> completed = jdbcTemplate.queryForList(
                "select course_id courseId,chapter_id chapterId,lesson_id lessonId,asset_id assetId "
                        + "from course_legacy_migration where resource_id=?",
                resourceId);
        if (!completed.isEmpty()) {
            Map<String, Object> row = completed.get(0);
            return new ProvisionedCourse(resourceId, number(row.get("courseId")), number(row.get("chapterId")),
                    number(row.get("lessonId")), nullableNumber(row.get("assetId")));
        }

        boolean published = number(resource.get("status")) == 1L
                && "approved".equals(String.valueOf(resource.get("review_status")));
        long chapterId = firstChapter(courseId, published);
        String lessonType = lessonType(resource);
        boolean hasAsset = hasMigratableAsset(resource, lessonType);
        long lessonId = firstLesson(chapterId, resource, lessonType, published && hasAsset);
        Long assetId = firstAsset(lessonId, resource, lessonType);

        jdbcTemplate.update(
                "insert into course_legacy_migration(resource_id,previous_course_id,course_id,chapter_id,lesson_id,asset_id) "
                        + "values(?,?,?,?,?,?) on duplicate key update course_id=values(course_id),"
                        + "chapter_id=values(chapter_id),lesson_id=values(lesson_id),asset_id=values(asset_id)",
                resourceId, previousCourseId, courseId, chapterId, lessonId, assetId);
        return new ProvisionedCourse(resourceId, courseId, chapterId, lessonId, assetId);
    }

    @Transactional
    public void syncCourse(long resourceId) {
        List<Long> courseIds = jdbcTemplate.query(
                "select id from courses where legacy_resource_id=?",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                resourceId);
        if (!courseIds.isEmpty()) {
            syncCourse(resourceId, courseIds.get(0).longValue());
        }
    }

    private long resolveCourse(Map<String, Object> resource) {
        long resourceId = number(resource.get("id"));
        List<Long> mapped = jdbcTemplate.query(
                "select id from courses where legacy_resource_id=? for update",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                resourceId);
        if (!mapped.isEmpty()) {
            return mapped.get(0).longValue();
        }

        Long existingCourseId = nullableNumber(resource.get("course_id"));
        if (existingCourseId != null) {
            int bound = jdbcTemplate.update(
                    "update courses set legacy_resource_id=? where id=? and legacy_resource_id is null",
                    resourceId, existingCourseId);
            if (bound == 1) {
                return existingCourseId.longValue();
            }
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into courses(legacy_resource_id,teacher_id,title,subject,description,created_at,updated_at) "
                            + "values(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, resourceId);
            setNullableLong(statement, 2, nullableNumber(resource.get("teacher_id")));
            statement.setString(3, text(resource.get("title")));
            statement.setString(4, text(resource.get("subject")));
            statement.setString(5, text(resource.get("description")));
            statement.setObject(6, resource.get("created_at"));
            statement.setObject(7, resource.get("updated_at"));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "course");
    }

    private void syncCourse(long resourceId, long courseId) {
        jdbcTemplate.update(
                "update courses c join resources r on r.id=? set c.teacher_id=r.teacher_id,c.title=r.title,"
                        + "c.subject=r.subject,c.description=r.description,c.updated_at=r.updated_at where c.id=?",
                resourceId, courseId);
    }

    private long firstChapter(long courseId, boolean published) {
        List<Long> chapterIds = jdbcTemplate.query(
                "select id from course_chapters where course_id=? order by sort_order,id limit 1 for update",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                courseId);
        if (!chapterIds.isEmpty()) {
            return chapterIds.get(0).longValue();
        }
        return insertAndReturnId(
                "insert into course_chapters(course_id,title,description,sort_order,status) values(?,?,?,?,?)",
                courseId, "第 1 章", "课程导学与基础内容", 1, published ? "published" : "draft");
    }

    private long firstLesson(long chapterId, Map<String, Object> resource, String lessonType, boolean published) {
        List<Long> lessonIds = jdbcTemplate.query(
                "select id from course_lessons where chapter_id=? order by sort_order,id limit 1 for update",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                chapterId);
        if (!lessonIds.isEmpty()) {
            return lessonIds.get(0).longValue();
        }
        int estimatedMinutes = Math.max(0, (int) number(resource.get("estimated_minutes")));
        String status = published ? "published" : hasMigratableAsset(resource, lessonType) ? "ready" : "draft";
        return insertAndReturnId(
                "insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) "
                        + "values(?,?,?,?,?,?,?)",
                chapterId, text(resource.get("title")), lessonType, estimatedMinutes * 60, 0, 1, status);
    }

    private Long firstAsset(long lessonId, Map<String, Object> resource, String lessonType) {
        List<Long> assetIds = jdbcTemplate.query(
                "select id from lesson_assets where lesson_id=? and status=1 order by id limit 1 for update",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                lessonId);
        if (!assetIds.isEmpty()) {
            return assetIds.get(0);
        }
        if (!hasMigratableAsset(resource, lessonType)) {
            return null;
        }
        String fileUrl = text(resource.get("file_url"));
        String content = "article".equals(lessonType) ? text(resource.get("content")) : null;
        return insertAndReturnId(
                "insert into lesson_assets(lesson_id,asset_type,file_url,file_name,file_size,mime_type,content_text,"
                        + "transcoding_status,status) values(?,?,?,?,?,?,?,?,1)",
                lessonId, lessonType, emptyToNull(fileUrl), fileName(fileUrl), null, mimeType(fileUrl, lessonType),
                emptyToNull(content), "ready");
    }

    private long insertAndReturnId(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "course structure");
    }

    private long generatedId(KeyHolder keyHolder, String target) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to read generated " + target + " ID.");
        }
        return key.longValue();
    }

    private String lessonType(Map<String, Object> resource) {
        if ("video".equals(text(resource.get("type")))) {
            return "video";
        }
        return text(resource.get("file_url")).isEmpty() ? "article" : "document";
    }

    private boolean hasMigratableAsset(Map<String, Object> resource, String lessonType) {
        if ("article".equals(lessonType)) {
            return !text(resource.get("content")).isEmpty();
        }
        return !text(resource.get("file_url")).isEmpty();
    }

    private String fileName(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }
        int slash = fileUrl.lastIndexOf('/');
        return slash >= 0 ? fileUrl.substring(slash + 1) : fileUrl;
    }

    private String mimeType(String fileUrl, String lessonType) {
        String lower = text(fileUrl).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".txt")) return "text/plain";
        return "article".equals(lessonType) ? "text/html" : null;
    }

    private void setNullableLong(PreparedStatement statement, int parameterIndex, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setObject(parameterIndex, null);
        } else {
            statement.setLong(parameterIndex, value.longValue());
        }
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : Long.valueOf(number(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public static final class ProvisionedCourse {
        private final long resourceId;
        private final long courseId;
        private final long chapterId;
        private final long lessonId;
        private final Long assetId;

        public ProvisionedCourse(long resourceId, long courseId, long chapterId, long lessonId, Long assetId) {
            this.resourceId = resourceId;
            this.courseId = courseId;
            this.chapterId = chapterId;
            this.lessonId = lessonId;
            this.assetId = assetId;
        }

        public long getResourceId() { return resourceId; }
        public long getCourseId() { return courseId; }
        public long getChapterId() { return chapterId; }
        public long getLessonId() { return lessonId; }
        public Long getAssetId() { return assetId; }
    }
}
