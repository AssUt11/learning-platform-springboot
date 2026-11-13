import { expect, test } from "@playwright/test";

const lesson = { id: 34, title: "索引查询", lessonType: "document", assetReady: true, contentText: "联合索引遵循最左前缀原则。", assets: [{ id: 91, assetType: "document", fileName: "索引讲义.pdf", contentText: "", transcodingStatus: "ready" }] };
const course = { id: 12, title: "数据库原理", subject: "数据库", type: "material", reviewStatus: "approved", outline: { chapters: [{ id: 7, title: "索引", lessons: [lesson] }] } };
const document = { documentId: "doc-preview", status: "READY", versionHash: "file-version-1", supported: true, fileName: "索引讲义.pdf", segmentCount: 1, characterCount: 31, segments: [{ id: 201, locationType: "PAGE", locationIndex: 2, text: "联合索引先匹配左侧列，查询条件和列顺序共同决定能使用的前缀。" }] };
const citation = { sourceType: "COURSEWARE_CHUNK", sourceId: "201", chunkId: 201, courseId: 12, lessonId: 34, title: "索引讲义.pdf · 第 2 页", fileName: "索引讲义.pdf", locationType: "PAGE", locationIndex: 2, versionHash: "file-version-1", excerpt: document.segments[0].text, url: "https://not-a-trusted-source.invalid/201", scope: "retrieved_context" };

async function setup(page, { role = "teacher", staleSource = false, unsupported = false } = {}) {
  let state = null;
  let pollCount = 0;
  const actions = [];
  await page.addInitScript(role => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 73, name: "课件验证", role }));
    localStorage.setItem("learning_token", "courseware-test-token");
  }, role);
  await page.route("**/api/**", async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/preferences") data = { subjects: ["数据库"] };
    else if (path === "/api/learning/profile") data = { needsPlanning: false };
    else if (path === "/api/categories") data = [];
    else if (path === "/api/teacher/resources") data = { resources: [course], total: 1 };
    else if (path === "/api/courses/12/outline") data = unsupported ? { ...course.outline, chapters: [{ ...course.outline.chapters[0], lessons: [{ ...lesson, assets: [{ ...lesson.assets[0], fileName: "旧讲义.doc" }] }] }] } : course.outline;
    else if (path === "/api/courses/12") data = course;
    else if (path === "/api/learning/courses/12/progress") data = { lessons: [], totalLessons: 1, progressPercent: 0 };
    else if (path === "/api/teacher/lessons/34/assets/91/courseware/extract") {
      actions.push({ path, method: request.method() }); state = "QUEUED"; data = { ...document, segments: [], status: state };
    } else if (path === "/api/teacher/lessons/34/assets/91/courseware/doc-preview/confirm") {
      actions.push({ path, body: request.postDataJSON() }); state = "CONFIRMED"; data = { ...document, status: state };
    } else if (path === "/api/teacher/lessons/34/assets/91/courseware") {
      if (state === "QUEUED" && ++pollCount > 1) state = "READY";
      data = unsupported ? { status: "NOT_STARTED", supported: false, segments: [], fileName: "旧讲义.doc" } : state ? { ...document, status: state, segments: state === "QUEUED" ? [] : document.segments } : { status: "NOT_STARTED", supported: true, segments: [] };
    } else if (path === "/api/ai/tutor/chat/stream") {
      const events = [
        ["conversation.created", { conversationId: 1, requestId: "source-request" }],
        ["message.started", { requestId: "source-request", knowledgeSource: "mysql+courseware" }],
        ["message.delta", { content: "联合索引通常从左侧列开始匹配条件。" }],
        ["citation.added", citation], ["citation.added", citation],
        ["message.completed", { messageId: 19 }]
      ];
      return route.fulfill({ contentType: "text/event-stream; charset=utf-8", body: events.map(([name, data]) => `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`).join("") });
    } else if (path === "/api/learning/courseware/chunks/201") {
      actions.push({ path, query: Object.fromEntries(url.searchParams), authorization: request.headers().authorization });
      if (staleSource) return route.fulfill({ status: 410, json: { code: 410, message: "资料已更新，请重新提问以获取当前版本的来源。" } });
      data = { ...citation, text: document.segments[0].text };
    }
    return route.fulfill({ json: { code: 200, data } });
  });
  return actions;
}

async function openTeacherAsset(page) {
  await page.goto("/teacher");
  await page.getByRole("button", { name: "管理章节与资料", exact: true }).click();
  const editor = page.getByRole("region", { name: "课程目录编辑器" });
  await editor.locator(".structure-asset > summary").click();
  await editor.locator(".courseware-asset-panel > summary").click();
  return editor.locator(".courseware-asset-panel");
}

test("教师解析在后台排队，预览确认后才可用于答疑，刷新可恢复状态", async ({ page }) => {
  const actions = await setup(page);
  let panel = await openTeacherAsset(page);
  await panel.getByRole("button", { name: "开始解析", exact: true }).click();
  await expect(panel).toContainText("离开页面后任务仍会继续");
  await expect(panel.getByRole("button", { name: "确认并用于 AI 答疑", exact: true })).toBeVisible();
  await expect(panel.getByLabel("解析内容预览")).toContainText("左侧列");
  await panel.getByRole("button", { name: "确认并用于 AI 答疑", exact: true }).click();
  await expect(panel).toContainText("已确认。资料仍遵循原有课程发布与审核权限。");
  expect(actions[1].body).toEqual({ versionHash: "file-version-1" });
  await page.reload(); panel = await openTeacherAsset(page);
  await expect(panel).toContainText("已确认");
  expect(actions).toHaveLength(2);
});

test("旧版文档说明支持范围，不把未解析资料显示成成功", async ({ page }) => {
  await setup(page, { unsupported: true });
  const panel = await openTeacherAsset(page);
  await expect(panel).toContainText("旧版 DOC/PPT 请先另存为新格式");
  await expect(panel.getByRole("button", { name: "开始解析", exact: true })).toHaveCount(0);
  await expect(panel.getByRole("button", { name: "确认并用于 AI 答疑", exact: true })).toHaveCount(0);
});

for (const staleSource of [false, true]) test(staleSource ? "过期资料引用提示重新获取，不静默展示历史片段" : "回答来源可展开并带当前登录凭据读取原文，忽略外部URL", async ({ page }) => {
  const actions = await setup(page, { role: "student", staleSource });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/learn/12/34");
  const tutor = page.getByRole("region", { name: "AI 伴学", exact: true });
  await tutor.getByPlaceholder("针对当前课时提问，Enter 发送，Shift+Enter 换行").fill("什么是最左前缀？");
  await tutor.getByRole("button", { name: "发送", exact: true }).click();
  await expect(tutor.locator(".courseware-sources li")).toHaveCount(1);
  await tutor.locator(".courseware-sources summary").click();
  await tutor.getByRole("button", { name: "查看参考原文", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "参考原文", exact: true });
  if (staleSource) await expect(dialog.getByRole("alert")).toContainText("资料已更新");
  else await expect(dialog.locator(".courseware-source-text")).toContainText("左侧列");
  expect(actions[0]).toEqual({ path: "/api/learning/courseware/chunks/201", query: { courseId: "12", lessonId: "34" }, authorization: "Bearer courseware-test-token" });
  const bounds = await dialog.boundingBox();
  expect(bounds.x).toBeGreaterThanOrEqual(0);
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390);
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
});
