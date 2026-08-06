package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherAnalyticsController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public TeacherAnalyticsController(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> teacher = currentUserService.requireTeacher(authorization);
        Object teacherId = teacher.get("id");
        Map<String, Object> result = new HashMap<String, Object>();
        List<Map<String, Object>> resourceStats = resourceStats(teacherId);
        result.put("summary", summary(teacherId));
        result.put("resourceStats", resourceStats);
        result.put("popularResources", firstItems(resourceStats, 5));
        result.put("warningStudents", warningStudents(teacherId));
        result.put("weakNodes", weakNodes(teacherId));
        result.put("reviews", jdbcTemplate.queryForList(
                "select cr.id,cr.rating,cr.content,cr.updated_at updatedAt,u.name authorName,r.title resourceTitle,"
                        + "(select count(*) from course_feedback_replies fr where fr.target_type='review' and fr.target_id=cr.id) replyCount "
                        + "from course_reviews cr join resources r on r.id=cr.resource_id "
                        + "join users u on u.id=cr.student_id where r.teacher_id=? order by cr.updated_at desc limit 30",
                teacherId));
        result.put("questions", jdbcTemplate.queryForList(
                "select cq.id,cq.content,cq.answer_count answerCount,cq.created_at createdAt,u.name authorName,r.title resourceTitle,"
                        + "(select count(*) from course_feedback_replies fr where fr.target_type='question' and fr.target_id=cq.id) replyCount "
                        + "from course_questions cq join resources r on r.id=cq.resource_id "
                        + "join users u on u.id=cq.student_id where r.teacher_id=? order by cq.created_at desc limit 30",
                teacherId));
        return result;
    }

    @PostMapping("/feedback/{type}/{feedbackId}/reply")
    @Transactional
    public Map<String, Object> reply(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String type,
            @PathVariable long feedbackId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> teacher = currentUserService.requireTeacher(authorization);
        String normalizedType = type == null ? "" : type.trim().toLowerCase();
        String content = body == null || body.get("content") == null ? "" : String.valueOf(body.get("content")).trim();
        if (!("question".equals(normalizedType) || "review".equals(normalizedType))) {
            throw new BusinessException(400, "反馈类型不正确");
        }
        if (content.isEmpty() || content.length() > 1000) {
            throw new BusinessException(400, "回复不能为空且不能超过 1000 字");
        }
        String table = "question".equals(normalizedType) ? "course_questions" : "course_reviews";
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " f join resources r on r.id=f.resource_id where f.id=? and r.teacher_id=?",
                Integer.class, feedbackId, teacher.get("id"));
        if (count == null || count == 0) {
            throw new BusinessException(404, "反馈不存在或不属于当前教师的课程");
        }
        jdbcTemplate.update(
                "insert into course_feedback_replies(target_type,target_id,teacher_id,content) values(?,?,?,?)",
                normalizedType, feedbackId, teacher.get("id"), content);
        if ("question".equals(normalizedType)) {
            jdbcTemplate.update("update course_questions set answer_count=answer_count+1 where id=?", feedbackId);
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("success", true);
        return result;
    }

    private Map<String, Object> summary(Object teacherId) {
        Map<String, Object> summary = new HashMap<String, Object>();
        summary.put("studentCount", singleNumber(
                "select count(distinct lr.student_id) from learning_records lr join resources r on r.id=lr.resource_id where r.teacher_id=?", teacherId));
        summary.put("activeStudents", singleNumber(
                "select count(distinct lr.student_id) from learning_records lr join resources r on r.id=lr.resource_id "
                        + "where r.teacher_id=? and lr.created_at >= date_sub(now(), interval 7 day)", teacherId));
        summary.put("resourceCount", singleNumber("select count(*) from resources where teacher_id=?", teacherId));
        summary.put("pendingCount", singleNumber("select count(*) from resources where teacher_id=? and review_status='pending'", teacherId));
        summary.put("courseCompletionRate", singleNumber(
                "select coalesce(round(sum(case when progress>=100 then 1 else 0 end)*100.0/nullif(count(*),0),0),0) "
                        + "from learning_records lr join resources r on r.id=lr.resource_id where r.teacher_id=?", teacherId));
        return summary;
    }

    private List<Map<String, Object>> resourceStats(Object teacherId) {
        return jdbcTemplate.queryForList(
                "select r.id,r.title,r.subject,r.type,r.review_status reviewStatus,r.review_feedback reviewFeedback,"
                        + "count(lr.id) playCount,"
                        + "coalesce(round(sum(case when lr.progress>=100 then 1 else 0 end)*100.0/nullif(count(lr.id),0),0),0) completionRate,"
                        + "coalesce(f.favoriteCount,0) favoriteCount,"
                        + "coalesce(round(avg(lr.progress),0),0) avgProgress "
                        + "from resources r "
                        + "left join learning_records lr on lr.resource_id=r.id "
                        + "left join (select resource_id,count(*) favoriteCount from resource_favorites group by resource_id) f on f.resource_id=r.id "
                        + "where r.teacher_id=? "
                        + "group by r.id,r.title,r.subject,r.type,r.review_status,r.review_feedback,r.created_at,f.favoriteCount "
                        + "order by playCount desc,favoriteCount desc,r.created_at desc",
                teacherId);
    }

    private List<Map<String, Object>> warningStudents(Object teacherId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select u.id,u.name,u.email,"
                        + "count(r.id) recordCount,"
                        + "coalesce(round(avg(case when r.id is not null then lr.progress end),0),0) avgProgress,"
                        + "max(case when r.id is not null then lr.created_at end) lastActiveAt,"
                        + "datediff(now(),coalesce(max(case when r.id is not null then lr.created_at end),u.created_at)) inactiveDays,"
                        + "sum(case when r.id is not null and lr.knowledge_node is not null and lr.progress<50 then 1 else 0 end) weakNodeCount "
                        + "from users u "
                        + "left join learning_records lr on lr.student_id=u.id "
                        + "left join resources r on r.id=lr.resource_id and r.teacher_id=? "
                        + "where u.role='student' "
                        + "group by u.id,u.name,u.email,u.created_at "
                        + "having recordCount=0 or avgProgress<40 or inactiveDays>=7 or weakNodeCount>=3 "
                        + "order by inactiveDays desc,avgProgress asc,weakNodeCount desc "
                        + "limit 8",
                teacherId);
        List<Map<String, Object>> warnings = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<String, Object>(row);
            int inactiveDays = intValue(item.get("inactiveDays"));
            int avgProgress = intValue(item.get("avgProgress"));
            int weakNodeCount = intValue(item.get("weakNodeCount"));
            int recordCount = intValue(item.get("recordCount"));
            item.put("riskLevel", inactiveDays >= 14 || avgProgress < 25 || recordCount == 0 ? "high" : "medium");
            item.put("reason", reason(recordCount, inactiveDays, avgProgress, weakNodeCount));
            warnings.add(item);
        }
        return warnings;
    }

    private List<Map<String, Object>> weakNodes(Object teacherId) {
        return jdbcTemplate.queryForList(
                "select coalesce(lr.subject,'Uncategorized') subject,lr.knowledge_node node,"
                        + "count(*) weakCount,coalesce(round(avg(progress),0),0) avgProgress "
                        + "from learning_records lr "
                        + "where lr.knowledge_node is not null and exists("
                        + "select 1 from resources r where r.teacher_id=? and r.subject=lr.subject) "
                        + "group by lr.subject,lr.knowledge_node "
                        + "having avg(lr.progress)<60 "
                        + "order by weakCount desc,avgProgress asc "
                        + "limit 8",
                teacherId);
    }

    private List<Map<String, Object>> firstItems(List<Map<String, Object>> rows, int size) {
        return new ArrayList<Map<String, Object>>(rows.subList(0, Math.min(rows.size(), size)));
    }

    private Number singleNumber(String sql, Object... params) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, params);
        return value == null ? 0 : value;
    }

    private String reason(int recordCount, int inactiveDays, int avgProgress, int weakNodeCount) {
        if (recordCount == 0) {
            return "No learning records";
        }
        if (inactiveDays >= 7) {
            return "No learning activity for over 7 days";
        }
        if (weakNodeCount >= 3) {
            return "Too many weak knowledge nodes";
        }
        if (avgProgress < 40) {
            return "Average learning progress is low";
        }
        return "Needs teacher attention";
    }

    private int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

}
