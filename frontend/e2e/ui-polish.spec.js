import { expect, test } from "@playwright/test";

async function setup(page, role = "student") {
  await page.addInitScript((role) => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 71, name: "界面验证", role }));
    localStorage.setItem("learning_token", "ui-check-token");
  }, role);
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/preferences") data = { subjects: ["Java"] };
    if (path === "/api/learning/profile") data = { needsPlanning: false, status: "deferred" };
    if (path === "/api/categories") data = [];
    if (path === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    if (path === "/api/teacher/courses") data = [];
    if (path === "/api/agents/reports") data = { reports: [] };
    return route.fulfill({ json: { code: 200, data } });
  });
}

test("快速转页与返回保留导航实例，最终内容和选中状态一致", async ({ page }) => {
  await setup(page);
  await page.goto("/home");
  const nav = page.getByRole("navigation", { name: "主导航" });
  const original = await nav.elementHandle();
  for (const name of ["我的学习", "AI 学伴", "首页", "AI 学伴"]) {
    await nav.getByRole("button", { name, exact: true }).click();
  }
  await expect(page.getByRole("heading", { name: "把学习记录转化为下一步行动" })).toBeVisible();
  await expect(nav.getByRole("button", { name: "AI 学伴", exact: true })).toHaveAttribute("aria-current", "page");
  expect(await original.evaluate((element) => element.isConnected)).toBe(true);
  await page.goBack();
  await expect(page).toHaveURL(/\/home$/);
  await page.goForward();
  await expect(page).toHaveURL(/\/agents$/);
  await expect(page.getByRole("heading", { name: "把学习记录转化为下一步行动" })).toBeVisible();
});

test("手机教师导航可展开全部入口，选择后收起，Escape返回触发按钮", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page, "teacher");
  await page.goto("/home");
  const nav = page.getByRole("navigation", { name: "主导航" });
  await expect(nav).toBeHidden();
  await page.getByRole("button", { name: "展开导航", exact: true }).click();
  await expect(nav.getByRole("button", { name: "课程上传/管理", exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(nav).toBeHidden();
  await expect(page.getByRole("button", { name: "展开导航", exact: true })).toBeFocused();
  await page.getByRole("button", { name: "展开导航", exact: true }).click();
  await nav.getByRole("button", { name: "AI 学伴", exact: true }).click();
  await expect(page).toHaveURL(/\/agents$/);
  await expect(nav).toBeHidden();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("动画API失败和减少动态效果都不会阻断实际转页", async ({ page }) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await setup(page);
  await page.addInitScript(() => {
    Element.prototype.animate = () => { throw new Error("animation unavailable"); };
  });
  await page.goto("/home");
  const nav = page.getByRole("navigation", { name: "主导航" });
  await nav.getByRole("button", { name: "我的学习", exact: true }).click();
  await expect(page.getByRole("heading", { name: "学习进度与个人记录" })).toBeVisible();
  await page.emulateMedia({ reducedMotion: "reduce" });
  await nav.getByRole("button", { name: "AI 学伴", exact: true }).click();
  await expect(page.getByRole("heading", { name: "把学习记录转化为下一步行动" })).toBeVisible();
  await expect(page.locator("main.content:visible")).toHaveCSS("opacity", "1");
  expect(errors).toEqual([]);
});
