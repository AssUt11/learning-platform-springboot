create database if not exists learning_platform
  default character set utf8mb4
  collate utf8mb4_unicode_ci;

use learning_platform;

drop table if exists agent_logs;
drop table if exists learning_records;
drop table if exists resource_favorites;
drop table if exists knowledge_prerequisites;
drop table if exists knowledge_nodes;
drop table if exists resources;
drop table if exists courses;
drop table if exists subject_categories;
drop table if exists student_preferences;
drop table if exists users;

create table users (
  id bigint primary key auto_increment,
  name varchar(80) not null,
  email varchar(120) not null unique,
  password_hash varchar(255) not null,
  role enum('teacher', 'student') not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  index idx_users_role(role)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table student_preferences (
  id bigint primary key auto_increment,
  student_id bigint not null,
  subject varchar(60) not null,
  created_at timestamp not null default current_timestamp,
  unique key uk_student_subject(student_id, subject),
  constraint fk_pref_student foreign key(student_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table subject_categories (
  id bigint primary key auto_increment,
  name varchar(60) not null unique,
  description text null,
  created_at timestamp not null default current_timestamp
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table courses (
  id bigint primary key auto_increment,
  teacher_id bigint null,
  title varchar(160) not null,
  subject varchar(60) not null,
  description text null,
  cover_url varchar(255) null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  index idx_courses_subject(subject),
  constraint fk_courses_teacher foreign key(teacher_id) references users(id) on delete set null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table resources (
  id bigint primary key auto_increment,
  teacher_id bigint null,
  course_id bigint null,
  title varchar(160) not null,
  subject varchar(60) not null,
  type enum('video', 'material') not null default 'material',
  level varchar(40) not null default '入门',
  description text null,
  content text null,
  file_url varchar(255) null,
  lifecycle_status enum('active', 'archived') not null default 'active',
  archived_at timestamp null,
  archive_reason varchar(500) null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  index idx_resources_subject(subject),
  index idx_resources_type(type),
  constraint fk_resources_teacher foreign key(teacher_id) references users(id) on delete set null,
  constraint fk_resources_course foreign key(course_id) references courses(id) on delete set null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table knowledge_nodes (
  id bigint primary key auto_increment,
  subject varchar(60) not null,
  title varchar(120) not null,
  node_level tinyint not null default 1,
  description text null,
  sort_order int not null default 0,
  created_at timestamp not null default current_timestamp,
  unique key uk_subject_title(subject, title),
  index idx_knowledge_subject(subject)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table knowledge_prerequisites (
  id bigint primary key auto_increment,
  subject varchar(60) not null,
  prerequisite_key varchar(220) not null,
  prerequisite_title varchar(120) not null,
  target_key varchar(220) not null,
  target_title varchar(120) not null,
  source_reference varchar(500) not null,
  sync_status enum('pending', 'synced', 'failed') not null default 'pending',
  sync_error varchar(500) null,
  created_by bigint null,
  updated_by bigint null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_knowledge_prerequisite(subject, prerequisite_key, target_key),
  index idx_knowledge_prerequisite_target(subject, target_key),
  index idx_knowledge_prerequisite_sync(sync_status, updated_at),
  constraint fk_knowledge_prerequisite_creator foreign key(created_by) references users(id) on delete set null,
  constraint fk_knowledge_prerequisite_updater foreign key(updated_by) references users(id) on delete set null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into knowledge_prerequisites(subject,prerequisite_key,prerequisite_title,target_key,target_title,source_reference) values
('Java', 'software_engineering/java/java基础', 'Java基础', 'software_engineering/java/面向对象', '面向对象', '知度课堂默认 Java 学习路径 v1'),
('Java', 'software_engineering/java/面向对象', '面向对象', 'software_engineering/java/spring', 'Spring', '知度课堂默认 Java 学习路径 v1'),
('Java', 'software_engineering/java/spring', 'Spring', 'software_engineering/java/spring_mvc', 'Spring MVC', '知度课堂默认 Java 学习路径 v1'),
('Java', 'software_engineering/java/spring_mvc', 'Spring MVC', 'software_engineering/java/spring_boot', 'Spring Boot', '知度课堂默认 Java 学习路径 v1');

create table learning_records (
  id bigint primary key auto_increment,
  student_id bigint not null,
  resource_id bigint null,
  subject varchar(60) null,
  knowledge_node varchar(120) null,
  progress tinyint not null default 0,
  duration_seconds int not null default 0,
  score decimal(5,2) null,
  created_at timestamp not null default current_timestamp,
  index idx_records_student(student_id),
  index idx_records_subject(subject),
  constraint fk_records_student foreign key(student_id) references users(id) on delete cascade,
  constraint fk_records_resource foreign key(resource_id) references resources(id) on delete set null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table resource_favorites (
  id bigint primary key auto_increment,
  student_id bigint not null,
  resource_id bigint not null,
  created_at timestamp not null default current_timestamp,
  unique key uk_student_resource(student_id, resource_id),
  index idx_favorites_resource(resource_id),
  constraint fk_favorites_student foreign key(student_id) references users(id) on delete cascade,
  constraint fk_favorites_resource foreign key(resource_id) references resources(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table agent_logs (
  id bigint primary key auto_increment,
  student_id bigint not null,
  agent_type enum('guide', 'analysis', 'path', 'recommend') not null,
  input_summary text null,
  output_result text null,
  report_url varchar(255) null,
  request_id varchar(64) null,
  provider_code varchar(40) null,
  model_code varchar(120) null,
  prompt_version varchar(40) null,
  tools_used text null,
  sources_used text null,
  graph_used tinyint not null default 0,
  status enum('running', 'completed', 'failed') not null default 'completed',
  duration_ms bigint null,
  error_code varchar(60) null,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  created_at timestamp not null default current_timestamp,
  index idx_agent_student(student_id),
  unique key uk_agent_request(request_id),
  constraint fk_agent_student foreign key(student_id) references users(id) on delete cascade
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into courses(title, subject, description) values
('Java 程序设计基础', 'Java', '覆盖 Java 语法、面向对象、集合、多线程和 Spring Boot 入门。'),
('C# 桌面与 Web 开发', 'C#', '覆盖 C# 语法、WinForms、LINQ、ASP.NET Core 和 Entity Framework。'),
('Python 数据分析基础', 'Python', '覆盖 Python 基础、NumPy、Pandas、数据可视化与 Flask 入门。'),
('MySQL 数据库设计', '数据库', '覆盖 SQL 查询、索引、事务、范式和性能优化。'),
('React 前端开发', '前端开发', '覆盖 HTML、CSS、JavaScript、React 组件和接口请求。'),
('知识图谱与推荐系统', '人工智能', '覆盖机器学习、知识图谱、推荐算法和智能分析协同。'),
('软件工程与测试实践', '软件工程', '掌握需求、建模、测试和敏捷协作。'),
('Web 安全攻防基础', '网络安全', '认识常见漏洞、防护思路和日志审计。'),
('移动应用开发实战', '移动开发', '覆盖 Android、小程序和 Flutter 入门项目。'),
('大数据处理基础', '大数据', '学习 Hadoop、Spark、Hive 和批流处理。'),
('云原生应用部署', '云计算', '使用 Docker、Kubernetes 和 DevOps 流水线部署应用。'),
('物联网应用开发', '物联网', '连接单片机、传感器、MQTT 和边缘计算场景。');

insert into subject_categories(name) values
('Java'),
('C#'),
('Python'),
('数据库'),
('前端开发'),
('人工智能'),
('软件工程'),
('网络安全'),
('移动开发'),
('大数据'),
('云计算'),
('物联网');

insert into resources(title, subject, type, level, description) values
('Java 面向对象核心训练', 'Java', 'video', '入门', '通过类、对象、继承和多态训练 Java 核心能力。'),
('C# WinForms 项目实战', 'C#', 'material', '进阶', '用项目方式学习桌面应用开发。'),
('Python 数据分析基础', 'Python', 'video', '入门', '使用 NumPy 和 Pandas 完成数据清洗与分析。'),
('MySQL 表设计与索引优化', '数据库', 'material', '进阶', '学习表结构、索引选择和 SQL 优化。'),
('React 组件化开发', '前端开发', 'video', '入门', '掌握组件拆分、状态管理和接口请求。'),
('知识图谱与推荐系统导论', '人工智能', 'material', '进阶', '理解知识图谱建模和个性化推荐流程。'),
('软件测试用例设计', '软件工程', 'material', '入门', '围绕需求编写等价类和边界值测试用例。'),
('Web 安全漏洞识别', '网络安全', 'video', '进阶', '识别 XSS、SQL 注入和权限绕过风险。'),
('Android 页面与数据交互', '移动开发', 'video', '入门', '完成移动端页面布局和接口交互。'),
('Spark 数据处理实战', '大数据', 'material', '进阶', '使用 Spark 处理日志和统计指标。'),
('Docker 到 Kubernetes 部署', '云计算', 'video', '进阶', '从镜像构建到 K8s 发布完成流程。'),
('MQTT 物联网通信基础', '物联网', 'material', '入门', '学习设备上报、消息订阅和边缘网关。');

insert into knowledge_nodes(subject, title, node_level, sort_order) values
('Java', 'Java基础', 1, 1),
('Java', '面向对象', 1, 2),
('Java', '集合框架', 1, 3),
('Java', 'Spring Boot', 1, 4),
('C#', 'C#基础', 1, 1),
('C#', 'WinForms', 1, 2),
('C#', 'ASP.NET Core', 1, 3),
('C#', 'Entity Framework', 1, 4),
('Python', 'Python基础', 1, 1),
('Python', '数据分析', 1, 2),
('Python', '网络爬虫', 1, 3),
('Python', 'Flask', 1, 4),
('数据库', 'MySQL', 1, 1),
('数据库', 'Redis', 1, 2),
('数据库', 'SQL优化', 1, 3),
('数据库', '数据库事务', 1, 4),
('前端开发', 'HTML/CSS', 1, 1),
('前端开发', 'JavaScript', 1, 2),
('前端开发', 'React', 1, 3),
('前端开发', 'Vue', 1, 4),
('人工智能', '机器学习', 1, 1),
('人工智能', '深度学习', 1, 2),
('人工智能', '自然语言处理', 1, 3),
('人工智能', '推荐系统', 1, 4),
('软件工程', '需求分析', 1, 1),
('软件工程', 'UML建模', 1, 2),
('软件工程', '软件测试', 1, 3),
('软件工程', '敏捷开发', 1, 4),
('网络安全', 'Web安全', 1, 1),
('网络安全', '密码学基础', 1, 2),
('网络安全', '渗透测试', 1, 3),
('网络安全', '安全运维', 1, 4),
('移动开发', 'Android开发', 1, 1),
('移动开发', 'iOS开发', 1, 2),
('移动开发', '微信小程序', 1, 3),
('移动开发', 'Flutter', 1, 4),
('大数据', 'Hadoop', 1, 1),
('大数据', 'Spark', 1, 2),
('大数据', 'Hive', 1, 3),
('大数据', '实时计算', 1, 4),
('云计算', 'Docker容器', 1, 1),
('云计算', 'Kubernetes', 1, 2),
('云计算', '云原生部署', 1, 3),
('云计算', 'DevOps', 1, 4),
('物联网', '单片机基础', 1, 1),
('物联网', '传感器应用', 1, 2),
('物联网', 'MQTT通信', 1, 3),
('物联网', '边缘计算', 1, 4);
