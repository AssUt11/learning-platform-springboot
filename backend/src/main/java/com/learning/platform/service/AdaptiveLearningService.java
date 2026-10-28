package com.learning.platform.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** 学习进度与测验成绩分开计算：看完课不等于掌握，未测验也不等于薄弱。 */
@Service
public class AdaptiveLearningService {
    private final JdbcTemplate jdbc;
    public AdaptiveLearningService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> evidence(long userId) {
        return jdbc.queryForList("""
            select r.id courseId,r.title courseTitle,r.subject,l.id lessonId,l.title knowledgeNode,
              coalesce(p.progress_percent,0) progress,coalesce(p.completed,0) completed,
              q.score quizScore,q.difficulty,coalesce(q.submitted_at,p.last_studied_at) updatedAt
            from course_lessons l join course_chapters ch on ch.id=l.chapter_id
            join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id
            left join lesson_progress p on p.lesson_id=l.id and p.user_id=?
            left join ai_practice_sessions q on q.id=(select max(q2.id) from ai_practice_sessions q2
              where q2.user_id=? and q2.lesson_id=l.id and q2.submitted_at is not null)
            where r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
              and ch.status='published' and l.status='published' and (p.user_id is not null or q.id is not null)
            order by (q.score is not null and q.score<60) desc,updatedAt desc,l.id limit 20
            """, userId, userId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> enrich(long userId, Map<String, Object> snapshot) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Object rawNodes = snapshot.get("nodes");
        if (!(rawNodes instanceof List)) return snapshot;
        Map<String, Object> next = null;
        for (Map<String, Object> savedNode : (List<Map<String, Object>>) rawNodes) {
            Map<String, Object> node = new LinkedHashMap<>(savedNode);
            if (node.get("courseId") instanceof Number) {
                long courseId = ((Number) node.get("courseId")).longValue();
                List<Map<String, Object>> rows = jdbc.queryForList("""
                    select l.id lessonId,coalesce(p.completed,0) completed,coalesce(p.progress_percent,0) progress,q.score
                    from course_lessons l join course_chapters ch on ch.id=l.chapter_id
                    join courses c on c.id=ch.course_id join resources r on r.id=c.legacy_resource_id
                    left join lesson_progress p on p.lesson_id=l.id and p.user_id=?
                    left join ai_practice_sessions q on q.id=(select max(q2.id) from ai_practice_sessions q2
                      where q2.user_id=? and q2.lesson_id=l.id and q2.submitted_at is not null)
                    where r.id=? and r.status=1 and r.review_status='approved' and r.lifecycle_status='active'
                      and ch.status='published' and l.status='published' order by ch.sort_order,ch.id,l.sort_order,l.id
                    """, userId, userId, courseId);
                applyFeedback(node, rows);
            } else {
                node.put("learningState", "自主练习"); node.put("recommendationReason", "暂无关联课程，请按本步骤的完成标准自主练习。");
            }
            nodes.add(node);
            // 保留已生成路径和图谱先修顺序，只跳过已完成步骤，不凭成绩重写公共先修关系。
            if (next == null && !"学习已完成".equals(node.get("learningState")) && !"课程已下架".equals(node.get("learningState"))) next = node;
        }
        snapshot.put("nodes", nodes);
        snapshot.put("feedbackUpdatedAt", Instant.now().toString());
        if (next != null) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("nodeId", next.get("id")); action.put("title", next.get("title"));
            action.put("courseId", next.get("courseId")); action.put("lessonId", next.get("nextLessonId"));
            action.put("reason", next.get("recommendationReason")); snapshot.put("nextAction", action);
        }
        return snapshot;
    }

    static void applyFeedback(Map<String, Object> node, List<Map<String, Object>> lessons) {
        if (lessons.isEmpty()) {
            node.put("learningState", "课程已下架"); node.put("recommendationReason", "关联课程暂不可学习，请重新规划。");
            return;
        }
        int totalProgress = 0, completed = 0, tested = 0, totalScore = 0;
        boolean allTestsPassed = true;
        Long reviewLesson = null, unfinishedLesson = null, untestedLesson = null, consolidateLesson = null;
        for (Map<String, Object> lesson : lessons) {
            totalProgress += number(lesson.get("progress"));
            boolean done = number(lesson.get("completed")) == 1;
            if (done) completed++; else if (unfinishedLesson == null) unfinishedLesson = ((Number) lesson.get("lessonId")).longValue();
            if (lesson.get("score") instanceof Number) {
                int score = number(lesson.get("score")); tested++; totalScore += score;
                if (score < 80) allTestsPassed = false;
                if (score < 80 && consolidateLesson == null) consolidateLesson = ((Number) lesson.get("lessonId")).longValue();
                if (score < 60 && reviewLesson == null) reviewLesson = ((Number) lesson.get("lessonId")).longValue();
            } else if (untestedLesson == null) untestedLesson = ((Number) lesson.get("lessonId")).longValue();
        }
        node.put("progressPercent", totalProgress / lessons.size());
        node.put("testedLessons", tested); node.put("totalLessons", lessons.size());
        node.put("quizScore", tested == 0 ? null : totalScore / tested);
        String state, reason;
        Long nextLesson;
        if (reviewLesson != null) {
            state = "优先复习"; reason = "最近一次课时测验低于60分，建议重看讲义并完成基础练习。"; nextLesson = reviewLesson;
        } else if (completed == lessons.size()) {
            // 无测验成绩时只说明阅读/观看已完成，不推断知识已经掌握。
            state = tested == lessons.size() && allTestsPassed ? "学习已完成" : "建议自测";
            reason = "学习已完成".equals(state) ? "课时学习全部完成且测验均达到80分，可进入下一步。" : "已学完课程，建议补充自测或巩固练习，再判断掌握情况。";
            nextLesson = untestedLesson != null ? untestedLesson : consolidateLesson != null ? consolidateLesson : ((Number) lessons.get(0).get("lessonId")).longValue();
        } else {
            state = totalProgress > 0 ? "继续学习" : "待学习";
            reason = "按原路径顺序继续未完成课时，学习后通过练习检验理解。"; nextLesson = unfinishedLesson;
        }
        node.put("learningState", state); node.put("recommendationReason", reason); node.put("nextLessonId", nextLesson);
    }
    private static int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }
}
