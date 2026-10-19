package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.Statement;
import java.util.List;

/** Imports versioned, original reading lessons once; later teacher edits are preserved. */
@Service
public class FoundationCourseInitializerService {
    private static final Logger LOG = LoggerFactory.getLogger(FoundationCourseInitializerService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CourseContentValidationService validation;
    private final long configuredTeacher;

    public FoundationCourseInitializerService(JdbcTemplate jdbc, ObjectMapper mapper,
            CourseContentValidationService validation, @Value("${app.catalog.seed-teacher-id:0}") long configuredTeacher) {
        this.jdbc = jdbc; this.mapper = mapper; this.validation = validation; this.configuredTeacher = configuredTeacher;
    }

    @Transactional
    public void ensureCourses() {
        // A retained marker also prevents deleted courses from reappearing on restart.
        jdbc.execute("""
                create table if not exists catalog_content_seeds (
                  seed_key varchar(160) primary key, resource_id bigint null unique,
                  created_at timestamp not null default current_timestamp,
                  constraint fk_catalog_seed_resource foreign key(resource_id) references resources(id) on delete set null
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        JsonNode pack;
        try (var input = new ClassPathResource("catalog/foundation-courses.json").getInputStream()) {
            pack = mapper.readTree(input);
        } catch (IOException error) { throw new IllegalStateException("Cannot read foundation courses", error); }
        List<Long> teachers = configuredTeacher > 0
                ? jdbc.query("select id from users where role='teacher' and id=?", (rs, n) -> rs.getLong(1), configuredTeacher)
                : jdbc.query("select id from users where role='teacher' order by id", (rs, n) -> rs.getLong(1));
        if (teachers.size() != 1) {
            LOG.warn("Foundation courses need one teacher; configure CATALOG_SEED_TEACHER_ID when multiple teachers exist.");
            return;
        }
        int imported = 0;
        for (JsonNode course : pack.path("courses")) {
            String seed = pack.path("packKey").asText() + "/" + course.path("key").asText();
            if (jdbc.queryForObject("select count(*) from catalog_content_seeds where seed_key=?", Integer.class, seed) > 0) continue;
            jdbc.update("insert into catalog_content_seeds(seed_key) values(?)", seed);
            importCourse(course, teachers.get(0), seed);
            imported++;
        }
        if (imported > 0) LOG.info("Imported {} foundation courses with readable lessons; teacher id={}", imported, teachers.get(0));
    }

    private void importCourse(JsonNode course, long teacherId, String seed) {
        String category = course.path("category").asText();
        String subject = course.path("subject").asText();
        String title = course.path("title").asText();
        String description = course.path("description").asText();
        if (title.isBlank() || subject.isBlank() || course.path("lessons").size() < 2)
            throw new IllegalStateException("Incomplete course pack: " + seed);
        jdbc.update("insert ignore into categories(name,description,sort_order,status) values(?,?,7,1)", category,
                "考研公共基础与计算机408入门，按知识点讲解、例题和自测组织。");
        long categoryId = jdbc.queryForObject("select id from categories where name=?", Long.class, category);
        jdbc.update("insert ignore into directions(category_id,name,sort_order,status) values(?,?,100,1)", categoryId, subject);
        long directionId = jdbc.queryForObject("select id from directions where category_id=? and name=?", Long.class, categoryId, subject);
        jdbc.update("insert ignore into subject_categories(name,description) values(?,?)", subject, description);
        int minutes = 0;
        for (JsonNode lesson : course.path("lessons")) minutes += lesson.path("minutes").asInt();
        long resourceId = insert("""
                insert into resources(teacher_id,category_id,direction_id,title,subject,type,level,description,
                  content,estimated_minutes,learner_count,status,review_status)
                values(?,?,?,?,?,'material','入门',?,?,?,0,0,'pending')
                """, teacherId, categoryId, directionId, title, subject, description,
                description + " 本课程为原创基础图文课，包含讲解、例题和自测。预计时长包含阅读与练习。", minutes);
        long courseId = insert("insert into courses(legacy_resource_id,teacher_id,title,subject,description) values(?,?,?,?,?)",
                resourceId, teacherId, title, subject, description);
        jdbc.update("update resources set course_id=? where id=?", courseId, resourceId);
        long chapterId = insert("insert into course_chapters(course_id,title,description,sort_order,status) values(?,?,?,1,'draft')",
                courseId, "基础知识与例题训练", description);
        int order = 0;
        for (JsonNode lesson : course.path("lessons")) {
            String lessonTitle = lesson.path("title").asText();
            String content = lesson.path("content").asText();
            if (content.length() < 150 || lesson.path("minutes").asInt() <= 0)
                throw new IllegalStateException("Missing learning content: " + lessonTitle);
            long lessonId = insert("""
                    insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status)
                    values(?,?,'article',?,1,?,'ready')
                    """, chapterId, lessonTitle, lesson.path("minutes").asInt() * 60, ++order);
            insert("insert into lesson_assets(lesson_id,asset_type,content_text,transcoding_status,status) values(?,'article',?,'ready',1)",
                    lessonId, content);
            jdbc.update("insert ignore into knowledge_nodes(subject,title,node_level,description,sort_order) values(?,?,2,?,?)",
                    subject, lessonTitle, content.substring(0, Math.min(160, content.length())), order);
        }
        validation.requirePublishable(resourceId);
        jdbc.update("update course_chapters set status='published' where id=?", chapterId);
        jdbc.update("update course_lessons set status='published' where chapter_id=?", chapterId);
        if (!validation.validatePublic(resourceId).isContentComplete()) throw new IllegalStateException("Course content validation failed: " + title);
        jdbc.update("update resources set status=1,review_status='approved',review_feedback='初始化原创基础图文课',reviewed_at=current_timestamp where id=?", resourceId);
        jdbc.update("update catalog_content_seeds set resource_id=? where seed_key=?", resourceId, seed);
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Missing generated course ID");
        return key.getKey().longValue();
    }
}
