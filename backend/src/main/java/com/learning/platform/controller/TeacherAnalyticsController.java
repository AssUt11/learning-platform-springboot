package com.learning.platform.controller;

import com.learning.platform.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
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
        currentUserService.requireTeacher(authorization);
        Map<String, Object> result = new HashMap<String, Object>();
        List<Map<String, Object>> resourceStats = resourceStats();
        result.put("summary", summary());
        result.put("resourceStats", resourceStats);
        result.put("popularResources", firstItems(resourceStats, 5));
        result.put("warningStudents", warningStudents());
        result.put("weakNodes", weakNodes());
        return result;
    }

    private Map<String, Object> summary() {
        Map<String, Object> summary = new HashMap<String, Object>();
        summary.put("studentCount", singleNumber("select count(*) from users where role='student'"));
        summary.put("activeStudents", singleNumber(
                "select count(distinct student_id) from learning_records where created_at >= date_sub(now(), interval 7 day)"));
        summary.put("resourceCount", singleNumber("select count(*) from resources"));
        summary.put("courseCompletionRate", singleNumber(
                "select coalesce(round(sum(case when progress>=100 then 1 else 0 end)*100.0/nullif(count(*),0),0),0) "
                        + "from learning_records where resource_id is not null"));
        return summary;
    }

    private List<Map<String, Object>> resourceStats() {
        return jdbcTemplate.queryForList(
                "select r.id,r.title,r.subject,r.type,"
                        + "count(lr.id) playCount,"
                        + "coalesce(round(sum(case when lr.progress>=100 then 1 else 0 end)*100.0/nullif(count(lr.id),0),0),0) completionRate,"
                        + "coalesce(f.favoriteCount,0) favoriteCount,"
                        + "coalesce(round(avg(lr.progress),0),0) avgProgress "
                        + "from resources r "
                        + "left join learning_records lr on lr.resource_id=r.id "
                        + "left join (select resource_id,count(*) favoriteCount from resource_favorites group by resource_id) f on f.resource_id=r.id "
                        + "group by r.id,r.title,r.subject,r.type,r.created_at,f.favoriteCount "
                        + "order by playCount desc,favoriteCount desc,r.created_at desc");
    }

    private List<Map<String, Object>> warningStudents() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select u.id,u.name,u.email,"
                        + "count(lr.id) recordCount,"
                        + "coalesce(round(avg(lr.progress),0),0) avgProgress,"
                        + "max(lr.created_at) lastActiveAt,"
                        + "datediff(now(),coalesce(max(lr.created_at),u.created_at)) inactiveDays,"
                        + "sum(case when lr.knowledge_node is not null and lr.progress<50 then 1 else 0 end) weakNodeCount "
                        + "from users u "
                        + "left join learning_records lr on lr.student_id=u.id "
                        + "where u.role='student' "
                        + "group by u.id,u.name,u.email,u.created_at "
                        + "having recordCount=0 or avgProgress<40 or inactiveDays>=7 or weakNodeCount>=3 "
                        + "order by inactiveDays desc,avgProgress asc,weakNodeCount desc "
                        + "limit 8");
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

    private List<Map<String, Object>> weakNodes() {
        return jdbcTemplate.queryForList(
                "select coalesce(subject,'Uncategorized') subject,knowledge_node node,"
                        + "count(*) weakCount,coalesce(round(avg(progress),0),0) avgProgress "
                        + "from learning_records "
                        + "where knowledge_node is not null "
                        + "group by subject,knowledge_node "
                        + "having avg(progress)<60 "
                        + "order by weakCount desc,avgProgress asc "
                        + "limit 8");
    }

    private List<Map<String, Object>> firstItems(List<Map<String, Object>> rows, int size) {
        return new ArrayList<Map<String, Object>>(rows.subList(0, Math.min(rows.size(), size)));
    }

    private Number singleNumber(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
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
