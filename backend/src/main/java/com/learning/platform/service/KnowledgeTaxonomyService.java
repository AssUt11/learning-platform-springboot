package com.learning.platform.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Keeps the curated taxonomy and adds database categories, directions and real lesson topics. */
@Service
public class KnowledgeTaxonomyService {
    private final JdbcTemplate jdbc;
    public KnowledgeTaxonomyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> result = categories(
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
        return includeDatabaseTaxonomy(result);
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
        return name == null ? "" : name.trim().replace("#", "sharp").replace("+", "plus").replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> includeDatabaseTaxonomy(List<Map<String, Object>> roots) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        roots.forEach(root -> byName.put(String.valueOf(root.get("name")), root));
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select id,name,description from categories where status=1 order by sort_order,id")) {
            String name = String.valueOf(row.get("name"));
            Map<String, Object> root = byName.computeIfAbsent(name, key -> {
                Map<String, Object> created = category(name, "catalog_category_" + row.get("id"), 1,
                        Objects.toString(row.get("description"), ""), new ArrayList<>());
                roots.add(created); return created;
            });
            byId.put(((Number)row.get("id")).longValue(), root);
        }
        Map<String, List<Map<String, Object>>> directionsBySubject = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("select id,category_id,name,description from directions where status=1 order by category_id,sort_order,id")) {
            Map<String, Object> root = byId.get(((Number)row.get("category_id")).longValue());
            if (root == null) continue;
            String name = String.valueOf(row.get("name"));
            List<Map<String, Object>> children = (List<Map<String, Object>>)root.get("children");
            Map<String, Object> direction = children.stream().filter(item -> name.equals(item.get("name"))).findFirst().orElse(null);
            if (direction == null) {
                direction = category(name, "catalog_direction_" + row.get("id"), 2, Objects.toString(row.get("description"), ""), new ArrayList<>());
                children.add(direction);
            }
            directionsBySubject.computeIfAbsent(name, key -> new ArrayList<>()).add(direction);
        }
        for (Map<String, Object> row : jdbc.queryForList("select subject,title,description from knowledge_nodes order by sort_order,id")) {
            for (Map<String, Object> direction : directionsBySubject.getOrDefault(String.valueOf(row.get("subject")), List.of())) {
                List<Map<String, Object>> children = (List<Map<String, Object>>)direction.get("children");
                String title = String.valueOf(row.get("title"));
                if (children.stream().noneMatch(item -> title.equals(item.get("name"))))
                    children.add(category(title, codeFromName(title), 3, Objects.toString(row.get("description"), ""), new ArrayList<>()));
            }
        }
        return roots;
    }

    public List<Map<String, Object>> flattened() {
        List<Map<String, Object>> result = new ArrayList<>();
        flatten(categories(), "", "", result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flatten(List<Map<String, Object>> items, String parentKey, String parentPath, List<Map<String, Object>> result) {
        for (Map<String, Object> item : items) {
            String key = (parentKey.isEmpty() ? "" : parentKey + "/") + item.get("code");
            String path = (parentPath.isEmpty() ? "" : parentPath + " > ") + item.get("name");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key); row.put("parentKey", parentKey); row.put("path", path);
            row.put("title", item.get("name")); row.put("code", item.get("code")); row.put("level", item.get("level"));
            row.put("description", item.get("description")); result.add(row);
            flatten((List<Map<String, Object>>)item.get("children"), key, path, result);
        }
    }
}
