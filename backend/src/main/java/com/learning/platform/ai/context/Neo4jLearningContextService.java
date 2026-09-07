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
    private long positive(long value, long fallback) { return value > 0L ? value : fallback; }

    private void ensureSchema(Session session) {
        if (schemaReady) return;
        synchronized (this) {
            if (schemaReady) return;
            session.run("CREATE CONSTRAINT ai_course_mysql_id IF NOT EXISTS FOR (c:Course) REQUIRE c.mysqlId IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT ai_lesson_mysql_id IF NOT EXISTS FOR (l:Lesson) REQUIRE l.mysqlId IS UNIQUE").consume();
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
}
