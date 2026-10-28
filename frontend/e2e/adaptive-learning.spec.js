import { expect, test } from "@playwright/test";

const lesson = { id: 34, title: "事务与一致性", lessonType: "article", assetReady: true, contentText: "事务保证修改的一致性。提交确认修改，回滚放弃修改。本节通过转账理解原子性与一致性。" };
const questions = [
  { stem: "事务的目标是什么？", options: ["保证一致性", "修改端口", "改变文件名", "删除表"] },
  { stem: "提交对应哪一步？", options: ["放弃修改", "确认修改", "建立索引", "修改连接"] }
];

async function student(page, { unavailable = false, generating = false } = {}) {
  let saved = { available: false, generating };
  let loads = 0;
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 7, name: "练习学生", role: "student" }));
    localStorage.setItem("learning_token", "test-token");
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/preferences") data = { subjects: ["数据库"] };
    else if (path === "/api/categories") data = [];
    else if (path === "/api/courses/12") data = { id: 12, title: "数据库原理", outline: { chapters: [{ id: 1, title: "第一章", lessons: [lesson] }] } };
    else if (path === "/api/learning/courses/12/progress") data = { totalLessons: 1, lessons: [] };
    else if (path.endsWith("/latest") && path.startsWith("/api/practice")) {
      loads++;
      if (generating && loads > 1) saved = { available: true, generating: false, quizId: "q1", difficulty: "基础", model: "zhidu-qwen3-4b-lora", questions, submitted: false };
      data = saved;
    } else if (path.endsWith("/generate")) {
      if (unavailable) return route.fulfill({ status: 503, json: { code: 503, message: "无法连接模型服务，请先启动推理服务。" } });
      saved = { available: true, quizId: "q1", difficulty: "基础", model: "zhidu-qwen3-4b-lora", questions, submitted: false }; data = saved;
    } else if (path.endsWith("/submit")) {
      const answers = route.request().postDataJSON().answers;
      saved = { ...saved, submitted: true, score: 50, answers, questions: questions.map((q, i) => ({ ...q, answer: i, explanation: "依据本课时讲义核对概念。" })) }; data = saved;
    } else if (path.startsWith("/api/learning/paths/")) data = {
      available: true, pathId: "p1", title: "数据库学习路线", summary: "依据课时与练习反馈继续学习。", createdAt: "2026-10-20T08:00:00Z", source: "snapshot", neo4jSynced: true,
      nodes: [{ id: "step-1", order: 1, title: lesson.title, goal: "理解事务", completionCriteria: "完成课程和练习", courseId: 12, progressPercent: 100, quizScore: 50, learningState: "优先复习", recommendationReason: "最近一次课时测验低于60分，建议重看讲义。" }], links: [],
      nextAction: { title: lesson.title, courseId: 12, lessonId: 34, reason: "最近一次课时测验低于60分，建议重看讲义。" }
    };
    await route.fulfill({ json: { code: 200, data } });
  });
}

test("课时练习可生成、作答、保存解析，刷新恢复后进入复习建议", async ({ page }) => {
  await student(page); await page.goto("/learn/12/34");
  const panel = page.getByRole("region", { name: "AI 课后练习" });
  await panel.getByRole("button", { name: "生成课时练习" }).click();
  await expect(panel).toContainText("事务的目标是什么");
  await expect(panel).not.toContainText("正确答案");
  await panel.getByLabel("A. 保证一致性", { exact: true }).check();
  await panel.getByLabel("C. 建立索引", { exact: true }).check();
  await panel.getByRole("button", { name: "提交并查看解析" }).click();
  await expect(panel).toContainText("本次自测 50 分 · 已保存");
  await page.reload(); await expect(panel).toContainText("正确答案 B");
  await expect(panel.getByRole("radio").first()).toBeDisabled();
  await page.screenshot({ path: "../.runtime/adaptive-practice-preview.png", fullPage: true });
  await panel.getByRole("button", { name: "查看更新后的学习路径" }).click();
  await expect(page.getByRole("region", { name: "自适应学习建议" })).toContainText("低于60分");
  await expect(page.locator(".personal-path-detail")).toContainText("优先复习");
  await page.getByRole("button", { name: "开始这一步" }).click();
  await expect(page).toHaveURL(/\/learn\/12\/34$/);
});

test("模型不可用时保留学习内容并显示真实错误", async ({ page }) => {
  await student(page, { unavailable: true }); await page.goto("/learn/12/34");
  const panel = page.getByRole("region", { name: "AI 课后练习" });
  await panel.getByRole("button", { name: "生成课时练习" }).click();
  await expect(panel.getByRole("alert")).toContainText("无法连接模型服务");
  await expect(page.locator(".learn-article-copy")).toContainText("回滚放弃修改");
});

test("返回正在生成练习的课时后自动读取完成结果", async ({ page }) => {
  await student(page, { generating: true }); await page.goto("/learn/12/34");
  const panel = page.getByRole("region", { name: "AI 课后练习" });
  await expect(panel.getByRole("button", { name: "正在生成…" })).toBeDisabled();
  await expect(panel).toContainText("事务的目标是什么", { timeout: 10000 });
  await expect(panel.getByRole("button", { name: "重新出题" })).toBeEnabled();
});

test("教师可从作品列表编排章节、添加PPT课时并上传资料", async ({ page }) => {
  const chapters = [];
  let upload = "";
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 8, name: "课程教师", role: "teacher" }));
    localStorage.setItem("learning_token", "test-token");
  });
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/teacher/resources") data = { resources: [{ id: 12, title: "考研数据库", subject: "考研计算机", type: "material", reviewStatus: "pending" }] };
    else if (path === "/api/categories") data = [];
    else if (path === "/api/courses/12/outline") data = { chapters };
    else if (path === "/api/teacher/courses/12/chapters") { chapters.push({ id: 10, title: route.request().postDataJSON().title, lessons: [] }); data = { chapterId: 10 }; }
    else if (path === "/api/teacher/chapters/10/lessons") { chapters[0].lessons.push({ id: 34, ...route.request().postDataJSON(), assets: [] }); data = { lessonId: 34 }; }
    else if (path === "/api/teacher/lessons/34/assets") { upload = route.request().postDataBuffer().toString(); chapters[0].lessons[0].assetReady = true; data = { assetId: 1 }; }
    await route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/teacher");
  await page.getByRole("button", { name: "管理章节与资料" }).click();
  const editor = page.getByRole("region", { name: "课程目录编辑器" });
  await editor.getByLabel("新章节名称", { exact: true }).fill("第一章 数据库事务");
  await editor.getByRole("button", { name: "添加章节", exact: true }).click();
  await expect(editor.getByLabel("章节 1 名称")).toHaveValue("第一章 数据库事务");
  await editor.getByLabel("新课时名称", { exact: true }).fill("事务课件");
  await editor.getByLabel("课时类型", { exact: true }).selectOption("document");
  await editor.getByRole("button", { name: "添加课时", exact: true }).click();
  await editor.getByLabel("资料文件", { exact: true }).setInputFiles({ name: "事务课件.pptx", mimeType: "application/vnd.openxmlformats-officedocument.presentationml.presentation", buffer: Buffer.from("fixture") });
  await editor.getByLabel("文字摘要（用于 AI 答疑和出题）").fill("事务保证数据一致性，提交确认修改，回滚撤销修改。");
  await editor.getByRole("button", { name: "保存资料", exact: true }).click();
  await expect(editor).toContainText("资料已就绪");
  expect(upload).toContain("事务课件.pptx"); expect(upload).toContain('name="assetType"\r\n\r\ndocument');
  await page.screenshot({ path: "../.runtime/teacher-structure-preview.png", fullPage: true });
});
