package com.learning.platform.controller;

import com.learning.platform.ai.application.LearningAgentAiService;
import com.learning.platform.ai.application.AiUserRateLimiter;
import com.learning.platform.ai.domain.LearningAgentResult;
import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.RecommendationContext;
import com.learning.platform.pattern.WordLearningAnalysisReportTemplate;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.CourseContentValidationService;
import com.learning.platform.service.StudentLearningPathService;
import com.learning.platform.service.AdaptiveLearningService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final LearningAgentAiService agentAiService;
    private final WordLearningAnalysisReportTemplate reportTemplate;
    private final CourseContentValidationService contentValidationService;
    private final AiUserRateLimiter rateLimiter;
    private final StudentLearningPathService learningPaths;
    private final AdaptiveLearningService adaptive;
    private final com.learning.platform.service.LearningPlanningService planning;

    @Value("${app.report-dir}")
    private String reportDir;

    public AgentController(JdbcTemplate jdbcTemplate,
                           CurrentUserService currentUserService,
                           LearningAgentAiService agentAiService,
                           WordLearningAnalysisReportTemplate reportTemplate,
                           CourseContentValidationService contentValidationService,
                           AiUserRateLimiter rateLimiter, StudentLearningPathService learningPaths, AdaptiveLearningService adaptive,
                           com.learning.platform.service.LearningPlanningService planning) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.agentAiService = agentAiService;
        this.reportTemplate = reportTemplate;
        this.contentValidationService = contentValidationService;
        this.rateLimiter = rateLimiter;
        this.learningPaths = learningPaths;
        this.adaptive = adaptive;
        this.planning = planning;
    }

    @PostMapping("/guide")
    public Map<String, Object> guide(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return runAgent("guide", authorization);
    }

    @PostMapping("/path")
    public Map<String, Object> path(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return runAgent("path", authorization);
    }

    @PostMapping("/recommend")
    public Map<String, Object> recommend(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return runAgent("recommend", authorization);
    }

    @PostMapping("/analysis")
    public Map<String, Object> analysis(@RequestHeader(value = "Authorization", required = false) String authorization) throws IOException {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        long userId = ((Number) user.get("id")).longValue();
        requireRateLimit(userId);
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        long reportId = insertAgentStart(userId, "analysis", requestId);
        File file = null;
        try {
            RecommendationContext context = buildContext(user);
            LearningAgentResult agentResult = agentAiService.execute("analysis", context, requestId);
            String output = agentResult.getContent();
            file = reportTemplate.generateWithAiAnalysis(user, context, output, new File(reportDir));
            String storedUrl = "/reports/" + file.getName();
            completeAgentLog(reportId, agentResult, storedUrl, elapsedMs(startedAt));
            Map<String, Object> result = result(output);
            result.put("downloadUrl", reportDownloadUrl(reportId));
            result.put("reportId", reportId);
            result.put("provider", agentAiService.providerCode());
            result.put("model", agentAiService.modelCode());
            addAgentMetadata(result, agentResult);
            return result;
        } catch (IOException | RuntimeException exception) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            failAgentLog(reportId, requestId, exception, elapsedMs(startedAt));
            throw exception;
        }
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id,output_result outputResult,created_at createdAt "
                        + "from agent_logs where student_id=? and agent_type='analysis' and status='completed' and report_url is not null "
                        + "order by created_at desc limit 20",
                user.get("id"));
        for (Map<String, Object> row : rows) {
            row.put("reportUrl", reportDownloadUrl(((Number) row.get("id")).longValue()));
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("reports", rows);
        return result;
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<Resource> downloadReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) throws IOException {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select report_url from agent_logs where id=? and student_id=? and agent_type='analysis'",
                id, user.get("id"));
        if (rows.isEmpty()) {
            throw new BusinessException(404, "报告不存在");
        }
        File file = resolveReportFile(String.valueOf(rows.get(0).get("report_url")));
        if (!file.isFile()) {
            throw new BusinessException(404, "报告文件不存在");
        }
        Resource resource = new FileSystemResource(file);
        String disposition = ContentDisposition.attachment()
                .filename(file.getName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    @DeleteMapping("/reports/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteReport(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable Long id) throws IOException {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select report_url from agent_logs where id=? and student_id=? and agent_type='analysis' for update",
                id, user.get("id"));
        if (rows.isEmpty()) {
            throw new BusinessException(404, "报告不存在");
        }
        String reportUrl = String.valueOf(rows.get(0).get("report_url"));
        File reportFile = resolveReportFile(reportUrl);
        int deleted = jdbcTemplate.update(
                "delete from agent_logs where id=? and student_id=? and agent_type='analysis'", id, user.get("id"));
        if (deleted != 1) {
            throw new BusinessException(409, "Report record changed while it was being deleted.");
        }
        Files.deleteIfExists(reportFile.toPath());
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        return result;
    }

    private long insertAgentStart(long studentId, String type, String requestId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                    "insert into agent_logs(student_id,agent_type,request_id,provider_code,model_code,prompt_version,status,input_summary) "
                            + "values(?,?,?,?,?,?,'running',?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, studentId);
            statement.setString(2, type);
            statement.setString(3, requestId);
            statement.setString(4, agentAiService.providerCode());
            statement.setString(5, agentAiService.modelCode());
            statement.setString(6, LearningAgentAiService.PROMPT_VERSION);
            statement.setString(7, "requestId=" + requestId + "; promptVersion=" + LearningAgentAiService.PROMPT_VERSION);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(500, "报告记录创建失败");
        }
        return key.longValue();
    }

    private String reportDownloadUrl(long reportId) {
        return "/api/agents/reports/" + reportId + "/download";
    }

    private File resolveReportFile(String reportUrl) throws IOException {
        if (reportUrl == null || !reportUrl.startsWith("/reports/")) {
            throw new BusinessException(404, "报告文件不存在");
        }
        String fileName = reportUrl.substring("/reports/".length());
        if (fileName.length() == 0 || !new File(fileName).getName().equals(fileName)) {
            throw new BusinessException(400, "报告文件路径无效");
        }
        File root = new File(reportDir).getCanonicalFile();
        File file = new File(root, fileName).getCanonicalFile();
        if (!root.equals(file.getParentFile())) {
            throw new BusinessException(400, "报告文件路径无效");
        }
        return file;
    }

    private Map<String, Object> runAgent(String type, String authorization) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        long userId = ((Number) user.get("id")).longValue();
        requireRateLimit(userId);
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        long logId = insertAgentStart(userId, type, requestId);
        try {
            RecommendationContext context = buildContext(user);
            LearningAgentResult agentResult = agentAiService.execute(type, context, requestId);
            Map<String, Object> path = "path".equals(type) ? learningPaths.save(userId, agentResult) : null;
            completeAgentLog(logId, agentResult, null, elapsedMs(startedAt));
            Map<String, Object> result = result(agentResult.getContent());
            result.put("provider", agentAiService.providerCode());
            result.put("model", agentAiService.modelCode());
            addAgentMetadata(result, agentResult);
            if (path != null) result.put("learningPath", path);
            return result;
        } catch (RuntimeException exception) {
            failAgentLog(logId, requestId, exception, elapsedMs(startedAt));
            throw exception;
        }
    }

    private void completeAgentLog(long logId, LearningAgentResult result, String reportUrl, long durationMs) {
        int updated = jdbcTemplate.update("update agent_logs set output_result=?,report_url=?,input_summary=?,tools_used=?,sources_used=?,"
                        + "graph_used=?,status='completed',duration_ms=?,error_code=null where id=? and request_id=?",
                result.getContent(), reportUrl, auditSummary(result), String.join(",", result.getToolsUsed()),
                String.join(",", result.getSources()), result.isGraphUsed() ? 1 : 0, durationMs,
                logId, result.getRequestId());
        if (updated != 1) throw new BusinessException(500, "AI 执行记录更新失败。");
    }

    private void failAgentLog(long logId, String requestId, Exception exception, long durationMs) {
        try {
            jdbcTemplate.update("update agent_logs set status='failed',duration_ms=?,error_code=? where id=? and request_id=?",
                    durationMs, errorCode(exception), logId, requestId);
        } catch (RuntimeException auditException) {
            exception.addSuppressed(auditException);
        }
    }

    private void requireRateLimit(long userId) {
        if (!rateLimiter.tryAcquire(userId)) {
            throw new BusinessException(429, "AI 请求过于频繁，请稍后再试。");
        }
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String errorCode(Exception exception) {
        if (exception instanceof BusinessException) return "HTTP_" + ((BusinessException) exception).getCode();
        if (exception instanceof IOException) return "REPORT_IO";
        String name = exception.getClass().getSimpleName().toUpperCase();
        return name.length() <= 60 ? name : name.substring(0, 60);
    }

    private RecommendationContext buildContext(Map<String, Object> user) {
        List<Map<String, Object>> feedback = adaptive.evidence(((Number) user.get("id")).longValue());
        String subject = firstPreference(user.get("id"));
        Integer count = jdbcTemplate.queryForObject("select count(*) from learning_records where student_id=?",
                Integer.class, user.get("id"));
        Integer recentCount = jdbcTemplate.queryForObject(
                "select count(*) from learning_records where student_id=? and created_at >= date_sub(now(), interval 7 day)",
                Integer.class, user.get("id"));
        Number averageProgress = jdbcTemplate.queryForObject(
                "select coalesce(round(avg(progress),0),0) from learning_records where student_id=?",
                Number.class, user.get("id"));
        List<Map<String, Object>> weakNodes = jdbcTemplate.queryForList(
                "select coalesce(knowledge_node,subject,'Unspecified') node,subject,coalesce(round(avg(progress),0),0) avgProgress,count(*) recordCount "
                        + "from learning_records where student_id=? and (progress<60 or progress is null) "
                        + "group by subject,knowledge_node order by avgProgress asc,recordCount desc limit 3",
                user.get("id"));
        List<Map<String, Object>> recentRecords = jdbcTemplate.queryForList(
                "select subject,knowledge_node knowledgeNode,progress,created_at createdAt "
                        + "from learning_records where student_id=? order by created_at desc limit 5",
                user.get("id"));
        List<Map<String, Object>> favoriteResources = jdbcTemplate.queryForList(
                "select r.id,r.title,r.subject,r.type,r.level from resource_favorites f "
                        + "join resources r on r.id=f.resource_id where f.student_id=? "
                        + "and r.status=1 and r.review_status='approved' and r.lifecycle_status='active' "
                        + "order by f.created_at desc limit 3",
                user.get("id"));
        // 优先把最新测验中的薄弱课程作为候选，仍需经过课程发布和完整性校验。
        List<Map<String, Object>> feedbackCourses = new ArrayList<>();
        for (Map<String, Object> item : feedback) {
            if (!(item.get("quizScore") instanceof Number) || ((Number) item.get("quizScore")).intValue() >= 60) continue;
            weakNodes.add(0, Map.of("node", item.get("knowledgeNode"), "subject", item.get("subject"),
                    "quizScore", item.get("quizScore"), "source", "课时测验"));
            feedbackCourses.addAll(jdbcTemplate.queryForList("select id,title,subject,type,level from resources where id=? and status=1 and review_status='approved' and lifecycle_status='active'", item.get("courseId")));
            if (feedbackCourses.size() >= 3) break;
        }
        List<Map<String, Object>> resources = new ArrayList<>(validatedResources(feedbackCourses));
        for (Map<String, Object> candidate : recommendedResources(subject, weakNodes, favoriteResources)) {
            if (resources.stream().noneMatch(row -> row.get("id").equals(candidate.get("id")))) resources.add(candidate);
        }
        return new RecommendationContext.Builder()
                .studentId(user.get("id"))
                .studentName(String.valueOf(user.get("name")))
                .subject(subject)
                .recordCount(count == null ? 0 : count)
                .recentRecordCount(recentCount == null ? 0 : recentCount)
                .averageProgress(averageProgress == null ? 0 : averageProgress.intValue())
                .resources(resources)
                .weakNodes(weakNodes)
                .recentRecords(recentRecords)
                .favoriteResources(favoriteResources)
                .learningEvidence(feedback)
                .studyIntent(planning.studyIntent(((Number)user.get("id")).longValue()))
                .build();
    }

    private List<Map<String, Object>> recommendedResources(String subject,
                                                           List<Map<String, Object>> weakNodes,
                                                           List<Map<String, Object>> favoriteResources) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("select id,title,subject,type,level from resources "
                + "where subject=? and status=1 and review_status='approved' and lifecycle_status='active'");
        params.add(subject);
        if (weakNodes != null && !weakNodes.isEmpty()) {
            sql.append(" and (title like ? or description like ? or subject=?)");
            String node = "%" + weakNodes.get(0).get("node") + "%";
            params.add(node);
            params.add(node);
            params.add(subject);
        }
        sql.append(" order by created_at desc limit 10");
        List<Map<String, Object>> rows = validatedResources(
                jdbcTemplate.queryForList(sql.toString(), params.toArray()));
        if (!rows.isEmpty()) return rows;
        if (favoriteResources != null && !favoriteResources.isEmpty()) {
            rows = validatedResources(favoriteResources);
            if (!rows.isEmpty()) return rows;
        }
        return validatedResources(jdbcTemplate.queryForList("select id,title,subject,type,level from resources "
                + "where status=1 and review_status='approved' and lifecycle_status='active' "
                + "order by created_at desc limit 10"));
    }

    private List<Map<String, Object>> validatedResources(List<Map<String, Object>> candidates) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> candidate : candidates) {
            Object rawId = candidate.get("id");
            if (!(rawId instanceof Number)) continue;
            try {
                if (!contentValidationService.validatePublic(((Number) rawId).longValue()).isContentComplete()) continue;
                result.add(candidate);
                if (result.size() == 3) break;
            } catch (BusinessException ignored) {
                // A resource that changes during selection is excluded from AI evidence.
            }
        }
        return result;
    }

    private String firstPreference(Object studentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select subject from student_preferences where student_id=? order by id limit 1", studentId);
        return rows.isEmpty() ? "Java" : String.valueOf(rows.get(0).get("subject"));
    }

    private Map<String, Object> result(String message) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("result", message);
        return result;
    }

    private void addAgentMetadata(Map<String, Object> response, LearningAgentResult agentResult) {
        response.put("agentMode", agentAiService.executionMode());
        response.put("requestId", agentResult.getRequestId());
        response.put("toolsUsed", agentResult.getToolsUsed());
        response.put("sources", agentResult.getSources());
        response.put("graphUsed", agentResult.isGraphUsed());
        response.put("promptVersion", LearningAgentAiService.PROMPT_VERSION);
    }

    private String auditSummary(LearningAgentResult result) {
        return "requestId=" + result.getRequestId()
                + "; promptVersion=" + LearningAgentAiService.PROMPT_VERSION
                + "; tools=" + String.join(",", result.getToolsUsed())
                + "; sources=" + String.join(",", result.getSources())
                + "; graphUsed=" + result.isGraphUsed();
    }
}
