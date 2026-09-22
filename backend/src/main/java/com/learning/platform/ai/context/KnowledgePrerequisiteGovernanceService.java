package com.learning.platform.ai.context;

import com.learning.platform.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgePrerequisiteGovernanceService {
    private final JdbcTemplate jdbcTemplate;
    private final Neo4jLearningContextService graphService;
    private final PrerequisiteCycleValidator cycleValidator;

    public KnowledgePrerequisiteGovernanceService(JdbcTemplate jdbcTemplate,
                                                   Neo4jLearningContextService graphService,
                                                   PrerequisiteCycleValidator cycleValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.graphService = graphService;
        this.cycleValidator = cycleValidator;
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList(selectSql() + " order by subject,target_title,prerequisite_title,id");
    }

    @Transactional
    public long create(long adminId, String subject, String prerequisiteKey, String prerequisiteTitle,
                       String targetKey, String targetTitle, String sourceReference) {
        validate(subject, 60, "学科");
        validate(prerequisiteKey, 220, "先修知识点键");
        validate(prerequisiteTitle, 120, "先修知识点名称");
        validate(targetKey, 220, "目标知识点键");
        validate(targetTitle, 120, "目标知识点名称");
        validate(sourceReference, 500, "来源说明");
        if (hasControlCharacter(prerequisiteKey) || hasControlCharacter(targetKey)) {
            throw new BusinessException(400, "知识点键不能包含控制字符。");
        }
        if (!prerequisiteKey.contains("/") || !targetKey.contains("/")) {
            throw new BusinessException(400, "知识点键必须包含分类路径，例如 software_engineering/java/spring_boot。");
        }
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "select prerequisite_key prerequisiteKey,target_key targetKey "
                        + "from knowledge_prerequisites where subject=?", subject);
        if (cycleValidator.createsCycle(existing, prerequisiteKey, targetKey)) {
            throw new BusinessException(409, "该先修关系会形成循环依赖，不能保存。");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                java.sql.PreparedStatement statement = connection.prepareStatement(
                        "insert into knowledge_prerequisites(subject,prerequisite_key,prerequisite_title,"
                                + "target_key,target_title,source_reference,created_by,updated_by) values(?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, subject);
                statement.setString(2, prerequisiteKey);
                statement.setString(3, prerequisiteTitle);
                statement.setString(4, targetKey);
                statement.setString(5, targetTitle);
                statement.setString(6, sourceReference);
                statement.setLong(7, adminId);
                statement.setLong(8, adminId);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "该先修关系已经存在。");
        }
        Number key = keyHolder.getKey();
        if (key == null) throw new BusinessException(500, "先修关系保存失败。");
        long id = key.longValue();
        audit(adminId, "KNOWLEDGE_PREREQUISITE_CREATED", id,
                prerequisiteTitle + " -> " + targetTitle + "；来源：" + sourceReference);
        return id;
    }

    @Transactional
    public void delete(long adminId, long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select prerequisite_title prerequisiteTitle,target_title targetTitle "
                        + "from knowledge_prerequisites where id=?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "先修关系不存在。");
        int deleted = jdbcTemplate.update("delete from knowledge_prerequisites where id=?", id);
        if (deleted != 1) throw new BusinessException(409, "先修关系已发生变化，请刷新后重试。");
        Map<String, Object> row = rows.get(0);
        audit(adminId, "KNOWLEDGE_PREREQUISITE_DELETED", id,
                row.get("prerequisiteTitle") + " -> " + row.get("targetTitle"));
    }

    public Map<String, Object> syncAll() {
        List<Map<String, Object>> relations = list();
        Neo4jLearningContextService.PrerequisiteSyncResult sync = graphService.syncPrerequisites(relations);
        String status = sync.isAvailable() ? "synced" : "failed";
        String error = sync.isAvailable() ? null : sync.getReason();
        jdbcTemplate.update("update knowledge_prerequisites set sync_status=?,sync_error=?", status, error);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("available", sync.isAvailable());
        result.put("syncedCount", sync.getSyncedCount());
        result.put("relationCount", relations.size());
        if (sync.getReason() != null) result.put("reason", sync.getReason());
        return result;
    }

    private String selectSql() {
        return "select id,subject,prerequisite_key prerequisiteKey,prerequisite_title prerequisiteTitle,"
                + "target_key targetKey,target_title targetTitle,source_reference sourceReference,"
                + "sync_status syncStatus,sync_error syncError,created_at createdAt,updated_at updatedAt "
                + "from knowledge_prerequisites";
    }

    private void validate(String value, int maxLength, String label) {
        if (value == null || value.trim().isEmpty()) throw new BusinessException(400, label + "不能为空。");
        if (value.length() > maxLength) throw new BusinessException(400, label + "不能超过 " + maxLength + " 个字符。");
    }

    private boolean hasControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return true;
        }
        return false;
    }

    private void audit(long adminId, String action, long targetId, String detail) {
        String safeDetail = detail.length() <= 500 ? detail : detail.substring(0, 500);
        jdbcTemplate.update("insert into admin_audit_logs(admin_id,action_type,target_type,target_id,detail) "
                        + "values(?,?,'knowledge_prerequisite',?,?)",
                adminId, action, targetId, safeDetail);
    }
}
