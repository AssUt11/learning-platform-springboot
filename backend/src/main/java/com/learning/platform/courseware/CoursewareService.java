package com.learning.platform.courseware;

import com.learning.platform.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@DependsOn("coursewareSchemaInitializer")
public class CoursewareService {
    private static final Logger LOG = LoggerFactory.getLogger(CoursewareService.class);
    private final JdbcTemplate jdbc;
    private final CoursewareFileParser parser;
    private final TransactionTemplate transactions;
    private final AtomicBoolean working = new AtomicBoolean();

    public CoursewareService(JdbcTemplate jdbc, CoursewareFileParser parser, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.transactions = new TransactionTemplate(manager);
    }

    @Transactional
    public Map<String, Object> request(long teacherId, long lessonId, long assetId) {
        Map<String, Object> asset = ownedAsset(teacherId, lessonId, assetId);
        if (!parser.supports(text(asset.get("fileName")))) throw unsupported();
        try { parser.resolve(text(asset.get("fileUrl"))); }
        catch (IOException exception) { throw new BusinessException(404, "课件文件已不存在，请重新上传。"); }
        String version = CoursewareAssetVersion.of(asset);
        List<Map<String, Object>> existing = jdbc.queryForList(
            "select * from courseware_documents where asset_id=? and asset_version=?", assetId, version);
        if (existing.isEmpty()) {
            String id = UUID.randomUUID().toString();
            try {
                jdbc.update("""
                    insert into courseware_documents(id,asset_id,public_course_id,lesson_id,teacher_id,
                      file_name,file_url,asset_version,status) values(?,?,?,?,?,?,?,?,'QUEUED')
                    """, id, assetId, asset.get("publicCourseId"), lessonId, teacherId,
                    text(asset.get("fileName")), text(asset.get("fileUrl")), version);
            } catch (DuplicateKeyException duplicate) {
                // Concurrent double-clicks share the unique asset version rather than create work twice.
            }
        } else if (List.of("FAILED", "SUPERSEDED").contains(text(existing.get(0).get("status")))) {
            jdbc.update("""
                update courseware_documents set status='QUEUED',error_message=null,attempt_count=0,
                  lease_until=null,worker_token=null where id=? and status in ('FAILED','SUPERSEDED')
                """, existing.get(0).get("id"));
        }
        return status(teacherId, lessonId, assetId);
    }

    public Map<String, Object> status(long teacherId, long lessonId, long assetId) {
        Map<String, Object> asset = ownedAsset(teacherId, lessonId, assetId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fileName", text(asset.get("fileName")));
        response.put("supported", parser.supports(text(asset.get("fileName"))));
        response.put("status", "NOT_STARTED");
        response.put("segments", List.of());
        response.put("segmentCount", 0);
        response.put("characterCount", 0);
        List<Map<String, Object>> documents = jdbc.queryForList(
            "select * from courseware_documents where asset_id=? order by (asset_version=?) desc,created_at desc,id desc limit 1",
            assetId, CoursewareAssetVersion.of(asset));
        if (documents.isEmpty()) return response;
        Map<String, Object> doc = documents.get(0);
        boolean current = CoursewareAssetVersion.of(asset).equals(doc.get("asset_version"));
        try { parser.resolve(text(asset.get("fileUrl"))); }
        catch (IOException | BusinessException exception) { current = false; }
        response.put("documentId", doc.get("id"));
        response.put("versionHash", doc.get("version_hash"));
        response.put("status", current && !"SUPERSEDED".equals(doc.get("status")) ? doc.get("status") : "STALE");
        response.put("segmentCount", doc.get("segment_count"));
        response.put("characterCount", doc.get("character_count"));
        response.put("error", current ? doc.get("error_message") : "课件已变更或失效，请重新解析并确认。");
        if (current && List.of("READY", "CONFIRMED").contains(text(doc.get("status")))) {
            response.put("segments", jdbc.queryForList("""
                select id,location_type locationType,location_index locationIndex,text_content text
                from courseware_chunks where document_id=? order by chunk_index
                """, doc.get("id")));
        }
        return response;
    }

    @Transactional
    public Map<String, Object> confirm(long teacherId, long lessonId, long assetId, String documentId, String versionHash) {
        Map<String, Object> asset = ownedAsset(teacherId, lessonId, assetId);
        List<Map<String, Object>> documents = jdbc.queryForList(
            "select * from courseware_documents where id=? and asset_id=? and teacher_id=? for update",
            documentId, assetId, teacherId);
        if (documents.size() != 1) throw new BusinessException(404, "解析结果不存在或无权确认。");
        Map<String, Object> doc = documents.get(0);
        if (!List.of("READY", "CONFIRMED").contains(text(doc.get("status")))) {
            throw new BusinessException(409, "课件尚未解析完成，不能确认。");
        }
        if (versionHash == null || !versionHash.equals(doc.get("version_hash")) ||
            !CoursewareAssetVersion.of(asset).equals(doc.get("asset_version"))) {
            throw new BusinessException(409, "课件版本已变化，请重新解析并确认。");
        }
        try {
            Path file = parser.resolve(text(asset.get("fileUrl")));
            if (!versionHash.equals(parser.hash(file))) throw new BusinessException(409, "课件文件已变化，请重新解析。");
        } catch (IOException exception) {
            throw new BusinessException(404, "课件文件已不存在，请重新上传。");
        }
        jdbc.update("update courseware_documents set status='SUPERSEDED' where asset_id=? and id<>? and status='CONFIRMED'", assetId, documentId);
        jdbc.update("update courseware_documents set status='CONFIRMED',confirmed_at=current_timestamp where id=?", documentId);
        return status(teacherId, lessonId, assetId);
    }

    /** A durable queue survives page changes and recovers expired leases after restarts. */
    @Scheduled(fixedDelay = 1500, initialDelay = 2500)
    public void processNext() {
        if (!working.compareAndSet(false, true)) return;
        String documentId = null;
        String token = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                update courseware_documents set status=case when attempt_count>=3 then 'FAILED' else 'QUEUED' end,
                  error_message=case when attempt_count>=3 then '解析任务多次中断，请重新解析。' else null end,
                  worker_token=null,lease_until=null where status='PROCESSING' and lease_until<current_timestamp
                """);
            List<Map<String, Object>> pending = jdbc.queryForList(
                "select * from courseware_documents where status='QUEUED' order by created_at,id limit 1");
            if (pending.isEmpty()) return;
            Map<String, Object> doc = pending.get(0);
            documentId = text(doc.get("id"));
            int claimed = jdbc.update("""
                update courseware_documents set status='PROCESSING',attempt_count=attempt_count+1,
                  worker_token=?,lease_until=timestampadd(minute,5,current_timestamp),error_message=null
                where id=? and status='QUEUED'
                """, token, documentId);
            if (claimed != 1) return;
            Map<String, Object> asset = ownedAsset(number(doc.get("teacher_id")), number(doc.get("lesson_id")), number(doc.get("asset_id")));
            if (!CoursewareAssetVersion.of(asset).equals(doc.get("asset_version"))) {
                throw new BusinessException(409, "课件已修改，请重新提交解析。");
            }
            CoursewareFileParser.ParsedDocument parsed = parser.parse(text(asset.get("fileUrl")), text(asset.get("fileName")));
            Map<String, Object> stillCurrent = ownedAsset(number(doc.get("teacher_id")), number(doc.get("lesson_id")), number(doc.get("asset_id")));
            if (!CoursewareAssetVersion.of(stillCurrent).equals(doc.get("asset_version"))) {
                throw new BusinessException(409, "解析期间课件已修改，请重新解析。");
            }
            finish(documentId, token, parsed);
        } catch (Exception exception) {
            if (documentId != null) {
                String message = exception instanceof BusinessException ? exception.getMessage()
                    : "无法解析课件，请检查文件完整性后重试。";
                jdbc.update("""
                    update courseware_documents set status='FAILED',error_message=?,lease_until=null,worker_token=null
                    where id=? and status='PROCESSING' and worker_token=?
                    """, message, documentId, token);
                LOG.warn("Courseware extraction {} failed ({})", documentId, exception.getClass().getSimpleName());
            } else LOG.warn("Courseware queue could not be checked ({})", exception.getClass().getSimpleName());
        } finally {
            working.set(false);
        }
    }

    private void finish(String documentId, String token, CoursewareFileParser.ParsedDocument parsed) {
        transactions.executeWithoutResult(transaction -> {
            List<Map<String, Object>> claim = jdbc.queryForList(
                "select worker_token,status from courseware_documents where id=? for update", documentId);
            if (claim.size() != 1 || !token.equals(claim.get(0).get("worker_token")) ||
                !"PROCESSING".equals(claim.get(0).get("status"))) return;
            jdbc.update("delete from courseware_chunks where document_id=?", documentId);
            List<Object[]> batches = new ArrayList<>();
            int index = 0;
            int characters = 0;
            for (var segment : parsed.segments()) {
                batches.add(new Object[] { documentId, index++, segment.locationType(), segment.locationIndex(), segment.text() });
                characters += segment.text().length();
            }
            jdbc.batchUpdate("""
                insert into courseware_chunks(document_id,chunk_index,location_type,location_index,text_content)
                values(?,?,?,?,?)
                """, batches);
            jdbc.update("""
                update courseware_documents set status='READY',version_hash=?,segment_count=?,character_count=?,
                  lease_until=null,worker_token=null,error_message=null where id=? and worker_token=?
                """, parsed.versionHash(), batches.size(), characters, documentId, token);
        });
    }

    private Map<String, Object> ownedAsset(long teacherId, long lessonId, long assetId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select a.id assetId,a.file_url fileUrl,a.file_name fileName,a.file_size fileSize,
              a.asset_type assetType,a.updated_at assetUpdatedAt,coalesce(a.content_text,'') contentText,r.id publicCourseId
            from lesson_assets a join course_lessons l on l.id=a.lesson_id
            join course_chapters ch on ch.id=l.chapter_id join courses c on c.id=ch.course_id
            join resources r on r.id=c.legacy_resource_id
            where a.id=? and l.id=? and r.teacher_id=? and a.status=1 and a.asset_type='document'
              and l.status<>'disabled' and l.lesson_type='document' and ch.status<>'disabled' and r.lifecycle_status='active'
            """, assetId, lessonId, teacherId);
        if (rows.size() != 1) throw new BusinessException(404, "文档课时资源不存在或无权访问。");
        return rows.get(0);
    }

    private long number(Object value) { return ((Number) value).longValue(); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private BusinessException unsupported() { return new BusinessException(400, "仅支持 PDF、PPTX、DOCX 和 UTF-8 TXT；旧版 DOC/PPT 请先转换。"); }
}
