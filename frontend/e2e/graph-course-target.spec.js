import { expect, test } from "@playwright/test";

const topic = "期望与方差：平均值和波动";
const scope = ["考研备考", "考研数学"];
const target = (courseId = 21, lessonId = 302, courseTitle = "考研概率统计：条件概率与随机变量") => ({
  courseId, lessonId, courseTitle, lessonTitle: topic
});

async function setup(page, matches, { delayTarget } = {}) {
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 61, name: "图谱学生", role: "student" }));
    localStorage.setItem("learning_token", "graph-scope-token");
    localStorage.setItem("learning_preferences", JSON.stringify(["考研数学"]));
  });
  const targetQueries = [];
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (url.pathname === "/api/knowledge/categories/tree") {
      data = { categories: [{ label: scope[0], children: [{ label: scope[1], children: [{ label: topic }, { label: "条件概率" }] }] }] };
    } else if (url.pathname === "/api/preferences") data = { subjects: [scope[1]] };
    else if (url.pathname === "/api/learning/profile") data = { needsPlanning: false };
    else if (url.pathname === "/api/learning/knowledge-progress") data = [];
    else if (url.pathname === "/api/knowledge-graph") {
      data = { source: "neo4j", nodes: [
        { id: "math-root", label: scope[1], type: "root", path: scope },
        { id: "expectation", label: topic, type: "knowledge", path: [...scope, topic] },
        { id: "conditional", label: "条件概率", type: "knowledge", path: [...scope, "条件概率"] }
      ], links: [{ source: "math-root", target: "expectation" }, { source: "math-root", target: "conditional" }] };
    } else if (url.pathname === "/api/knowledge/course-targets") {
      targetQueries.push(Object.fromEntries(url.searchParams));
      if (delayTarget) await delayTarget;
      data = matches;
    } else if (/^\/api\/courses\/\d+$/.test(url.pathname)) {
      const courseId = Number(url.pathname.split("/").at(-1));
      const lessonId = courseId === 21 ? 302 : 401;
      data = { id: courseId, title: matches.find(item => item.courseId === courseId)?.courseTitle || "考研课程", subject: scope[1], content: "学习材料。", outline: { chapters: [{ id: 1, title: "随机变量", lessons: [
        { id: 301, title: "条件概率", lessonType: "article", contentText: "第一课材料。" },
        { id: lessonId, title: topic, lessonType: "article", contentText: "期望与方差的公式与例题。" }
      ] }] } };
    } else if (/^\/api\/learning\/courses\/\d+\/progress$/.test(url.pathname)) {
      data = { totalLessons: 2, completedLessons: 0, lessons: [] };
    } else if (url.pathname === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    else if (url.pathname === "/api/categories") data = [];
    await route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/graph?view=catalog");
  await page.locator('.graph-stage [data-knowledge-name="期望与方差：平均值和波动"]').press("Enter");
  await expect(page.locator(".node-detail h3")).toHaveText(topic);
  return targetQueries;
}

test("图谱知识点直接进入同名课时，刷新仍显示该课时而非第一课", async ({ page }) => {
  const queries = await setup(page, [target()]);
  await page.getByRole("button", { name: "学习该知识点", exact: true }).click();
  await expect(page).toHaveURL(/\/learn\/21\/302$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(topic);
  expect(queries).toEqual([{ category: scope[0], direction: scope[1], title: topic }]);
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(topic);
  await page.goBack();
  await expect(page).toHaveURL(/\/graph\?view=catalog$/);
});

test("多个同名课时保留所属课程选择，不能自动跳第一个结果", async ({ page }) => {
  await setup(page, [target(), target(22, 401, "概率论进阶")]);
  await page.getByRole("button", { name: "学习该知识点", exact: true }).click();
  const list = page.getByRole("list", { name: "包含当前知识点的课程" });
  await expect(list).toBeVisible();
  await expect(page).toHaveURL(/\/graph/);
  const chosen = list.locator("li").filter({ hasText: "所属课程：概率论进阶" });
  await chosen.getByRole("button").click();
  await expect(page).toHaveURL(/\/learn\/22\/401$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(topic);
});

test("没有关联时留在当前节点，清楚区分未配置课时与方向课程", async ({ page }) => {
  await setup(page, []);
  await page.getByRole("button", { name: "学习该知识点", exact: true }).click();
  await expect(page.locator(".knowledge-course-targets")).toContainText("这个知识点还没有关联到已发布的课程课时");
  await expect(page.locator(".node-detail h3")).toHaveText(topic);
  await expect(page).toHaveURL(/\/graph\?view=catalog$/);
  await expect(page.getByRole("button", { name: "查看方向课程", exact: true })).toBeVisible();
});

test("知识点可通过明确关联进入不同标题的实际课时", async ({ page }) => {
  await setup(page, [{ ...target(), lessonTitle: "估计量与抽样波动", topicTitle: topic, matchType: "mapped" }]);
  await page.getByRole("button", { name: "学习该知识点", exact: true }).click();
  await expect(page).toHaveURL(/\/learn\/21\/302$/);
});

test("查找途中切换节点不会被旧请求带离新节点", async ({ page }) => {
  let release;
  const held = new Promise(resolve => { release = resolve; });
  await setup(page, [target()], { delayTarget: held });
  const lookupResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === "/api/knowledge/course-targets"
  );
  await page.getByRole("button", { name: "学习该知识点", exact: true }).click();
  await page.locator('.graph-stage [data-knowledge-name="条件概率"]').press("Enter");
  release();
  await lookupResponse;
  await expect(page.locator(".node-detail h3")).toHaveText("条件概率");
  await expect(page).toHaveURL(/\/graph\?view=catalog$/);
  await expect(page.getByRole("button", { name: "学习该知识点", exact: true })).toBeEnabled();
});
