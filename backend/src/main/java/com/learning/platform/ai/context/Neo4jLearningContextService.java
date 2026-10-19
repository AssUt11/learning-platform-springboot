package com.learning.platform.ai.context;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class Neo4jLearningContextService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jLearningContextService.class);
    private final boolean enabled;
    private final String uri;
    private final String username;
    private final String password;
    private final long connectionTimeoutMs;
    private final long acquisitionTimeoutMs;
    private final long retryTimeoutMs;
    private final long failureCooldownMs;
    private volatile Driver driver;
    private volatile long retryAfterEpochMs;
    private volatile boolean schemaReady;

    public Neo4jLearningContextService(
            @Value("${app.neo4j.enabled:false}") boolean enabled,
            @Value("${app.neo4j.uri:neo4j://127.0.0.1:7687}") String uri,
            @Value("${app.neo4j.username:neo4j}") String username,
            @Value("${app.neo4j.password:}") String password,
            @Value("${app.neo4j.connection-timeout-ms:2000}") long connectionTimeoutMs,
            @Value("${app.neo4j.acquisition-timeout-ms:2000}") long acquisitionTimeoutMs,
            @Value("${app.neo4j.retry-timeout-ms:800}") long retryTimeoutMs,
            @Value("${app.neo4j.failure-cooldown-ms:10000}") long failureCooldownMs) {
        this.enabled = enabled;
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.connectionTimeoutMs = positive(connectionTimeoutMs, 2000L);
        this.acquisitionTimeoutMs = positive(acquisitionTimeoutMs, 2000L);
        this.retryTimeoutMs = positive(retryTimeoutMs, 800L);
        this.failureCooldownMs = positive(failureCooldownMs, 10000L);
    }

    public GraphContext load(long internalCourseId, long publicCourseId, long lessonId,
                             String courseTitle, String lessonTitle, String subject) {
        if (!enabled) return GraphContext.unavailable("disabled");
        if (System.currentTimeMillis() < retryAfterEpochMs) return GraphContext.unavailable("cooldown");
        try {
            Driver activeDriver = driver();
            try (Session session = activeDriver.session()) {
                ensureSchema(session);
                session.executeWrite(transaction -> {
                    transaction.run(new Query(""
                                    + "MERGE (c:Course {mysqlId: $courseId}) "
                                    + "SET c.publicCourseId=$publicCourseId, c.title=$courseTitle, c.subject=$subject, "
                                    + "    c.source='learning-platform', c.syncedAt=datetime() "
                                    + "MERGE (l:Lesson {mysqlId: $lessonId}) "
                                    + "SET l.title=$lessonTitle, l.source='learning-platform', l.syncedAt=datetime() "
                                    + "MERGE (c)-[:HAS_LESSON]->(l)",
                            parameters(internalCourseId, publicCourseId, lessonId, courseTitle, lessonTitle, subject))).consume();
                    return null;
                });
                List<Map<String, Object>> nodes = session.executeRead(transaction -> transaction.run(new Query(""
                                + "MATCH (k:KnowledgeNode) "
                                + "WITH k, toLower(coalesce(k.title,k.name,'')) AS title "
                                + "WHERE title <> '' AND ("
                                + "  title CONTAINS toLower($subject) OR toLower($subject) CONTAINS title OR "
                                + "  toLower($lessonTitle) CONTAINS title OR title CONTAINS toLower($lessonTitle)) "
                                + "RETURN coalesce(k.title,k.name,'') AS title, "
                                + "       coalesce(k.description,'') AS description, coalesce(k.path,'') AS path "
                                + "ORDER BY coalesce(k.level,999), title LIMIT 8",
                        parameters(internalCourseId, publicCourseId, lessonId, courseTitle, lessonTitle, subject)))
                        .list(record -> {
                            Map<String, Object> node = new LinkedHashMap<String, Object>();
                            node.put("title", record.get("title").asString(""));
                            node.put("description", record.get("description").asString(""));
                            node.put("path", record.get("path").asString(""));
                            return node;
                        }));
                retryAfterEpochMs = 0L;
                return new GraphContext(true, nodes, null);
            }
        } catch (RuntimeException exception) {
            markUnavailable();
            LOGGER.warn("Neo4j AI context is temporarily unavailable; continuing with MySQL context: {}",
                    exception.getClass().getSimpleName());
            return GraphContext.unavailable("unavailable");
        }
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("enabled", enabled);
        if (!enabled) {
            result.put("available", false);
            result.put("reason", "NEO4J_DISABLED");
            return result;
        }
        if (System.currentTimeMillis() < retryAfterEpochMs) {
            result.put("available", false);
            result.put("reason", "NEO4J_COOLDOWN");
            return result;
        }
        try {
            driver().verifyConnectivity();
            result.put("available", true);
        } catch (RuntimeException exception) {
            markUnavailable();
            result.put("available", false);
            result.put("reason", "NEO4J_UNAVAILABLE");
        }
        return result;
    }

    /** One graph transaction; MySQL remains authoritative when the graph is unavailable. */
    public boolean syncCourseCatalog(Map<String, Object> payload) {
        if (!enabled || System.currentTimeMillis() < retryAfterEpochMs) return false;
        try (Session session = driver().session()) {
            ensureSchema(session);
            session.run("CREATE CONSTRAINT catalog_chapter_mysql_id IF NOT EXISTS FOR (c:Chapter) REQUIRE c.mysqlId IS UNIQUE").consume();
            session.executeWrite(tx -> {
                tx.run(new Query("""
                        UNWIND $taxonomy AS row MERGE (n:KnowledgeNode {key:row.key})
                        SET n.title=row.title,n.name=row.title,n.code=row.code,n.level=row.level,
                            n.description=row.description,n.path=row.path,n.source='learning-platform'
                        """, payload)).consume();
                tx.run(new Query("""
                        UNWIND $taxonomy AS row WITH row WHERE row.parentKey <> ''
                        MATCH (p:KnowledgeNode {key:row.parentKey}),(n:KnowledgeNode {key:row.key})
                        MERGE (p)-[:CONTAINS]->(n)
                        """, payload)).consume();
                // Retain node identities and user annotations; rebuild only relationships owned by this sync.
                tx.run("MATCH (c:Course {source:'learning-platform'}) SET c.published=false").consume();
                tx.run("MATCH ()-[r]->() WHERE r.managedBy='catalog-sync' DELETE r").consume();
                tx.run(new Query("""
                        UNWIND $courses AS row MERGE (c:Course {mysqlId:row.courseId})
                        SET c.publicCourseId=row.publicCourseId,c.title=row.title,c.subject=row.subject,
                            c.description=row.description,c.published=row.published,c.source='learning-platform',c.syncedAt=datetime()
                        WITH c,row MATCH (d:KnowledgeNode {key:row.directionKey})
                        MERGE (d)-[r:HAS_COURSE]->(c) SET r.managedBy='catalog-sync'
                        """, payload)).consume();
                tx.run(new Query("""
                        UNWIND $chapters AS row MATCH (c:Course {mysqlId:row.courseId})
                        MERGE (ch:Chapter {mysqlId:row.id})
                        SET ch.title=row.title,ch.sortOrder=row.sortOrder,ch.status=row.status,ch.source='learning-platform'
                        MERGE (c)-[r:HAS_CHAPTER]->(ch) SET r.managedBy='catalog-sync'
                        """, payload)).consume();
                tx.run(new Query("""
                        UNWIND $lessons AS row MATCH (c:Course {mysqlId:row.courseId}),(ch:Chapter {mysqlId:row.chapterId})
                        MERGE (l:Lesson {mysqlId:row.id})
                        SET l.title=row.title,l.lessonType=row.lessonType,l.durationSeconds=row.durationSeconds,
                            l.status=row.status,l.content=row.content,l.source='learning-platform'
                        MERGE (ch)-[a:HAS_LESSON]->(l) SET a.managedBy='catalog-sync'
                        MERGE (c)-[b:HAS_LESSON]->(l) SET b.managedBy='catalog-sync'
                        """, payload)).consume();
                tx.run(new Query("""
                        UNWIND $topics AS row MATCH (c:Course {mysqlId:row.courseId}),(l:Lesson {mysqlId:row.lessonId}),
                            (k:KnowledgeNode {key:row.key})
                        MERGE (c)-[a:COVERS]->(k) SET a.managedBy='catalog-sync'
                        MERGE (l)-[b:TEACHES]->(k) SET b.managedBy='catalog-sync'
                        """, payload)).consume();
                return null;
            });
            retryAfterEpochMs = 0L;
            return true;
        } catch (RuntimeException error) {
            markUnavailable();
            LOGGER.warn("Course catalog graph sync failed; it will be retried: {}", error.getClass().getSimpleName());
            return false;
        }
    }

    public PrerequisiteContext findPrerequisites(String requestedTopic) {
        String topic = safe(requestedTopic);
        if (!enabled) return PrerequisiteContext.unavailable(topic, "NEO4J_DISABLED");
        if (System.currentTimeMillis() < retryAfterEpochMs) {
            return PrerequisiteContext.unavailable(topic, "NEO4J_COOLDOWN");
        }
        try {
            try (Session session = driver().session()) {
                ensureSchema(session);
                List<Map<String, Object>> targets = session.executeRead(transaction -> transaction.run(new Query(""
                                + "MATCH (target:KnowledgeNode) "
                                + "WHERE toLower(coalesce(target.title,target.name,'')) = toLower($topic) "
                                + "   OR toLower(coalesce(target.title,target.name,'')) CONTAINS toLower($topic) "
                                + "RETURN target.key AS nodeKey, coalesce(target.title,target.name,'') AS title "
                                + "ORDER BY CASE WHEN toLower(coalesce(target.title,target.name,'')) = toLower($topic) THEN 0 ELSE 1 END "
                                + "LIMIT 1", Values.parameters("topic", topic)))
                        .list(record -> Map.of("nodeKey", record.get("nodeKey").asString(""),
                                "title", record.get("title").asString(""))));
                if (targets.isEmpty() || String.valueOf(targets.get(0).get("nodeKey")).isEmpty()) {
                    retryAfterEpochMs = 0L;
                    return new PrerequisiteContext(true, false, topic, Collections.emptyList(), "TOPIC_NOT_FOUND");
                }
                String nodeKey = String.valueOf(targets.get(0).get("nodeKey"));
                String resolvedTitle = String.valueOf(targets.get(0).get("title"));
                List<Map<String, Object>> prerequisites = session.executeRead(transaction -> transaction.run(new Query(""
                                + "MATCH (scope:KnowledgeNode {key:$nodeKey}) "
                                + "MATCH (scope)-[:CONTAINS*0..2]->(goal:KnowledgeNode) "
                                + "CALL { "
                                + "  WITH goal "
                                + "  MATCH p=(pre:KnowledgeNode)-[:PREREQUISITE_OF*1..4]->(goal) "
                                + "  RETURN pre, length(p) AS distance "
                                + "  UNION "
                                + "  WITH goal "
                                + "  MATCH p=(goal)-[:REQUIRES*1..4]->(pre:KnowledgeNode) "
                                + "  RETURN pre, length(p) AS distance "
                                + "} "
                                + "WITH pre, goal, min(distance) AS distance "
                                + "RETURN coalesce(pre.title,pre.name,'') AS title, "
                                + "       coalesce(goal.title,goal.name,'') AS target, distance "
                                + "ORDER BY target, distance DESC, title LIMIT 20", Values.parameters("nodeKey", nodeKey)))
                        .list(record -> Map.of("title", record.get("title").asString(""),
                                "target", record.get("target").asString(""),
                                "distance", record.get("distance").asInt(0))));
                retryAfterEpochMs = 0L;
                return new PrerequisiteContext(true, true, resolvedTitle, prerequisites,
                        prerequisites.isEmpty() ? "NO_PREREQUISITE_RELATIONSHIPS" : null);
            }
        } catch (RuntimeException exception) {
            markUnavailable();
            LOGGER.warn("Neo4j prerequisite lookup is unavailable: {}", exception.getClass().getSimpleName());
            return PrerequisiteContext.unavailable(topic, "NEO4J_UNAVAILABLE");
        }
    }

    public PrerequisiteSyncResult syncPrerequisites(List<Map<String, Object>> relations) {
        if (!enabled) return PrerequisiteSyncResult.unavailable("NEO4J_DISABLED");
        if (System.currentTimeMillis() < retryAfterEpochMs) {
            return PrerequisiteSyncResult.unavailable("NEO4J_COOLDOWN");
        }
        List<Map<String, Object>> safeRelations = new ArrayList<Map<String, Object>>();
        if (relations != null) {
            for (Map<String, Object> relation : relations) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", relation.get("id"));
                row.put("subject", relation.get("subject"));
                row.put("prerequisiteKey", relation.get("prerequisiteKey"));
                row.put("prerequisiteTitle", relation.get("prerequisiteTitle"));
                row.put("targetKey", relation.get("targetKey"));
                row.put("targetTitle", relation.get("targetTitle"));
                row.put("sourceReference", relation.get("sourceReference"));
                row.put("prerequisiteParentKey", parentKey(String.valueOf(relation.get("prerequisiteKey"))));
                row.put("targetParentKey", parentKey(String.valueOf(relation.get("targetKey"))));
                safeRelations.add(row);
            }
        }
        try {
            try (Session session = driver().session()) {
                ensureSchema(session);
                session.executeWrite(transaction -> {
                    transaction.run("MATCH ()-[r:PREREQUISITE_OF {source:'learning-platform'}]->() DELETE r").consume();
                    transaction.run(new Query(""
                                    + "UNWIND $relations AS row "
                                    + "MERGE (pre:KnowledgeNode {key:row.prerequisiteKey}) "
                                    + "SET pre.title=row.prerequisiteTitle, pre.name=row.prerequisiteTitle, "
                                    + "    pre.subject=row.subject, pre.source='learning-platform' "
                                    + "MERGE (preParent:KnowledgeNode {key:row.prerequisiteParentKey}) "
                                    + "ON CREATE SET preParent.title=row.subject, preParent.name=row.subject, "
                                    + "              preParent.subject=row.subject, preParent.source='learning-platform' "
                                    + "MERGE (preParent)-[:CONTAINS]->(pre) "
                                    + "MERGE (target:KnowledgeNode {key:row.targetKey}) "
                                    + "SET target.title=row.targetTitle, target.name=row.targetTitle, "
                                    + "    target.subject=row.subject, target.source='learning-platform' "
                                    + "MERGE (targetParent:KnowledgeNode {key:row.targetParentKey}) "
                                    + "ON CREATE SET targetParent.title=row.subject, targetParent.name=row.subject, "
                                    + "              targetParent.subject=row.subject, targetParent.source='learning-platform' "
                                    + "MERGE (targetParent)-[:CONTAINS]->(target) "
                                    + "MERGE (pre)-[r:PREREQUISITE_OF {source:'learning-platform'}]->(target) "
                                    + "SET r.sourceId=row.id, r.sourceReference=row.sourceReference, r.syncedAt=datetime()",
                            Values.parameters("relations", safeRelations))).consume();
                    return null;
                });
                retryAfterEpochMs = 0L;
                return new PrerequisiteSyncResult(true, safeRelations.size(), null);
            }
        } catch (RuntimeException exception) {
            markUnavailable();
            LOGGER.warn("Neo4j prerequisite synchronization failed: {}", exception.getClass().getSimpleName());
            return PrerequisiteSyncResult.unavailable("NEO4J_UNAVAILABLE");
        }
    }

    private Map<String, Object> parameters(long internalCourseId, long publicCourseId, long lessonId,
                                           String courseTitle, String lessonTitle, String subject) {
        return Values.parameters(
                "courseId", internalCourseId,
                "publicCourseId", publicCourseId,
                "lessonId", lessonId,
                "courseTitle", safe(courseTitle),
                "lessonTitle", safe(lessonTitle),
                "subject", safe(subject)).asMap();
    }

    /** Personal plan steps deliberately do not modify the shared KnowledgeNode graph. */
    public boolean saveStudentPath(long userId, String pathId, String title, String snapshot,
                                   List<Map<String, Object>> nodes) {
        if (!enabled || System.currentTimeMillis() < retryAfterEpochMs) return false;
        try (Session session = driver().session()) {
            session.run("CREATE CONSTRAINT student_path_id IF NOT EXISTS FOR (p:StudentLearningPath) REQUIRE p.pathId IS UNIQUE").consume();
            session.executeWrite(tx -> {
                tx.run("MERGE (p:StudentLearningPath {pathId:$pathId}) "
                        + "SET p.userId=$userId,p.title=$title,p.snapshotJson=$snapshot,p.updatedAt=datetime() "
                        + "WITH p UNWIND $nodes AS row "
                        + "MERGE (s:StudentLearningPathStep {pathId:$pathId,stepId:row.id}) "
                        + "SET s.userId=$userId,s.title=row.title,s.stepOrder=row.order,s.goal=row.goal,"
                        + "s.completionCriteria=row.completionCriteria,s.courseId=row.courseId "
                        + "MERGE (p)-[:HAS_STEP]->(s)",
                        Values.parameters("pathId", pathId, "userId", userId, "title", title, "snapshot", snapshot, "nodes", nodes)).consume();
                tx.run("MATCH (p:StudentLearningPath {pathId:$pathId,userId:$userId})-[:HAS_STEP]->(a) "
                        + "MATCH (p)-[:HAS_STEP]->(b) WHERE b.stepOrder=a.stepOrder+1 "
                        + "MERGE (a)-[:NEXT_STEP]->(b)", Values.parameters("pathId", pathId, "userId", userId)).consume();
                return null;
            });
            return true;
        } catch (RuntimeException error) {
            markUnavailable();
            LOGGER.warn("Student learning path saved locally; Neo4j sync unavailable: {}", error.getClass().getSimpleName());
            return false;
        }
    }

    public String readStudentPath(long userId, String pathId) {
        if (!enabled || System.currentTimeMillis() < retryAfterEpochMs) return null;
        try (Session session = driver().session()) {
            List<String> snapshots = session.executeRead(tx -> tx.run(
                    "MATCH (p:StudentLearningPath {pathId:$pathId,userId:$userId}) RETURN p.snapshotJson AS snapshot",
                    Values.parameters("pathId", pathId, "userId", userId)).list(row -> row.get("snapshot").asString()));
            return snapshots.isEmpty() ? null : snapshots.get(0);
        } catch (RuntimeException error) {
            markUnavailable();
            return null;
        }
    }

    private synchronized Driver driver() {
        if (driver == null) {
            Config config = Config.builder()
                    .withConnectionTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
                    .withConnectionAcquisitionTimeout(acquisitionTimeoutMs, TimeUnit.MILLISECONDS)
                    .withMaxTransactionRetryTime(retryTimeoutMs, TimeUnit.MILLISECONDS)
                    .build();
            Driver candidate = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
            try {
                candidate.verifyConnectivity();
                driver = candidate;
            } catch (RuntimeException exception) {
                candidate.close();
                throw exception;
            }
        }
        return driver;
    }

    private synchronized void markUnavailable() {
        retryAfterEpochMs = System.currentTimeMillis() + failureCooldownMs;
        schemaReady = false;
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (driver != null) driver.close();
        driver = null;
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String parentKey(String value) {
        int separator = value == null ? -1 : value.lastIndexOf('/');
        return separator > 0 ? value.substring(0, separator) : value;
    }
    private long positive(long value, long fallback) { return value > 0L ? value : fallback; }

    private void ensureSchema(Session session) {
        if (schemaReady) return;
        synchronized (this) {
            if (schemaReady) return;
            session.run("CREATE CONSTRAINT ai_course_mysql_id IF NOT EXISTS FOR (c:Course) REQUIRE c.mysqlId IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT ai_lesson_mysql_id IF NOT EXISTS FOR (l:Lesson) REQUIRE l.mysqlId IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT ai_knowledge_node_key IF NOT EXISTS FOR (k:KnowledgeNode) REQUIRE k.key IS UNIQUE").consume();
            schemaReady = true;
        }
    }

    public static class GraphContext {
        private final boolean available;
        private final List<Map<String, Object>> nodes;
        private final String reason;

        public GraphContext(boolean available, List<Map<String, Object>> nodes, String reason) {
            this.available = available;
            this.nodes = nodes == null ? new ArrayList<Map<String, Object>>() : nodes;
            this.reason = reason;
        }

        public static GraphContext unavailable(String reason) {
            return new GraphContext(false, new ArrayList<Map<String, Object>>(), reason);
        }

        public boolean isAvailable() { return available; }
        public List<Map<String, Object>> getNodes() { return nodes; }
        public String getReason() { return reason; }
    }

    public static class PrerequisiteContext {
        private final boolean available;
        private final boolean topicFound;
        private final String topic;
        private final List<Map<String, Object>> prerequisites;
        private final String reason;

        public PrerequisiteContext(boolean available, boolean topicFound, String topic,
                                   List<Map<String, Object>> prerequisites, String reason) {
            this.available = available;
            this.topicFound = topicFound;
            this.topic = topic;
            this.prerequisites = prerequisites == null ? Collections.emptyList() : prerequisites;
            this.reason = reason;
        }

        public static PrerequisiteContext unavailable(String topic, String reason) {
            return new PrerequisiteContext(false, false, topic, Collections.emptyList(), reason);
        }

        public boolean isAvailable() { return available; }
        public boolean isTopicFound() { return topicFound; }
        public String getTopic() { return topic; }
        public List<Map<String, Object>> getPrerequisites() { return prerequisites; }
        public String getReason() { return reason; }
    }

    public static class PrerequisiteSyncResult {
        private final boolean available;
        private final int syncedCount;
        private final String reason;

        public PrerequisiteSyncResult(boolean available, int syncedCount, String reason) {
            this.available = available;
            this.syncedCount = syncedCount;
            this.reason = reason;
        }

        public static PrerequisiteSyncResult unavailable(String reason) {
            return new PrerequisiteSyncResult(false, 0, reason);
        }

        public boolean isAvailable() { return available; }
        public int getSyncedCount() { return syncedCount; }
        public String getReason() { return reason; }
    }
}
