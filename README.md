# 知度学习：知识图谱课程学习平台

知度学习围绕“课程分类 → 课程学习 → 智慧图谱 → 社区反馈 → 学习分析”构建学习闭环。系统包含学生端、教师端和管理员端，保留 React + Vite、Spring Boot 2.7、Java 17、JdbcTemplate、MySQL 和 Neo4j 技术栈，并提供可直接构建发布的 Docker Compose 编排。

当前迭代版本：**v1.1.0-rc.1**（候选版，PR #1 待合并）。[版本发布页](https://github.com/AssUt11/learning-platform-springboot/releases)提供版本标签、说明和源码下载。

## 目录结构

项目版本演进见 [迭代记录](CHANGELOG.md)，每轮更新的详细差异保留在 GitHub 提交与 PR 中。

- `backend`：Spring Boot 后端
- `frontend/src/api`：分类、课程、图谱、学习和分析接口模块
- `frontend/src/components`：导航、搜索、分类和课程卡片组件
- `frontend/src/utils/request.js`：Axios 统一请求、Token 和错误处理
- `database/navicat_mysql.sql`：基础 MySQL 初始化脚本；后端启动时补齐新增表
- `database/catalog_phase1.sql`：第一阶段分类、方向增量脚本
- `database/course_community.sql`：课程问答、笔记、评价、教师回复和审核治理增量脚本
- `database/agent_chat.sql`：Agent 对话会话、消息的增量建表脚本；后端启动时也会自动创建
- [课程扩充与 Neo4j 同步](docs/课程扩充与Neo4j同步.md)：考研 10 门、技术方向 20 门基础图文课，课程图谱同步与验证
- [个人学习路径图谱与启动排查](docs/个人学习路径图谱与启动排查-2026-09-20.md)：路径保存、图谱同步、启动排查与验证范围
- [AI 伴学实施文档](docs/langchain4j/README.md)：LangChain4j 迁移方案和待执行的阶段任务；当前运行实现支持 Ollama / LLaMA-Factory API
- [LLaMA-Factory 微调模型接入](docs/LLaMA-Factory微调模型接入.md)：基础模型与 LoRA 加载、本机启动、后端切换和验证说明
- [Agent 对话功能](docs/Agent对话功能.md)：独立微调模型聊天、多轮追问、会话历史、接口与验证方式
- [课程编排与自适应学习](docs/课程编排与自适应学习.md)：教师章节与资料编辑、AI 课时练习、成绩保存和动态学习路径建议
- [学习规划对话与课程阶段](docs/学习规划对话与课程阶段.md)：首页前五问选项引导、路径预览与采用、三阶段课程和图谱分类
- [界面色阶与生成展示](docs/界面色阶与生成展示.md)：微对比背景、水墨打字效果、稳定状态框与消息滚动交互
- [全链路联调记录](docs/全链路联调记录-2026-10-16.md)：隔离 MySQL、Neo4j 与真实微调模型的业务验证结果

## 三端能力

- 学生端：通过五问学习档案预览并采用个人路径，按基础、中期、进阶筛选课程；使用课时答疑、AI自测、问答、私人笔记、评价和学习分析。个人路径展示最新学习与测验反馈，知识图谱按真实课时进度显示学习状态。
- 教师端：创建课程、编排章节课时、上传MP4视频或PPT等文档、设置课程阶段，查看审核反馈，回复学生评价和问题，查看课程播放、完成率、收藏、薄弱知识点和学生预警。
- 管理员端：审核教师投稿、控制课程上下架、治理评论与问答、查看运行健康状态、执行安全的数据修复并保留操作审计记录。

课程卡片会进入独立的 `/course/{id}` 学习页；只有 `review_status=approved` 且已上架的课程会显示在学生端。

学生和教师顶部导航的“消息”后提供“Agent 对话”，可直接与 LLaMA-Factory 微调模型连续聊天，历史按账号保存。需要启用 `llamafactory` 启动配置，并保持模型 API 运行。

## 本地启动

需要 Java 17、Maven 3.9、Node.js 18.19+（含 npm）和 MySQL 8。AI 另需 Ollama 或 LLaMA-Factory 模型服务，实时图谱另需 Neo4j。克隆项目后先在 `frontend` 目录执行 `npm ci` 安装锁定版本的依赖。

1. 创建空的 `learning_platform` 数据库；后端启动时会自动增量建表和补齐数据。
2. 检查 `backend/src/main/resources/application.yml` 中的 MySQL 和 Neo4j 配置。
3. 使用 IDEA 运行 `LearningPlatformApplication`。
4. 在 `frontend` 目录执行：

```bash
npm ci
npm run dev
```

在 Windows CMD 中切换盘符时必须使用 `cd /d`：

```bat
cd /d D:\project\learning-platform-springboot\frontend
npm run dev
```

`database/navicat_mysql.sql` 会先删除现有业务表，只能用于全新的、可丢弃的数据库。
已有数据库不要重复执行；操作前务必备份。

默认地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8081`

应用启动时 `SchemaInitializer` 会增量创建 `categories`、`directions` 表，并为现有课程资源补齐分类、方向、预计时长和学习人数字段，不会删除已有学习记录。

后端默认监听 8081；出现 `Port 8081 was already in use` 时，先停止已有后端实例再启动。手动运行 Spring Boot 时需在终端或 IDEA 中设置环境变量，根目录 `.env` 不会被自动读取。

## 构建与打包

在项目根目录执行（已完成 `frontend` 的 `npm ci`）：

```powershell
mvn -f backend/pom.xml clean package
java -jar backend/target/learning-platform-1.1.0-rc.1.jar
```

Maven 会执行后端测试、构建前端并将页面嵌入 JAR，打包后可直接访问 `http://localhost:8081`。根目录 `mvnw.cmd` 是本机 `.tools` 目录的辅助启动脚本，不是标准 Maven Wrapper；其他电脑请使用已安装的 `mvn`。

## 第一阶段接口

- `GET /api/categories`：启用的大类、方向、课程数、学习人数和完成率
- `GET /api/categories/{categoryId}/directions`：大类下的方向
- `GET /api/courses?pageNum=1&pageSize=12`：分页课程列表
- `GET /api/courses/{courseId}`：课程详情和知识点

新接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 环境变量

```text
SERVER_PORT=8081
DB_URL=jdbc:mysql://localhost:3306/learning_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=root
AUTH_TOKEN_SECRET=请替换为至少32字节的随机密钥
AUTH_TOKEN_TTL_SECONDS=86400
TEACHER_REGISTRATION_CODE=请替换为教师注册码
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=请替换为至少8位的管理员密码
NEO4J_ENABLED=true
NEO4J_URI=neo4j://127.0.0.1:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=12345678
```

生产环境必须设置固定的 `AUTH_TOKEN_SECRET`、自定义的
`TEACHER_REGISTRATION_CODE` 和强度足够的 `ADMIN_PASSWORD`。开发环境未设置密钥时会在每次后端启动时生成新密钥，
因此重启后原登录状态会失效，需要重新登录。

## Neo4j 知识图谱

知识图谱接口：

```text
GET /api/knowledge-graph?subject=人工智能&path=人工智能
```

Neo4j 不可用时页面会显示备用分类图谱；连接恢复后刷新页面即可重新载入实时节点关系。

个人路径流程：进入“AI 学伴” → “学习路径分析” → “查看我的路径图谱”。直接打开“智慧图谱”会显示当前学生最新保存的路径，也可切换到公共知识图谱。Neo4j 离线时路径保存在 MySQL，页面提供重试同步入口。

生成路径前配置模型服务和正确的 `NEO4J_*` 环境变量后重启后端。Ollama 模式需安装模型并设置 `AI_TUTOR_ENABLED=true`、`OLLAMA_BASE_URL`、`OLLAMA_MODEL`，默认模型为 `qwen3:8b-q4_K_M`；使用本机微调模型请按 [LLaMA-Factory 接入说明](docs/LLaMA-Factory微调模型接入.md)启动。真实模型输出质量和真实 Neo4j 写入仍需在对应服务启用后验收，已有测试覆盖结构校验、离线保存和用户隔离。

## Docker Compose

Windows 已安装 Docker Desktop 时，可在项目根目录执行：

```bat
set AUTH_TOKEN_SECRET=请替换为至少32字节的随机密钥
set TEACHER_REGISTRATION_CODE=请替换为自定义教师注册码
set ADMIN_EMAIL=admin@example.com
set ADMIN_PASSWORD=请替换为至少8位的管理员密码
docker compose up --build -d
```

鉴权密钥、教师注册码和管理员密码未设置时，Compose 会拒绝启动，避免使用公开默认值。
该编排会启动 MySQL 8、Neo4j 5、Spring Boot 和 Nginx 前端。默认访问 `http://localhost`，数据分别保存到具名卷中。正式部署前先复制 `.env.example` 为 `.env` 并替换全部占位值。

## 自动化端到端测试

无需后端的页面回归测试，在 `frontend` 目录执行（需要 Microsoft Edge）：

```powershell
npx --no-install playwright test e2e/student-buttons.spec.js e2e/learning-path.spec.js e2e/teacher-edit.spec.js
```

真实接口测试需要独立测试数据库及连接该数据库的后端，通过 `E2E_BACKEND_URL` 指定后端地址。在 `frontend` 目录执行：

```powershell
npm run test:e2e
```

测试会用 Microsoft Edge 依次验证学生、教师、管理员流程。学生和教师测试账号使用 `e2e-` 前缀，并在每个用例结束后精确清理。请在终端设置 `.env.example` 中的 `E2E_*` 变量，确保清理数据库与被测后端使用同一个独立数据库。真实学习流程要求数据库名以 `learning_platform_e2e_` 开头；路径联调用例另需 `E2E_PATH_FLOW=1` 和提供两步路径的专用模型测试服务，未配置时跳过。不要对业务数据库运行测试。

仓库排除了依赖目录、构建输出、运行日志、上传文件、备份及本地环境配置；`backend/src/main/resources/static` 保留随源码交付的页面，供 IDEA 直接启动使用。
