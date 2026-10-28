package com.learning.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Periodically mirrors catalogue metadata and lesson content, with durable retry status. */
@Service
@DependsOn("schemaInitializer")
public class CatalogGraphSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogGraphSyncService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final KnowledgeTaxonomyService taxonomy;
    private final Neo4jLearningContextService graph;
    private final CourseContentValidationService validation;
    private final boolean enabled;

    public CatalogGraphSyncService(JdbcTemplate jdbc, ObjectMapper mapper, KnowledgeTaxonomyService taxonomy,
            Neo4jLearningContextService graph, CourseContentValidationService validation,
            @Value("${app.neo4j.enabled:false}") boolean enabled) {
        this.jdbc=jdbc; this.mapper=mapper; this.taxonomy=taxonomy; this.graph=graph; this.validation=validation; this.enabled=enabled;
    }

    @PostConstruct public void initialize() {
        jdbc.execute("""
                create table if not exists catalog_graph_sync_state (
                  state_key varchar(40) primary key, payload_hash char(64) null,
                  status enum('pending','synced','failed') not null default 'pending',
                  course_count int not null default 0, lesson_count int not null default 0,
                  last_error varchar(300) null, synced_at timestamp null,
                  updated_at timestamp not null default current_timestamp on update current_timestamp
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbc.update("insert ignore into catalog_graph_sync_state(state_key) values('catalog')");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { sync(true); }

    @Scheduled(fixedDelayString="${app.neo4j.course-sync-delay-ms:30000}", initialDelay=30000)
    public void scheduledSync() { sync(false); }

    public Map<String,Object> status() {
        Map<String,Object> result=new LinkedHashMap<>(jdbc.queryForMap("select status,course_count courseCount,lesson_count lessonCount,last_error lastError,synced_at syncedAt from catalog_graph_sync_state where state_key='catalog'"));
        result.put("enabled", enabled); return result;
    }

    public synchronized Map<String,Object> sync(boolean force) {
        if (!enabled) return status();
        try {
            Map<String,Object> payload=snapshot();
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)));
            Map<String,Object> previous=jdbc.queryForMap("select status,payload_hash from catalog_graph_sync_state where state_key='catalog'");
            if (!force && "synced".equals(previous.get("status")) && hash.equals(previous.get("payload_hash"))) return status();
            int courses=((List<?>)payload.get("courses")).size(), lessons=((List<?>)payload.get("lessons")).size();
            jdbc.update("update catalog_graph_sync_state set status='pending',last_error=null where state_key='catalog'");
            if (graph.syncCourseCatalog(payload)) {
                jdbc.update("update catalog_graph_sync_state set status='synced',payload_hash=?,course_count=?,lesson_count=?,last_error=null,synced_at=current_timestamp where state_key='catalog'",hash,courses,lessons);
                LOG.info("Course catalogue synced to Neo4j: {} courses, {} lessons",courses,lessons);
            } else jdbc.update("update catalog_graph_sync_state set status='failed',last_error='Neo4j 暂不可用，将自动重试' where state_key='catalog'");
        } catch (Exception error) {
            jdbc.update("update catalog_graph_sync_state set status='failed',last_error='课程图谱同步失败，将自动重试' where state_key='catalog'");
            LOG.warn("Catalogue snapshot sync failed: {}",error.getClass().getSimpleName());
        }
        return status();
    }

    private Map<String,Object> snapshot() {
        List<Map<String,Object>> nodes=taxonomy.flattened();
        Map<String,String> keys=new HashMap<>();
        nodes.forEach(node -> keys.put(String.valueOf(node.get("path")),String.valueOf(node.get("key"))));
        List<Map<String,Object>> courses=jdbc.queryForList("""
                select c.id courseId,r.id publicCourseId,r.title,r.subject,coalesce(r.description,'') description,
                  r.status,r.review_status reviewStatus,coalesce(cat.name,'') categoryName,coalesce(d.name,r.subject) directionName,s.stage_code learningStage
                from resources r join courses c on c.legacy_resource_id=r.id
                left join categories cat on cat.id=r.category_id left join directions d on d.id=r.direction_id
                left join course_learning_stages s on s.resource_id=r.id order by r.id
                """);
        List<Map<String,Object>> chapters=new ArrayList<>(),lessons=new ArrayList<>(),topics=new ArrayList<>();
        for (Map<String,Object> course:courses) {
            if (course.get("learningStage")==null) course.put("learningStage",LearningStages.infer(course.get("title"),""));
            Object courseId=course.get("courseId");
            String directionPath=course.get("categoryName")+" > "+course.get("directionName");
            course.put("directionKey",keys.getOrDefault(directionPath,""));
            boolean published=((Number)course.get("status")).intValue()==1 && "approved".equals(course.get("reviewStatus"))
                    && validation.validatePublic(((Number)course.get("publicCourseId")).longValue()).isContentComplete();
            course.put("published",published);
            chapters.addAll(jdbc.queryForList("select id,course_id courseId,title,sort_order sortOrder,status from course_chapters where course_id=? and status<>'disabled' order by sort_order,id",courseId));
            List<Map<String,Object>> courseLessons=jdbc.queryForList("""
                    select l.id,ch.course_id courseId,l.chapter_id chapterId,l.title,l.lesson_type lessonType,
                      l.duration_seconds durationSeconds,l.status,
                      coalesce((select a.content_text from lesson_assets a where a.lesson_id=l.id and a.status=1
                        and a.asset_type=l.lesson_type and a.transcoding_status='ready' order by a.id limit 1),'') content
                    from course_lessons l join course_chapters ch on ch.id=l.chapter_id
                    where ch.course_id=? and ch.status<>'disabled' and l.status<>'disabled'
                    order by ch.sort_order,ch.id,l.sort_order,l.id
                    """,courseId);
            lessons.addAll(courseLessons);
            for (Map<String,Object> lesson:courseLessons) {
                String key=keys.get(directionPath+" > "+lesson.get("title"));
                if (key!=null) topics.add(Map.of("courseId",courseId,"lessonId",lesson.get("id"),"key",key));
            }
        }
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("taxonomy",nodes); payload.put("courses",courses); payload.put("chapters",chapters);
        payload.put("lessons",lessons); payload.put("topics",topics); return payload;
    }
}
