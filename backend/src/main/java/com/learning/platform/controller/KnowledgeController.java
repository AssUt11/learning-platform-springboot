package com.learning.platform.controller;

import com.learning.platform.exception.BusinessException;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.KnowledgeTaxonomyService;
import com.learning.platform.util.ApiResponses;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeTaxonomyService taxonomy;
    private final CurrentUserService currentUserService;
    private final boolean neo4jEnabled;
    private final String neo4jUri;
    private final String neo4jUsername;
    private final String neo4jPassword;
    private final long neo4jConnectionTimeoutMs;
    private final long neo4jAcquisitionTimeoutMs;
    private final long neo4jRetryTimeoutMs;
    private final long neo4jFailureCooldownMs;
    private Driver neo4jDriver;
    private volatile boolean neo4jTaxonomySynced;
    private volatile long neo4jRetryAfterEpochMs;

    public KnowledgeController(JdbcTemplate jdbcTemplate,
                               KnowledgeTaxonomyService taxonomy,
                               CurrentUserService currentUserService,
                               @Value("${app.neo4j.enabled:false}") boolean neo4jEnabled,
                               @Value("${app.neo4j.uri:neo4j://127.0.0.1:7687}") String neo4jUri,
                               @Value("${app.neo4j.username:neo4j}") String neo4jUsername,
                               @Value("${app.neo4j.password:12345678}") String neo4jPassword,
                               @Value("${app.neo4j.connection-timeout-ms:2000}") long neo4jConnectionTimeoutMs,
                               @Value("${app.neo4j.acquisition-timeout-ms:2000}") long neo4jAcquisitionTimeoutMs,
                               @Value("${app.neo4j.retry-timeout-ms:800}") long neo4jRetryTimeoutMs,
                               @Value("${app.neo4j.failure-cooldown-ms:10000}") long neo4jFailureCooldownMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.taxonomy = taxonomy;
        this.currentUserService = currentUserService;
        this.neo4jEnabled = neo4jEnabled;
        this.neo4jUri = neo4jUri;
        this.neo4jUsername = neo4jUsername;
        this.neo4jPassword = neo4jPassword;
        this.neo4jConnectionTimeoutMs = positiveTimeout(neo4jConnectionTimeoutMs, 2000L);
        this.neo4jAcquisitionTimeoutMs = positiveTimeout(neo4jAcquisitionTimeoutMs, 2000L);
        this.neo4jRetryTimeoutMs = positiveTimeout(neo4jRetryTimeoutMs, 800L);
        this.neo4jFailureCooldownMs = positiveTimeout(neo4jFailureCooldownMs, 10000L);
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        List<Map<String, Object>> subjects = jdbcTemplate.queryForList(
                "select name, description from subject_categories order by id");
        List<Map<String, Object>> resultSubjects = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> subject : subjects) {
            String name = String.valueOf(subject.get("name"));
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("name", name);
            item.put("description", subject.get("description"));
            item.put("nodes", jdbcTemplate.queryForList(
                    "select id,title,node_level nodeLevel,description,sort_order sortOrder from knowledge_nodes where subject=? order by sort_order,id",
                    name));
            resultSubjects.add(item);
        }
        return ApiResponses.of("subjects", resultSubjects);
    }

    @GetMapping("/knowledge/categories/tree")
    public Map<String, Object> knowledgeCategoryTree() {
        return ApiResponses.of("categories", defaultKnowledgeCategories());
    }

    @GetMapping("/knowledge-graph")
    public Map<String, Object> knowledgeGraph(@RequestParam String subject,
                                              @RequestParam(required = false, defaultValue = "") String path) {
        long startedAt = System.nanoTime();
        if (neo4jEnabled) {
            if (System.currentTimeMillis() < neo4jRetryAfterEpochMs) {
                Map<String, Object> fallback = mysqlGraph(subject);
                fallback.put("fallbackReason", "Neo4j 正在短暂冷却，当前显示备用数据");
                fallback.put("responseTimeMs", elapsedMilliseconds(startedAt));
                return fallback;
            }
            try {
                Map<String, Object> graph = neo4jGraph(subject, path);
                if (!((List<?>) graph.get("nodes")).isEmpty()) {
                    neo4jRetryAfterEpochMs = 0L;
                    graph.put("responseTimeMs", elapsedMilliseconds(startedAt));
                    return graph;
                }
            } catch (Exception exception) {
                markNeo4jUnavailable();
                Map<String, Object> fallback = mysqlGraph(subject);
                fallback.put("fallbackReason", neo4jFallbackReason(exception));
                fallback.put("responseTimeMs", elapsedMilliseconds(startedAt));
                return fallback;
            }
        }
        Map<String, Object> fallback = mysqlGraph(subject);
        fallback.put("fallbackReason", neo4jEnabled ? "Neo4j 中暂无对应分类数据" : "Neo4j 尚未启用");
        fallback.put("responseTimeMs", elapsedMilliseconds(startedAt));
        return fallback;
    }

    private String neo4jFallbackReason(Exception exception) {
        String message = String.valueOf(exception.getMessage()).toLowerCase();
        if (message.contains("unauthorized") || message.contains("authentication") || message.contains("credentials")) {
            return "Neo4j 认证失败，请检查 NEO4J_USERNAME 和 NEO4J_PASSWORD";
        }
        if (message.contains("connection") || message.contains("routing") || message.contains("unavailable")) {
            return "Neo4j 连接失败，请确认 Study 实例正在运行";
        }
        return "Neo4j 图谱暂不可用，当前显示备用数据";
    }

    @PostMapping("/teacher/subjects")
    public Map<String, Object> createSubject(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestBody Map<String, Object> body) {
        currentUserService.requireTeacher(authorization);
        String name = text(body.get("name"));
        String description = text(body.get("description"));
        if (name.length() == 0) {
            throw new BusinessException(400, "Subject name cannot be empty.");
        }
        jdbcTemplate.update("insert ignore into subject_categories(name,description) values(?,?)", name, description);
        return ApiResponses.ok();
    }

    @PostMapping("/teacher/knowledge-nodes")
    public Map<String, Object> createKnowledgeNode(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @RequestBody Map<String, Object> body) {
        currentUserService.requireTeacher(authorization);
        String subject = text(body.get("subject"));
        String title = text(body.get("title"));
        String description = text(body.get("description"));
        int nodeLevel = number(body.get("nodeLevel"), 1);
        int sortOrder = number(body.get("sortOrder"), 0);
        if (subject.length() == 0 || title.length() == 0) {
            throw new BusinessException(400, "Subject and knowledge node title cannot be empty.");
        }
        jdbcTemplate.update("insert ignore into subject_categories(name) values(?)", subject);
        jdbcTemplate.update("insert into knowledge_nodes(subject,title,node_level,description,sort_order) values(?,?,?,?,?)",
                subject, title, nodeLevel, description, sortOrder);
        return ApiResponses.ok();
    }

    @PutMapping("/teacher/knowledge-nodes/{id}")
    public Map<String, Object> updateKnowledgeNode(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        currentUserService.requireTeacher(authorization);
        String subject = text(body.get("subject"));
        String title = text(body.get("title"));
        String description = text(body.get("description"));
        int nodeLevel = number(body.get("nodeLevel"), 1);
        int sortOrder = number(body.get("sortOrder"), 0);
        if (subject.length() == 0 || title.length() == 0) {
            throw new BusinessException(400, "Subject and knowledge node title cannot be empty.");
        }
        jdbcTemplate.update("insert ignore into subject_categories(name) values(?)", subject);
        jdbcTemplate.update("update knowledge_nodes set subject=?,title=?,node_level=?,description=?,sort_order=? where id=?",
                subject, title, nodeLevel, description, sortOrder, id);
        return ApiResponses.ok();
    }

    @DeleteMapping("/teacher/knowledge-nodes/{id}")
    public Map<String, Object> deleteKnowledgeNode(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable Long id) {
        currentUserService.requireTeacher(authorization);
        jdbcTemplate.update("delete from knowledge_nodes where id=?", id);
        return ApiResponses.ok();
    }

    private Map<String, Object> neo4jGraph(String subject, String path) {
        Driver driver = neo4jDriver();
        ensureNeo4jTaxonomy(driver);
        String cypher = ""
                + "MATCH (n:KnowledgeNode) "
                + "WHERE ($path <> '' AND coalesce(n.path, '') = $path) "
                + "   OR ($path = '' AND coalesce(n.title, n.name, n.label, '') = $subject) "
                + "WITH n ORDER BY coalesce(n.level, 999), coalesce(n.path, '') LIMIT 1 "
                + "OPTIONAL MATCH (n)-[r1:CONTAINS]->(m:KnowledgeNode) "
                + "OPTIONAL MATCH (m)-[r2:CONTAINS]->(k:KnowledgeNode) "
                + "RETURN collect(distinct n) + collect(distinct m) + collect(distinct k) AS nodes, "
                + "       collect(distinct r1) + collect(distinct r2) AS relationships";
        try (Session session = driver.session()) {
            org.neo4j.driver.Record record = session.run(new Query(cypher, Values.parameters("subject", subject, "path", path))).single();
            Map<String, Map<String, Object>> nodes = new LinkedHashMap<String, Map<String, Object>>();
            List<Map<String, Object>> links = new ArrayList<Map<String, Object>>();
            for (org.neo4j.driver.Value value : record.get("nodes").values()) {
                if (!value.isNull()) {
                    addNeo4jNode(nodes, value.asNode(), subject);
                }
            }
            for (org.neo4j.driver.Value value : record.get("relationships").values()) {
                if (!value.isNull()) {
                    Relationship relationship = value.asRelationship();
                    Map<String, Object> link = new HashMap<String, Object>();
                    link.put("source", relationship.startNodeElementId());
                    link.put("target", relationship.endNodeElementId());
                    link.put("type", relationship.type());
                    links.add(link);
                    Map<String, Object> child = nodes.get(relationship.endNodeElementId());
                    if (child != null) {
                        child.put("parentId", relationship.startNodeElementId());
                    }
                }
            }
            ensureRoot(nodes, links, subject);
            return graphResult("neo4j", nodes, links, subject);
        }
    }

    private Map<String, Object> mysqlGraph(String subject) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<String, Map<String, Object>>();
        List<Map<String, Object>> links = new ArrayList<Map<String, Object>>();
        String rootId = subject + "-root";
        Map<String, Object> root = new HashMap<String, Object>();
        root.put("id", rootId);
        root.put("label", subject);
        root.put("type", "root");
        root.put("subject", subject);
        nodes.put(rootId, root);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id,title,node_level nodeLevel,description,sort_order sortOrder from knowledge_nodes where subject=? order by sort_order,id",
                subject);
        for (Map<String, Object> row : rows) {
            String id = "mysql-" + row.get("id");
            Map<String, Object> node = new HashMap<String, Object>();
            node.put("id", id);
            node.put("label", row.get("title"));
            node.put("type", number(row.get("nodeLevel"), 1) > 1 ? "point" : "knowledge");
            node.put("level", row.get("nodeLevel"));
            node.put("description", row.get("description"));
            node.put("subject", subject);
            nodes.put(id, node);

            Map<String, Object> link = new HashMap<String, Object>();
            link.put("source", rootId);
            link.put("target", id);
            link.put("type", "contains");
            links.add(link);

            int index = 1;
            for (String pointTitle : subtopics(String.valueOf(row.get("title")))) {
                String childId = id + "-point-" + index;
                Map<String, Object> child = new HashMap<String, Object>();
                child.put("id", childId);
                child.put("label", pointTitle);
                child.put("type", "point");
                child.put("level", 2);
                child.put("parentId", id);
                child.put("description", "围绕“" + row.get("title") + "”继续拆解的细分知识点，可用于课前预习、练习设计和薄弱点追踪。");
                child.put("subject", subject);
                nodes.put(childId, child);

                Map<String, Object> childLink = new HashMap<String, Object>();
                childLink.put("source", id);
                childLink.put("target", childId);
                childLink.put("type", "contains");
                links.add(childLink);
                index++;
            }
        }
        return graphResult("mysql", nodes, links, subject);
    }

    private List<String> subtopics(String title) {
        String text = title == null ? "" : title;
        List<String> result = new ArrayList<String>();
        if (text.contains("Spring")) {
            result.add("项目结构");
            result.add("接口路由");
            result.add("数据访问");
        } else if (text.contains("Java")) {
            result.add("变量与类型");
            result.add("流程控制");
            result.add("异常处理");
        } else if (text.contains("面向") || text.contains("对象")) {
            result.add("类与对象");
            result.add("封装继承");
            result.add("接口多态");
        } else if (text.contains("集合")) {
            result.add("List 与 Set");
            result.add("Map 使用");
            result.add("遍历与排序");
        } else if (text.contains("SQL") || text.contains("MySQL")) {
            result.add("查询语句");
            result.add("索引设计");
            result.add("执行计划");
        } else if (text.contains("React")) {
            result.add("组件拆分");
            result.add("状态管理");
            result.add("接口请求");
        } else if (text.contains("Python") || text.contains("Pandas")) {
            result.add("数据读取");
            result.add("清洗转换");
            result.add("统计分析");
        } else if (text.contains("安全") || text.contains("Web")) {
            result.add("漏洞识别");
            result.add("攻击链分析");
            result.add("防护策略");
        } else {
            result.add("核心概念");
            result.add("操作流程");
            result.add("实战练习");
        }
        result.add("常见错误");
        result.add("案例练习");
        result.add("能力检测");
        return result;
    }

    private synchronized Driver neo4jDriver() {
        if (neo4jDriver == null) {
            Config config = Config.builder()
                    .withConnectionTimeout(neo4jConnectionTimeoutMs, TimeUnit.MILLISECONDS)
                    .withConnectionAcquisitionTimeout(neo4jAcquisitionTimeoutMs, TimeUnit.MILLISECONDS)
                    .withMaxTransactionRetryTime(neo4jRetryTimeoutMs, TimeUnit.MILLISECONDS)
                    .build();
            Driver candidate = GraphDatabase.driver(
                    neo4jUri, AuthTokens.basic(neo4jUsername, neo4jPassword), config);
            try {
                candidate.verifyConnectivity();
                neo4jDriver = candidate;
            } catch (RuntimeException exception) {
                candidate.close();
                throw exception;
            }
        }
        return neo4jDriver;
    }

    private synchronized void markNeo4jUnavailable() {
        neo4jRetryAfterEpochMs = System.currentTimeMillis() + neo4jFailureCooldownMs;
        neo4jTaxonomySynced = false;
        if (neo4jDriver != null) {
            neo4jDriver.close();
            neo4jDriver = null;
        }
    }

    private long elapsedMilliseconds(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private long positiveTimeout(long configured, long fallback) {
        return configured > 0L ? configured : fallback;
    }

    private void ensureNeo4jTaxonomy(Driver driver) {
        if (neo4jTaxonomySynced) {
            return;
        }
        synchronized (this) {
            if (neo4jTaxonomySynced) {
                return;
            }
            List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
            List<Map<String, Object>> relationships = new ArrayList<Map<String, Object>>();
            collectTaxonomy(defaultKnowledgeCategories(), new ArrayList<String>(), new ArrayList<String>(), "", nodes, relationships);
            try (Session session = driver.session()) {
                session.executeWrite(transaction -> {
                    transaction.run(new Query(""
                                    + "UNWIND $nodes AS row "
                                    + "MERGE (n:KnowledgeNode {key: row.key}) "
                                    + "SET n.title = row.title, n.name = row.title, n.code = row.code, "
                                    + "    n.level = row.level, n.description = row.description, "
                                    + "    n.path = row.path, n.source = 'learning-platform'",
                            Values.parameters("nodes", nodes))).consume();
                    transaction.run(new Query(""
                                    + "UNWIND $relationships AS row "
                                    + "MATCH (parent:KnowledgeNode {key: row.parentKey}) "
                                    + "MATCH (child:KnowledgeNode {key: row.childKey}) "
                                    + "MERGE (parent)-[:CONTAINS]->(child)",
                            Values.parameters("relationships", relationships))).consume();
                    return null;
                });
            }
            neo4jTaxonomySynced = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void collectTaxonomy(List<Map<String, Object>> items,
                                 List<String> parentCodes,
                                 List<String> parentNames,
                                 String parentKey,
                                 List<Map<String, Object>> nodes,
                                 List<Map<String, Object>> relationships) {
        for (Map<String, Object> item : items) {
            String title = text(item.get("name"));
            String code = text(item.get("code"));
            List<String> codes = new ArrayList<String>(parentCodes);
            List<String> names = new ArrayList<String>(parentNames);
            codes.add(code.length() > 0 ? code : codeFromName(title));
            names.add(title);
            String key = String.join("/", codes);

            Map<String, Object> node = new HashMap<String, Object>();
            node.put("key", key);
            node.put("title", title);
            node.put("code", code);
            node.put("level", number(item.get("level"), names.size()));
            node.put("description", text(item.get("description")));
            node.put("path", String.join(" > ", names));
            nodes.add(node);

            if (parentKey.length() > 0) {
                Map<String, Object> relationship = new HashMap<String, Object>();
                relationship.put("parentKey", parentKey);
                relationship.put("childKey", key);
                relationships.add(relationship);
            }

            Object childValue = item.get("children");
            if (childValue instanceof List && !((List<?>) childValue).isEmpty()) {
                collectTaxonomy((List<Map<String, Object>>) childValue, codes, names, key, nodes, relationships);
            }
        }
    }

    private void addNeo4jNode(Map<String, Map<String, Object>> nodes, Node source, String subject) {
        String id = source.elementId();
        if (nodes.containsKey(id)) {
            return;
        }
        Map<String, Object> node = new HashMap<String, Object>();
        node.put("id", id);
        node.put("label", nodeText(source, "title", "name", "label", "id"));
        node.put("description", nodeText(source, "description", "desc", "summary"));
        node.put("subject", nodeText(source, "subject", "category"));
        node.put("type", nodeType(source, subject));
        node.put("labels", labels(source));
        if (source.containsKey("level") && !source.get("level").isNull()) {
            node.put("level", source.get("level").asInt());
        }
        if (source.containsKey("path") && !source.get("path").isNull()) {
            List<String> path = new ArrayList<String>();
            for (String part : source.get("path").asString().split("\\s*>\\s*")) {
                if (part.trim().length() > 0) {
                    path.add(part.trim());
                }
            }
            node.put("path", path);
        }
        nodes.put(id, node);
    }

    private void ensureRoot(Map<String, Map<String, Object>> nodes, List<Map<String, Object>> links, String subject) {
        String rootId = null;
        for (Map<String, Object> node : nodes.values()) {
            if (subject.equals(node.get("label")) || "root".equals(node.get("type"))) {
                rootId = String.valueOf(node.get("id"));
                node.put("type", "root");
                break;
            }
        }
        if (rootId == null) {
            rootId = subject + "-root";
            Map<String, Object> root = new HashMap<String, Object>();
            root.put("id", rootId);
            root.put("label", subject);
            root.put("type", "root");
            root.put("subject", subject);
            nodes.put(rootId, root);
        }
        Set<String> connected = new LinkedHashSet<String>();
        for (Map<String, Object> link : links) {
            connected.add(String.valueOf(link.get("source")));
            connected.add(String.valueOf(link.get("target")));
        }
        for (String id : nodes.keySet()) {
            if (!id.equals(rootId) && !connected.contains(id)) {
                Map<String, Object> link = new HashMap<String, Object>();
                link.put("source", rootId);
                link.put("target", id);
                link.put("type", "contains");
                links.add(link);
            }
        }
    }

    private Map<String, Object> graphResult(String source,
                                            Map<String, Map<String, Object>> nodeMap,
                                            List<Map<String, Object>> links,
                                            String subject) {
        List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>(nodeMap.values());
        Map<String, Object> stats = new HashMap<String, Object>();
        stats.put("completionRate", learningRate(subject, 100));
        stats.put("achievementRate", learningRate(subject, 60));
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("source", source);
        result.put("nodes", nodes);
        result.put("links", links);
        result.put("stats", stats);
        return result;
    }

    private int learningRate(String subject, int threshold) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "select count(*) total,sum(case when progress>=? then 1 else 0 end) done from learning_records where subject=? and knowledge_node is not null",
                    threshold, subject);
            int total = number(row.get("total"), 0);
            if (total == 0) {
                return 0;
            }
            return Math.round(number(row.get("done"), 0) * 100f / total);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String nodeText(Node node, String... keys) {
        for (String key : keys) {
            if (node.containsKey(key) && !node.get(key).isNull()) {
                String value = node.get(key).asObject().toString().trim();
                if (value.length() > 0) {
                    return value;
                }
            }
        }
        return "";
    }

    private String nodeType(Node node, String subject) {
        String label = nodeText(node, "title", "name", "label");
        if (subject.equals(label)) {
            return "root";
        }
        if (node.containsKey("level") && !node.get("level").isNull()) {
            int level = node.get("level").asInt(0);
            if (level == 2) {
                return "direction";
            }
            if (level == 3) {
                return "knowledge";
            }
            if (level > 3) {
                return "point";
            }
        }
        for (String neo4jLabel : node.labels()) {
            String lower = neo4jLabel.toLowerCase();
            if (lower.contains("course") || lower.contains("category") || lower.contains("root")) {
                return "root";
            }
            if (lower.contains("point") || lower.contains("node")) {
                return "point";
            }
        }
        return "knowledge";
    }

    private List<String> labels(Node node) {
        List<String> result = new ArrayList<String>();
        for (String label : node.labels()) {
            result.add(label);
        }
        return result;
    }

    private List<Map<String, Object>> defaultKnowledgeCategories() {
        return taxonomy.categories();
    }

    private String codeFromName(String name) {
        return name == null ? "" : name.trim().replace("#", "sharp").replace("+", "plus").replaceAll("\\s+", "_").toLowerCase();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int number(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    @PreDestroy
    public void closeNeo4j() {
        if (neo4jDriver != null) {
            neo4jDriver.close();
        }
    }
}
