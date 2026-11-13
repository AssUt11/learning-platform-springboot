# 课程扩充与 Neo4j 同步

## 新增 30 门基础课程

考研 10 门、技术方向 20 门，每门 1 章、2 节，共 30 章、60 节原创图文课。每节包含知识讲解、例题或案例、自测和参考答案。预计时长包含阅读与练习，内容定位为基础导学，不代表完整考试大纲或完整职业培训。

| 方向 | 新增课程 |
|---|---|
| 考研数学（3） | 高等数学：极限、导数与积分；线性代数：矩阵与方程组；概率统计：条件概率与随机变量 |
| 考研英语（2） | 阅读：句子结构与证据定位；写作：段落组织与表达修改 |
| 考研政治（1） | 实践认识与矛盾分析 |
| 计算机408（4） | 数据结构；计算机组成原理；操作系统；计算机网络 |
| 软件工程（5） | Java 并发；Python 自动化；TypeScript 接口；SQL 分析；接口测试 |
| 人工智能（4） | 回归与数据泄漏；神经网络与梯度；文本表示与分类；卷积与图像分类 |
| 大数据（4） | HDFS 可靠性；Spark 分区与 Shuffle；事实与维度建模；图表选择与误导检查 |
| 云计算（3） | Linux 权限与进程；Docker 配置与持久化；Kubernetes 工作负载与探针 |
| 网络安全（2） | 身份认证与资源授权；哈希、加密与 TLS |
| 移动与物联网（2） | MQTT 主题与可靠性；嵌入式 C 位操作与中断 |

在 [首页](http://localhost:8081/home) 点击“考研备考”标签可筛选 10 门考研课。分类树新增“考研备考”，下分考研数学、考研英语、考研政治、计算机408。

## 导入与数据保存

- 内容源：`backend/src/main/resources/catalog/foundation-courses.json`。
- `FoundationCourseInitializerService` 在原表结构就绪后导入：先创建待发布课程、章节、课时和正文，经过现有课程完整性校验后发布。
- `catalog_content_seeds` 记录版本化导入标记；重复启动不重复创建，不覆盖教师后续修改。删除课程时保留空资源标记，避免重启后再次出现。
- 本机唯一教师为 id 14，新增课程归属此账号。多个教师的环境须设置 `CATALOG_SEED_TEACHER_ID` 为指定教师 id 后重启；未指定且教师不唯一时跳过导入。新环境需先有教师账号。
- MySQL 继续保存课程、课时正文和学生进度，阅读与完成课时沿用原业务接口。
- 新表可由后端自动创建，也可执行 [增量建表脚本](../database/catalog_content_v1.sql)。

## Neo4j 同步

`CatalogGraphSyncService` 在启动完成后同步一次，每 30 秒检查一次课程快照。内容未变化则跳过；课程内容、发布状态变化或前次失败时执行同步。`catalog_graph_sync_state` 保存状态、课程数、课时数、最后成功时间和错误提示。MySQL 和 Neo4j 使用最终一致的同步方式，图谱失败不会回滚业务课程。

| 节点/关系 | 内容 |
|---|---|
| `KnowledgeNode` | 分类、方向、知识点；复用原图谱键，追加数据库的新分类和课时主题 |
| `Course {mysqlId}` | 内部课程 id；`publicCourseId` 对应页面资源 id，保存标题、学科、简介和 `published` 状态 |
| `Chapter {mysqlId}` | 章节、顺序、状态 |
| `Lesson {mysqlId}` | 课时、正文、类型、时长、状态 |
| `CONTAINS` / `HAS_COURSE` | 分类层级，以及方向与课程的关联 |
| `HAS_CHAPTER` / `HAS_LESSON` | 课程、章节与课时结构 |
| `COVERS` / `TEACHES` | 课程、课时与知识点的关联 |

同步在一个 Neo4j 事务中执行，通过唯一键和 `MERGE` 防止重复。只重建标为 `catalog-sync` 管理的关系，不清空图库，不删除个人路径、先修关系或用户注释。已下架课程保留节点并标记 `published=false`；查询可学习课程时应过滤此属性。

管理员接口均需管理员令牌：

- `GET /api/admin/catalog/graph/status`：查询同步状态。
- `POST /api/admin/catalog/graph/sync`：强制同步，可用于图库恢复后重建当前课程关系。

本次恢复运行的是原 Neo4j Desktop 实例 `dbms-f0f77adb-9479-4671-b8d9-06e94431552b`，Bolt 地址为 `127.0.0.1:7687`，入口为 [Neo4j Browser](http://127.0.0.1:7474)。沿用现有账号设置，未重新初始化实例或重置密码。

在 Neo4j Browser 查看课程结构：

```cypher
MATCH (d:KnowledgeNode)-[:HAS_COURSE]->(c:Course)-[:HAS_CHAPTER]->(ch:Chapter)-[:HAS_LESSON]->(l:Lesson)
WHERE c.source = 'learning-platform' AND c.published = true
RETURN d,c,ch,l LIMIT 120;

MATCH (c:Course)-[:COVERS]->(k:KnowledgeNode)
WHERE c.source = 'learning-platform' AND c.subject IN ['考研数学','考研英语','考研政治','计算机408']
RETURN c,k;
```

## 2026-10-19 验证

- 后端 83 项测试：82 项通过，1 项可选真实模型协议测试默认跳过，无失败、无错误。
- 新增课程真实接口与浏览器测试通过，覆盖 30 门课的课时可用性、考研筛选、正文阅读、课时完成和刷新恢复。
- 独立后端重启后仍为 30 门、30 章、60 节、60 份正文，重复标题为 0。
- 22 项页面回归全部通过。
- 本机业务库增加 30 门课程；原有 20 条资源的标题、学科、教师、发布审核状态保持一致。
- 原 Neo4j 实例同步 50 个课程、51 个有效章节、82 个有效课时；新增 30 门课程关联 60 个知识点。考研图谱接口返回 `source=neo4j`。
- 将同步状态置为失败后，定时任务自动恢复为 `synced`；再次查询仍为 50 个课程、50 个唯一课程编号，无重复节点。
- 更新前快照在忽略提交的 `.runtime/backend/pre-catalog-20261019.sql`、`.runtime/backend/pre-catalog-neo4j-20261019.json`。

本机后端从 `.runtime/backend/learning-platform-catalog-20261019.jar` 独立运行副本启动，避免 Windows 锁定构建文件。8081 已运行；如要在 IDEA 再次启动，先停止该项目旧后端。

重跑真实课程测试：先启动使用独立测试库、已导入课程包的后端，再执行：

```powershell
Set-Location frontend
$env:E2E_BASE_URL = 'http://127.0.0.1:8082'
$env:E2E_BACKEND_URL = 'http://127.0.0.1:8082'
$env:E2E_DB_NAME = 'learning_platform_e2e_full_20261016'
$env:E2E_CATALOG_FLOW = '1'
npx playwright test e2e/catalog-foundation-live.spec.js --reporter=list
```
