package com.learning.platform.service;

import com.learning.platform.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves scoped exact titles and authored topic mappings to actual published lessons. */
@Service
public class KnowledgeCourseTargetService {

    private final JdbcTemplate jdbc;
    private final CourseContentValidationService validation;

    public KnowledgeCourseTargetService(
        JdbcTemplate jdbc,
        CourseContentValidationService validation
    ) {
        this.jdbc = jdbc;
        this.validation = validation;
    }

    public List<Map<String, Object>> targets(String category, String direction, String title) {
        String categoryName = required(category, 120);
        String directionName = required(direction, 120);
        String lessonTitle = required(title, 250);
        List<Map<String, Object>> candidates = jdbc.queryForList(
            """
            select r.id courseId,r.title courseTitle,l.id lessonId,l.title lessonTitle,
              ch.title chapterTitle,l.lesson_type lessonType,target.topicTitle topicTitle,
              case when l.title=target.topicTitle then 'exact' else 'mapped' end matchType
            from resources r join courses c on c.legacy_resource_id=r.id
            join course_chapters ch on ch.course_id=c.id
            join course_lessons l on l.chapter_id=ch.id
            left join categories cat on cat.id=r.category_id
            left join directions d on d.id=r.direction_id
            cross join (select ? categoryName,? directionName,? topicTitle) target
            where ((coalesce(cat.name,r.subject)=target.categoryName
                and coalesce(d.name,r.subject)=target.directionName and l.title=target.topicTitle)
              or exists(select 1 from lesson_knowledge_topics kt where kt.lesson_id=l.id
                and kt.category_name=target.categoryName and kt.direction_name=target.directionName
                and kt.topic_title=target.topicTitle))
              and r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
              and ch.status='published' and l.status='published'
              and exists(select 1 from lesson_assets a where a.lesson_id=l.id and a.status=1
                and a.asset_type=l.lesson_type and a.transcoding_status='ready')
            order by r.id,ch.sort_order,ch.id,l.sort_order,l.id
            """,
            categoryName,
            directionName,
            lessonTitle
        );
        Map<Long, Boolean> visibleCourses = new LinkedHashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            long courseId = ((Number) candidate.get("courseId")).longValue();
            boolean visible = visibleCourses.computeIfAbsent(courseId, (id) ->
                validation.validatePublic(id).isContentComplete()
            );
            if (visible) result.add(candidate);
        }
        return result;
    }

    private String required(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new BusinessException(400, "请提供完整的知识点分类、方向和标题。");
        }
        return text;
    }
}
