package com.learning.platform.controller;

import com.learning.platform.service.CurrentUserService;
import com.learning.platform.util.ApiResponses;
import org.neo4j.driver.AuthTokens;
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

@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final boolean neo4jEnabled;
    private final String neo4jUri;
    private final String neo4jUsername;
    private final String neo4jPassword;
    private Driver neo4jDriver;

    public KnowledgeController(JdbcTemplate jdbcTemplate,
                               CurrentUserService currentUserService,
                               @Value("${app.neo4j.enabled:false}") boolean neo4jEnabled,
                               @Value("${app.neo4j.uri:bolt://localhost:7687}") String neo4jUri,
                               @Value("${app.neo4j.username:neo4j}") String neo4jUsername,
                               @Value("${app.neo4j.password:password}") String neo4jPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.neo4jEnabled = neo4jEnabled;
        this.neo4jUri = neo4jUri;
        this.neo4jUsername = neo4jUsername;
        this.neo4jPassword = neo4jPassword;
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
    public Map<String, Object> knowledgeGraph(@RequestParam String subject) {
        if (neo4jEnabled) {
            try {
                Map<String, Object> graph = neo4jGraph(subject);
                if (((List<?>) graph.get("nodes")).size() > 1) {
                    return graph;
                }
            } catch (Exception ignored) {
                // MySQL catalog data keeps the graph page usable when Neo4j is not running.
            }
        }
        return mysqlGraph(subject);
    }

    @PostMapping("/teacher/subjects")
    public Map<String, Object> createSubject(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestBody Map<String, Object> body) {
        currentUserService.requireTeacher(authorization);
        String name = text(body.get("name"));
        String description = text(body.get("description"));
        if (name.length() == 0) {
            throw new RuntimeException("Subject name cannot be empty.");
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
            throw new RuntimeException("Subject and knowledge node title cannot be empty.");
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
            throw new RuntimeException("Subject and knowledge node title cannot be empty.");
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

    private Map<String, Object> neo4jGraph(String subject) {
        Driver driver = neo4jDriver();
        String cypher = ""
                + "MATCH (n) "
                + "WHERE coalesce(n.subject, n.category, '') = $subject "
                + "   OR coalesce(n.name, n.title, n.label, '') = $subject "
                + "WITH collect(distinct n) AS baseNodes "
                + "UNWIND baseNodes AS n "
                + "OPTIONAL MATCH (n)-[r]-(m) "
                + "WHERE m IN baseNodes "
                + "   OR coalesce(m.subject, m.category, '') = $subject "
                + "   OR coalesce(n.name, n.title, n.label, '') = $subject "
                + "RETURN collect(distinct n) AS nodes, collect(distinct m) AS neighbors, collect(distinct r) AS relationships";
        try (Session session = driver.session()) {
            org.neo4j.driver.Record record = session.run(new Query(cypher, Values.parameters("subject", subject))).single();
            Map<String, Map<String, Object>> nodes = new LinkedHashMap<String, Map<String, Object>>();
            List<Map<String, Object>> links = new ArrayList<Map<String, Object>>();
            for (org.neo4j.driver.Value value : record.get("nodes").values()) {
                if (!value.isNull()) {
                    addNeo4jNode(nodes, value.asNode(), subject);
                }
            }
            for (org.neo4j.driver.Value value : record.get("neighbors").values()) {
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

    private Driver neo4jDriver() {
        if (neo4jDriver == null) {
            neo4jDriver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUsername, neo4jPassword));
        }
        return neo4jDriver;
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
        return categories(
                category("软件工程", "software_engineering", 1, "面向软件项目开发、测试、协作和交付的综合学习大类.", categories(
                        category("Java", "java", 2, "", leaves("Java基础", "数据类型与变量", "流程控制", "面向对象", "集合框架", "异常处理", "IO流", "多线程", "JDBC", "Maven", "Spring", "Spring MVC", "Spring Boot", "MyBatis Plus")),
                        category("Python", "python", 2, "", leaves("Python基础", "数据类型", "函数", "面向对象", "文件处理", "异常处理", "爬虫基础", "Flask", "Django", "数据分析", "自动化脚本")),
                        category("C#", "csharp", 2, "", leaves("C#基础", "面向对象", "集合", "LINQ", "WinForms", "WPF", "ASP.NET", ".NET Core")),
                        category("前端开发", "frontend", 2, "", leaves("HTML", "CSS", "JavaScript", "TypeScript", "DOM", "Ajax", "Axios", "Vue", "React", "Vite", "Element Plus")),
                        category("数据库", "database", 2, "", leaves("SQL基础", "MySQL", "表设计", "索引", "事务", "锁机制", "Redis", "MongoDB", "数据库优化")),
                        category("软件测试", "software_testing", 2, "", leaves("测试基础", "单元测试", "接口测试", "自动化测试", "性能测试", "测试报告"))
                )),
                category("人工智能", "ai", 1, "从机器学习、深度学习到自然语言处理和视觉任务的学习大类.", categories(
                        category("机器学习", "machine_learning", 2, "", leaves("线性回归", "逻辑回归", "决策树", "随机森林", "SVM", "KNN", "模型评估")),
                        category("深度学习", "deep_learning", 2, "", leaves("神经网络", "CNN", "RNN", "LSTM", "Transformer", "PyTorch")),
                        category("自然语言处理", "nlp", 2, "", leaves("分词", "文本分类", "词向量", "BERT", "GPT", "大语言模型")),
                        category("计算机视觉", "computer_vision", 2, "", leaves("图像处理", "图像分类", "目标检测", "图像分割", "OpenCV"))
                )),
                category("大数据", "big_data", 1, "围绕数据采集、存储、计算、建模和可视化的学习大类.", categories(
                        category("Hadoop", "hadoop", 2, "", leaves("HDFS", "MapReduce", "YARN")),
                        category("Spark", "spark", 2, "", leaves("Spark Core", "Spark SQL", "Spark Streaming", "MLlib")),
                        category("数据仓库", "data_warehouse", 2, "", leaves("Hive", "数据建模", "ETL", "OLAP")),
                        category("数据可视化", "data_visualization", 2, "", leaves("ECharts", "图表设计", "数据看板", "报表分析"))
                )),
                category("云计算", "cloud_computing", 1, "面向 Linux、容器、集群编排和云原生服务治理的学习大类.", categories(
                        category("Linux", "linux", 2, "", leaves("常用命令", "文件权限", "Shell脚本", "进程管理")),
                        category("Docker", "docker", 2, "", leaves("镜像", "容器", "Dockerfile", "Docker Compose", "容器部署")),
                        category("Kubernetes", "kubernetes", 2, "", leaves("Pod", "Service", "Deployment", "ConfigMap", "Ingress")),
                        category("微服务", "microservices", 2, "", leaves("服务注册", "服务发现", "网关", "配置中心", "分布式事务"))
                )),
                category("网络安全", "cyber_security", 1, "覆盖 Web 安全、密码学、协议安全和安全运维的学习大类.", categories(
                        category("Web安全", "web_security", 2, "", leaves("SQL注入", "XSS", "CSRF", "文件上传漏洞", "权限绕过")),
                        category("密码学", "cryptography", 2, "", leaves("对称加密", "非对称加密", "哈希算法", "数字签名")),
                        category("网络协议安全", "network_protocol_security", 2, "", leaves("TCP/IP", "HTTP", "HTTPS", "DNS", "防火墙")),
                        category("安全运维", "security_ops", 2, "", leaves("日志分析", "入侵检测", "漏洞扫描", "应急响应"))
                )),
                category("移动与物联网", "mobile_iot", 1, "面向移动应用、设备通信、边缘计算和嵌入式开发的学习大类.", categories(
                        category("移动开发", "mobile_development", 2, "", leaves("Android基础", "Kotlin", "Flutter", "小程序开发", "移动端适配")),
                        category("物联网", "iot", 2, "", leaves("传感器", "MQTT", "设备通信", "边缘计算", "智能硬件")),
                        category("嵌入式开发", "embedded", 2, "", leaves("C语言", "单片机", "STM32", "串口通信", "RTOS"))
                ))
        );
    }

    private Map<String, Object> category(String name, String code, int level, String description, List<Map<String, Object>> children) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", name);
        item.put("code", code);
        item.put("level", level);
        item.put("description", description);
        item.put("children", children);
        return item;
    }

    private List<Map<String, Object>> leaves(String... names) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (String name : names) {
            result.add(category(name, codeFromName(name), 3, "", new ArrayList<Map<String, Object>>()));
        }
        return result;
    }

    @SafeVarargs
    private final List<Map<String, Object>> categories(Map<String, Object>... items) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : items) {
            result.add(item);
        }
        return result;
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
