# AI 伴学 v1 数据变更与回滚

## 上线措施

1. 备份 `learning_platform` 数据库。
2. 在测试库执行 `ai_tutor_v1.sql`，确认六张表及外键、索引创建成功。
3. 设置 `AI_TUTOR_ENABLED=false` 启动新版后端，先验证课程、视频和学习进度。
4. 确认 Ollama 和 Neo4j 健康后，再设置 `AI_TUTOR_ENABLED=true` 灰度开放。

应用启动时的 `AiSchemaInitializer` 也会幂等创建相同表，主要用于本地开发环境；生产环境应先执行 SQL 脚本。

## 应用回滚

优先只设置：

```text
AI_TUTOR_ENABLED=false
```

该操作不会删除会话数据，也不会影响课程主流程。

## 数据库回滚

只有在确认不再需要 AI 会话和反馈数据并已完成备份后，才按以下顺序手动执行：

```sql
drop table if exists ai_feedback;
drop table if exists ai_message_citation;
drop table if exists ai_usage_log;
drop table if exists ai_message;
drop table if exists ai_conversation;
drop table if exists ai_prompt_template;
```

数据库回滚会永久删除 AI 会话、消息、用量和反馈，不应作为普通应用版本回滚步骤。

## Neo4j 回滚

本期只新增 `Course`、`Lesson` 和 `HAS_LESSON`，不会删除或改写既有 `KnowledgeNode`。确需清理时，在完成图数据库备份后执行：

```cypher
MATCH (n)
WHERE (n:Course OR n:Lesson) AND n.source = 'learning-platform'
DETACH DELETE n;
```

如需同时移除本期创建的唯一约束：

```cypher
DROP CONSTRAINT ai_course_mysql_id IF EXISTS;
DROP CONSTRAINT ai_lesson_mysql_id IF EXISTS;
```
