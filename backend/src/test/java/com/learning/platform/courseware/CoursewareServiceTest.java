package com.learning.platform.courseware;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.learning.platform.exception.BusinessException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class CoursewareServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CoursewareFileParser parser = mock(CoursewareFileParser.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final CoursewareService service = new CoursewareService(jdbc, parser, manager);
    private final Map<String, Object> asset = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        asset.putAll(Map.of("assetId", 5L, "fileUrl", "/uploads/course.pdf", "fileName", "course.pdf",
            "fileSize", 100L, "assetType", "document", "assetUpdatedAt", Timestamp.valueOf("2026-11-08 12:00:00"),
            "publicCourseId", 21L, "contentText", "教师摘要"));
        when(jdbc.queryForList(contains("where a.id=?"), eq(5L), eq(7L), eq(11L))).thenReturn(List.of(asset));
        when(parser.supports("course.pdf")).thenReturn(true);
        when(parser.resolve("/uploads/course.pdf")).thenReturn(Path.of("course.pdf"));
    }

    @Test
    void onlyOwningTeacherCanInspectAnAsset() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.status(99L, 7L, 5L));
        assertEquals(404, error.getCode());
        verifyNoInteractions(parser);
    }

    @Test
    void parsedButUnconfirmedDocumentIsOnlyAPreview() {
        Map<String, Object> document = document("READY");
        when(jdbc.queryForList(contains("order by (asset_version=?)"), eq(5L), anyString())).thenReturn(List.of(document));
        when(jdbc.queryForList(contains("from courseware_chunks"), eq("doc-1")))
            .thenReturn(List.of(Map.of("id", 1L, "locationType", "PAGE", "locationIndex", 1, "text", "课程原文")));

        Map<String, Object> result = service.status(11L, 7L, 5L);

        assertEquals("READY", result.get("status"));
        assertEquals("doc-1", result.get("documentId"));
        assertEquals(1, ((List<?>) result.get("segments")).size());
        verify(jdbc, never()).update(contains("CONFIRMED"), any(Object[].class));
    }

    @Test
    void changingTextInSameTimestampInvalidatesTheAssetVersion() {
        String original = CoursewareAssetVersion.of(asset);
        Map<String, Object> doc = document("CONFIRMED");
        asset.put("contentText", "修改后的摘要");
        assertNotEquals(original, CoursewareAssetVersion.of(asset));
        when(jdbc.queryForList(contains("order by (asset_version=?)"), eq(5L), anyString())).thenReturn(List.of(doc));

        Map<String, Object> result = service.status(11L, 7L, 5L);

        assertEquals("STALE", result.get("status"));
        assertTrue(((List<?>) result.get("segments")).isEmpty());
        verify(jdbc, never()).queryForList(contains("from courseware_chunks"), anyString());
    }

    @Test
    void confirmationRejectsAnUnseenHashWithoutMutatingAnyTable() {
        when(jdbc.queryForList(contains("where id=? and asset_id=?"), eq("doc-1"), eq(5L), eq(11L)))
            .thenReturn(List.of(document("READY")));

        BusinessException error = assertThrows(BusinessException.class,
            () -> service.confirm(11L, 7L, 5L, "doc-1", "wrong-hash"));

        assertEquals(409, error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void confirmationChecksFileBytesAndNeverUpdatesTheOriginalAsset() throws Exception {
        Map<String, Object> document = document("READY");
        when(jdbc.queryForList(contains("where id=? and asset_id=?"), eq("doc-1"), eq(5L), eq(11L)))
            .thenReturn(List.of(document));
        when(parser.hash(Path.of("course.pdf"))).thenReturn("verified-file-hash");
        when(jdbc.queryForList(contains("order by (asset_version=?)"), eq(5L), anyString())).thenReturn(List.of(document("CONFIRMED")));

        assertEquals("CONFIRMED", service.confirm(11L, 7L, 5L, "doc-1", "verified-file-hash").get("status"));

        verify(parser).hash(Path.of("course.pdf"));
        verify(jdbc).update(contains("set status='CONFIRMED'"), eq("doc-1"));
        verify(jdbc, never()).update(contains("update lesson_assets"), any(Object[].class));
    }

    @Test
    void missingFileMakesPreviouslyConfirmedTextStale() throws Exception {
        when(jdbc.queryForList(contains("order by (asset_version=?)"), eq(5L), anyString())).thenReturn(List.of(document("CONFIRMED")));
        when(parser.resolve("/uploads/course.pdf")).thenThrow(new java.nio.file.NoSuchFileException("course.pdf"));

        Map<String, Object> result = service.status(11L, 7L, 5L);

        assertEquals("STALE", result.get("status"));
        assertTrue(((List<?>) result.get("segments")).isEmpty());
    }

    private Map<String, Object> document(String status) {
        return Map.of("id", "doc-1", "asset_version", CoursewareAssetVersion.of(asset),
            "version_hash", "verified-file-hash", "status", status, "segment_count", 1, "character_count", 100);
    }
}
