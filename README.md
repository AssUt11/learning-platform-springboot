# 知度学习：知识图谱课程学习平台

知度学习围绕“课程分类 → 课程学习 → 智慧图谱 → 社区反馈 → 学习分析”构建学习闭环。系统包含学生端、教师端和管理员端，保留 React + Vite、Spring Boot 2.7、Java 17、JdbcTemplate、MySQL 和 Neo4j 技术栈，并提供可直接构建发布的 Docker Compose 编排。

## 目录结构

- `backend`：Spring Boot 后端
- `frontend/src/api`：分类、课程、图谱、学习和分析接口模块
- `frontend/src/components`：导航、搜索、分类和课程卡片组件
- `frontend/src/utils/request.js`：Axios 统一请求、Token 和错误处理
- `database/navicat_mysql.sql`：完整 MySQL 初始化脚本
- `database/catalog_phase1.sql`：第一阶段分类、方向增量脚本
- `database/course_community.sql`：课程问答、笔记、评价、教师回复和审核治理增量脚本
- `docs/学校服务器部署说明.md`：正式服务器部署、更新和回滚
- `docs/运维手册.md`：日常检查、备份、故障与安全操作
- `docs/最终验收记录.md`：最终版验证结果与学校确认项
- `docs/备份恢复演练记录.md`：数据库与视频文件恢复验证证据

## 三端能力

- 学生端：浏览与学习已审核课程，使用章节、问答、私人笔记、评价、课程知识图谱、学习进度和 AI 学伴。
- 教师端：上传视频或文字课程，查看投稿审核状态与管理员反馈，回复学生评价和问题，查看课程播放、完成率、收藏、薄弱知识点和学生预警。
- 管理员端：审核教师投稿、控制课程上下架、治理评论与问答、查看运行健康状态、执行安全的数据修复并保留操作审计记录。

课程卡片会进入独立的 `/course/{id}` 学习页；只有 `review_status=approved` 且已上架的课程会显示在学生端。

## 本地启动

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

后端、MySQL 和 Neo4j 启动后，在 `frontend` 目录执行：

```powershell
npm run test:e2e
```

测试会用 Microsoft Edge 依次验证学生、教师、管理员流程。学生和教师测试账号使用 `e2e-` 前缀，并在每个用例结束后精确清理。正式测试环境请通过 `.env.example` 中的 `E2E_*` 变量提供测试凭据。
