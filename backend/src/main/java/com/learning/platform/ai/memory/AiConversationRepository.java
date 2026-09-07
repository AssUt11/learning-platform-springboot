package com.learning.platform.ai.memory;

import com.learning.platform.ai.domain.AiProviderMessage;
import com.learning.platform.ai.domain.AiUsage;
import com.learning.platform.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AiConversationRepository {
    private final JdbcTemplate jdbcTemplate;

    public AiConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long resolveConversation(Long conversationId, long userId, long courseId, long lessonId, String title) {
        if (conversationId != null) {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from ai_conversation where id=? and user_id=? and course_id=? and lesson_id=? and status='active'",
                    Integer.class, conversationId, userId, courseId, lessonId);
            if (count == null || count != 1) throw new BusinessException(404, "AI 会话不存在或不属于当前课时。");
            return conversationId;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                    "insert into ai_conversation(user_id,course_id,lesson_id,title) values(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setLong(2, courseId);
            statement.setLong(3, lessonId);
            statement.setString(4, shorten(title, 160));
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) throw new BusinessException(500, "AI 会话创建失败。");
        return keyHolder.getKey().longValue();
    }

    public long insertMessage(long conversationId, String role, String content, String provider,
                              String model, String requestId, String promptVersion, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement(
                    "insert into ai_message(conversation_id,role,content,provider_code,model_code,request_id,prompt_version,status) "
                            + "values(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, conversationId);
            statement.setString(2, role);
            statement.setString(3, content == null ? "" : content);
            statement.setString(4, provider);
            statement.setString(5, model);
            statement.setString(6, requestId);
            statement.setString(7, promptVersion);
            statement.setString(8, status);
            return statement;
        }, keyHolder);
        if (keyHolder.getKey() == null) throw new BusinessException(500, "AI 消息创建失败。");
        jdbcTemplate.update("update ai_conversation set updated_at=current_timestamp where id=?", conversationId);
        return keyHolder.getKey().longValue();
    }

    public void updateMessage(long messageId, String content, String status) {
        jdbcTemplate.update("update ai_message set content=?,status=? where id=?", content == null ? "" : content, status, messageId);
    }

    public List<AiProviderMessage> recentHistory(long conversationId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select role,content from (select m.id,m.role,m.content from ai_message m "
                        + "where m.conversation_id=? and m.status='completed' and ("
                        + "m.role='ASSISTANT' or (m.role='USER' and m.request_id is not null and exists ("
                        + "select 1 from ai_message a where a.conversation_id=m.conversation_id "
                        + "and a.request_id=m.request_id and a.role='ASSISTANT' and a.status='completed'))) "
                        + "order by m.id desc limit ?) recent order by id", conversationId, Math.max(0, limit));
        List<AiProviderMessage> messages = new ArrayList<AiProviderMessage>();
        for (Map<String, Object> row : rows) {
            String role = "ASSISTANT".equals(String.valueOf(row.get("role"))) ? "assistant" : "user";
            messages.add(new AiProviderMessage(role, String.valueOf(row.get("content"))));
        }
        return messages;
    }

    public void insertUsage(String requestId, long userId, long courseId, long lessonId,
                            String provider, String model, AiUsage usage, boolean neo4jUsed,
                            int graphNodeCount, String status, String errorCode) {
        AiUsage safe = usage == null ? new AiUsage(0, 0, 0L, 0L, 0D) : usage;
        jdbcTemplate.update("insert into ai_usage_log(request_id,user_id,course_id,lesson_id,provider_code,model_code,"
                        + "prompt_eval_count,eval_count,load_duration_ms,total_duration_ms,tokens_per_second,neo4j_used,"
                        + "graph_node_count,status,error_code) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "on duplicate key update prompt_eval_count=values(prompt_eval_count),eval_count=values(eval_count),"
                        + "load_duration_ms=values(load_duration_ms),total_duration_ms=values(total_duration_ms),"
                        + "tokens_per_second=values(tokens_per_second),status=values(status),error_code=values(error_code)",
                requestId, userId, courseId, lessonId, provider, model,
                safe.getPromptTokens(), safe.getOutputTokens(), safe.getLoadDurationMs(), safe.getTotalDurationMs(),
                safe.getTokensPerSecond(), neo4jUsed ? 1 : 0, graphNodeCount, status, errorCode);
    }

    public void insertCitation(long messageId, String sourceType, long sourceId, String title) {
        jdbcTemplate.update("insert ignore into ai_message_citation(message_id,source_type,source_id,title) values(?,?,?,?)",
                messageId, sourceType, String.valueOf(sourceId), shorten(title, 200));
    }

    public List<Map<String, Object>> listConversations(long userId) {
        return jdbcTemplate.queryForList(
                "select c.id,c.course_id courseId,c.lesson_id lessonId,c.title,c.created_at createdAt,c.updated_at updatedAt,"
                        + "(select left(m.content,180) from ai_message m where m.conversation_id=c.id and m.role='ASSISTANT' "
                        + "and m.status='completed' order by m.id desc limit 1) lastAnswer "
                        + "from ai_conversation c where c.user_id=? and c.status='active' order by c.updated_at desc limit 50",
                userId);
    }

    public Map<String, Object> conversation(long conversationId, long userId) {
        List<Map<String, Object>> conversations = jdbcTemplate.queryForList(
                "select id,course_id courseId,lesson_id lessonId,title,created_at createdAt,updated_at updatedAt "
                        + "from ai_conversation where id=? and user_id=? and status='active'", conversationId, userId);
        if (conversations.isEmpty()) throw new BusinessException(404, "AI 会话不存在。");
        Map<String, Object> result = new HashMap<String, Object>(conversations.get(0));
        result.put("messages", jdbcTemplate.queryForList(
                "select id,role,content,request_id requestId,status,created_at createdAt from ai_message "
                        + "where conversation_id=? and status in ('completed','cancelled','failed') order by id", conversationId));
        result.put("citations", jdbcTemplate.queryForList(
                "select c.message_id messageId,c.source_type sourceType,c.source_id sourceId,c.title "
                        + "from ai_message_citation c join ai_message m on m.id=c.message_id "
                        + "where m.conversation_id=? order by c.id", conversationId));
        return result;
    }

    public void deleteConversation(long conversationId, long userId) {
        int updated = jdbcTemplate.update(
                "update ai_conversation set status='deleted' where id=? and user_id=? and status='active'",
                conversationId, userId);
        if (updated != 1) throw new BusinessException(404, "AI 会话不存在。");
    }

    public void feedback(long messageId, long userId, int rating, String reason) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from ai_message m join ai_conversation c on c.id=m.conversation_id "
                        + "where m.id=? and m.role='ASSISTANT' and c.user_id=? and c.status='active'",
                Integer.class, messageId, userId);
        if (count == null || count != 1) throw new BusinessException(404, "AI 回答不存在。");
        jdbcTemplate.update("insert into ai_feedback(user_id,message_id,rating,reason) values(?,?,?,?) "
                        + "on duplicate key update rating=values(rating),reason=values(reason),updated_at=current_timestamp",
                userId, messageId, rating, shorten(reason, 500));
    }

    private String shorten(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
