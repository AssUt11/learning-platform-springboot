package com.learning.platform.ai.context;

import com.learning.platform.service.LessonPlaybackService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiLearningContextService {
    private final JdbcTemplate jdbcTemplate;
    private final LessonPlaybackService lessonPlaybackService;
    private final Neo4jLearningContextService neo4jService;

    public AiLearningContextService(JdbcTemplate jdbcTemplate,
                                    LessonPlaybackService lessonPlaybackService,
                                    Neo4jLearningContextService neo4jService) {
        this.jdbcTemplate = jdbcTemplate;
        this.lessonPlaybackService = lessonPlaybackService;
        this.neo4jService = neo4jService;
    }

    public LearningContext load(Map<String, Object> student, long publicCourseId, long lessonId) {
        // Reuse the same published-course and lesson ownership checks used by the player.
        lessonPlaybackService.playback(student, publicCourseId, lessonId);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select c.id internalCourseId,r.id publicCourseId,r.title courseTitle,r.subject," +
                        "coalesce(r.description,'') courseDescription,coalesce(r.content,'') courseContent," +
                        "ch.title chapterTitle,coalesce(ch.description,'') chapterDescription," +
                        "l.id lessonId,l.title lessonTitle,l.lesson_type lessonType " +
                        "from course_lessons l join course_chapters ch on ch.id=l.chapter_id " +
                        "join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id " +
                        "where r.id=? and l.id=?",
                publicCourseId, lessonId);
        long internalCourseId = number(row.get("internalCourseId"));
        String courseTitle = text(row.get("courseTitle"));
        String lessonTitle = text(row.get("lessonTitle"));
        String subject = text(row.get("subject"));
        List<Map<String, Object>> assets = jdbcTemplate.queryForList(
                "select coalesce(content_text,'') contentText,coalesce(file_name,'') fileName " +
                        "from lesson_assets where lesson_id=? and status=1 and content_text is not null " +
                        "order by id limit 3", lessonId);
        Neo4jLearningContextService.GraphContext graph = neo4jService.load(
                internalCourseId, publicCourseId, lessonId, courseTitle, lessonTitle, subject);

        StringBuilder context = new StringBuilder();
        append(context, "课程", courseTitle);
        append(context, "学科", subject);
        append(context, "课程简介", text(row.get("courseDescription")));
        append(context, "章节", text(row.get("chapterTitle")));
        append(context, "章节简介", text(row.get("chapterDescription")));
        append(context, "当前课时", lessonTitle);
        append(context, "课时类型", text(row.get("lessonType")));
        append(context, "课程公开文本", text(row.get("courseContent")));
        for (Map<String, Object> asset : assets) {
            append(context, "课时资料" + optionalName(asset.get("fileName")), text(asset.get("contentText")));
        }
        if (!graph.getNodes().isEmpty()) {
            context.append("\nNeo4j 相关知识点（仅作参考）：\n");
            for (Map<String, Object> node : graph.getNodes()) {
                context.append("- ").append(text(node.get("title")));
                String description = text(node.get("description"));
                if (!description.isEmpty()) context.append("：").append(description);
                String path = text(node.get("path"));
                if (!path.isEmpty()) context.append("（路径：").append(path).append("）");
                context.append('\n');
            }
        }
        return new LearningContext(publicCourseId, lessonId, courseTitle, lessonTitle, subject,
                context.toString(), graph.isAvailable(), graph.getNodes().size());
    }

    private void append(StringBuilder builder, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        builder.append(label).append("：").append(value.trim()).append('\n');
    }

    private String optionalName(Object value) {
        String name = text(value);
        return name.isEmpty() ? "" : "（" + name + "）";
    }

    private long number(Object value) { return ((Number) value).longValue(); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
