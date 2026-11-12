import { expect, test } from "@playwright/test";

const linux = {
  id: 54, title: "Linux 基础：权限、进程与日志排查", subject: "Linux", directionName: "Linux", categoryName: "云计算",
  teacher: "课程教师", type: "material", description: "建立从文件权限到服务监听的检查顺序。", coverUrl: "/missing-cover.png",
  outline: { chapters: [{ id: 7, title: "Linux 实践", lessons: [
    { id: 701, title: "文件权限与进程", lessonType: "article", contentText: "学习目标：理解用户、权限与进程之间的关系。", durationSeconds: 600 },
    { id: 702, title: "日志与服务监听排查", lessonType: "article", contentText: "学习目标：先看日志，再检查服务监听与进程状态。", durationSeconds: 720 }
  ] }] }
};

async function setup(page) {
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 85, name: "课程验证", role: "student" }));
    localStorage.setItem("learning_token", "course-discovery-token");
  });
  await page.route("**/missing-cover.png", route => route.fulfill({ status: 404, body: "missing" }));
  await page.route("**/api/**", route => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/preferences") data = { subjects: ["Linux"] };
    if (path === "/api/learning/profile") data = { needsPlanning: false };
    if (path === "/api/categories") data = [];
    if (path === "/api/courses") data = { list: [linux], total: 1, totalPages: 1 };
    if (path === "/api/courses/54") data = linux;
    if (path === "/api/learning/courses/54/progress") data = { totalLessons: 2, completedLessons: 0, progressPercent: 0, lessons: [] };
    if (path === "/api/learning/summary") data = { summary: { learnedResourceCount: 3, completedResourceCount: 1, favoriteCount: 2 } };
    if (path === "/api/learning/resume") data = { available: true, courseId: 54, lessonId: 702, lessonTitle: "日志与服务监听排查", courseTitle: linux.title };
    return route.fulfill({ json: { code: 200, data } });
  });
}

test("损坏课程照片回退本地学科封面，课程重点准确进入指定课时", async ({ page }) => {
  await setup(page);
  await page.goto("/home");
  const cover = page.getByRole("button", { name: `查看课程：${linux.title}`, exact: true });
  await expect(cover.locator(".course-artwork-canvas")).toBeVisible();
  await expect(cover.locator(".course-artwork-photo")).toHaveCount(0);
  await cover.click();
  await expect(page).toHaveURL(/\/course\/54$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(linux.title);
  const highlights = page.getByRole("region", { name: "这门课会学什么" });
  await expect(highlights).toContainText("先看日志");
  await highlights.getByRole("button", { name: /日志与服务监听排查/ }).click();
  await expect(page).toHaveURL(/\/learn\/54\/702$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("日志与服务监听排查");
});

test("首页成长区显示账号接口的真实数量，分析入口可以转页", async ({ page }) => {
  await setup(page);
  await page.goto("/home");
  const workbench = page.getByRole("region", { name: "每一次学习，都有下一步。" });
  await expect(workbench.locator("dl dd")).toHaveText(["3", "1", "2"]);
  await expect(workbench).toContainText("日志与服务监听排查");
  await workbench.getByRole("button", { name: /我应该先补哪块知识/ }).click();
  await expect(page).toHaveURL(/\/agents$/);
  await expect(page.getByRole("heading", { name: "把学习记录转化为下一步行动" })).toBeVisible();
});
