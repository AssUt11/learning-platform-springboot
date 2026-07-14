package com.learning.platform.controller;

import com.learning.platform.pattern.AgentStrategyFactory;
import com.learning.platform.pattern.RecommendationContext;
import com.learning.platform.pattern.WordLearningAnalysisReportTemplate;
import com.learning.platform.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
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
        Map<String, Object> user = currentUserService.requireStudent(authorization);
        RecommendationContext context = buildContext(user);
        File file = reportTemplate.generate(user, context, new File(reportDir));
        String url = "/reports/" + file.getName();
        jdbcTemplate.update("insert into agent_logs(student_id,agent_type,output_result,report_url) values(?,?,?,?)",
                user.get("id"), "analysis", "Learning analysis Word report", url);
        Map<String, Object> result = result("Learning analysis agent generated a Word report: " + url);
        result.put("downloadUrl", url);
        return result;
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireStudent(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id,output_result outputResult,report_url reportUrl,created_at createdAt "
                        + "from agent_logs where student_id=? and agent_type='analysis' and report_url is not null "
                        + "order by created_at desc limit 20",
                user.get("id"));
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("reports", rows);
        return result;
    }

    @DeleteMapping("/reports/{id}")
    public Map<String, Object> deleteReport(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable Long id) {
        Map<String, Object> user = currentUserService.requireStudent(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select report_url from agent_logs where id=? and student_id=? and agent_type='analysis'",
                id, user.get("id"));
        if (rows.isEmpty()) {
            throw new RuntimeException("Report not found.");
        }
        String reportUrl = String.valueOf(rows.get(0).get("report_url"));
        if (reportUrl.startsWith("/reports/")) {
            File file = new File(reportDir, reportUrl.substring("/reports/".length()));
            if (file.exists()) {
                file.delete();
            }
        }
        jdbcTemplate.update("delete from agent_logs where id=? and student_id=? and agent_type='analysis'", id, user.get("id"));
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        return result;
    }

    private Map<String, Object> runAgent(String type, String authorization) {
        Map<String, Object> user = currentUserService.requireStudent(authorization);
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
