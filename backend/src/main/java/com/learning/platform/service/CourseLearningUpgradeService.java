package com.learning.platform.service;

import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Incremental, one-time enrichment. Existing lesson IDs, progress and teacher edits survive restarts. */
@Service
public class CourseLearningUpgradeService {

    private final JdbcTemplate jdbc;
    private final CourseReadingContent content;

    public CourseLearningUpgradeService(JdbcTemplate jdbc, CourseReadingContent content) {
        this.jdbc = jdbc;
        this.content = content;
    }

    @Transactional
    public void ensureLearningMaterials() {
        jdbc.execute("""
            create table if not exists catalog_learning_upgrades (
              source_lesson_id bigint primary key,
              article_lesson_id bigint not null,
              video_lesson_id bigint not null,
              version varchar(40) not null,
              created_at timestamp not null default current_timestamp,
              constraint fk_learning_upgrade_source foreign key(source_lesson_id) references course_lessons(id) on delete cascade,
              constraint fk_learning_upgrade_article foreign key(article_lesson_id) references course_lessons(id) on delete cascade,
              constraint fk_learning_upgrade_video foreign key(video_lesson_id) references course_lessons(id) on delete cascade
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
        var lessons = jdbc.queryForList("""
            select l.id,l.chapter_id chapterId,l.title,l.lesson_type lessonType,l.sort_order sortOrder,
              r.id resourceId,r.content courseContent from course_lessons l join course_chapters ch on ch.id=l.chapter_id
              join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id
              left join catalog_learning_upgrades u on u.source_lesson_id=l.id
              where r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
              and ch.status='published' and l.status='published' and l.lesson_type in ('article','video')
              and u.source_lesson_id is null order by l.id
            """);
        for (var lesson : lessons) {
            String title = String.valueOf(lesson.get("title"));
            if (!content.supports(title)) continue;
            long source = ((Number) lesson.get("id")).longValue();
            long chapter = ((Number) lesson.get("chapterId")).longValue();
            int order = ((Number) lesson.get("sortOrder")).intValue() * 10;
            boolean video = "video".equals(lesson.get("lessonType"));
            var assets = jdbc.queryForList("select id,content_text,file_url from lesson_assets where lesson_id=? and asset_type=? and status=1 and transcoding_status='ready' order by id", source, video ? "video" : "article");
            if (assets.size() != 1) continue;
            String original = assets.get(0).get("content_text") instanceof String text && !text.isBlank()
                ? text : String.valueOf(lesson.getOrDefault("courseContent", ""));
            String expanded = content.expand(title, original);
            long articleId;
            long videoId;
            if (video) {
                videoId = source;
                jdbc.update("update course_lessons set sort_order=? where id=?", order, source);
                // These two bundled recordings were measured in the real media player, not inferred from the old 45-minute placeholder.
                String file = String.valueOf(assets.get(0).get("file_url"));
                int measured = file.startsWith("/uploads/1779430126093-") ? 106 : file.startsWith("/uploads/1779430545143-") ? 311 : 0;
                if (measured > 0) jdbc.update("update course_lessons set duration_seconds=? where id=?", measured, source);
                articleId = insert("insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) values(?,?,'article',?,1,?,'published')", chapter, title + " · 图文精讲", Math.max(300, expanded.length() / 5), order + 1);
                insert("insert into lesson_assets(lesson_id,asset_type,content_text,transcoding_status,status) values(?,'article',?,'ready',1)", articleId, expanded);
            } else {
                articleId = source;
                jdbc.update("update lesson_assets set content_text=? where id=?", expanded, assets.get(0).get("id"));
                jdbc.update("update course_lessons set sort_order=?,duration_seconds=greatest(duration_seconds,?) where id=?", order + 1, Math.max(300, expanded.length() / 5), source);
                // A draft without an asset is an explicit material slot, never a fabricated playable video.
                videoId = insert("insert into course_lessons(chapter_id,title,lesson_type,duration_seconds,free_preview,sort_order,status) values(?,?,'video',0,0,?,'draft')", chapter, title + " · 视频讲解", order);
            }
            jdbc.update("insert into catalog_learning_upgrades(source_lesson_id,article_lesson_id,video_lesson_id,version) values(?,?,?,'reading-video-v1')", source, articleId, videoId);
            jdbc.update("""
                update resources r set estimated_minutes=(select ceil(coalesce(sum(l.duration_seconds),0)/60.0)
                  from courses c join course_chapters ch on ch.course_id=c.id join course_lessons l on l.chapter_id=ch.id
                  where c.legacy_resource_id=r.id and ch.status='published' and l.status='published') where r.id=?
                """, lesson.get("resourceId"));
        }
    }

    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Missing lesson ID");
        return key.getKey().longValue();
    }
}
