package com.learning.platform.ai.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.learning.platform.courseware.CoursewareAssetVersion;
import com.learning.platform.courseware.CoursewareFileParser;
import com.learning.platform.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CoursewareRetrievalServiceTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void selectsQuestionRelevantChineseMaterialAndKeepsCurrentLessonScope() throws Exception {
        MemoryJdbc jdbc = new MemoryJdbc();
        jdbc.rows = List.of(row(1, "索引帮助缩小数据扫描范围。"), row(2, "事务隔离级别影响并发事务之间看到的数据。"));
        CoursewareRetrievalService service = new CoursewareRetrievalService(jdbc, files());
        List<CoursewareSnippet> result = service.retrieve(21, 302, "请问事务隔离级别是什么？");
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getChunkId());
        assertTrue(jdbc.sql.contains("d.status='CONFIRMED'"));
        assertTrue(jdbc.sql.contains("r.review_status='approved'"));
        assertTrue(jdbc.sql.contains("ch.status='published'"));
        assertTrue(jdbc.sql.contains("l.lesson_type='document'"));
        assertEquals(21L, jdbc.arguments[jdbc.arguments.length - 2]);
        assertEquals(302L, jdbc.arguments[jdbc.arguments.length - 1]);
        assertEquals("PAGE", result.get(0).citation().get("locationType"));
        assertEquals("/api/learning/courseware/chunks/2?courseId=21&lessonId=302", result.get(0).citation().get("url"));
    }

    @Test
    void rejectsWithdrawnEditedOrPhysicallyChangedVersionsAndDeduplicatesText() throws Exception {
        MemoryJdbc jdbc = new MemoryJdbc();
        Map<String, Object> withdrawn = row(1, "事务隔离资料一");
        withdrawn.put("documentStatus", "SUPERSEDED");
        Map<String, Object> edited = row(2, "事务隔离资料二");
        edited.put("contentText", "教师修改了补充文字");
        Map<String, Object> fileChanged = row(3, "事务隔离资料四");
        fileChanged.put("versionHash", "b".repeat(64));
        Map<String, Object> valid = row(4, "事务隔离资料四");
        Map<String, Object> duplicate = row(5, "事务隔离资料四");
        Map<String, Object> unready = row(6, "事务隔离资料五");
        unready.put("assetReadiness", "processing");
        jdbc.rows = List.of(withdrawn, edited, fileChanged, valid, duplicate, unready);
        CoursewareFileParser files = files();
        List<CoursewareSnippet> result = new CoursewareRetrievalService(jdbc, files).retrieve(21, 302, "事务隔离");
        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).getChunkId());
        verify(files, times(1)).hash(any(Path.class));
    }

    @Test
    void teacherSummaryIsPartOfTheSharedFingerprintAndDoesNotInvalidateUnchangedAssets() throws Exception {
        MemoryJdbc jdbc = new MemoryJdbc();
        Map<String, Object> document = row(6, "事务隔离课件正文");
        document.put("contentText", "教师已填写的非空补充说明");
        document.put("documentAssetVersion", CoursewareAssetVersion.of(document));
        jdbc.rows = List.of(document);
        CoursewareRetrievalService service = new CoursewareRetrievalService(jdbc, files());
        assertEquals(1, service.retrieve(21, 302, "事务隔离").size());
        assertTrue(jdbc.sql.contains("coalesce(a.content_text,'') contentText"));
        assertEquals(6L, service.source(21, 302, 6).get("chunkId"));
    }

    @Test
    void staleSourceReturnsGoneInsteadOfHistoricalText() throws Exception {
        MemoryJdbc jdbc = new MemoryJdbc();
        Map<String, Object> stale = row(1, "历史原文不应返回");
        stale.put("assetStatus", 0);
        jdbc.rows = List.of(stale);
        BusinessException error = assertThrows(BusinessException.class,
            () -> new CoursewareRetrievalService(jdbc, files()).source(21, 302, 1));
        assertEquals(410, error.getCode());
        assertFalse(error.getMessage().contains("历史原文"));
    }

    @Test
    void missingPhysicalFileIsNotAReferenceAndLikePatternsStayParameters() throws Exception {
        MemoryJdbc jdbc = new MemoryJdbc();
        jdbc.rows = List.of(row(1, "SQL 事务资料"));
        CoursewareFileParser files = files();
        when(files.resolve(anyString())).thenThrow(new IOException("missing"));
        assertTrue(new CoursewareRetrievalService(jdbc, files).retrieve(21, 302, "SQL %_'事务").isEmpty());
        assertFalse(jdbc.sql.contains("%_'事务"));
        assertTrue(jdbc.sql.contains("escape '!'"));
    }

    @Test
    void emptyGenericQuestionDoesNotLoadAllCourseware() throws Exception {
        MemoryJdbc jdbc = new MemoryJdbc();
        assertTrue(new CoursewareRetrievalService(jdbc, files()).retrieve(21, 302, "请问是什么").isEmpty());
        assertNull(jdbc.sql);
    }

    private static CoursewareFileParser files() throws Exception {
        CoursewareFileParser parser = mock(CoursewareFileParser.class);
        when(parser.resolve(anyString())).thenReturn(Path.of("test-courseware.pdf"));
        when(parser.hash(any(Path.class))).thenReturn(HASH);
        return parser;
    }

    private static Map<String, Object> row(long id, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chunkId", id); row.put("documentId", "doc-1"); row.put("assetId", 88L);
        row.put("courseId", 21L); row.put("lessonId", 302L); row.put("fileName", "事务讲义.pdf");
        row.put("locationType", "PAGE"); row.put("locationIndex", 2); row.put("textContent", content);
        row.put("fileUrl", "/uploads/courseware.pdf"); row.put("fileSize", 500L);
        row.put("assetType", "document"); row.put("assetUpdatedAt", "2026-11-08 10:00:00");
        row.put("contentText", ""); row.put("assetStatus", 1); row.put("documentStatus", "CONFIRMED");
        row.put("assetReadiness", "ready");
        row.put("versionHash", HASH); row.put("documentAssetVersion", CoursewareAssetVersion.of(row));
        return row;
    }

    private static final class MemoryJdbc extends JdbcTemplate {
        private List<Map<String, Object>> rows = new ArrayList<>();
        private String sql;
        private Object[] arguments;
        @Override public List<Map<String, Object>> queryForList(String sql, Object... arguments) {
            this.sql = sql; this.arguments = arguments;
            return rows;
        }
    }
}
