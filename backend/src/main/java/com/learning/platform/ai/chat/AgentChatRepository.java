package com.learning.platform.ai.chat;

import com.learning.platform.ai.domain.AiProviderMessage;
import com.learning.platform.ai.domain.AiUsage;
import com.learning.platform.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.*;

@Repository
public class AgentChatRepository {
    private final JdbcTemplate jdbc;
    public AgentChatRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> list(long userId) {
        return jdbc.queryForList("""
                select c.id,c.title,c.created_at createdAt,c.updated_at updatedAt,
                  (select left(m.content,120) from ai_agent_message m where m.conversation_id=c.id
                    and m.role='assistant' and m.status='completed' order by m.id desc limit 1) preview
                from ai_agent_conversation c where c.user_id=? order by c.updated_at desc,c.id desc limit 50
                """, userId);
    }

    public Map<String, Object> conversation(long userId, long conversationId) {
        Map<String, Object> result = new LinkedHashMap<>(requireOwned(userId, conversationId));
        result.put("messages", jdbc.queryForList("""
                select * from (
                  select id,role,content,status,request_id requestId,provider_code provider,model_code model,
                    created_at createdAt from ai_agent_message where conversation_id=? order by id desc limit 200
                ) recent order by id
                """, conversationId));
        return result;
    }

    @Transactional
    public Turn prepare(long userId, Long conversationId, String question, String requestId, String provider, String model) {
        long id;
        if (conversationId == null) {
            String title = question.substring(0, question.offsetByCodePoints(0, Math.min(45, question.codePointCount(0, question.length()))));
            id = insert("insert into ai_agent_conversation(user_id,title) values(?,?)", userId, title);
        } else {
            id = conversationId;
            requireOwned(userId, id);
        }
        // Include only complete user/assistant pairs; an interrupted answer is not usable model memory.
        List<AiProviderMessage> history = jdbc.query("""
                select role,content from (
                  select m.id,m.role,m.content from ai_agent_message m
                  where m.conversation_id=? and m.status='completed' and exists (
                    select 1 from ai_agent_message a where a.conversation_id=m.conversation_id
                      and a.request_id=m.request_id and a.role='assistant' and a.status='completed')
                  order by m.id desc limit 8
                ) recent order by id
                """, (rs, row) -> new AiProviderMessage(rs.getString("role"), rs.getString("content")), id);
        insert("insert into ai_agent_message(conversation_id,role,content,status,request_id) values(?,'user',?,'completed',?)",
                id, question, requestId);
        long messageId = insert("""
                insert into ai_agent_message(conversation_id,role,content,status,request_id,provider_code,model_code)
                values(?,'assistant','','queued',?,?,?)
                """, id, requestId, provider, model);
        jdbc.update("update ai_agent_conversation set updated_at=current_timestamp where id=?", id);
        return new Turn(id, messageId, history);
    }

    @Transactional
    public void finish(Turn turn, String content, String status, AiUsage usage) {
        jdbc.update("""
                update ai_agent_message set content=?,status=?,prompt_tokens=?,output_tokens=?,duration_ms=? where id=?
                """, content, status, usage == null || usage.getPromptTokens() < 0 ? null : usage.getPromptTokens(),
                usage == null || usage.getOutputTokens() < 0 ? null : usage.getOutputTokens(),
                usage == null ? null : usage.getTotalDurationMs(), turn.messageId());
        jdbc.update("update ai_agent_conversation set updated_at=current_timestamp where id=?", turn.conversationId());
    }

    public void generating(long messageId) {
        jdbc.update("update ai_agent_message set status='generating' where id=? and status='queued'", messageId);
    }

    private Map<String, Object> requireOwned(long userId, long conversationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id,title,created_at createdAt,updated_at updatedAt from ai_agent_conversation where id=? and user_id=?",
                conversationId, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "对话不存在。");
        return rows.get(0);
    }

    private long insert(String sql, Object... values) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        if (key.getKey() == null) throw new BusinessException(500, "对话保存失败。");
        return key.getKey().longValue();
    }

    public record Turn(long conversationId, long messageId, List<AiProviderMessage> history) { }
}
