package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses/{courseId}")
public class CourseCommunityController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public CourseCommunityController(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/community")
    public Result<Map<String, Object>> community(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId) {
        requireCourse(courseId);
        Map<String, Object> currentUser = currentUserService.optional(authorization);
        List<Map<String, Object>> questions = jdbcTemplate.queryForList(
                "select q.id,q.content,q.answer_count answerCount,q.created_at createdAt,"
                        + "coalesce(u.name,'课程学员') authorName "
                        + "from course_questions q join users u on u.id=q.student_id "
                        + "where q.resource_id=? order by q.created_at desc limit 60",
                courseId);
        List<Map<String, Object>> reviews = jdbcTemplate.queryForList(
                "select r.id,r.rating,r.content,r.created_at createdAt,r.updated_at updatedAt,"
                        + "coalesce(u.name,'课程学员') authorName "
                        + "from course_reviews r join users u on u.id=r.student_id "
                        + "where r.resource_id=? order by r.updated_at desc limit 60",
                courseId);
        List<Map<String, Object>> notes = new ArrayList<Map<String, Object>>();
        if (currentUser != null
                && ("student".equals(currentUser.get("role")) || "teacher".equals(currentUser.get("role")))) {
            notes = jdbcTemplate.queryForList(
                    "select id,lesson_title lessonTitle,content,created_at createdAt,updated_at updatedAt "
                            + "from course_notes where resource_id=? and student_id=? order by updated_at desc",
                    courseId, currentUser.get("id"));
        }
        Map<String, Object> reviewSummary = jdbcTemplate.queryForMap(
                "select count(*) reviewCount,coalesce(round(avg(rating),1),0) averageRating "
                        + "from course_reviews where resource_id=?",
                courseId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("questions", questions);
        result.put("notes", notes);
        result.put("reviews", reviews);
        result.put("replies", jdbcTemplate.queryForList(
                "select fr.id,fr.target_type targetType,fr.target_id targetId,fr.content,fr.created_at createdAt,"
                        + "coalesce(u.name,'课程教师') teacherName "
                        + "from course_feedback_replies fr join users u on u.id=fr.teacher_id "
                        + "where (fr.target_type='question' and fr.target_id in(select id from course_questions where resource_id=?)) "
                        + "or (fr.target_type='review' and fr.target_id in(select id from course_reviews where resource_id=?)) "
                        + "order by fr.created_at asc",
                courseId, courseId));
        result.put("reviewSummary", reviewSummary);
        return Result.success(result);
    }

    @PostMapping("/questions")
    public Result<Map<String, Object>> addQuestion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId,
            @RequestBody Map<String, Object> body) {
        requireCourse(courseId);
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        String content = requiredText(body == null ? null : body.get("content"), "问题内容", 500);
        jdbcTemplate.update("insert into course_questions(resource_id,student_id,content) values(?,?,?)",
                courseId, user.get("id"), content);
        return Result.success(singleValue("created", true));
    }

    @PostMapping("/notes")
    public Result<Map<String, Object>> addNote(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId,
            @RequestBody Map<String, Object> body) {
        requireCourse(courseId);
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        String lessonTitle = optionalText(body == null ? null : body.get("lessonTitle"), "课程总览", 180);
        String content = requiredText(body == null ? null : body.get("content"), "笔记内容", 3000);
        jdbcTemplate.update("insert into course_notes(resource_id,student_id,lesson_title,content) values(?,?,?,?)",
                courseId, user.get("id"), lessonTitle, content);
        return Result.success(singleValue("created", true));
    }

    @DeleteMapping("/notes/{noteId}")
    public Result<Map<String, Object>> deleteNote(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId,
            @PathVariable long noteId) {
        requireCourse(courseId);
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        int deleted = jdbcTemplate.update(
                "delete from course_notes where id=? and resource_id=? and student_id=?",
                noteId, courseId, user.get("id"));
        if (deleted == 0) {
            throw new BusinessException(404, "笔记不存在或无权删除");
        }
        return Result.success(singleValue("deleted", true));
    }

    @PostMapping("/reviews")
    public Result<Map<String, Object>> saveReview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long courseId,
            @RequestBody Map<String, Object> body) {
        requireCourse(courseId);
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        int rating = rating(body == null ? null : body.get("rating"));
        String content = requiredText(body == null ? null : body.get("content"), "评价内容", 1000);
        jdbcTemplate.update(
                "insert into course_reviews(resource_id,student_id,rating,content) values(?,?,?,?) "
                        + "on duplicate key update rating=values(rating),content=values(content),updated_at=current_timestamp",
                courseId, user.get("id"), rating, content);
        return Result.success(singleValue("saved", true));
    }

    private void requireCourse(long courseId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from resources where id=? and status=1", Integer.class, courseId);
        if (count == null || count == 0) {
            throw new BusinessException(404, "课程不存在或已下架");
        }
    }

    private int rating(Object value) {
        try {
            int result = Integer.parseInt(String.valueOf(value));
            if (result < 1 || result > 5) {
                throw new BusinessException(400, "评分必须在 1 到 5 之间");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "评分必须在 1 到 5 之间");
        }
    }

    private String requiredText(Object value, String fieldName, int maximumLength) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty() || text.length() > maximumLength) {
            throw new BusinessException(400, fieldName + "不能为空且不能超过 " + maximumLength + " 字");
        }
        return text;
    }

    private String optionalText(Object value, String fallback, int maximumLength) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        if (text.length() > maximumLength) {
            throw new BusinessException(400, "章节名称不能超过 " + maximumLength + " 字");
        }
        return text;
    }

    private Map<String, Object> singleValue(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(key, value);
        return result;
    }
}
