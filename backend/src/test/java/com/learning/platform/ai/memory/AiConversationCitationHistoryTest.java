package com.learning.platform.ai.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiConversationCitationHistoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void historicalDocumentReferencesRetainIdentityButRequireReauthorizationForText() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public List<Map<String, Object>> queryForList(String sql, Object... arguments) {
                if (sql.contains("from ai_conversation where")) return List.of(Map.of("id", 4L));
                if (!sql.contains("ai_message_citation")) return List.of();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("messageId", 9L); row.put("sourceType", "COURSEWARE_CHUNK");
                row.put("sourceId", "8"); row.put("title", "讲义.pdf · 第 2 页");
                row.put("metadataJson", "{\"chunkId\":8,\"messageId\":999,\"referenceNumber\":1,"
                    + "\"versionHash\":\"v1\",\"excerpt\":\"旧课件摘录\",\"text\":\"旧正文\","
                    + "\"url\":\"/api/learning/courseware/chunks/8?courseId=21&lessonId=302\"}");
                return List.of(row);
            }
        };
        Map<String, Object> history = new AiConversationRepository(jdbc).conversation(4, 7);
        Map<String, Object> reference = ((List<Map<String, Object>>) history.get("citations")).get(0);
        assertEquals(9L, reference.get("messageId"));
        assertEquals(8, reference.get("chunkId"));
        assertEquals(1, reference.get("referenceNumber"));
        assertEquals("v1", reference.get("versionHash"));
        assertEquals(true, reference.get("historical"));
        assertFalse(reference.containsKey("excerpt"));
        assertFalse(reference.containsKey("text"));
        assertFalse(reference.containsKey("metadataJson"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void oldTitleOnlyReferencesRemainReadableWithoutMetadata() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public List<Map<String, Object>> queryForList(String sql, Object... arguments) {
                if (sql.contains("from ai_conversation where")) return List.of(Map.of("id", 4L));
                if (!sql.contains("ai_message_citation")) return List.of();
                return List.of(Map.of("messageId", 9L, "sourceType", "COURSE", "sourceId", "21", "title", "数据库"));
            }
        };
        Map<String, Object> history = new AiConversationRepository(jdbc).conversation(4, 7);
        Map<String, Object> reference = ((List<Map<String, Object>>) history.get("citations")).get(0);
        assertEquals("数据库", reference.get("title"));
        assertEquals("COURSE", reference.get("sourceType"));
    }
}
