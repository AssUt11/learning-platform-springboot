import { expect, test } from "@playwright/test";

const courses = Array.from({ length: 13 }, (_, index) => ({
  id: index + 1, title: `测试课程 ${index + 1}`, subject: "Java", type: "material", teacher: "测试教师"
}));

async function openStudentHome(page, { empty = false } = {}) {
  const requests = [];
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 1, name: "按钮测试学生", role: "student" }));
    localStorage.setItem("learning_token", "test-token");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (url.pathname === "/api/courses") {
      requests.push(url.searchParams);
      const keyword = url.searchParams.get("keyword") || "";
      const pageNum = Number(url.searchParams.get("pageNum") || 1);
      const pageSize = Number(url.searchParams.get("pageSize") || 12);
      const list = empty ? [] : keyword ? courses.slice(0, 1) : courses;
      data = { list: list.slice((pageNum - 1) * pageSize, pageNum * pageSize), pageNum, pageSize, total: list.length, totalPages: Math.max(1, Math.ceil(list.length / pageSize)) };
    } else if (url.pathname === "/api/preferences") {
      data = { subjects: ["软件工程"] };
    } else if (url.pathname === "/api/categories") {
      data = [{ id: 1, name: "软件工程", directions: [{ id: 11, name: "Java" }] }];
    } else if (/^\/api\/courses\/\d+$/.test(url.pathname)) {
      data = { ...courses[0], outline: { chapters: [{ id: 1, title: "第一章", lessons: [{ id: 1, title: "第一课", lessonType: "article", contentText: "课程正文" }] }] } };
    }
    await route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/home");
  await expect(page.getByRole("heading", { name: /让知识形成结构/ })).toBeVisible();
  await expect.poll(() => requests.length).toBeGreaterThan(0);
  return requests;
}

test("本周精选恢复全部课程，连续点击仍可重新加载", async ({ page }) => {
  const requests = await openStudentHome(page);
  const tabs = page.locator(".studio-course-tabs");
  await tabs.getByRole("button", { name: "Java", exact: true }).click();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(1);
  await tabs.getByRole("button", { name: "本周精选" }).click();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(12);
  const before = requests.length;
  await tabs.getByRole("button", { name: "本周精选" }).click();
  await expect.poll(() => requests.length).toBeGreaterThan(before);
  await expect(tabs.getByRole("button", { name: "本周精选" })).toHaveClass(/active/);
});

test("分页显示接口返回的每门课程，没有第9至12门的缺口", async ({ page }) => {
  await openStudentHome(page);
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(12);
  await page.getByRole("button", { name: "下一页", exact: true }).click();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(1);
  await expect(page.locator(".studio-course-grid")).toContainText("测试课程 13");
  await page.getByRole("button", { name: "上一页", exact: true }).click();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(12);
});

test("首页图谱方向按钮打开对应方向", async ({ page }) => {
  await openStudentHome(page);
  await page.locator(".studio-map-stage").getByRole("button", { name: "Java", exact: true }).click();
  await expect(page).toHaveURL(/\/graph\?view=catalog$/);
  await expect(page.locator(".graph-toolbar-title h2")).toHaveText("Java");
});

test("知识点筛选会传递所选知识点", async ({ page }) => {
  const requests = await openStudentHome(page);
  await page.getByText("精确筛选课程", { exact: true }).click();
  await page.getByLabel("课程大类", { exact: true }).selectOption("软件工程");
  await page.getByLabel("学习方向", { exact: true }).selectOption("Java");
  await page.getByLabel("知识点", { exact: true }).selectOption("面向对象");
  await page.getByRole("button", { name: "确认筛选" }).click();
  await expect.poll(() => requests.at(-1)?.get("keyword")).toBe("面向对象");
  expect(requests.at(-1).get("directionId")).toBe("11");
});

test("空课程列表不会展示无法打开的示例课程", async ({ page }) => {
  await openStudentHome(page, { empty: true });
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(0);
  await expect(page.getByText(/暂无匹配课程/)).toBeVisible();
});

test("从其他页面返回首页时，列表与筛选状态一致", async ({ page }) => {
  await openStudentHome(page);
  await page.locator(".studio-course-tabs").getByRole("button", { name: "Java", exact: true }).click();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(1);
  await page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: "我的学习" }).click();
  await expect(page.getByRole("heading", { name: "学习进度与个人记录" })).toBeVisible();
  await page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: "首页", exact: true }).click();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(12);
});

test("翻页失败显示错误，点击重试后恢复", async ({ page }) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await openStudentHome(page);
  await page.route("**/api/courses?**", (route) => route.fulfill({ status: 503, json: { message: "测试服务暂不可用" } }), { times: 1 });
  await page.getByRole("button", { name: "下一页", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("测试服务暂不可用");
  await page.getByRole("button", { name: "重新加载", exact: true }).click();
  await expect(page.locator(".studio-course-grid")).toContainText("测试课程 13");
  expect(errors).toEqual([]);
});

test("较慢的旧搜索响应不会覆盖新筛选结果", async ({ page }) => {
  await openStudentHome(page);
  let release;
  const pending = new Promise((resolve) => { release = resolve; });
  let started = false;
  await page.route((url) => url.pathname === "/api/courses" && url.searchParams.get("keyword") === "Java", async (route) => {
    started = true;
    await pending;
    await route.fulfill({ json: { code: 200, data: { list: courses.slice(0, 1), total: 1, pageNum: 1, pageSize: 12, totalPages: 1 } } });
  });
  const tabs = page.locator(".studio-course-tabs");
  try {
    await tabs.getByRole("button", { name: "Java", exact: true }).click();
    await expect.poll(() => started).toBe(true);
    await tabs.getByRole("button", { name: "本周精选" }).click();
    await expect(page.locator(".studio-course-tabs")).toContainText("13 门课程可学");
    const response = page.waitForResponse((item) => new URL(item.url()).searchParams.get("keyword") === "Java");
    release();
    await response;
    await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(12);
  } finally {
    release();
  }
});

test("清空搜索输入恢复课程列表", async ({ page }) => {
  await openStudentHome(page);
  const input = page.getByRole("textbox", { name: "搜索课程或知识点" });
  await input.fill("Java");
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(1);
  await input.clear();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(12);
});

test("课程详情请求失败时显示重试入口，不伪装成无课时课程", async ({ page }) => {
  await openStudentHome(page);
  await page.route("**/api/courses/1", (route) => route.fulfill({ status: 503, json: { message: "课程详情暂不可用" } }), { times: 1 });
  await page.getByRole("button", { name: "查看课程：测试课程 1", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("课程详情暂不可用");
  await page.getByRole("button", { name: "重新加载", exact: true }).click();
  await expect(page.getByRole("button", { name: "开始学习", exact: true })).toBeEnabled();
  await expect(page.locator(".lesson-list")).toContainText("第一课");
});
