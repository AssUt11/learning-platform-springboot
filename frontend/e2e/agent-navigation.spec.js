import { expect, test } from "@playwright/test";

const scenes = { guide: "课程诊断", analysis: "学习分析报告", path: "学习路径分析", recommend: "学习资源分析" };
const card = (page, scene) => page.locator(".agent-card").filter({
  has: page.getByRole("heading", { name: scenes[scene], exact: true })
});
const navigate = (page, label) => page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: label, exact: true }).click();
const resultFor = scene => ({
  result: `${scenes[scene]}的已完成结果`, model: "test-model", toolsUsed: ["get_learning_profile"], sources: ["records"],
  ...(scene === "path" ? { learningPath: { available: true, pathId: "navigation-path", neo4jSynced: false } } : {})
});

async function setup(page) {
  const pending = new Map();
  const calls = Object.fromEntries(Object.keys(scenes).map(id => [id, 0]));
  let reportReady = false;
  await page.addInitScript(() => {
    if (localStorage.getItem("e2e_analysis_initialized")) return;
    localStorage.setItem("e2e_analysis_initialized", "1");
    localStorage.setItem("learning_user", JSON.stringify({ id: 41, name: "分析测试学生", role: "student" }));
    localStorage.setItem("learning_token", "analysis-token-one");
  });
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const scene = url.pathname.split("/").at(-1);
    if (url.pathname.startsWith("/api/agents/") && Object.hasOwn(scenes, scene) && route.request().method() === "POST") {
      calls[scene]++;
      pending.set(scene, async (status = 200) => {
        if (scene === "analysis" && status === 200) reportReady = true;
        const payload = status === 200 ? resultFor(scene) : { message: "测试模型暂不可用" };
        await route.fulfill({ status, json: payload });
        pending.delete(scene);
      });
      return;
    }
    let data = {};
    if (url.pathname === "/api/preferences") data = { subjects: ["Java"] };
    else if (url.pathname === "/api/categories") data = [];
    else if (url.pathname === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    else if (url.pathname === "/api/agents/reports") data = { reports: reportReady
      ? [{ id: 900, outputResult: "导航测试报告", createdAt: "2026-10-19T08:00:00" }] : [] };
    else if (url.pathname === "/api/auth/login") {
      await route.fulfill({ json: { user: { id: 42, name: "另一学生", role: "student" }, token: "analysis-token-two", needsInterestSelection: false } });
      return;
    }
    await route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/agents");
  return { pending, calls };
}

test("四类分析切页后继续运行，返回显示进度，完成结果和报告保留", async ({ page }, testInfo) => {
  const { pending, calls } = await setup(page);
  for (const scene of Object.keys(scenes)) {
    await card(page, scene).getByRole("button", { name: "开始分析", exact: true }).click();
    await expect.poll(() => calls[scene]).toBe(1);
  }
  await navigate(page, "首页");
  await navigate(page, "AI 学伴");
  for (const scene of Object.keys(scenes)) {
    await expect(card(page, scene).getByRole("button", { name: "分析中...", exact: true })).toBeDisabled();
  }
  await navigate(page, "首页");
  for (const scene of Object.keys(scenes)) await pending.get(scene)();
  await navigate(page, "AI 学伴");
  for (const scene of Object.keys(scenes)) {
    await expect(card(page, scene)).toContainText(resultFor(scene).result);
    await expect(card(page, scene).getByRole("button", { name: "开始分析", exact: true })).toBeEnabled();
    expect(calls[scene]).toBe(1);
  }
  await expect(page.locator(".report-list")).toContainText("导航测试报告");
  await expect(card(page, "path").getByRole("button", { name: "查看我的路径图谱" })).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: testInfo.outputPath("analysis-retained.png"), fullPage: true });
});

test("已完成的四类结果刷新后恢复，刷新不会重新提交分析", async ({ page }) => {
  const { pending, calls } = await setup(page);
  for (const scene of Object.keys(scenes)) {
    await card(page, scene).getByRole("button", { name: "开始分析", exact: true }).click();
    await expect.poll(() => pending.has(scene)).toBe(true);
    await pending.get(scene)();
    await expect(card(page, scene)).toContainText(resultFor(scene).result);
  }
  await page.reload();
  for (const scene of Object.keys(scenes)) {
    await expect(card(page, scene)).toContainText(resultFor(scene).result);
    expect(calls[scene]).toBe(1);
  }
  await expect(card(page, "path").getByRole("button", { name: "查看我的路径图谱" })).toBeVisible();
});

test("离页后发生的失败保留提示，重试只提交一次新请求", async ({ page }) => {
  const { pending, calls } = await setup(page);
  await card(page, "guide").getByRole("button", { name: "开始分析", exact: true }).click();
  await expect.poll(() => pending.has("guide")).toBe(true);
  await navigate(page, "首页");
  await pending.get("guide")(503);
  await navigate(page, "AI 学伴");
  await expect(card(page, "guide").getByRole("alert")).toContainText("测试模型暂不可用");
  await card(page, "guide").getByRole("button", { name: "开始分析", exact: true }).click();
  await expect.poll(() => calls.guide).toBe(2);
  await pending.get("guide")();
  await expect(card(page, "guide")).toContainText(resultFor("guide").result);
  await expect(card(page, "guide").getByRole("alert")).toHaveCount(0);
});

test("退出切换账号后，不显示旧结果，旧请求的迟到响应也不写入新账号", async ({ page }) => {
  const { pending } = await setup(page);
  await card(page, "guide").getByRole("button", { name: "开始分析", exact: true }).click();
  await expect.poll(() => pending.has("guide")).toBe(true);
  await pending.get("guide")();
  await expect(card(page, "guide")).toContainText(resultFor("guide").result);
  await card(page, "path").getByRole("button", { name: "开始分析", exact: true }).click();
  await expect.poll(() => pending.has("path")).toBe(true);
  await page.getByRole("button", { name: /分析测试学生/ }).click();
  await page.getByRole("menuitem", { name: /退出登录/ }).click();
  await expect(page.getByRole("heading", { name: "登录学习空间" })).toBeVisible();
  await page.getByPlaceholder("name@example.com").fill("another@example.com");
  await page.getByPlaceholder("请输入密码").fill("Test-password-2026");
  await page.getByRole("button", { name: "登录系统", exact: true }).click();
  await navigate(page, "AI 学伴");
  await pending.get("path")();
  await expect(card(page, "guide").locator(".agent-output")).toHaveCount(0);
  await expect(card(page, "path").locator(".agent-output")).toHaveCount(0);
  await expect(card(page, "path").getByRole("button", { name: "开始分析", exact: true })).toBeEnabled();
  expect(await page.evaluate(() => sessionStorage.getItem("learning_agent_analysis_v1:41"))).toBeNull();
  await page.reload();
  await expect(page.locator(".agent-output")).toHaveCount(0);
});
