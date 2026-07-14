package com.learning.platform.controller;

import com.learning.platform.pattern.LearningRecordSubject;
import com.learning.platform.pattern.ResourceRowAdapter;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.FileStorageService;
import com.learning.platform.util.ApiResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResourceController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final LearningRecordSubject learningRecordSubject;

    public ResourceController(JdbcTemplate jdbcTemplate,
                              CurrentUserService currentUserService,
                              FileStorageService fileStorageService,
                              LearningRecordSubject learningRecordSubject) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
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
        boolean student = user != null && "student".equals(user.get("role"));
        int pageNumber = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);
        int offset = (pageNumber - 1) * pageSize;
        String favoriteSelect = student ? "mine.id is not null favorited," : "false favorited,";
        String progressSelect = student ? "coalesce(myr.progress,0) progress " : "0 progress ";
        String studentJoins = student
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
                + studentJoins
                + where
                + "order by r.created_at desc limit ? offset ?";
        List<Object> params = new ArrayList<Object>();
        if (student) {
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
    public Map<String, Object> toggleFavorite(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id) {
        Map<String, Object> user = currentUserService.requireStudent(authorization);
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from resource_favorites where student_id=? and resource_id=?",
                Integer.class, user.get("id"), id);
        boolean favorited = count == null || count == 0;
        if (favorited) {
            jdbcTemplate.update("insert ignore into resource_favorites(student_id,resource_id) values(?,?)", user.get("id"), id);
        } else {
            jdbcTemplate.update("delete from resource_favorites where student_id=? and resource_id=?", user.get("id"), id);
        }
        Integer favoriteCount = jdbcTemplate.queryForObject(
                "select count(*) from resource_favorites where resource_id=?", Integer.class, id);
        Map<String, Object> result = ApiResponses.ok();
        result.put("favorited", favorited);
        result.put("favoriteCount", favoriteCount == null ? 0 : favoriteCount);
        return result;
    }

    @PostMapping("/teacher/resources")
    public Map<String, Object> createResource(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestParam String title,
                                              @RequestParam String subject,
                                              @RequestParam(defaultValue = "material") String type,
                                              @RequestParam(defaultValue = "Beginner") String level,
                                              @RequestParam(defaultValue = "") String description,
                                              @RequestParam(defaultValue = "") String content,
                                              @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        Map<String, Object> user = currentUserService.requireTeacher(authorization);
        String fileUrl = fileStorageService.storeResourceFile(file);
        jdbcTemplate.update("insert into resources(teacher_id,title,subject,type,level,description,content,file_url) values(?,?,?,?,?,?,?,?)",
                user.get("id"), title, subject, type, level, description, textOrDefault(content, defaultContent(title, subject, description)), fileUrl);
        Map<String, Object> result = ApiResponses.ok();
        result.put("fileUrl", fileUrl);
        return result;
    }

    @PutMapping("/teacher/resources/{id}")
    public Map<String, Object> updateResource(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        Map<String, Object> user = currentUserService.requireTeacher(authorization);
        int updated = jdbcTemplate.update("update resources set title=?,subject=?,type=?,level=?,description=? where id=? and teacher_id=?",
                text(body.get("title")), text(body.get("subject")), textOrDefault(body.get("type"), "material"),
                textOrDefault(body.get("level"), "入门"), text(body.get("description")), id, user.get("id"));
        jdbcTemplate.update("update resources set content=? where id=? and teacher_id=?",
                textOrDefault(body.get("content"), defaultContent(body.get("title"), body.get("subject"), body.get("description"))),
                id, user.get("id"));
        if (updated == 0) {
            throw new RuntimeException("Resource not found or cannot be edited.");
        }
        return ApiResponses.ok();
    }

    @DeleteMapping("/teacher/resources/{id}")
    public Map<String, Object> deleteResource(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id) {
        Map<String, Object> user = currentUserService.requireTeacher(authorization);
        int deleted = jdbcTemplate.update("delete from resources where id=? and teacher_id=?", id, user.get("id"));
        if (deleted == 0) {
            throw new RuntimeException("Resource not found or cannot be deleted.");
        }
        return ApiResponses.ok();
    }

    @PostMapping("/preferences")
    public Map<String, Object> savePreferences(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @RequestBody Map<String, Object> body) {
        Map<String, Object> user = currentUserService.requireStudent(authorization);
        jdbcTemplate.update("delete from student_preferences where student_id=?", user.get("id"));
        Object subjectsObject = body.get("subjects");
        if (subjectsObject instanceof List) {
            List<?> subjects = (List<?>) subjectsObject;
            for (Object subject : subjects) {
                jdbcTemplate.update("insert into student_preferences(student_id,subject) values(?,?)",
                        user.get("id"), String.valueOf(subject));
            }
        }
        return ApiResponses.ok();
    }

    @GetMapping("/preferences")
    public Map<String, Object> getPreferences(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireStudent(authorization);
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
        Map<String, Object> user = currentUserService.requireLogin(authorization);
        String recordKey = recordKey(body);
        Object progress = body.getOrDefault("progress", 0);
        jdbcTemplate.update("insert into learning_records(student_id,resource_id,record_key,subject,knowledge_node,progress) "
                        + "values(?,?,?,?,?,?) "
                        + "on duplicate key update progress=greatest(progress,values(progress)),"
                        + "subject=coalesce(values(subject),subject),knowledge_node=coalesce(values(knowledge_node),knowledge_node),"
                        + "created_at=current_timestamp",
                user.get("id"), body.get("resourceId"), recordKey, body.get("subject"),
                body.get("knowledgeNode"), progress);
        learningRecordSubject.notifyCreated(user, body);
        return ApiResponses.ok();
    }

    @GetMapping("/learning/summary")
    public Map<String, Object> learningSummary(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireStudent(authorization);
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

    private String recordKey(Map<String, Object> body) {
        Object resourceId = body.get("resourceId");
        if (resourceId != null && String.valueOf(resourceId).trim().length() > 0) {
            return "resource:" + resourceId;
        }
        String subject = textOrDefault(body.get("subject"), "unknown");
        String node = textOrDefault(body.get("knowledgeNode"), "unknown");
        return "node:" + subject + ":" + node;
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
