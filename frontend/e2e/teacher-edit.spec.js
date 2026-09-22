import { expect, test } from "@playwright/test";

test("课程列表点击编辑后显示并聚焦表单，可保存和取消编辑", async ({ page }) => {
  const updates = [];
  let courses = [
    { id: 21, title: "Java基础课程", subject: "Java", type: "material", level: "入门", description: "课程简介", content: "第一门课程的正文", reviewStatus: "approved" },
    { id: 22, title: "Python基础课程", subject: "Python", type: "material", level: "进阶", description: "Python简介", content: "第二门课程的正文", reviewStatus: "pending" }
  ];
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 10, name: "测试教师", role: "teacher" }));
    localStorage.setItem("learning_token", "test-token");
  });
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (url.pathname === "/api/teacher/resources") data = { resources: courses, total: courses.length };
    else if (/^\/api\/teacher\/resources\/\d+$/.test(url.pathname) && route.request().method() === "PUT") {
      const id = Number(url.pathname.split("/").at(-1));
      const update = route.request().postDataJSON();
      updates.push({ id, ...update });
      courses = courses.map((course) => course.id === id ? { ...course, ...update, reviewStatus: "pending" } : course);
    } else if (url.pathname === "/api/categories") data = [];
    await route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/teacher");
  const title = page.getByPlaceholder("课程或资源标题");
  const first = page.locator(".teacher-resource-item").filter({ hasText: "Java基础课程" });
  await first.getByRole("button", { name: "编辑", exact: true }).click();
  await expect(title).toHaveValue("Java基础课程");
  await expect(title).toBeInViewport();
  await expect(title).toBeFocused();
  await expect(page.getByPlaceholder("内容简介")).toHaveValue("课程简介");
  await title.fill("Java基础课程（已修改）");
  await page.getByRole("button", { name: "提交重新审核" }).click();
  await expect(page.getByRole("status")).toContainText("修改已提交");
  await expect(first).toContainText("Java基础课程（已修改）");
  await expect(first).toContainText("审核中");
  expect(updates).toHaveLength(1);
  expect(updates[0]).toMatchObject({ id: 21, title: "Java基础课程（已修改）", content: "第一门课程的正文" });
  const second = page.locator(".teacher-resource-item").filter({ hasText: "Python基础课程" });
  await second.getByRole("button", { name: "编辑", exact: true }).click();
  await expect(title).toHaveValue("Python基础课程");
  await expect(title).toBeInViewport();
  await title.fill("不应保存的草稿");
  await page.getByRole("button", { name: "取消编辑" }).click();
  await expect(title).toHaveValue("");
  await expect(second).toContainText("Python基础课程");
  expect(updates).toHaveLength(1);
});
