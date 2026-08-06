# 知度课堂数据库表审计（2026-08-06）

## 审计结论

- 审计前共 24 张业务表，清理后保留 23 张。
- 已删除 `knowledge_edges`：该表为 0 行、无入向外键、无视图、无触发器，且前后端业务代码没有任何读写引用。
- `knowledge_nodes` 继续保留。当前知识图谱由现有节点、目录关系以及 Neo4j 关系生成，不依赖 MySQL 的 `knowledge_edges`。
- 其余空表没有删除，因为课程问答、笔记、评价、收藏、课时进度等现有接口仍在读写这些表；“当前为空”不代表“无业务用途”。

## 保留表分类

| 分类 | 表 | 保留原因 |
| --- | --- | --- |
| 用户与偏好 | `users`、`student_preferences` | 登录、角色、学生兴趣选择 |
| 课程目录 | `categories`、`directions`、`subject_categories` | 课程筛选、知识目录、教师选题 |
| 课程内容 | `resources`、`courses`、`course_chapters`、`course_lessons`、`lesson_assets` | 课程、章节、课时与独立视频绑定 |
| 学习过程 | `learning_records`、`lesson_progress`、`resource_favorites` | 学习记录、课时进度、收藏 |
| 课程互动 | `course_questions`、`course_notes`、`course_reviews`、`course_feedback_replies` | 问答、笔记、评价及教师回复 |
| 知识结构 | `knowledge_nodes` | 课程知识点及图谱节点 |
| 消息与智能体 | `direct_messages`、`agent_logs` | 站内消息和 AI 学伴记录 |
| 治理与兼容 | `admin_audit_logs`、`course_legacy_migration`、`course_demo_seeds` | 管理审计、历史课程迁移、演示课程幂等标记 |

## 删除依据

`knowledge_edges` 删除前检查结果：

- 数据行数：0
- 业务代码引用：0
- 引用该表的外键：0
- 依赖该表的视图：0
- 依赖该表的触发器：0
- 该表自身仅有两个指向 `knowledge_nodes` 的外键，删除不会级联删除节点数据

同时已从以下初始化来源移除，避免服务重启后重新创建：

- `backend/src/main/java/com/learning/platform/config/SchemaInitializer.java`
- `database/navicat_mysql.sql`

## 恢复与备份

删除前备份：

- 完整数据库：`database/backups/learning_platform_before_edge_cleanup_20260806_113414.sql`
- 目标表结构：`database/backups/knowledge_edges_before_cleanup_20260806_113414.sql`

如需恢复目标表，可在项目根目录执行：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p learning_platform --execute="source database/backups/knowledge_edges_before_cleanup_20260806_113414.sql"
```
