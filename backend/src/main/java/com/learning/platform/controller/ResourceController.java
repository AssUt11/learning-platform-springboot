package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.LearningRecordSubject;
import com.learning.platform.pattern.ResourceRowAdapter;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.CourseContentValidationService;
import com.learning.platform.service.FileStorageService;
import com.learning.platform.service.LegacyCourseMigrationService;
import com.learning.platform.util.ApiResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResourceController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final LegacyCourseMigrationService legacyCourseMigrationService;
    private final CourseContentValidationService validationService;
    private final LearningRecordSubject learningRecordSubject;

    public ResourceController(JdbcTemplate jdbcTemplate,
                              CurrentUserService currentUserService,
                               FileStorageService fileStorageService,
                               LegacyCourseMigrationService legacyCourseMigrationService,
                               CourseContentValidationService validationService,
                               LearningRecordSubject learningRecordSubject) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
        this.legacyCourseMigrationService = legacyCourseMigrationService;
        this.validationService = validationService;
        this.learningRecordSubject = learningRecordSubject;
    }

    @GetMapping("/resources")
    public Map<String, Object> listResources(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "12") int size,
                                             @RequestParam(required = false) String subject,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String type) {
        Map<String, Object> user = currentUserService.optional(authorization);
        boolean learner = user != null
                && ("student".equals(user.get("role")) || "teacher".equals(user.get("role")));
        int pageNumber = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);
        int offset = (pageNumber - 1) * pageSize;
        String favoriteSelect = learner ? "mine.id is not null favorited," : "false favorited,";
        String progressSelect = learner ? "coalesce(myr.progress,0) progress " : "0 progress ";
        String learnerJoins = learner
                ? "left join resource_favorites mine on mine.resource_id=r.id and mine.student_id=? "
                + "left join learning_records myr on myr.resource_id=r.id and myr.student_id=? "
                : "";

        List<Object> filterParams = new ArrayList<Object>();
        String where = resourceWhere(subject, keyword, type, filterParams);
        String resourceBase = "from resources r "
                + "join (select max(id) id from resources group by title) latest on latest.id=r.id "
                + "left join users u on u.id=r.teacher_id ";
        String countSql = "select count(*) " + resourceBase + where;
        Number totalNumber = jdbcTemplate.queryForObject(countSql, Number.class, filterParams.toArray());
        int total = totalNumber == null ? 0 : totalNumber.intValue();

        String sql = "select r.id,r.title,r.subject,r.type,r.level,r.description,r.content,r.file_url fileUrl,r.created_at createdAt,"
                + "coalesce(u.name,'Platform Teacher') teacher,45 minutes,"
                + "coalesce(f.favoriteCount,0) favoriteCount,"
                + favoriteSelect
                + progressSelect
                + resourceBase
                + "left join (select resource_id,count(*) favoriteCount from resource_favorites group by resource_id) f on f.resource_id=r.id "
                + learnerJoins
                + where
                + "order by r.created_at desc limit ? offset ?";
        List<Object> params = new ArrayList<Object>();
        if (learner) {
            params.add(user.get("id"));
            params.add(user.get("id"));
        }
        params.addAll(filterParams);
        params.add(pageSize);
        params.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            resources.add(new ResourceRowAdapter(row).toViewModel());
        }
        Map<String, Object> result = ApiResponses.of("resources", resources);
        result.put("total", total);
        result.put("page", pageNumber);
        result.put("size", pageSize);
        result.put("totalPages", (int) Math.ceil(total * 1.0 / pageSize));
        return result;
    }

    @PostMapping("/resources/{id}/favorite")
    @Transactional
    public Map<String, Object> toggleFavorite(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        long resourceId = positiveLong(id, "resourceId");
        List<Map<String, Object>> resources = jdbcTemplate.queryForList(
                "select id from resources where id=? for update", resourceId);
        if (resources.isEmpty()) {
            throw new BusinessException(404, "Resource does not exist.");
        }
        int deleted = jdbcTemplate.update(
                "delete from resource_favorites where student_id=? and resource_id=?", user.get("id"), resourceId);
        boolean favorited = deleted == 0;
        if (favorited) {
            jdbcTemplate.update("insert into resource_favorites(student_id,resource_id) values(?,?)",
                    user.get("id"), resourceId);
        }
        Integer favoriteCount = jdbcTemplate.queryForObject(
                "select count(*) from resource_favorites where resource_id=?", Integer.class, resourceId);
        Map<String, Object> result = ApiResponses.ok();
        result.put("favorited", favorited);
        result.put("favoriteCount", favoriteCount == null ? 0 : favoriteCount);
        return result;
    }

    @PostMapping("/teacher/resources")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createResource(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestParam String title,
                                              @RequestParam String subject,
                                              @RequestParam(defaultValue = "material") String type,
                                              @RequestParam(defaultValue = "入门") String level,
                                              @RequestParam(required = false) String learningStage,
                                              @RequestParam(defaultValue = "") String description,
                                              @RequestParam(defaultValue = "") String content,
                                              @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        Map<String, Object> user = currentUserService.requireTeacher(authorization);
        String normalizedTitle = requiredText(title, "title", 160);
        String normalizedSubject = requiredText(subject, "subject", 60);
        String normalizedType = resourceType(type);
        String normalizedLevel = requiredText(level, "level", 40);
        String normalizedStage = text(learningStage).isEmpty()
                ? com.learning.platform.service.LearningStages.infer(normalizedTitle, normalizedLevel)
                : com.learning.platform.service.LearningStages.require(learningStage);
        String normalizedDescription = text(description);
        String normalizedContent = textOrDefault(content,
                defaultContent(normalizedTitle, normalizedSubject, normalizedDescription));
        String fileUrl = fileStorageService.storeResourceFile(file);
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "insert into resources(teacher_id,title,subject,type,level,description,content,file_url,status,review_status) "
                                + "values(?,?,?,?,?,?,?,?,0,'pending')",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setObject(1, user.get("id"));
                statement.setString(2, normalizedTitle);
                statement.setString(3, normalizedSubject);
                statement.setString(4, normalizedType);
                statement.setString(5, normalizedLevel);
                statement.setString(6, normalizedDescription);
                statement.setString(7, normalizedContent);
                statement.setString(8, fileUrl);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("Failed to read the new course ID.");
            }
            long resourceId = key.longValue();
            jdbcTemplate.update("insert into course_learning_stages(resource_id,stage_code,updated_by) values(?,?,?)",resourceId,normalizedStage,user.get("id"));
            legacyCourseMigrationService.ensureStructure(resourceId);
            Map<String, Object> result = ApiResponses.ok();
            result.put("courseId", resourceId);
            result.put("fileUrl", fileUrl);
            result.put("validation", validationService.validateForPublication(resourceId));
            return result;
        } catch (RuntimeException exception) {
            cleanupFailedUpload(fileUrl, exception);
            throw exception;
        }
    }

    @GetMapping("/teacher/resources")
    public Map<String, Object> teacherResources(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireTeacher(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select r.id,r.title,r.subject,r.type,r.level,r.description,r.content,r.file_url fileUrl,s.stage_code learningStage,"
                        + "r.estimated_minutes minutes,r.learner_count learnerCount,r.status,"
                        + "r.review_status reviewStatus,r.review_feedback reviewFeedback,r.reviewed_at reviewedAt,"
                        + "r.created_at createdAt,r.updated_at updatedAt "
                        + "from resources r left join course_learning_stages s on s.resource_id=r.id where r.teacher_id=? order by r.updated_at desc,r.id desc",
                user.get("id"));
        Map<String, Object> result = ApiResponses.ok();
        result.put("resources", rows);
        result.put("total", rows.size());
        return result;
    }

    @PutMapping("/teacher/resources/{id}")
    @Transactional
    public Map<String, Object> updateResource(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        Map<String, Object> user = currentUserService.requireTeacher(authorization);
        if (body == null) {
            throw new BusinessException(400, "Resource data is required.");
        }
        String title = requiredText(body.get("title"), "title", 160);
        String subject = requiredText(body.get("subject"), "subject", 60);
        String type = resourceType(body.get("type"));
        String level = requiredText(textOrDefault(body.get("level"), "入门"), "level", 40);
        String stage = body.containsKey("learningStage") ? com.learning.platform.service.LearningStages.require(body.get("learningStage")) : null;
        String description = text(body.get("description"));
        String content = textOrDefault(body.get("content"), defaultContent(title, subject, description));
        int updated = jdbcTemplate.update(
                "update resources set title=?,subject=?,type=?,level=?,description=?,content=?,status=0,review_status='pending',review_feedback=null,reviewed_by=null,reviewed_at=null where id=? and teacher_id=?",
                title, subject, type, level, description, content, id, user.get("id"));
        if (updated == 0) {
            throw new BusinessException(404, "Resource not found or cannot be edited.");
        }
        if(stage!=null) jdbcTemplate.update("insert into course_learning_stages(resource_id,stage_code,updated_by) values(?,?,?) on duplicate key update stage_code=values(stage_code),updated_by=values(updated_by)",id,stage,user.get("id"));
        legacyCourseMigrationService.syncCourse(id.longValue());
        Map<String, Object> result = ApiResponses.ok();
        result.put("validation", validationService.validateForPublication(id.longValue()));
        return result;
    }

    @DeleteMapping("/teacher/resources/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteResource(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id) throws IOException {
        Map<String, Object> user = currentUserService.requireTeacher(authorization);
        long resourceId = positiveLong(id, "resourceId");
        int deleted = jdbcTemplate.update(
                "update resources set status=0,review_status='pending',review_feedback=null,reviewed_by=null,reviewed_at=null "
                        + "where id=? and teacher_id=?",
                resourceId, user.get("id"));
        if (deleted != 1) {
            throw new BusinessException(404, "Resource not found or cannot be deleted.");
        }
        return ApiResponses.ok();
    }

    @PostMapping("/preferences")
    @Transactional
    public Map<String, Object> savePreferences(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @RequestBody Map<String, Object> body) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        Object subjectsObject = body == null ? null : body.get("subjects");
        if (!(subjectsObject instanceof List)) {
            throw new BusinessException(400, "subjects must be a list.");
        }
        LinkedHashSet<String> subjects = new LinkedHashSet<String>();
        for (Object value : (List<?>) subjectsObject) {
            subjects.add(requiredText(value, "subject", 60));
        }
        if (subjects.isEmpty() || subjects.size() > 3) {
            throw new BusinessException(400, "Please select between 1 and 3 subjects.");
        }
        jdbcTemplate.update("delete from student_preferences where student_id=?", user.get("id"));
        for (String selectedSubject : subjects) {
            jdbcTemplate.update("insert into student_preferences(student_id,subject) values(?,?)",
                    user.get("id"), selectedSubject);
        }
        return ApiResponses.ok();
    }

    @GetMapping("/preferences")
    public Map<String, Object> getPreferences(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select subject from student_preferences where student_id=? order by id", user.get("id"));
        List<String> subjects = new ArrayList<String>();
        for (Map<String, Object> row : rows) {
            subjects.add(String.valueOf(row.get("subject")));
        }
        return ApiResponses.of("subjects", subjects);
    }

    @PostMapping("/learning/records")
    public Map<String, Object> saveRecord(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody Map<String, Object> body) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        Map<String, Object> record = validatedRecord(body);
        jdbcTemplate.update("insert into learning_records(student_id,resource_id,record_key,subject,knowledge_node,progress) "
                        + "values(?,?,?,?,?,?) "
                        + "on duplicate key update progress=greatest(progress,values(progress)),"
                        + "subject=coalesce(values(subject),subject),knowledge_node=coalesce(values(knowledge_node),knowledge_node),"
                        + "created_at=current_timestamp",
                user.get("id"), record.get("resourceId"), record.get("recordKey"), record.get("subject"),
                record.get("knowledgeNode"), record.get("progress"));
        learningRecordSubject.notifyCreated(user, record);
        return ApiResponses.ok();
    }

    @GetMapping("/learning/summary")
    public Map<String, Object> learningSummary(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        Object studentId = user.get("id");
        Map<String, Object> result = ApiResponses.ok();
        result.put("summary", studentSummary(studentId));
        result.put("subjectProgress", subjectProgress(studentId));
        result.put("resourceRecords", resourceRecords(studentId));
        result.put("nodeRecords", nodeRecords(studentId));
        result.put("favorites", favoriteResources(studentId));
        result.put("recentRecords", recentRecords(studentId));
        return result;
    }

    private Map<String, Object> studentSummary(Object studentId) {
        Map<String, Object> summary = jdbcTemplate.queryForMap(
                "select count(distinct case when resource_id is not null then resource_id end) learnedResourceCount,"
                        + "coalesce(round(avg(case when resource_id is not null then progress end),0),0) avgResourceProgress,"
                        + "coalesce(sum(case when resource_id is not null and progress>=100 then 1 else 0 end),0) completedResourceCount,"
                        + "count(distinct case when knowledge_node is not null then concat(coalesce(subject,''),'|',knowledge_node) end) learnedNodeCount,"
                        + "coalesce(max(created_at),'') lastActiveAt "
                        + "from learning_records where student_id=?",
                studentId);
        Number favoriteCount = jdbcTemplate.queryForObject(
                "select count(*) from resource_favorites where student_id=?", Number.class, studentId);
        summary.put("favoriteCount", favoriteCount == null ? 0 : favoriteCount);
        return summary;
    }

    private List<Map<String, Object>> subjectProgress(Object studentId) {
        return jdbcTemplate.queryForList(
                "select coalesce(lr.subject,r.subject,'未分类') subject,"
                        + "count(*) recordCount,"
                        + "coalesce(round(avg(lr.progress),0),0) avgProgress,"
                        + "coalesce(sum(case when lr.progress>=100 then 1 else 0 end),0) completedCount "
                        + "from learning_records lr "
                        + "left join resources r on r.id=lr.resource_id "
                        + "where lr.student_id=? "
                        + "group by coalesce(lr.subject,r.subject,'未分类') "
                        + "order by recordCount desc,avgProgress desc limit 8",
                studentId);
    }

    private List<Map<String, Object>> resourceRecords(Object studentId) {
        return jdbcTemplate.queryForList(
                "select lr.id,lr.resource_id resourceId,r.title,r.subject,r.type,r.level,r.file_url fileUrl,"
                        + "coalesce(u.name,'Platform Teacher') teacher,lr.progress,lr.created_at lastLearnedAt "
                        + "from learning_records lr "
                        + "join resources r on r.id=lr.resource_id "
                        + "left join users u on u.id=r.teacher_id "
                        + "where lr.student_id=? and lr.resource_id is not null "
                        + "order by lr.created_at desc limit 8",
                studentId);
    }

    private List<Map<String, Object>> nodeRecords(Object studentId) {
        return jdbcTemplate.queryForList(
                "select id,subject,knowledge_node knowledgeNode,progress,created_at lastLearnedAt "
                        + "from learning_records "
                        + "where student_id=? and knowledge_node is not null "
                        + "order by created_at desc limit 8",
                studentId);
    }

    private List<Map<String, Object>> favoriteResources(Object studentId) {
        return jdbcTemplate.queryForList(
                "select r.id,r.title,r.subject,r.type,r.level,r.file_url fileUrl,"
                        + "coalesce(u.name,'Platform Teacher') teacher,f.created_at favoritedAt "
                        + "from resource_favorites f "
                        + "join resources r on r.id=f.resource_id "
                        + "left join users u on u.id=r.teacher_id "
                        + "where f.student_id=? "
                        + "order by f.created_at desc limit 6",
                studentId);
    }

    private List<Map<String, Object>> recentRecords(Object studentId) {
        return jdbcTemplate.queryForList(
                "select lr.id,"
                        + "case when lr.resource_id is not null then 'resource' else 'node' end recordType,"
                        + "coalesce(r.title,lr.knowledge_node,'学习记录') title,"
                        + "coalesce(lr.subject,r.subject,'未分类') subject,"
                        + "lr.progress,lr.created_at createdAt "
                        + "from learning_records lr "
                        + "left join resources r on r.id=lr.resource_id "
                        + "where lr.student_id=? "
                        + "order by lr.created_at desc limit 10",
                studentId);
    }

    private String resourceWhere(String subject, String keyword, String type, List<Object> params) {
        List<String> conditions = new ArrayList<String>();
        conditions.add("r.status=1");
        conditions.add("r.review_status='approved'");
        if (hasText(subject)) {
            conditions.add("r.subject=?");
            params.add(subject.trim());
        }
        if (hasText(type)) {
            conditions.add("r.type=?");
            params.add(type.trim());
        }
        if (hasText(keyword)) {
            conditions.add("(r.title like ? or r.description like ? or r.content like ? or r.subject like ? or coalesce(u.name,'') like ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (conditions.isEmpty()) {
            return "";
        }
        return "where " + String.join(" and ", conditions) + " ";
    }

    private Map<String, Object> validatedRecord(Map<String, Object> body) {
        if (body == null) {
            throw new BusinessException(400, "Learning record is required.");
        }

        Map<String, Object> record = new HashMap<String, Object>();
        record.put("progress", progress(body.get("progress")));
        Object resourceValue = body.get("resourceId");
        if (resourceValue != null && !text(resourceValue).isEmpty()) {
            long resourceId = positiveLong(resourceValue, "resourceId");
            List<Map<String, Object>> resources = jdbcTemplate.queryForList(
                    "select id,subject from resources where id=?", resourceId);
            if (resources.isEmpty()) {
                throw new BusinessException(404, "Resource does not exist.");
            }
            record.put("resourceId", resourceId);
            record.put("recordKey", "resource:" + resourceId);
            record.put("subject", String.valueOf(resources.get(0).get("subject")));
            record.put("knowledgeNode", null);
            return record;
        }

        String subject = requiredText(body.get("subject"), "subject", 60);
        String knowledgeNode = requiredText(body.get("knowledgeNode"), "knowledgeNode", 120);
        Integer subjectCount = jdbcTemplate.queryForObject(
                "select (select count(*) from subject_categories where name=?) "
                        + "+ (select count(*) from directions where name=? and status=1)",
                Integer.class, subject, subject);
        if (subjectCount == null || subjectCount == 0) {
            throw new BusinessException(404, "Learning subject does not exist.");
        }
        record.put("resourceId", null);
        record.put("recordKey", "node:" + subject + ":" + knowledgeNode);
        record.put("subject", subject);
        record.put("knowledgeNode", knowledgeNode);
        return record;
    }

    private int progress(Object value) {
        if (value == null || text(value).isEmpty()) {
            return 0;
        }
        try {
            int progress = new BigDecimal(text(value)).intValueExact();
            if (progress < 0 || progress > 100) {
                throw new BusinessException(400, "progress must be between 0 and 100.");
            }
            return progress;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(400, "progress must be an integer between 0 and 100.");
        }
    }

    private long positiveLong(Object value, String fieldName) {
        try {
            long id = new BigDecimal(text(value)).longValueExact();
            if (id <= 0) {
                throw new BusinessException(400, fieldName + " must be a positive integer.");
            }
            return id;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new BusinessException(400, fieldName + " must be a positive integer.");
        }
    }

    private String requiredText(Object value, String fieldName, int maximumLength) {
        String result = text(value);
        if (result.isEmpty() || result.length() > maximumLength) {
            throw new BusinessException(400,
                    fieldName + " is required and must not exceed " + maximumLength + " characters.");
        }
        return result;
    }

    private String resourceType(Object value) {
        String type = textOrDefault(value, "material");
        if (!("video".equals(type) || "material".equals(type))) {
            throw new BusinessException(400, "type must be either video or material.");
        }
        return type;
    }

    private void cleanupFailedUpload(String fileUrl, RuntimeException originalException) {
        try {
            fileStorageService.deleteStoredFile(fileUrl);
        } catch (RuntimeException | IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String defaultValue) {
        String text = text(value);
        return text.length() == 0 ? defaultValue : text;
    }

    private String defaultContent(Object title, Object subject, Object description) {
        String resourceTitle = textOrDefault(title, "课程资源");
        String resourceSubject = textOrDefault(subject, "通用学习");
        String resourceDescription = text(description);
        return "【文本学习】" + resourceTitle + "。" + resourceDescription + "。本节课程适合在观看视频或下载资料之前先进行文字预习。学习时先明确课程目标：理解"
                + resourceSubject + "方向中的核心概念、常见术语、典型场景和基本工作流程；再把知识放到一个可执行的小任务中验证。阅读过程中请准备笔记，记录三个问题：这个知识点解决什么问题，它依赖哪些前置知识，实际项目中容易在哪一步出错。"
                + "第一阶段关注概念。不要急着记忆大量细节，先用自己的话解释课程标题中的关键词，并把它和已经学过的知识建立联系。如果遇到陌生名词，可以先写下猜测，再通过示例代码、流程图或操作截图验证。第二阶段关注流程。把课程内容拆成输入、处理、输出三个部分，说明每一步产生什么结果，为什么需要这样设计。第三阶段关注实践。选择一个最小练习，例如完成一个接口、编写一个查询、实现一个组件、整理一组数据或搭建一个简单环境。练习完成后要回看错误信息和调试过程，因为这些细节往往比最终结果更能反映掌握程度。"
                + "学习完成后，请用五句话总结：本节最重要的概念是什么，关键步骤有哪些，容易混淆的点在哪里，可以迁移到什么项目场景，下一步应该补强哪项能力。建议把完成度分为了解、会做、能解释、能迁移四级。如果只能跟着步骤完成，说明还停留在会做阶段；如果能够脱离教程复述原因并改造练习，才算真正掌握。"
                + "本课程的文本学习内容也可以作为复习清单使用。再次复习时，请优先检查自己是否能独立画出流程、说出关键参数、解释一次失败案例，并把学习记录更新到平台中。这样系统后续生成学习路径、薄弱点分析和资源推荐时，才能更准确地反映你的真实状态。";
    }
}
