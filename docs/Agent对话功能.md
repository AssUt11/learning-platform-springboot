# Agent 对话

## 使用方式

学生、教师登录后，在顶部“消息”后点击“Agent 对话”。输入问题后可以连续追问，也可点击“新建对话”开始其他话题。

- 使用已配置的 LLaMA-Factory 微调模型 `zhidu-qwen3-4b-lora`，复用现有模型适配器和限流设置。
- 回答逐段显示，支持“停止生成”；切换站内页面时保持生成，返回后可查看回答。
- 会话和消息存入 MySQL。刷新后恢复当前会话，历史列表可重新打开之前的会话。
- 历史按登录账号隔离，学生与教师都只能读取和续写自己的会话。
- 采用浅色界面；Enter 发送，Shift+Enter 换行，支持中文输入法。

这是独立的多轮问答入口。课程页答疑和四类学习分析继续使用原入口；独立对话目前只使用聊天上下文，不自动检索课程或调用业务工具。模型可能回答不准确，需要核对。

## 启动

1. 按 [微调模型接入文档](LLaMA-Factory微调模型接入.md) 启动模型 API。
2. 启动 MySQL，再使用 IDEA 的 `LearningPlatform-LLaMA-Factory` 配置，或运行 `scripts/start-backend-llamafactory.ps1`。
3. 访问 [知度课堂](http://localhost:8081)，登录后打开“Agent 对话”。新增后端代码需要重启后端才能生效。

模型服务默认地址为 `http://127.0.0.1:8000/v1`。页面连接状态来自真实模型健康探测；未启用 AI、选择了其他提供方或模型离线时会显示原因，不使用演示回答替代。

## 接口与存储

所有接口均需教师或学生的登录令牌，用户身份由后端鉴权取得。

| 接口 | 作用 |
|---|---|
| `GET /api/agent-chat/status` | 微调模型连接状态 |
| `GET /api/agent-chat/conversations` | 最近 50 段会话 |
| `GET /api/agent-chat/conversations/{id}` | 会话信息与最近 200 条消息 |
| `POST /api/agent-chat/stream` | 创建或继续会话，SSE 返回回答 |
| `POST /api/agent-chat/requests/{id}/cancel` | 停止当前账号的请求 |

发送体：`{"conversationId":null,"message":"请解释 Java 变量"}`。后续追问使用返回的 `conversationId`；不传 `userId`、`courseId` 或 `lessonId`。

SSE 事件依次为 `conversation.created`、`message.started`、`message.delta`、`message.completed`，失败或取消为 `error`。停止时关闭上游网络请求；模型端计算是否立即停止取决于推理服务。

`AgentChatSchemaInitializer` 启动时只创建 `ai_agent_conversation`、`ai_agent_message` 两张新表，不删除原课程、账号、报告或课程答疑数据。消息保留完成、失败和取消状态；模型上下文只选择最近 4 轮完整问答，并按配置的字符预算裁剪。未知 Token 用量保存为 `NULL`。

### 在 Navicat 中查看保存的数据

本机使用 MySQL `127.0.0.1:3306` 的 `learning_platform` 数据库。连接后刷新“表”列表：

| 表 | 保存内容 |
|---|---|
| `ai_agent_conversation` | 所属账号 `user_id`、会话标题、创建时间、最近更新时间 |
| `ai_agent_message` | 所属会话、用户问题和模型回答、生成状态、请求编号、模型名称和用量 |

问题发送时立即写入用户消息，并创建待生成的回答记录；生成完成时保存完整回答，停止时保存已生成的部分内容和取消状态。会话和消息保存在 MySQL 中，退出登录不会删除；重新登录同一账号后可从历史列表打开。

后端会自动建表；如果需要手动初始化其他已有环境，选择项目数据库后执行 [增量建表脚本](../database/agent_chat.sql)。该脚本可以重复执行，需先有项目的 `users` 表。

查询会话与消息（在 Navicat 的查询窗口执行）：

```sql
-- 最近的会话以及所属账号；账号管理仍使用 users 表。
SELECT c.id, c.user_id, u.name, u.role, c.title, c.updated_at
FROM ai_agent_conversation c
JOIN users u ON u.id = c.user_id
ORDER BY c.updated_at DESC, c.id DESC;

-- 查看一段会话，将 1 替换为上面查到的会话 id。
SELECT id, role, content, status, model_code, created_at
FROM ai_agent_message
WHERE conversation_id = 1
ORDER BY id;
```

2026-10-19 检查：本机两张表均已创建，使用 InnoDB、UTF-8 字符集和外键关联；业务库当时为 0 段会话、0 条消息。开始实际对话后才会产生记录，页面不会预填演示聊天。

切换站内页面不会卸载对话组件。刷新或关闭网页会中断当前连接；完成的回答仍保存在数据库，可以继续追问，但不自动续传未完成的生成。后端重启也不会恢复正在生成的请求。

## 验证

```powershell
# 后端服务和鉴权测试
.\mvnw.cmd -f backend/pom.xml '-Dtest=AgentChatServiceTest,AgentChatControllerTest' test

# 前端导航、流式消息、续问、历史恢复、失败重试（模拟接口）
Set-Location frontend
npx playwright test e2e/agent-chat.spec.js --reporter=list

# 真实模型测试：先启动连接独立测试库的后端，避免在业务库创建测试账号。
$env:E2E_BACKEND_URL = 'http://127.0.0.1:8082'
$env:E2E_DB_NAME = 'learning_platform_e2e_full_20261016'
$env:E2E_AGENT_CHAT_FLOW = '1'
npx playwright test e2e/agent-chat-live.spec.js --reporter=list
```

2026-10-19 验证记录：

- 后端打包成功，80 项测试中 79 项通过，1 项真实模型协议测试按默认开关跳过；失败与错误均为 0。
- 前端 Agent 对话、分析切页、学习路径和学生按钮回归共 22 项通过。
- 独立 MySQL 3307、后端 8082 使用打包后的应用与真实模型，通过两轮问答、历史持久化、刷新恢复、跨账号访问拒绝，以及停止生成后保存取消状态的验证。真实测试使用独立库中的合成学生账号。
- 本机 8081 已重启启用新版，`/chat` 返回 200，未登录访问聊天接口返回 401，模型服务仍为 `zhidu-qwen3-4b-lora`。重启前备份保存在忽略提交的 `.runtime/backend/pre-agent-chat-20261019.sql`；原账号、资源、课程、课时和学习记录行数前后一致。

本机当前运行打包后的 JAR。若要在 IDEA 中重新运行，先停止占用 8081 的该项目后端，再运行 `LearningPlatform-LLaMA-Factory`，避免重复启动产生端口冲突。
