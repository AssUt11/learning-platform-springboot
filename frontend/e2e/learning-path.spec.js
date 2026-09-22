import { expect, test } from "@playwright/test";

const path = {
  available: true, pathId: "test-path-1", title: "Java 到接口开发", summary: "根据本次分析先补基础，再做项目。",
  createdAt: "2026-09-20T02:00:00Z", source: "neo4j", neo4jSynced: true, graphUsed: true,
  nodes: [
    { id: "step-1", order: 1, title: "Java 基础", goal: "掌握变量和控制流", completionCriteria: "通过基础练习", courseId: 12 },
    { id: "step-2", order: 2, title: "接口实战", goal: "完成一个接口", completionCriteria: "接口测试通过" }
  ], links: [{ source: "step-1", target: "step-2", type: "NEXT_STEP" }], evidence: [], analysis: "测试分析"
};

async function setup(page, { available = true, source = "neo4j" } = {}) {
  let generated = available;
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 5, name: "路径学生", role: "student" }));
    localStorage.setItem("learning_token", "test-token");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (url.pathname === "/api/agents/path") {
      generated = true;
      data = { result: "先学 Java 基础，再做接口实战。", toolsUsed: [], sources: [], learningPath: path };
    } else if (url.pathname.startsWith("/api/learning/paths/")) {
      data = generated ? { ...path, source, neo4jSynced: source === "neo4j" } : { available: false };
    } else if (url.pathname === "/api/preferences") data = { subjects: ["Java"] };
    else if (url.pathname === "/api/categories") data = [];
    await route.fulfill({ json: { code: 200, data } });
  });
}

test("分析生成的路径可在智慧图谱中打开、点击步骤并刷新恢复", async ({ page }, testInfo) => {
  await setup(page, { available: false });
  await page.goto("/agents");
  await page.locator(".agent-card").filter({ has: page.getByRole("heading", { name: "学习路径分析", exact: true }) }).getByRole("button", { name: "开始分析" }).click();
  await page.getByRole("button", { name: "查看我的路径图谱" }).click();
  await expect(page).toHaveURL(/pathId=test-path-1/);
  await expect(page.getByRole("heading", { name: "Java 到接口开发" })).toBeVisible();
  await expect(page.locator(".personal-path-canvas line")).toHaveCount(1);
  await page.getByRole("button", { name: "步骤 2 接口实战" }).click();
  await expect(page.locator(".personal-path-detail")).toContainText("接口测试通过");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: testInfo.outputPath("learning-path-graph.png"), fullPage: true });
  await page.reload();
  await expect(page.getByRole("heading", { name: "Java 到接口开发" })).toBeVisible();
  await page.getByRole("button", { name: "进入学习课程" }).click();
  await expect(page).toHaveURL(/\/course\/12$/);
});

test("智慧图谱默认显示当前学生最新路径并保留公共图谱入口", async ({ page }) => {
  await setup(page);
  await page.goto("/graph");
  await expect(page.getByRole("heading", { name: "Java 到接口开发" })).toBeVisible();
  await page.getByRole("button", { name: "公共知识图谱", exact: true }).click();
  await expect(page.locator(".graph-toolbar-title")).toBeVisible();
  await page.goBack();
  await expect(page.getByRole("heading", { name: "Java 到接口开发" })).toBeVisible();
});

test("Neo4j未同步时显示真实状态并允许重试", async ({ page }) => {
  await setup(page, { source: "snapshot" });
  await page.goto("/graph");
  await expect(page.getByText("路径已保存 · 待同步 Neo4j", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "同步到 Neo4j" }).click();
  await expect(page.getByRole("alert")).toContainText("Neo4j 暂不可用");
  await expect(page.locator(".personal-path-node")).toHaveCount(2);
});

test("没有路径时提示先生成", async ({ page }) => {
  await setup(page, { available: false });
  await page.goto("/graph");
  await page.getByRole("button", { name: "生成学习路径", exact: true }).click();
  await expect(page).toHaveURL(/\/agents$/);
});
