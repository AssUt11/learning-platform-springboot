package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.pattern.AgentStrategyFactory;
import com.learning.platform.pattern.RecommendationContext;
import com.learning.platform.pattern.WordLearningAnalysisReportTemplate;
import com.learning.platform.service.CurrentUserService;
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

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final AgentStrategyFactory strategyFactory;
    private final WordLearningAnalysisReportTemplate reportTemplate;

    @Value("${app.report-dir}")
    private String reportDir;

    public AgentController(JdbcTemplate jdbcTemplate,
                           CurrentUserService currentUserService,
                           AgentStrategyFactory strategyFactory,
                           WordLearningAnalysisReportTemplate reportTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.strategyFactory = strategyFactory;
        this.reportTemplate = reportTemplate;
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
        RecommendationContext context = buildContext(user);
        File file = reportTemplate.generate(user, context, new File(reportDir));
        String storedUrl = "/reports/" + file.getName();
        long reportId;
        try {
            reportId = insertAnalysisLog(user.get("id"), "Learning analysis Word report", storedUrl);
        } catch (RuntimeException exception) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        String downloadUrl = reportDownloadUrl(reportId);
        Map<String, Object> result = result("Learning analysis agent generated a Word report.");
        result.put("downloadUrl", downloadUrl);
        result.put("reportId", reportId);
        return result;
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireLearner(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id,output_result outputResult,created_at createdAt "
                        + "from agent_logs where student_id=? and agent_type='analysis' and report_url is not null "
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

    private long insertAnalysisLog(Object studentId, String outputResult, String storedUrl) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                    "insert into agent_logs(student_id,agent_type,output_result,report_url) values(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, studentId);
            statement.setString(2, "analysis");
            statement.setString(3, outputResult);
            statement.setString(4, storedUrl);
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
        RecommendationContext context = buildContext(user);
        String output = strategyFactory.getStrategy(type).execute(context);
        jdbcTemplate.update("insert into agent_logs(student_id,agent_type,output_result) values(?,?,?)",
                user.get("id"), type, output);
        return result(output);
    }

    private RecommendationContext buildContext(Map<String, Object> user) {
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
                "select r.title,r.subject,r.type from resource_favorites f "
                        + "join resources r on r.id=f.resource_id where f.student_id=? order by f.created_at desc limit 3",
                user.get("id"));
        List<Map<String, Object>> resources = recommendedResources(subject, weakNodes, favoriteResources);
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
                .build();
    }

    private List<Map<String, Object>> recommendedResources(String subject,
                                                           List<Map<String, Object>> weakNodes,
                                                           List<Map<String, Object>> favoriteResources) {
        List<Object> params = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("select title,subject,type,level from resources where subject=?");
        params.add(subject);
        if (weakNodes != null && !weakNodes.isEmpty()) {
            sql.append(" and (title like ? or description like ? or subject=?)");
            String node = "%" + weakNodes.get(0).get("node") + "%";
            params.add(node);
            params.add(node);
            params.add(subject);
        }
        sql.append(" order by created_at desc limit 3");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        if (!rows.isEmpty()) {
            return rows;
        }
        if (favoriteResources != null && !favoriteResources.isEmpty()) {
            return favoriteResources;
        }
        return jdbcTemplate.queryForList("select title,subject,type,level from resources order by created_at desc limit 3");
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
}
