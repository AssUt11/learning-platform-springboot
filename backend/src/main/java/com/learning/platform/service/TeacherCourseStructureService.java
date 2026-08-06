package com.learning.platform.service;

import com.learning.platform.dto.CreateChapterRequest;
import com.learning.platform.dto.CreateLessonRequest;
import com.learning.platform.dto.LessonAssetRequest;
import com.learning.platform.dto.ReorderRequest;
import com.learning.platform.dto.UpdateChapterRequest;
import com.learning.platform.dto.UpdateLessonRequest;
import com.learning.platform.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TeacherCourseStructureService {
    private static final Set<String> LESSON_TYPES = new HashSet<String>(
            Arrays.asList("video", "article", "document", "exercise"));
    private static final Set<String> DOCUMENT_EXTENSIONS = new HashSet<String>(
            Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "txt"));
    private static final Set<String> ASSET_STATUSES = new HashSet<String>(
            Arrays.asList("pending", "processing", "ready", "failed"));

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;
    private final LegacyCourseMigrationService legacyCourseMigrationService;

    public TeacherCourseStructureService(JdbcTemplate jdbcTemplate, FileStorageService fileStorageService,
                                         LegacyCourseMigrationService legacyCourseMigrationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
        this.legacyCourseMigrationService = legacyCourseMigrationService;
    }

    @Transactional
    public long createChapter(long teacherId, long publicCourseId, CreateChapterRequest request) {
        CourseRef course = ownedCourse(publicCourseId, teacherId);
        String title = requiredText(request == null ? null : request.getTitle(), "title", 160);
        String description = optionalText(request == null ? null : request.getDescription(), "description", 500);
        int sortOrder = requestedSort(request == null ? null : request.getSortOrder(),
                "select coalesce(max(sort_order),0)+1 from course_chapters where course_id=? and status<>'disabled'",
                course.internalCourseId);
        long chapterId = insertAndReturnId(
                "insert into course_chapters(course_id,title,description,sort_order,status) values(?,?,?,?, 'draft')",
                course.internalCourseId, title, emptyToNull(description), sortOrder);
        markPending(course.publicCourseId);
        return chapterId;
    }

    @Transactional
    public void updateChapter(long teacherId, long chapterId, UpdateChapterRequest request) {
        CourseRef course = courseForChapter(chapterId, teacherId, true);
        String title = requiredText(request == null ? null : request.getTitle(), "title", 160);
        String description = optionalText(request == null ? null : request.getDescription(), "description", 500);
        int sortOrder = request == null || request.getSortOrder() == null
                ? currentSort("course_chapters", chapterId)
                : validSort(request.getSortOrder());
        jdbcTemplate.update(
                "update course_chapters set title=?,description=?,sort_order=?,status='draft' where id=? and status<>'disabled'",
                title, emptyToNull(description), sortOrder, chapterId);
        markPending(course.publicCourseId);
    }

    @Transactional
    public void disableChapter(long teacherId, long chapterId) {
        CourseRef course = courseForChapter(chapterId, teacherId, true);
        jdbcTemplate.update("update course_lessons set status='disabled' where chapter_id=? and status<>'disabled'", chapterId);
        jdbcTemplate.update("update course_chapters set status='disabled' where id=?", chapterId);
        markPending(course.publicCourseId);
    }

    @Transactional
    public void reorderChapters(long teacherId, long publicCourseId, ReorderRequest request) {
        CourseRef course = ownedCourse(publicCourseId, teacherId);
        List<Long> existing = jdbcTemplate.query(
                "select id from course_chapters where course_id=? and status<>'disabled' order by sort_order,id for update",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                course.internalCourseId);
        List<Long> requested = validatedOrder(request, existing, "章节");
        for (int index = 0; index < requested.size(); index++) {
            jdbcTemplate.update("update course_chapters set sort_order=?,status='draft' where id=? and course_id=?",
                    index + 1, requested.get(index), course.internalCourseId);
        }
        markPending(course.publicCourseId);
    }

    @Transactional
    public long createLesson(long teacherId, long chapterId, CreateLessonRequest request) {
        CourseRef course = courseForChapter(chapterId, teacherId, true);
        String title = requiredText(request == null ? null : request.getTitle(), "title", 180);
        String lessonType = lessonType(request == null ? null : request.getLessonType());
        int durationSeconds = duration(request == null ? null : request.getDurationSeconds());
        int freePreview = request != null && Boolean.TRUE.equals(request.getFreePreview()) ? 1 : 0;
        int sortOrder = requestedSort(request == null ? null : request.getSortOrder(),
                "select coalesce(max(sort_order),0)+1 from course_lessons where chapter_id=? and status<>'disabled'",
                chapterId);
        long lessonId = insertAndReturnId(
                "insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) "
                        + "values(?,?,?,?,?,?,'draft')",
                chapterId, title, lessonType, durationSeconds, freePreview, sortOrder);
        markPending(course.publicCourseId);
        return lessonId;
    }

    @Transactional
    public void updateLesson(long teacherId, long lessonId, UpdateLessonRequest request) {
        LessonRef lesson = lessonForTeacher(lessonId, teacherId, true);
        String title = requiredText(request == null ? null : request.getTitle(), "title", 180);
        String lessonType = lessonType(request == null ? null : request.getLessonType());
        int durationSeconds = duration(request == null ? null : request.getDurationSeconds());
        int freePreview = request != null && Boolean.TRUE.equals(request.getFreePreview()) ? 1 : 0;
        int sortOrder = request == null || request.getSortOrder() == null
                ? currentSort("course_lessons", lessonId)
                : validSort(request.getSortOrder());
        jdbcTemplate.update(
                "update course_lessons set title=?,lesson_type=?,duration_seconds=?,free_preview=?,sort_order=?,status='draft' "
                        + "where id=? and status<>'disabled'",
                title, lessonType, durationSeconds, freePreview, sortOrder, lessonId);
        markPending(lesson.course.publicCourseId);
    }

    @Transactional
    public void disableLesson(long teacherId, long lessonId) {
        LessonRef lesson = lessonForTeacher(lessonId, teacherId, true);
        jdbcTemplate.update("update lesson_assets set status=0 where lesson_id=? and status=1", lessonId);
        jdbcTemplate.update("update course_lessons set status='disabled' where id=?", lessonId);
        markPending(lesson.course.publicCourseId);
    }

    @Transactional
    public void reorderLessons(long teacherId, long chapterId, ReorderRequest request) {
        CourseRef course = courseForChapter(chapterId, teacherId, true);
        List<Long> existing = jdbcTemplate.query(
                "select id from course_lessons where chapter_id=? and status<>'disabled' order by sort_order,id for update",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                chapterId);
        List<Long> requested = validatedOrder(request, existing, "课时");
        for (int index = 0; index < requested.size(); index++) {
            jdbcTemplate.update("update course_lessons set sort_order=?,status='draft' where id=? and chapter_id=?",
                    index + 1, requested.get(index), chapterId);
        }
        markPending(course.publicCourseId);
    }

    @Transactional(rollbackFor = Exception.class)
    public long createAsset(long teacherId, long lessonId, String requestedAssetType, String contentText,
                            String coverUrl, MultipartFile file) throws IOException {
        LessonRef lesson = lessonForTeacher(lessonId, teacherId, true);
        String assetType = lessonType(requestedAssetType);
        if (!assetType.equals(lesson.lessonType)) {
            throw new BusinessException(409, "资源类型必须与课时类型一致。");
        }
        validateAssetInput(assetType, contentText, file);
        String storedUrl = fileStorageService.storeResourceFile(file);
        try {
            if ("video".equals(assetType)) {
                jdbcTemplate.update(
                        "update lesson_assets set status=0 where lesson_id=? and asset_type='video' and status=1",
                        lessonId);
            }
            String originalName = file == null || file.isEmpty() ? null
                    : StringUtils.cleanPath(String.valueOf(file.getOriginalFilename()));
            Long fileSize = file == null || file.isEmpty() ? null : Long.valueOf(file.getSize());
            String mimeType = file == null || file.isEmpty() ? "text/html" : emptyToNull(file.getContentType());
            long assetId = insertAndReturnId(
                    "insert into lesson_assets(lesson_id,asset_type,file_url,file_name,file_size,mime_type,cover_url,"
                            + "content_text,transcoding_status,status) values(?,?,?,?,?,?,?,?, 'ready',1)",
                    lessonId, assetType, storedUrl, originalName, fileSize, mimeType,
                    emptyToNull(optionalText(coverUrl, "coverUrl", 255)),
                    emptyToNull(optionalText(contentText, "contentText", 1000000)));
            jdbcTemplate.update("update course_lessons set status='ready' where id=?", lessonId);
            markPending(lesson.course.publicCourseId);
            return assetId;
        } catch (RuntimeException exception) {
            cleanupUpload(storedUrl, exception);
            throw exception;
        }
    }

    @Transactional
    public void updateAsset(long teacherId, long lessonId, long assetId, LessonAssetRequest request) {
        AssetRef asset = assetForTeacher(lessonId, assetId, teacherId);
        String assetType = lessonType(request == null || text(request.getAssetType()).isEmpty()
                ? asset.assetType : request.getAssetType());
        if (!assetType.equals(asset.lesson.lessonType)) {
            throw new BusinessException(409, "资源类型必须与课时类型一致。");
        }
        String contentText = optionalText(request == null ? null : request.getContentText(), "contentText", 1000000);
        String coverUrl = optionalText(request == null ? null : request.getCoverUrl(), "coverUrl", 255);
        String requestedStatus = text(request == null ? null : request.getTranscodingStatus());
        String status = requestedStatus.isEmpty() ? asset.transcodingStatus : assetStatus(requestedStatus);
        if (("article".equals(assetType) || "exercise".equals(assetType)) && contentText.isEmpty()) {
            throw new BusinessException(400, "图文或练习资源内容不能为空。");
        }
        jdbcTemplate.update(
                "update lesson_assets set asset_type=?,content_text=?,cover_url=?,transcoding_status=? "
                        + "where id=? and lesson_id=? and status=1",
                assetType, emptyToNull(contentText), emptyToNull(coverUrl), status, assetId, lessonId);
        refreshLessonReadiness(lessonId);
        markPending(asset.lesson.course.publicCourseId);
    }

    @Transactional
    public void disableAsset(long teacherId, long lessonId, long assetId) {
        AssetRef asset = assetForTeacher(lessonId, assetId, teacherId);
        jdbcTemplate.update("update lesson_assets set status=0 where id=? and lesson_id=?", assetId, lessonId);
        refreshLessonReadiness(lessonId);
        markPending(asset.lesson.course.publicCourseId);
    }

    @Transactional
    public void publishCourseStructure(long publicCourseId) {
        legacyCourseMigrationService.ensureStructure(publicCourseId);
        List<Long> courseIds = jdbcTemplate.query(
                "select id from courses where legacy_resource_id=?",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                publicCourseId);
        if (courseIds.isEmpty()) {
            throw new BusinessException(409, "课程目录尚未初始化。");
        }
        long internalCourseId = courseIds.get(0).longValue();
        jdbcTemplate.update("update course_chapters set status='published' where course_id=? and status<>'disabled'",
                internalCourseId);
        jdbcTemplate.update(
                "update course_lessons l join course_chapters ch on ch.id=l.chapter_id "
                        + "set l.status=case when exists(select 1 from lesson_assets a where a.lesson_id=l.id "
                        + "and a.status=1 and a.transcoding_status='ready') then 'published' else 'draft' end "
                        + "where ch.course_id=? and l.status<>'disabled'",
                internalCourseId);
    }

    private CourseRef ownedCourse(long publicCourseId, long teacherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.id publicCourseId,c.id internalCourseId from resources r "
                        + "join courses c on c.legacy_resource_id=r.id where r.id=? and r.teacher_id=?",
                publicCourseId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "课程不存在或无权编辑。");
        }
        return courseRef(rows.get(0));
    }

    private CourseRef courseForChapter(long chapterId, long teacherId, boolean activeOnly) {
        String active = activeOnly ? " and ch.status<>'disabled'" : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.id publicCourseId,c.id internalCourseId from course_chapters ch "
                        + "join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id "
                        + "where ch.id=? and r.teacher_id=?" + active,
                chapterId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "章节不存在或无权编辑。");
        }
        return courseRef(rows.get(0));
    }

    private LessonRef lessonForTeacher(long lessonId, long teacherId, boolean activeOnly) {
        String active = activeOnly ? " and l.status<>'disabled' and ch.status<>'disabled'" : "";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.id publicCourseId,c.id internalCourseId,l.lesson_type lessonType,l.chapter_id chapterId "
                        + "from course_lessons l join course_chapters ch on ch.id=l.chapter_id "
                        + "join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id "
                        + "where l.id=? and r.teacher_id=?" + active,
                lessonId, teacherId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "课时不存在或无权编辑。");
        }
        Map<String, Object> row = rows.get(0);
        return new LessonRef(lessonId, number(row.get("chapterId")), text(row.get("lessonType")), courseRef(row));
    }

    private AssetRef assetForTeacher(long lessonId, long assetId, long teacherId) {
        LessonRef lesson = lessonForTeacher(lessonId, teacherId, true);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select asset_type assetType,transcoding_status transcodingStatus from lesson_assets "
                        + "where id=? and lesson_id=? and status=1",
                assetId, lessonId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "课时资源不存在或不属于当前课时。");
        }
        return new AssetRef(assetId, text(rows.get(0).get("assetType")),
                text(rows.get(0).get("transcodingStatus")), lesson);
    }

    private void markPending(long publicCourseId) {
        jdbcTemplate.update(
                "update resources set status=0,review_status='pending',review_feedback=null,reviewed_by=null,reviewed_at=null "
                        + "where id=?",
                publicCourseId);
    }

    private void refreshLessonReadiness(long lessonId) {
        Integer ready = jdbcTemplate.queryForObject(
                "select count(*) from lesson_assets where lesson_id=? and status=1 and transcoding_status='ready'",
                Integer.class, lessonId);
        jdbcTemplate.update("update course_lessons set status=? where id=? and status<>'disabled'",
                ready != null && ready > 0 ? "ready" : "draft", lessonId);
    }

    private List<Long> validatedOrder(ReorderRequest request, List<Long> existing, String label) {
        List<Long> requested = request == null ? null : request.getIds();
        if (requested == null) {
            throw new BusinessException(400, label + "排序 ID 列表不能为空。");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<Long>();
        for (Long id : requested) {
            if (id == null || id.longValue() <= 0 || !unique.add(id)) {
                throw new BusinessException(400, label + "排序 ID 必须为不重复的正整数。");
            }
        }
        if (unique.size() != existing.size() || !new HashSet<Long>(existing).equals(new HashSet<Long>(unique))) {
            throw new BusinessException(409, label + "排序必须完整包含当前父级下的全部有效 ID。");
        }
        return new ArrayList<Long>(unique);
    }

    private void validateAssetInput(String assetType, String contentText, MultipartFile file) {
        boolean hasFile = file != null && !file.isEmpty();
        if ("video".equals(assetType)) {
            if (!hasFile || !"mp4".equals(extension(file.getOriginalFilename()))) {
                throw new BusinessException(400, "视频课时必须上传 MP4 文件。");
            }
            return;
        }
        if ("document".equals(assetType)) {
            if (!hasFile || !DOCUMENT_EXTENSIONS.contains(extension(file.getOriginalFilename()))) {
                throw new BusinessException(400, "文档课时必须上传 PDF、Word、PPT 或 TXT 文件。");
            }
            return;
        }
        if (hasFile) {
            throw new BusinessException(400, "图文或练习课时请提交文本内容，不接收文件。");
        }
        if (text(contentText).isEmpty()) {
            throw new BusinessException(400, "图文或练习资源内容不能为空。");
        }
    }

    private String lessonType(Object value) {
        String type = text(value).toLowerCase(Locale.ROOT);
        if (!LESSON_TYPES.contains(type)) {
            throw new BusinessException(400, "lessonType 必须是 video、article、document 或 exercise。");
        }
        return type;
    }

    private String assetStatus(String value) {
        String status = text(value).toLowerCase(Locale.ROOT);
        if (!ASSET_STATUSES.contains(status)) {
            throw new BusinessException(400, "transcodingStatus 参数不合法。");
        }
        return status;
    }

    private int duration(Integer value) {
        int result = value == null ? 0 : value.intValue();
        if (result < 0 || result > 86400) {
            throw new BusinessException(400, "durationSeconds 必须在 0 到 86400 之间。");
        }
        return result;
    }

    private int requestedSort(Integer requested, String sql, long parentId) {
        if (requested != null) {
            return validSort(requested);
        }
        Number next = jdbcTemplate.queryForObject(sql, Number.class, parentId);
        return next == null ? 1 : next.intValue();
    }

    private int validSort(Integer value) {
        if (value == null || value.intValue() <= 0 || value.intValue() > 10000) {
            throw new BusinessException(400, "sortOrder 必须在 1 到 10000 之间。");
        }
        return value.intValue();
    }

    private int currentSort(String table, long id) {
        Number value = jdbcTemplate.queryForObject("select sort_order from " + table + " where id=?", Number.class, id);
        return value == null ? 1 : value.intValue();
    }

    private String requiredText(Object value, String field, int maximumLength) {
        String result = text(value);
        if (result.isEmpty() || result.length() > maximumLength) {
            throw new BusinessException(400, field + " 不能为空且不能超过 " + maximumLength + " 字符。");
        }
        return result;
    }

    private String optionalText(Object value, String field, int maximumLength) {
        String result = text(value);
        if (result.length() > maximumLength) {
            throw new BusinessException(400, field + " 不能超过 " + maximumLength + " 字符。");
        }
        return result;
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
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("无法读取新建课程结构 ID。");
        }
        return key.longValue();
    }

    private void cleanupUpload(String fileUrl, RuntimeException original) {
        try {
            fileStorageService.deleteStoredFile(fileUrl);
        } catch (RuntimeException | IOException cleanupError) {
            original.addSuppressed(cleanupError);
        }
    }

    private String extension(String fileName) {
        String cleaned = StringUtils.cleanPath(String.valueOf(fileName));
        int dot = cleaned.lastIndexOf('.');
        return dot < 0 ? "" : cleaned.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private CourseRef courseRef(Map<String, Object> row) {
        return new CourseRef(number(row.get("publicCourseId")), number(row.get("internalCourseId")));
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static final class CourseRef {
        private final long publicCourseId;
        private final long internalCourseId;

        private CourseRef(long publicCourseId, long internalCourseId) {
            this.publicCourseId = publicCourseId;
            this.internalCourseId = internalCourseId;
        }
    }

    private static final class LessonRef {
        private final long lessonId;
        private final long chapterId;
        private final String lessonType;
        private final CourseRef course;

        private LessonRef(long lessonId, long chapterId, String lessonType, CourseRef course) {
            this.lessonId = lessonId;
            this.chapterId = chapterId;
            this.lessonType = lessonType;
            this.course = course;
        }
    }

    private static final class AssetRef {
        private final long assetId;
        private final String assetType;
        private final String transcodingStatus;
        private final LessonRef lesson;

        private AssetRef(long assetId, String assetType, String transcodingStatus, LessonRef lesson) {
            this.assetId = assetId;
            this.assetType = assetType;
            this.transcodingStatus = transcodingStatus;
            this.lesson = lesson;
        }
    }
}
