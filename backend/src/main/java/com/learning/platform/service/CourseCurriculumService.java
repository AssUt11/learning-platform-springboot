package com.learning.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseCurriculumService {
    private final JdbcTemplate jdbc;
    private final CourseCurriculumCatalog catalog;

    public CourseCurriculumService(JdbcTemplate jdbc, CourseCurriculumCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @Transactional
    public void ensureCurricula() {
        jdbc.execute("""
            create table if not exists lesson_knowledge_topics (
              lesson_id bigint not null,category_name varchar(120) not null,direction_name varchar(120) not null,
              topic_title varchar(250) not null,primary key(lesson_id,category_name,direction_name,topic_title),
              index idx_lesson_topic_scope(category_name,direction_name,topic_title),
              constraint fk_lesson_topic_lesson foreign key(lesson_id) references course_lessons(id) on delete cascade
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
        jdbc.execute("""
            create table if not exists catalog_curriculum_seeds (
              resource_id bigint primary key,version varchar(80) not null,
              created_at timestamp not null default current_timestamp,
              constraint fk_curriculum_resource foreign key(resource_id) references resources(id) on delete cascade
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
        var courses = jdbc.queryForList("""
            select r.id,r.title,c.id internalId,coalesce(cat.name,r.subject) category,
              coalesce(d.name,r.subject) direction from resources r join courses c on c.legacy_resource_id=r.id
            left join categories cat on cat.id=r.category_id left join directions d on d.id=r.direction_id
            left join catalog_curriculum_seeds seed on seed.resource_id=r.id
            where r.status=1 and r.review_status='approved' and r.lifecycle_status='active' and seed.resource_id is null
            order by r.id
            """);
        for (var course : courses) {
            String title = String.valueOf(course.get("title"));
            String profile = catalog.profile(title);
            if (profile == null) continue;
            var original = jdbc.queryForList("select id,title from course_chapters where course_id=? and status<>'disabled' order by sort_order,id", course.get("internalId"));
            if (original.isEmpty()) continue;
            var units = jdbc.queryForList("""
                select u.source_lesson_id,u.article_lesson_id,u.video_lesson_id,l.title
                from catalog_learning_upgrades u join course_lessons l on l.id=u.source_lesson_id
                join course_chapters ch on ch.id=l.chapter_id where ch.course_id=? order by l.sort_order,l.id
                """, course.get("internalId"));
            List<Long> chapters = new ArrayList<>();
            for (var chapter : original) chapters.add(number(chapter.get("id")));
            String[] names = {"概念准备与基础精讲", "核心方法与条件辨析", "案例推演与应用实践", "综合任务与进阶训练"};
            while (chapters.size() < 4) {
                int index = chapters.size();
                chapters.add(insert("insert into course_chapters(course_id,title,description,sort_order,status) values(?,?,?,?, 'published')",
                    course.get("internalId"), names[index], "围绕“" + title + "”开展" + names[index] + "。", index + 1));
            }
            // Only reorganize the original single-chapter seed. Existing custom chapter arrangements remain intact.
            if (original.size() == 1) {
                String previousTitle = String.valueOf(original.get(0).get("title"));
                if (Set.of("第 1 章", "基础知识与例题训练").contains(previousTitle))
                    jdbc.update("update course_chapters set title=?,description=? where id=?", names[0], "建立“" + title + "”的基础概念。", chapters.get(0));
                if (units.size() == 2) {
                    var second = units.get(1);
                    jdbc.update("update course_lessons set chapter_id=? where id in (?,?)", chapters.get(1), second.get("article_lesson_id"), second.get("video_lesson_id"));
                }
            }
            String category = String.valueOf(course.get("category"));
            String direction = "knowledge-graph".equals(profile) ? "知识图谱" : String.valueOf(course.get("direction"));
            if ("knowledge-graph".equals(profile)) ensureKnowledgeGraphDirection(course.get("id"));
            for (int index = 0; index < units.size(); index++) {
                long article = number(units.get(index).get("article_lesson_id"));
                var row = jdbc.queryForList("select title from course_lessons where id=?", article);
                if (!row.isEmpty()) mapTopics(article, category, direction, String.valueOf(row.get(0).get("title")), catalog.baseTopics(profile, title, index));
            }
            JsonNode additions = catalog.lessons(profile);
            for (int index = 0; index < additions.size(); index++) {
                JsonNode lesson = additions.get(index);
                String content = catalog.reading(title, lesson);
                int chapter = units.size() == 2 ? 2 + index / 2 : Math.min(1 + index, 3);
                long lessonId = insert("insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) values(?,?,'article',?,1,?,'published')",
                    chapters.get(chapter), lesson.path(0).asText(), Math.max(300, content.length() / 5), index + 100);
                insert("insert into lesson_assets(lesson_id,asset_type,content_text,transcoding_status,status) values(?,'article',?,'ready',1)", lessonId, content);
                mapTopics(lessonId, category, direction, lesson.path(0).asText(), Arrays.asList(lesson.path(1).asText().split("\\|")));
            }
            jdbc.update("""
                update resources r set estimated_minutes=(select ceil(sum(l.duration_seconds)/60.0) from courses c
                  join course_chapters ch on ch.course_id=c.id join course_lessons l on l.chapter_id=ch.id
                  where c.legacy_resource_id=r.id and ch.status='published' and l.status='published') where r.id=?
                """, course.get("id"));
            jdbc.update("insert into catalog_curriculum_seeds(resource_id,version) values(?,?)", course.get("id"), catalog.version());
        }
    }

    private void ensureKnowledgeGraphDirection(Object resourceId) {
        jdbc.update("insert ignore into directions(category_id,name,sort_order,status) select category_id,'知识图谱',100,1 from resources where id=? and category_id is not null", resourceId);
        jdbc.update("update resources r join directions d on d.category_id=r.category_id and d.name='知识图谱' set r.direction_id=d.id where r.id=? and r.direction_id is null", resourceId);
    }

    private void mapTopics(long lessonId, String category, String direction, String title, JsonNode aliases) {
        List<String> topics = new ArrayList<>();
        aliases.forEach(item -> topics.add(item.asText()));
        mapTopics(lessonId, category, direction, title, topics);
    }

    private void mapTopics(long lessonId, String category, String direction, String title, List<String> aliases) {
        Set<String> topics = new LinkedHashSet<>(aliases);
        topics.add(title);
        for (String topic : topics) if (!topic.isBlank()) jdbc.update(
            "insert ignore into lesson_knowledge_topics(lesson_id,category_name,direction_name,topic_title) values(?,?,?,?)",
            lessonId, category, direction, topic.trim());
    }

    private long number(Object value) { return ((Number) value).longValue(); }
    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Missing curriculum ID");
        return key.getKey().longValue();
    }
}
