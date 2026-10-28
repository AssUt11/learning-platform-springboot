package com.learning.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import com.learning.platform.ai.domain.LearningAgentResult;
import com.learning.platform.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class StudentLearningPathService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Neo4jLearningContextService neo4j;
    private final AdaptiveLearningService adaptive;

    public StudentLearningPathService(JdbcTemplate jdbc, ObjectMapper mapper, Neo4jLearningContextService neo4j, AdaptiveLearningService adaptive) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.neo4j = neo4j;
        this.adaptive = adaptive;
    }

    public Map<String, Object> save(long userId, LearningAgentResult result) {
        if (result.getLearningPath().isEmpty()) throw new BusinessException(502, "没有生成有效的学习路径。");
        Map<String, Object> snapshot = new LinkedHashMap<>(result.getLearningPath());
        snapshot.put("pathId", result.getRequestId());
        snapshot.put("createdAt", Instant.now().toString());
        snapshot.put("graphUsed", result.isGraphUsed());
        snapshot.put("analysis", result.getContent());
        snapshot.put("available", true);
        String json = encode(snapshot);
        jdbc.update("insert into student_learning_paths(path_id,user_id,snapshot_json) values(?,?,?)",
                result.getRequestId(), userId, json);
        boolean synced = sync(userId, snapshot, json);
        return adaptive.enrich(userId, view(snapshot, synced, synced ? "neo4j" : "snapshot"));
    }

    public Map<String, Object> latest(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select path_id from student_learning_paths where user_id=? order by id desc limit 1", userId);
        return rows.isEmpty() ? Map.of("available", false) : get(userId, String.valueOf(rows.get(0).get("path_id")));
    }

    public Map<String, Object> get(long userId, String pathId) {
        Map<String, Object> row = owned(userId, pathId);
        String graphJson = neo4j.readStudentPath(userId, pathId);
        return adaptive.enrich(userId, view(decode(graphJson == null ? String.valueOf(row.get("snapshot_json")) : graphJson),
                ((Number) row.get("neo4j_synced")).intValue() == 1, graphJson == null ? "snapshot" : "neo4j"));
    }

    public Map<String, Object> retrySync(long userId, String pathId) {
        Map<String, Object> row = owned(userId, pathId);
        String json = String.valueOf(row.get("snapshot_json"));
        Map<String, Object> snapshot = decode(json);
        boolean synced = sync(userId, snapshot, json);
        return adaptive.enrich(userId, view(snapshot, synced, synced ? "neo4j" : "snapshot"));
    }

    private Map<String, Object> owned(long userId, String pathId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select snapshot_json,neo4j_synced from student_learning_paths where path_id=? and user_id=?", pathId, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "学习路径不存在。");
        return rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private boolean sync(long userId, Map<String, Object> snapshot, String json) {
        String pathId = String.valueOf(snapshot.get("pathId"));
        boolean synced = neo4j.saveStudentPath(userId, pathId, String.valueOf(snapshot.get("title")), json,
                (List<Map<String, Object>>) snapshot.get("nodes"));
        if (synced) jdbc.update("update student_learning_paths set neo4j_synced=1 where path_id=? and user_id=?", pathId, userId);
        return synced;
    }

    private Map<String, Object> view(Map<String, Object> snapshot, boolean synced, String source) {
        snapshot.put("neo4jSynced", synced);
        snapshot.put("source", source);
        return snapshot;
    }

    private String encode(Map<String, Object> snapshot) {
        try { return mapper.writeValueAsString(snapshot); }
        catch (Exception error) { throw new BusinessException(500, "学习路径保存失败。"); }
    }
    private Map<String, Object> decode(String json) {
        try { return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { }); }
        catch (Exception error) { throw new BusinessException(500, "学习路径读取失败。"); }
    }
}
