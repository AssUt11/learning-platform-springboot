package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.CourseContentValidationService;
import com.learning.platform.service.TeacherCourseStructureService;
import com.learning.platform.vo.CourseContentValidationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final TeacherCourseStructureService structureService;
    private final CourseContentValidationService validationService;

    public AdminController(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService,
                           TeacherCourseStructureService structureService,
                           CourseContentValidationService validationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.structureService = structureService;
        this.validationService = validationService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        currentUserService.requireAdmin(authorization);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("summary", summary());
        result.put("pendingResources", validatedResources(
                resourceSelect() + " where r.review_status='pending' order by r.created_at asc"));
        result.put("resources", validatedResources(
                resourceSelect() + " order by r.updated_at desc limit 100"));
        result.put("reviews", jdbcTemplate.queryForList(
                "select cr.id,cr.resource_id resourceId,cr.rating,cr.content,cr.updated_at updatedAt,"
                        + "r.title resourceTitle,u.name authorName "
                        + "from course_reviews cr join resources r on r.id=cr.resource_id "
                        + "join users u on u.id=cr.student_id order by cr.updated_at desc limit 100"));
        result.put("questions", jdbcTemplate.queryForList(
                "select cq.id,cq.resource_id resourceId,cq.content,cq.answer_count answerCount,cq.created_at createdAt,"
                        + "r.title resourceTitle,u.name authorName "
                        + "from course_questions cq join resources r on r.id=cq.resource_id "
                        + "join users u on u.id=cq.student_id order by cq.created_at desc limit 100"));
        result.put("health", health());
        result.put("auditLogs", jdbcTemplate.queryForList(
                "select a.id,a.action_type actionType,a.target_type targetType,a.target_id targetId,a.detail,"
                        + "a.created_at createdAt,u.name adminName "
                        + "from admin_audit_logs a join users u on u.id=a.admin_id "
                        + "order by a.created_at desc limit 30"));
        return result;
    }

    @GetMapping("/content-validation")
    public Map<String, Object> contentValidation(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        currentUserService.requireAdmin(authorization);
        Map<String, Object> result = ok();
        result.put("publicIssues", validationService.incompletePublishedCourses());
        result.put("governanceIssues", validationService.incompleteApprovedCourses());
        return result;
    }

    @PostMapping("/resources/{resourceId}/review")
    @Transactional
    public Map<String, Object> reviewResource(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long resourceId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        String action = text(body == null ? null : body.get("action"));
        String feedback = text(body == null ? null : body.get("feedback"));
        if (!("approve".equals(action) || "reject".equals(action))) {
            throw new BusinessException(400, "审核操作必须是 approve 或 reject");
        }
        if (feedback.length() > 500) {
            throw new BusinessException(400, "审核反馈不能超过 500 字");
        }
        if ("approve".equals(action)) {
            validationService.requirePublishable(resourceId);
            structureService.publishCourseStructure(resourceId);
            CourseContentValidationResult publicValidation = validationService.validatePublic(resourceId);
            if (!publicValidation.isContentComplete()) {
                throw new BusinessException(409, "发布后的课程目录仍不完整："
                        + String.join("；", publicValidation.getReasons()));
            }
        }
        String reviewStatus = "approve".equals(action) ? "approved" : "rejected";
        int status = "approve".equals(action) ? 1 : 0;
        int updated = jdbcTemplate.update(
                "update resources set review_status=?,status=?,review_feedback=?,reviewed_by=?,reviewed_at=current_timestamp where id=?",
                reviewStatus, status, feedback, admin.get("id"), resourceId);
        if (updated == 0) {
            throw new BusinessException(404, "课程资源不存在");
        }
        audit(admin, "approve".equals(action) ? "RESOURCE_APPROVED" : "RESOURCE_REJECTED",
                "resource", resourceId, feedback);
        return ok();
    }

    @PostMapping("/resources/{resourceId}/visibility")
    @Transactional
    public Map<String, Object> updateVisibility(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long resourceId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        boolean visible = body != null && Boolean.parseBoolean(String.valueOf(body.get("visible")));
        List<Map<String, Object>> resources = jdbcTemplate.queryForList(
                "select review_status,lifecycle_status from resources where id=?", resourceId);
        if (resources.isEmpty()) {
            throw new BusinessException(404, "课程资源不存在");
        }
        if (visible) {
            if ("archived".equals(String.valueOf(resources.get(0).get("lifecycle_status")))) {
                throw new BusinessException(409, "归档课程不能直接上架，请先取消归档");
            }
            String reviewStatus = String.valueOf(resources.get(0).get("review_status"));
            if (!"approved".equals(reviewStatus)) {
                throw new BusinessException(409, "只有审核通过的内容才能重新上架");
            }
            validationService.requirePublishable(resourceId);
            structureService.publishCourseStructure(resourceId);
            if (!validationService.validatePublic(resourceId).isContentComplete()) {
                throw new BusinessException(409, "发布后的课程目录仍不完整。");
            }
        }
        int updated = jdbcTemplate.update("update resources set status=? where id=?", visible ? 1 : 0, resourceId);
        if (updated == 0) {
            throw new BusinessException(404, "课程资源不存在");
        }
        audit(admin, visible ? "RESOURCE_PUBLISHED" : "RESOURCE_UNPUBLISHED", "resource", resourceId, "");
        return ok();
    }

    @PostMapping("/resources/{resourceId}/archive")
    @Transactional
    public Map<String, Object> updateArchiveStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long resourceId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        boolean archived = body != null && Boolean.parseBoolean(String.valueOf(body.get("archived")));
        String reason = text(body == null ? null : body.get("reason"));
        if (reason.length() > 500) {
            throw new BusinessException(400, "归档原因不能超过 500 字");
        }
        if (archived && reason.isEmpty()) {
            throw new BusinessException(400, "归档课程必须填写归档原因");
        }
        int updated;
        if (archived) {
            updated = jdbcTemplate.update(
                    "update resources set lifecycle_status='archived',archived_at=current_timestamp,archive_reason=?,status=0 where id=?",
                    reason, resourceId);
        } else {
            updated = jdbcTemplate.update(
                    "update resources set lifecycle_status='active',archived_at=null,archive_reason=null,status=0 where id=?",
                    resourceId);
        }
        if (updated == 0) {
            throw new BusinessException(404, "课程资源不存在");
        }
        audit(admin, archived ? "RESOURCE_ARCHIVED" : "RESOURCE_UNARCHIVED", "resource", resourceId, reason);
        return ok();
    }

    @DeleteMapping("/reviews/{reviewId}")
    @Transactional
    public Map<String, Object> deleteReview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long reviewId) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        jdbcTemplate.update("delete from course_feedback_replies where target_type='review' and target_id=?", reviewId);
        int deleted = jdbcTemplate.update("delete from course_reviews where id=?", reviewId);
        if (deleted == 0) {
            throw new BusinessException(404, "评价不存在");
        }
        audit(admin, "REVIEW_DELETED", "review", reviewId, "管理员删除课程评价");
        return ok();
    }

    @DeleteMapping("/questions/{questionId}")
    @Transactional
    public Map<String, Object> deleteQuestion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long questionId) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        jdbcTemplate.update("delete from course_feedback_replies where target_type='question' and target_id=?", questionId);
        int deleted = jdbcTemplate.update("delete from course_questions where id=?", questionId);
        if (deleted == 0) {
            throw new BusinessException(404, "问题不存在");
        }
        audit(admin, "QUESTION_DELETED", "question", questionId, "管理员删除课程问题");
        return ok();
    }

    @PostMapping("/repair")
    @Transactional
    public Map<String, Object> repair(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> admin = currentUserService.requireAdmin(authorization);
        List<CourseContentValidationResult> publicIssues = validationService.incompletePublishedCourses();
        List<Long> unpublishedIds = validationService.unpublishIncompleteCourses(
                numberValue(admin.get("id")), publicIssues.size());
        int learnerCounts = jdbcTemplate.update(
                "update resources r set learner_count=(select count(distinct lr.student_id) from learning_records lr where lr.resource_id=r.id)");
        int invalidStatus = jdbcTemplate.update(
                "update resources set status=0 where review_status in ('pending','rejected') and status<>0");
        int orphanReplies = jdbcTemplate.update(
                "delete fr from course_feedback_replies fr left join course_questions q on fr.target_type='question' and q.id=fr.target_id "
                        + "left join course_reviews rv on fr.target_type='review' and rv.id=fr.target_id "
                        + "where (fr.target_type='question' and q.id is null) or (fr.target_type='review' and rv.id is null)");
        String detail = "安全下架内容不完整课程 " + unpublishedIds.size() + " 门（" + unpublishedIds
                + "），刷新学习人数 " + learnerCounts + " 条，修正状态 " + invalidStatus
                + " 条，清理孤立回复 " + orphanReplies + " 条";
        audit(admin, "SITE_REPAIR", "system", null, detail);
        Map<String, Object> result = ok();
        result.put("detail", detail);
        result.put("health", health());
        return result;
    }

    private Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("studentCount", number("select count(*) from users where role='student'"));
        result.put("teacherCount", number("select count(*) from users where role='teacher'"));
        result.put("pendingCount", number("select count(*) from resources where review_status='pending'"));
        result.put("publishedCount", countCompletePublishedCourses());
        result.put("reviewCount", number("select count(*) from course_reviews"));
        result.put("questionCount", number("select count(*) from course_questions"));
        return result;
    }

    private Map<String, Object> health() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("database", number("select 1"));
        result.put("missingContent", number("select count(*) from resources where content is null or char_length(trim(content))=0"));
        result.put("pendingReview", number("select count(*) from resources where review_status='pending'"));
        result.put("hiddenResources", number("select count(*) from resources where status=0"));
        result.put("archivedResources", number("select count(*) from resources where lifecycle_status='archived'"));
        result.put("orphanRecords", number("select count(*) from learning_records lr left join resources r on r.id=lr.resource_id where lr.resource_id is not null and r.id is null"));
        List<CourseContentValidationResult> publicIssues = validationService.incompletePublishedCourses();
        List<CourseContentValidationResult> governanceIssues = validationService.incompleteApprovedCourses();
        result.put("publicCourseIssueCount", publicIssues.size());
        result.put("publicCourseIssues", publicIssues);
        result.put("incompleteCourseCount", governanceIssues.size());
        result.put("incompleteCourses", governanceIssues);
        result.put("status", publicIssues.isEmpty() ? "healthy" : "attention");
        return result;
    }

    private String resourceSelect() {
        return "select r.id,r.title,r.subject,r.type,r.level,r.status,r.teacher_id teacherId,"
                + "r.review_status reviewStatus,r.review_feedback reviewFeedback,r.created_at createdAt,r.updated_at updatedAt,"
                + "r.lifecycle_status lifecycleStatus,r.archived_at archivedAt,r.archive_reason archiveReason,"
                + "coalesce(t.name,'教师待补充') teacher,"
                + "coalesce((select count(*) from course_reviews cr where cr.resource_id=r.id),0) reviewCount,"
                + "coalesce((select count(*) from course_questions cq where cq.resource_id=r.id),0) questionCount "
                + "from resources r left join users t on t.id=r.teacher_id";
    }

    private List<Map<String, Object>> validatedResources(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            long courseId = numberValue(row.get("id"));
            boolean publiclyVisible = numberValue(row.get("status")) == 1L
                    && "approved".equals(String.valueOf(row.get("reviewStatus")));
            CourseContentValidationResult validation = publiclyVisible
                    ? validationService.validatePublic(courseId)
                    : validationService.validateForPublication(courseId);
            row.put("chapterCount", validation.getChapterCount());
            row.put("publishedLessonCount", validation.getPublishedLessonCount());
            row.put("totalLessonCount", validation.getTotalLessonCount());
            row.put("playableVideoCount", validation.getPlayableVideoCount());
            row.put("contentComplete", validation.isContentComplete());
            row.put("ownershipMissing", validation.isOwnershipMissing());
            row.put("reasonCodes", validation.getReasonCodes());
            row.put("reasons", validation.getReasons());
        }
        return rows;
    }

    private long countCompletePublishedCourses() {
        List<Long> ids = jdbcTemplate.query(
                "select id from resources where review_status='approved' and status=1 order by id",
                (resultSet, rowNumber) -> resultSet.getLong("id"));
        long count = 0;
        for (Long id : ids) {
            if (validationService.validatePublic(id.longValue()).isContentComplete()) count++;
        }
        return count;
    }

    private void audit(Map<String, Object> admin, String action, String targetType, Long targetId, String detail) {
        jdbcTemplate.update(
                "insert into admin_audit_logs(admin_id,action_type,target_type,target_id,detail) values(?,?,?,?,?)",
                admin.get("id"), action, targetType, targetId, detail);
    }

    private Number number(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0 : value;
    }

    private long numberValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> ok() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("success", true);
        return result;
    }
}
