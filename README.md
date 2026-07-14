# 智能导学系统 Spring Boot 版本

目录结构：

- `backend`：Java Spring Boot 后端，适合 IDEA 直接打开或作为 Maven 项目导入
- `frontend`：React 前端
- `database/navicat_mysql.sql`：Navicat 可直接执行的 MySQL 脚本

## 启动

1. 在 Navicat 中执行 `database/navicat_mysql.sql`
2. 修改 `backend/src/main/resources/application.yml` 中的 MySQL 用户名和密码
3. IDEA 打开 `D:\project\learning-platform-springboot\backend`
4. 运行 `LearningPlatformApplication`
5. 前端进入 `frontend` 后执行：

```bash
npm install
npm run dev
```

默认地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8081`

也可以通过环境变量覆盖默认配置，例如：

```bash
SERVER_PORT=8081
DB_URL=jdbc:mysql://localhost:3306/learning_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=root
APP_UPLOAD_DIR=D:/project/learning-platform-springboot/backend/uploads
APP_REPORT_DIR=D:/project/learning-platform-springboot/backend/reports
```

## Neo4j 知识图谱

知识图谱接口为 `GET /api/knowledge-graph?subject=Java`。默认使用 MySQL 目录数据兜底；如需连接 Neo4j，修改 `backend/src/main/resources/application.yml`：

```yml
app:
  neo4j:
    enabled: true
    uri: bolt://localhost:7687
    username: neo4j
    password: 你的密码
```
