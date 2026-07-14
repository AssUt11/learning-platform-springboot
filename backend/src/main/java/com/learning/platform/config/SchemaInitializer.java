package com.learning.platform.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class SchemaInitializer {
    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        createTables();
        seedCatalog();
        seedCourses();
        seedResources();
        backfillResourceContent();
    }

    private void createTables() {
        jdbcTemplate.execute("create table if not exists users ("
                + "id bigint primary key auto_increment,"
                + "name varchar(80) not null,"
                + "email varchar(120) not null unique,"
                + "password_hash varchar(255) not null,"
                + "role enum('teacher','student') not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_users_role(role)"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists student_preferences ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "subject varchar(60) not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_student_subject(student_id, subject),"
                + "constraint fk_pref_student foreign key(student_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists subject_categories ("
                + "id bigint primary key auto_increment,"
                + "name varchar(60) not null unique,"
                + "description text null,"
                + "created_at timestamp not null default current_timestamp"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists courses ("
                + "id bigint primary key auto_increment,"
                + "teacher_id bigint null,"
                + "title varchar(160) not null,"
                + "subject varchar(60) not null,"
                + "description text null,"
                + "cover_url varchar(255) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_courses_subject(subject),"
                + "constraint fk_courses_teacher foreign key(teacher_id) references users(id) on delete set null"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists resources ("
                + "id bigint primary key auto_increment,"
                + "teacher_id bigint null,"
                + "course_id bigint null,"
                + "title varchar(160) not null,"
                + "subject varchar(60) not null,"
                + "type enum('video','material') not null default 'material',"
                + "level varchar(40) not null default '入门',"
                + "description text null,"
                + "content text null,"
                + "file_url varchar(255) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "updated_at timestamp not null default current_timestamp on update current_timestamp,"
                + "index idx_resources_subject(subject),"
                + "index idx_resources_type(type),"
                + "constraint fk_resources_teacher foreign key(teacher_id) references users(id) on delete set null,"
                + "constraint fk_resources_course foreign key(course_id) references courses(id) on delete set null"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists knowledge_nodes ("
                + "id bigint primary key auto_increment,"
                + "subject varchar(60) not null,"
                + "title varchar(120) not null,"
                + "node_level tinyint not null default 1,"
                + "description text null,"
                + "sort_order int not null default 0,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_subject_title(subject, title),"
                + "index idx_knowledge_subject(subject)"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists knowledge_edges ("
                + "id bigint primary key auto_increment,"
                + "source_node_id bigint not null,"
                + "target_node_id bigint not null,"
                + "relation_type varchar(40) not null default '前修',"
                + "created_at timestamp not null default current_timestamp,"
                + "constraint fk_edges_source foreign key(source_node_id) references knowledge_nodes(id) on delete cascade,"
                + "constraint fk_edges_target foreign key(target_node_id) references knowledge_nodes(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists learning_records ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "resource_id bigint null,"
                + "record_key varchar(220) null,"
                + "subject varchar(60) null,"
                + "knowledge_node varchar(120) null,"
                + "progress tinyint not null default 0,"
                + "duration_seconds int not null default 0,"
                + "score decimal(5,2) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_records_student(student_id),"
                + "index idx_records_subject(subject),"
                + "unique key uk_student_record_key(student_id, record_key),"
                + "constraint fk_records_student foreign key(student_id) references users(id) on delete cascade,"
                + "constraint fk_records_resource foreign key(resource_id) references resources(id) on delete set null"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists resource_favorites ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "resource_id bigint not null,"
                + "created_at timestamp not null default current_timestamp,"
                + "unique key uk_student_resource(student_id, resource_id),"
                + "index idx_favorites_resource(resource_id),"
                + "constraint fk_favorites_student foreign key(student_id) references users(id) on delete cascade,"
                + "constraint fk_favorites_resource foreign key(resource_id) references resources(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        jdbcTemplate.execute("create table if not exists agent_logs ("
                + "id bigint primary key auto_increment,"
                + "student_id bigint not null,"
                + "agent_type enum('guide','analysis','path','recommend') not null,"
                + "input_summary text null,"
                + "output_result text null,"
                + "report_url varchar(255) null,"
                + "created_at timestamp not null default current_timestamp,"
                + "index idx_agent_student(student_id),"
                + "constraint fk_agent_student foreign key(student_id) references users(id) on delete cascade"
                + ") engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci");

        safeExecute("alter table learning_records add column record_key varchar(220) null after resource_id");
        safeExecute("alter table learning_records add unique key uk_student_record_key(student_id, record_key)");
        safeExecute("alter table resources add column content text null after description");
    }

    private void safeExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private void seedCatalog() {
        seedCategory("Java", "Java 后端开发、Spring Boot 与企业项目基础");
        seedCategory("C#", "C# 桌面应用、Web 开发与 .NET 生态");
        seedCategory("Python", "Python 基础、数据分析、爬虫与轻量 Web 开发");
        seedCategory("数据库", "关系型数据库、缓存、SQL 优化与事务设计");
        seedCategory("前端开发", "HTML/CSS、JavaScript、React/Vue 与前端工程化");
        seedCategory("人工智能", "机器学习、深度学习、NLP 与推荐系统");
        seedCategory("软件工程", "需求分析、建模、测试、敏捷与项目协作");
        seedCategory("网络安全", "Web 安全、密码学、渗透测试与安全运维");
        seedCategory("移动开发", "Android、iOS、小程序与跨端应用开发");
        seedCategory("大数据", "Hadoop、Spark、Hive 与实时计算");
        seedCategory("云计算", "Docker、Kubernetes、云原生部署与 DevOps");
        seedCategory("物联网", "单片机、传感器、MQTT 与边缘计算");

        seedNodes("Java", "Java基础", "面向对象", "集合框架", "Spring Boot");
        seedNodes("C#", "C#基础", "WinForms", "ASP.NET Core", "Entity Framework");
        seedNodes("Python", "Python基础", "数据分析", "网络爬虫", "Flask");
        seedNodes("数据库", "MySQL", "Redis", "SQL优化", "数据库事务");
        seedNodes("前端开发", "HTML/CSS", "JavaScript", "React", "Vue");
        seedNodes("人工智能", "机器学习", "深度学习", "自然语言处理", "推荐系统");
        seedNodes("软件工程", "需求分析", "UML建模", "软件测试", "敏捷开发");
        seedNodes("网络安全", "Web安全", "密码学基础", "渗透测试", "安全运维");
        seedNodes("移动开发", "Android开发", "iOS开发", "微信小程序", "Flutter");
        seedNodes("大数据", "Hadoop", "Spark", "Hive", "实时计算");
        seedNodes("云计算", "Docker容器", "Kubernetes", "云原生部署", "DevOps");
        seedNodes("物联网", "单片机基础", "传感器应用", "MQTT通信", "边缘计算");
    }

    private void seedCourses() {
        seedCourse("Java 企业级后端开发", "Java", "从 Java 基础到 Spring Boot 接口开发与项目实战");
        seedCourse("C# 桌面与 Web 开发", "C#", "学习 WinForms、ASP.NET Core 与 .NET 数据访问");
        seedCourse("Python 数据分析入门", "Python", "覆盖 Python 基础、Pandas 数据处理和可视化");
        seedCourse("MySQL 与 Redis 数据存储", "数据库", "学习 SQL 查询、索引、事务和缓存设计");
        seedCourse("React 前端项目开发", "前端开发", "完成组件拆分、状态管理和接口联调");
        seedCourse("机器学习与推荐系统", "人工智能", "理解常见模型、特征工程和推荐流程");
    }

    private void seedResources() {
        seedResource("Java Spring Boot 接口实战", "Java", "video", "入门", "完成用户、课程和学习记录接口开发");
        seedResource("C# WinForms 小项目训练", "C#", "material", "入门", "用窗体控件完成桌面应用练习");
        seedResource("Python Pandas 数据清洗", "Python", "video", "入门", "使用 Pandas 完成数据读取、清洗和统计");
        seedResource("MySQL 索引与事务优化", "数据库", "material", "进阶", "学习索引选择、事务隔离和慢 SQL 分析");
        seedResource("React 组件化开发", "前端开发", "video", "入门", "构建可复用组件并完成接口请求");
        seedResource("机器学习模型入门", "人工智能", "material", "进阶", "从特征、训练到评估理解模型流程");
    }

    private void backfillResourceContent() {
        seedResource("Java Spring Boot 接口实战", "Java", "video", "入门", "完成用户、课程和学习记录接口开发");
        seedResource("C# WinForms 小项目训练", "C#", "material", "入门", "用窗体控件完成桌面应用练习");
        seedResource("Python Pandas 数据清洗", "Python", "video", "入门", "使用 Pandas 完成数据读取、清洗和统计");
        seedResource("MySQL 索引与事务优化", "数据库", "material", "进阶", "学习索引选择、事务隔离和慢 SQL 分析");
        seedResource("React 组件化开发", "前端开发", "video", "入门", "构建可复用组件并完成接口请求");
        seedResource("机器学习模型入门", "人工智能", "material", "进阶", "从特征、训练到评估理解模型流程");
        seedResource("软件测试用例设计", "软件工程", "material", "入门", "围绕需求编写等价类、边界值和场景测试用例");
        seedResource("Web 安全漏洞识别", "网络安全", "video", "进阶", "识别 XSS、SQL 注入、权限绕过和日志审计风险");
        seedResource("Android 页面与数据交互", "移动开发", "video", "入门", "完成移动端页面布局、接口请求和状态展示");
        seedResource("Spark 数据处理实战", "大数据", "material", "进阶", "使用 Spark 处理日志、宽表和统计指标");
        seedResource("Docker 到 Kubernetes 部署", "云计算", "video", "进阶", "从镜像构建到 K8s 发布完成应用部署流程");
        seedResource("MQTT 物联网通信基础", "物联网", "material", "入门", "学习设备上报、消息订阅、QoS 和边缘网关设计");
        jdbcTemplate.update("update resources set content=concat("
                + "'【文本学习】',title,'。',coalesce(description,''),'。本节课程适合在观看视频或下载资料之前先进行文字预习。学习时先明确课程目标：理解',subject,'方向中的核心概念、常见术语、典型场景和基本工作流程；再把知识放到一个可执行的小任务中验证。阅读过程中请准备笔记，记录三个问题：这个知识点解决什么问题，它依赖哪些前置知识，实际项目中容易在哪一步出错。',"
                + "'第一阶段关注概念。不要急着记忆大量细节，先用自己的话解释课程标题中的关键词，并把它和已经学过的知识建立联系。如果遇到陌生名词，可以先写下猜测，再通过示例代码、流程图或操作截图验证。第二阶段关注流程。把课程内容拆成输入、处理、输出三个部分，说明每一步产生什么结果，为什么需要这样设计。第三阶段关注实践。选择一个最小练习，例如完成一个接口、编写一个查询、实现一个组件、整理一组数据或搭建一个简单环境。练习完成后要回看错误信息和调试过程，因为这些细节往往比最终结果更能反映掌握程度。',"
                + "'学习完成后，请用五句话总结：本节最重要的概念是什么，关键步骤有哪些，容易混淆的点在哪里，可以迁移到什么项目场景，下一步应该补强哪项能力。建议把完成度分为了解、会做、能解释、能迁移四级。如果只能跟着步骤完成，说明还停留在会做阶段；如果能够脱离教程复述原因并改造练习，才算真正掌握。',"
                + "'本课程的文本学习内容也可以作为复习清单使用。再次复习时，请优先检查自己是否能独立画出流程、说出关键参数、解释一次失败案例，并把学习记录更新到平台中。这样系统后续生成学习路径、薄弱点分析和资源推荐时，才能更准确地反映你的真实状态。') "
                + "where content is null or char_length(content)<500");
    }

    private void seedCategory(String name, String description) {
        jdbcTemplate.update("insert ignore into subject_categories(name, description) values(?, ?)", name, description);
    }

    private void seedNodes(String subject, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            jdbcTemplate.update("insert ignore into knowledge_nodes(subject, title, node_level, sort_order) values(?, ?, 1, ?)",
                    subject, titles[i], i + 1);
        }
    }

    private void seedCourse(String title, String subject, String description) {
        jdbcTemplate.update("insert into courses(title, subject, description) "
                        + "select ?, ?, ? where not exists (select 1 from courses where title=?)",
                title, subject, description, title);
    }

    private void seedResource(String title, String subject, String type, String level, String description) {
        jdbcTemplate.update("insert into resources(title, subject, type, level, description) "
                        + "select ?, ?, ?, ?, ? where not exists (select 1 from resources where title=?)",
                title, subject, type, level, description, title);
    }
}
