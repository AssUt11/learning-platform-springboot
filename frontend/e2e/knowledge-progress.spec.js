import { expect, test } from "@playwright/test";

const categories = [{ label: "软件工程", children: [
  { label: "Java", children: [{ label: "集合框架" }, { label: "JDBC" }] },
  { label: "Python", children: [{ label: "集合框架" }, { label: "函数" }] }
] }];
const node = (page, subject, name) => page.locator(`.graph-stage button[data-knowledge-subject="${subject}"][data-knowledge-name="${name}"]`);
const nav = (page) => page.getByRole("navigation", { name: "主导航" });

async function setup(page, { fallback = false, initialSubject = "软件工程", records = [
  { subject: "Java", knowledgeNode: "集合框架", progress: 100 },
  { subject: "Java", knowledgeNode: "JDBC", progress: 45 }
] } = {}) {
  const state = { records, failLoad: false, failLessonSave: false, legacyWrites: [], lessonSaves: [], lessonProgress: 0, lessonCompleted: false };
  await page.addInitScript((subject) => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 51, name: "图谱测试学生", role: "student" }));
    localStorage.setItem("learning_token", "graph-token");
    localStorage.setItem("learning_preferences", JSON.stringify([subject]));
  }, initialSubject);
  const lessons = [{ id: 101, title: "JDBC", lessonType: "article", contentText: "JDBC 学习讲义与示例。" }];
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (!url.pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (url.pathname === "/api/preferences") data = { subjects: [initialSubject] };
    else if (url.pathname === "/api/knowledge/categories/tree") data = { categories };
    else if (url.pathname === "/api/learning/knowledge-progress") {
      if (state.failLoad) return route.fulfill({ status: 503, json: { message: "进度服务暂不可用" } });
      data = request.headers().authorization === "Bearer graph-other" ? [] : state.records;
    } else if (url.pathname === "/api/learning/records") {
      state.legacyWrites.push(request.postDataJSON());
    } else if (url.pathname === "/api/knowledge-graph") {
      const subject = url.searchParams.get("subject");
      const nodes = [], links = [];
      const visit = (item, path = [], parentId = null) => {
        const fullPath = [...path, item.label];
        const id = fullPath.join("/");
        const included = subject === "软件工程" || fullPath.includes(subject);
        if (included) {
          nodes.push({ id, label: item.label, path: fullPath, parentId,
            type: item.label === subject ? "root" : fullPath.length === 2 ? "direction" : "knowledge" });
          if (parentId && item.label !== subject) links.push({ source: parentId, target: id });
        }
        (item.children || []).forEach((child) => visit(child, fullPath, id));
      };
      categories.forEach((item) => visit(item));
      data = { source: fallback ? "mysql" : "neo4j", nodes, links, stats: {} };
    } else if (url.pathname === "/api/courses/7") {
      data = { id: 7, title: "Java 知识点学习", subject: "Java", outline: { chapters: [{ id: 1, title: "第一章", lessons }] } };
    } else if (url.pathname === "/api/learning/courses/7/progress") {
      data = { totalLessons: 1, completedLessons: state.lessonCompleted ? 1 : 0, lessons: [
        { lessonId: 101, completed: state.lessonCompleted, progressPercent: state.lessonProgress }
      ] };
    } else if (url.pathname === "/api/learning/lessons/101/progress") {
      const body = request.postDataJSON();
      state.lessonSaves.push(body);
      if (state.failLessonSave) return route.fulfill({ status: 503, json: { message: "课时进度保存失败" } });
      const percent = body.eventType === "completed" ? 100 : body.eventType === "reading" ? Math.min(99, body.readingProgressPercent || 1) : 0;
      state.lessonProgress = Math.max(state.lessonProgress, percent);
      state.lessonCompleted = state.lessonProgress === 100;
      state.records = [...state.records.filter((record) => record.subject !== "Java" || record.knowledgeNode !== "JDBC"),
        { subject: "Java", knowledgeNode: "JDBC", progress: state.lessonProgress }];
      data = { lessonId: 101, courseId: 7, completed: state.lessonCompleted, progressPercent: state.lessonProgress, lastPositionSeconds: state.lessonCompleted ? 1 : 0 };
    } else if (url.pathname === "/api/auth/login") {
      return route.fulfill({ json: { user: { id: 52, name: "另一学生", role: "student" }, token: "graph-other", needsInterestSelection: false } });
    } else if (url.pathname === "/api/categories") data = [];
    else if (url.pathname === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    await route.fulfill({ json: { code: 200, data } });
  });
  return state;
}

async function selectNode(locator) { await locator.press("Enter"); }

test("完成、学习中与未开始有不同颜色，同名知识点按方向隔离", async ({ page }) => {
  await setup(page);
  await page.goto("/graph?view=catalog");
  await expect(node(page, "Java", "集合框架")).toHaveAttribute("data-learning-status", "completed");
  await expect(node(page, "Java", "集合框架")).toHaveCSS("background-color", "rgb(73, 121, 107)");
  await expect(node(page, "Java", "集合框架").locator(".graph-completion-mark")).toBeVisible();
  await expect(node(page, "Java", "集合框架").locator(".graph-completion-mark")).toHaveCSS("width", "16px");
  await expect(node(page, "Java", "JDBC")).toHaveAttribute("data-learning-status", "in-progress");
  await expect(node(page, "Python", "集合框架")).toHaveAttribute("data-learning-status", "not-started");
  await expect(page.locator(".graph-toolbar-actions")).toContainText("已完成 1/4");
});

test("查看或重复点击节点不开始学习，也没有图谱手动完成入口", async ({ page }) => {
  const state = await setup(page, { records: [] });
  await page.goto("/graph?view=catalog");
  const target = node(page, "Java", "JDBC");
  await selectNode(target);
  await selectNode(target);
  await expect(target).toHaveAttribute("data-learning-status", "not-started");
  await expect(page.locator(".node-detail [role=status]")).toContainText("0%");
  await expect(page.getByRole("button", { name: "标记已完成", exact: true })).toHaveCount(0);
  expect(state.legacyWrites).toEqual([]);
  expect(state.lessonSaves).toEqual([]);
  await nav(page).getByRole("button", { name: "首页", exact: true }).click();
  await nav(page).getByRole("button", { name: "智慧图谱", exact: true }).click();
  await page.getByRole("button", { name: "公共知识图谱", exact: true }).click();
  await expect(target).toHaveAttribute("data-learning-status", "not-started");
  await page.reload();
  await expect(target).toHaveAttribute("data-learning-status", "not-started");
});

test("课时达到100%后自动标绿，刷新和切页后保留", async ({ page }, testInfo) => {
  await setup(page, { initialSubject: "Java", records: [] });
  await page.goto("/learn/7/101");
  await page.getByRole("button", { name: "完成本课", exact: true }).click();
  await expect(page.getByRole("button", { name: "本课时已完成", exact: true })).toBeDisabled();
  await nav(page).getByRole("button", { name: "智慧图谱", exact: true }).click();
  await page.getByRole("button", { name: "公共知识图谱", exact: true }).click();
  await expect(node(page, "Java", "JDBC")).toHaveAttribute("data-learning-status", "completed");
  await expect(node(page, "Java", "集合框架")).toHaveAttribute("data-learning-status", "not-started");
  await expect(page.locator(".graph-toolbar-actions")).toContainText("已完成 1/2");
  await page.reload();
  await expect(node(page, "Java", "JDBC")).toHaveAttribute("data-learning-status", "completed");
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: testInfo.outputPath("hookers-green-knowledge.png"), fullPage: true });
});

test("Neo4j离线使用备用图谱时，完成颜色仍可保存和恢复", async ({ page }) => {
  await setup(page, { fallback: true, initialSubject: "Java" });
  await page.goto("/graph?view=catalog");
  await expect(page.locator(".graph-source-status")).toContainText("备用数据");
  await expect(node(page, "Java", "集合框架")).toHaveAttribute("data-learning-status", "completed");
  await page.reload();
  await expect(node(page, "Java", "集合框架")).toHaveAttribute("data-learning-status", "completed");
  await expect(node(page, "Java", "JDBC")).toHaveAttribute("data-learning-status", "in-progress");
  await expect(page.locator(".graph-toolbar-actions")).toContainText("已完成 1/2");
});

test("仅打开课时仍未开始，产生阅读活动后才显示学习中", async ({ page }) => {
  const state = await setup(page, { initialSubject: "Java", records: [] });
  await page.goto("/learn/7/101");
  await expect(page.locator(".learn-article-copy")).toBeVisible();
  expect(state.lessonSaves).toEqual([]);
  await nav(page).getByRole("button", { name: "智慧图谱", exact: true }).click();
  await page.getByRole("button", { name: "公共知识图谱", exact: true }).click();
  await expect(node(page, "Java", "JDBC")).toHaveAttribute("data-learning-status", "not-started");
  await page.goto("/learn/7/101");
  await page.locator(".learn-article-copy").hover();
  await page.mouse.wheel(0, 80);
  await expect.poll(() => state.lessonProgress).toBeGreaterThan(0);
  expect(state.lessonCompleted).toBe(false);
  await nav(page).getByRole("button", { name: "智慧图谱", exact: true }).click();
  await page.getByRole("button", { name: "公共知识图谱", exact: true }).click();
  await expect(node(page, "Java", "JDBC")).toHaveAttribute("data-learning-status", "in-progress");
  await expect(page.locator(".graph-toolbar-actions")).toContainText("已完成 0/2");
});

test("99%仍是学习中，学习保存失败也不会提前标绿", async ({ page }) => {
  const state = await setup(page, { initialSubject: "Java", records: [{ subject: "Java", knowledgeNode: "JDBC", progress: 99 }] });
  state.lessonProgress = 99;
  state.failLessonSave = true;
  await page.goto("/learn/7/101");
  await page.getByRole("button", { name: "完成本课", exact: true }).click();
  await expect(page.locator(".learn-notice")).toContainText("课时进度保存失败");
  await nav(page).getByRole("button", { name: "智慧图谱", exact: true }).click();
  await page.getByRole("button", { name: "公共知识图谱", exact: true }).click();
  await expect(node(page, "Java", "JDBC")).toHaveAttribute("data-learning-status", "in-progress");
  await expect(page.locator(".graph-toolbar-actions")).toContainText("已完成 0/2");
});

test("切换账号不会显示前一个账号的完成颜色", async ({ page }) => {
  await setup(page);
  await page.goto("/graph?view=catalog");
  await expect(node(page, "Java", "集合框架")).toHaveAttribute("data-learning-status", "completed");
  await page.getByRole("button", { name: /图谱测试学生/ }).click();
  await page.getByRole("menuitem", { name: /退出登录/ }).click();
  await page.getByPlaceholder("name@example.com").fill("other@example.com");
  await page.getByPlaceholder("请输入密码").fill("Test-password-2026");
  await page.getByRole("button", { name: "登录系统", exact: true }).click();
  await nav(page).getByRole("button", { name: "智慧图谱", exact: true }).click();
  await page.getByRole("button", { name: "公共知识图谱", exact: true }).click();
  await expect(node(page, "Java", "集合框架")).toHaveAttribute("data-learning-status", "not-started");
});

test("进度加载失败显示重试提示，恢复后显示真实完成色", async ({ page }) => {
  const state = await setup(page);
  state.failLoad = true;
  await page.goto("/graph?view=catalog");
  await expect(page.getByRole("alert")).toContainText("进度服务暂不可用");
  await expect(page.locator(".graph-toolbar-actions")).toContainText("进度暂不可用");
  await selectNode(node(page, "Java", "JDBC"));
  await expect(page.getByRole("alert")).toContainText("进度服务暂不可用");
  state.failLoad = false;
  await page.getByRole("button", { name: "重新加载进度", exact: true }).click();
  await expect(node(page, "Java", "集合框架")).toHaveAttribute("data-learning-status", "completed");
});
