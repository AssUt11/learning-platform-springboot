package com.learning.platform.service;

import com.learning.platform.vo.CourseContentValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
public class DemoCourseInitializerService {
    public static final String SEED_KEY = "demo-course-two-chapters-two-videos-v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoCourseInitializerService.class);

    private final JdbcTemplate jdbcTemplate;
    private final CourseContentValidationService validationService;

    public DemoCourseInitializerService(JdbcTemplate jdbcTemplate,
                                        CourseContentValidationService validationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.validationService = validationService;
    }

    @Transactional
    public void ensureDemoCourse() {
        Integer seeded = jdbcTemplate.queryForObject(
                "select count(*) from course_demo_seeds where seed_key=?", Integer.class, SEED_KEY);
        if (seeded != null && seeded > 0) return;

        List<Map<String, Object>> teachers = jdbcTemplate.queryForList(
                "select id,name,email from users where role='teacher' order by id");
        if (teachers.size() != 1) {
            LOGGER.warn("Demo course was not initialized because exactly one teacher is required; found {}.",
                    teachers.size());
            return;
        }
        List<Map<String, Object>> sourceVideos = jdbcTemplate.queryForList(
                "select a.id sourceAssetId,a.file_url fileUrl,a.file_name fileName,a.file_size fileSize,"
                        + "a.mime_type mimeType,r.category_id categoryId,r.direction_id directionId "
                        + "from lesson_assets a join (select min(id) id from lesson_assets "
                        + "where asset_type='video' and status=1 and transcoding_status='ready' "
                        + "and file_url is not null and file_url<>'' group by file_url) picked on picked.id=a.id "
                        + "join course_lessons l on l.id=a.lesson_id "
                        + "join course_chapters ch on ch.id=l.chapter_id join courses c on c.id=ch.course_id "
                        + "join resources r on r.id=c.legacy_resource_id "
                        + "order by a.id limit 2");
        if (sourceVideos.size() != 2) {
            LOGGER.warn("Demo course was not initialized because two distinct ready local videos are required; found {}.",
                    sourceVideos.size());
            return;
        }

        long teacherId = number(teachers.get(0).get("id"));
        int videoADuration = measuredDurationSeconds(text(sourceVideos.get(0).get("fileUrl")));
        int videoBDuration = measuredDurationSeconds(text(sourceVideos.get(1).get("fileUrl")));
        long resourceId = insertAndReturnId(
                "insert into resources(teacher_id,category_id,direction_id,title,subject,type,level,description,content,"
                        + "file_url,estimated_minutes,learner_count,status,review_status) "
                        + "values(?,?,?,?,?,'video','入门',?,?,null,?,0,0,'pending')",
                teacherId, sourceVideos.get(0).get("categoryId"), sourceVideos.get(0).get("directionId"),
                "多章节视频课程示例", "Java",
                "使用两个现有本地视频验证章节、课时和视频资源之间的一对一播放关系。",
                "本课程用于验证真实多章节课程结构。第一章学习基础内容，第二章进入进阶实践。",
                (videoADuration + videoBDuration + 59) / 60);
        long internalCourseId = insertAndReturnId(
                "insert into courses(legacy_resource_id,teacher_id,title,subject,description) values(?,?,?,?,?)",
                resourceId, teacherId, "多章节视频课程示例", "Java",
                "两个章节分别绑定不同本地视频，不使用课程级视频地址。" );
        jdbcTemplate.update("update resources set course_id=? where id=?", internalCourseId, resourceId);

        long chapterA = insertAndReturnId(
                "insert into course_chapters(course_id,title,description,sort_order,status) values(?,?,?,?, 'draft')",
                internalCourseId, "第 1 章 · 基础入门", "通过第一个本地视频建立基础理解。", 1);
        long chapterB = insertAndReturnId(
                "insert into course_chapters(course_id,title,description,sort_order,status) values(?,?,?,?, 'draft')",
                internalCourseId, "第 2 章 · 进阶实践", "第二个本地视频只服务于本章课时。", 2);
        long lessonA = insertAndReturnId(
                "insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) "
                        + "values(?,?,'video',?,0,1,'ready')",
                chapterA, "1-1 视频 A：基础演示", videoADuration);
        long lessonB = insertAndReturnId(
                "insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) "
                        + "values(?,?,'video',?,0,1,'ready')",
                chapterB, "2-1 视频 B：进阶演示", videoBDuration);
        insertAndReturnId(
                "insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) "
                        + "values(?,?,'video',0,0,2,'draft')",
                chapterB, "2-2 待补充视频（教师预览测试）");
        long assetA = copyVideoAsset(lessonA, sourceVideos.get(0));
        long assetB = copyVideoAsset(lessonB, sourceVideos.get(1));

        CourseContentValidationResult candidate = validationService.validateForPublication(resourceId);
        if (!candidate.isPublishable()) {
            throw new IllegalStateException("Demo course validation failed: " + candidate.getReasons());
        }
        jdbcTemplate.update("update course_chapters set status='published' where course_id=?", internalCourseId);
        jdbcTemplate.update("update course_lessons set status='published' where id in (?,?)", lessonA, lessonB);
        CourseContentValidationResult published = validationService.validatePublic(resourceId);
        if (!published.isContentComplete()) {
            throw new IllegalStateException("Published demo course validation failed: " + published.getReasons());
        }
        jdbcTemplate.update("update resources set status=1,review_status='approved' where id=?", resourceId);
        jdbcTemplate.update(
                "insert into course_demo_seeds(seed_key,resource_id,teacher_id,chapter_a_id,chapter_b_id,"
                        + "lesson_a_id,lesson_b_id,asset_a_id,asset_b_id) values(?,?,?,?,?,?,?,?,?)",
                SEED_KEY, resourceId, teacherId, chapterA, chapterB, lessonA, lessonB, assetA, assetB);
    }

    private long copyVideoAsset(long lessonId, Map<String, Object> source) {
        return insertAndReturnId(
                "insert into lesson_assets(lesson_id,asset_type,file_url,file_name,file_size,mime_type,"
                        + "transcoding_status,status) values(?,'video',?,?,?,?,'ready',1)",
                lessonId, source.get("fileUrl"), source.get("fileName"), source.get("fileSize"), source.get("mimeType"));
    }

    private int measuredDurationSeconds(String fileUrl) {
        if (fileUrl.startsWith("/uploads/1779430126093-")) return 106;
        if (fileUrl.startsWith("/uploads/1779430545143-")) return 311;
        throw new IllegalStateException("No verified media duration is recorded for " + fileUrl);
    }

    private long insertAndReturnId(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Failed to read generated demo course ID.");
        return key.longValue();
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
