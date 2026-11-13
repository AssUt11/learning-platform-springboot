# AI 伴学分析状态保留

更新日期：2026-10-19。

四类分析原先把结果和运行状态保存在 `AgentsPage` 的局部状态中，页面卸载后这些状态丢失。现在由独立的登录会话存储管理请求和结果，页面只订阅状态。

- 点击分析后，在站内切换首页、我的学习或图谱等页面，请求继续执行；返回 AI 学伴会显示实际的运行状态或已完成结果。
- 同一类分析运行期间不重复提交；四类分析分别保存状态和结果。
- 生成的路径信息继续保留“查看我的路径图谱”入口；报告完成后，报告列表重新加载。
- 已完成结果保存在当前浏览器标签页的 `sessionStorage`，刷新可恢复。正在进行的请求仍依赖当前页面运行环境，不支持关闭标签页或刷新后继续接收原请求；不会把旧的运行标记恢复成永久加载状态。
- 退出登录时清除缓存；切换登录会话后，旧请求的迟到结果不会写入新会话。缓存不包含登录令牌。
- 会话存储不可用时，仍可在当前页面运行环境中切页保留结果。

核心实现：`frontend/src/utils/agentAnalysisStore.js`、`frontend/src/utils/useAgentAnalysis.js`。

## 验证

浏览器自动化共 18 项通过，含新增 4 项导航与保存测试、原有 4 项学习路径测试和 10 项学生交互回归。使用模拟接口精确控制响应完成时机，验证请求在离页期间完成时的状态恢复与账号隔离，不调用真实模型或修改业务数据库。

```powershell
cd frontend
npx playwright test e2e/agent-navigation.spec.js e2e/learning-path.spec.js e2e/student-buttons.spec.js --reporter=list
npm run build
```

生产构建同步到 `backend/src/main/resources/static`，用于 IDEA 直接运行后端；同时同步到当前进程的 `backend/target/classes/static`。刷新页面后加载新版前端，无需修改数据库或重启后端。
