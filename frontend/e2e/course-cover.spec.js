import { expect, test } from "@playwright/test";

const courses = [
  { id: 31, title: "Linux 基础：权限、进程与日志排查", subject: "Linux", type: "article", coverUrl: "/uploads/missing-cover.png" },
  { id: 32, title: "考研概率统计：条件概率与随机变量", subject: "考研数学", type: "article" },
  { id: 33, title: "Spark 数据处理：分区、聚合与 Shuffle", subject: "Spark", type: "article" },
  { id: 34, title: "C# WinForms 小项目训练", subject: "C#", type: "video", fileUrl: "/uploads/missing-video.mp4" }
];

async function setup(page, list = courses) {
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 71, name: "封面验证", role: "student" }));
    localStorage.setItem("learning_token", "cover-check-token");
  });
  await page.route("**/api/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/preferences") data = { subjects: ["Java"] };
    if (path === "/api/learning/profile") data = { needsPlanning: false, status: "deferred" };
    if (path === "/api/categories") data = [];
    if (path === "/api/courses") data = { list, total: list.length, totalPages: 1 };
    return route.fulfill({ json: { code: 200, data } });
  });
  await page.route("**/uploads/missing-*", (route) => route.fulfill({ status: 404, body: "Not found" }));
}

test("课程封面按精确学科匹配，坏图片和坏视频仍保留可用的插画与课程入口", async ({ page }) => {
  await setup(page);
  await page.goto("/home");
  await expect(page.locator(".resource-card [data-course-art=linux] .course-artwork-canvas")).toBeVisible();
  await expect(page.locator(".resource-card [data-course-art=probability]")).toBeVisible();
  await expect(page.locator(".resource-card [data-course-art=spark]")).toBeVisible();
  const videoCover = page.getByRole("button", { name: "查看课程：C# WinForms 小项目训练", exact: true });
  await expect(videoCover.locator("[data-course-art=csharp] .course-artwork-canvas")).toBeVisible();
  await expect(videoCover.locator(".course-artwork-photo")).toHaveCount(0);
  if (process.env.COURSE_ARTIFACT_DIR) {
    await page.locator(".studio-course-grid").screenshot({ path: `${process.env.COURSE_ARTIFACT_DIR}/covers-desktop.png` });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.locator(".studio-course-grid").screenshot({ path: `${process.env.COURSE_ARTIFACT_DIR}/covers-mobile.png` });
  }
  await videoCover.click();
  await expect(page).toHaveURL(/\/course\/34$/);
});

test("教师提供的有效封面优先显示，失败资源不会扩展卡片或产生页面横向滚动", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setup(page, [{ ...courses[2], coverUrl: "/uploads/cover-check.png" }, courses[0]]);
  await page.route("**/uploads/cover-check.png", (route) => route.fulfill({
    contentType: "image/png",
    body: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jC1kAAAAASUVORK5CYII=", "base64")
  }));
  await page.goto("/home");
  const picture = page.locator("[data-course-art=spark] .course-artwork-photo");
  await expect(picture).toBeVisible();
  await picture.scrollIntoViewIfNeeded();
  await expect.poll(() => picture.evaluate((image) => image.complete && image.naturalWidth > 0)).toBe(true);
  await expect(page.locator("[data-course-art=linux] .course-artwork-canvas")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  if (process.env.COURSE_ARTIFACT_DIR) {
    await page.locator(".studio-course-grid").screenshot({ path: `${process.env.COURSE_ARTIFACT_DIR}/valid-photo-fixture-mobile.png` });
  }
});
