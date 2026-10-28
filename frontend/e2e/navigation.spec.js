import { expect, test } from "@playwright/test";

const student = { id: 51, name: "跳转测试学生", role: "student" };

async function mockApi(page, handlePreferences) {
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    if (path === "/api/preferences" && handlePreferences) return handlePreferences(route);
    if (path === "/api/auth/login") {
      return route.fulfill({ json: { user: student, token: "navigation-token", needsInterestSelection: false } });
    }
    let data = {};
    if (path === "/api/preferences") data = { subjects: ["Java"] };
    if (path === "/api/categories") data = [];
    if (path === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    await route.fulfill({ json: { code: 200, data } });
  });
}

async function restoreSession(page) {
  await page.addInitScript((user) => {
    if (sessionStorage.getItem("navigation-fixture-initialized")) return;
    sessionStorage.setItem("navigation-fixture-initialized", "1");
    localStorage.setItem("learning_user", JSON.stringify(user));
    localStorage.setItem("learning_token", "navigation-token");
  }, student);
}

const navigation = (page) => page.getByRole("navigation", { name: "主导航" });
const loginHeading = (page) => page.getByRole("heading", { name: "登录学习空间" });

async function login(page) {
  await page.goto("/login");
  await page.getByPlaceholder("name@example.com").fill("navigation@example.com");
  await page.getByPlaceholder("请输入密码").fill("Test-password-2026");
  await page.getByRole("button", { name: "登录系统", exact: true }).click();
}

for (const status of [401, 200]) {
  test(`重启后旧登录失效（HTTP ${status}）停留在登录页，重新登录无需刷新`, async ({ page }) => {
    await restoreSession(page);
    let expired = true;
    await mockApi(page, (route) => route.fulfill(expired
      ? { status, json: { code: 401, message: "Please log in first" } }
      : { json: { code: 200, data: { subjects: ["Java"] } } }));
    await page.goto("/home");
    await expect(loginHeading(page)).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
    await expect(navigation(page)).toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem("learning_token"))).toBeNull();
    expired = false;
    await page.getByPlaceholder("name@example.com").fill("navigation@example.com");
    await page.getByPlaceholder("请输入密码").fill("Test-password-2026");
    await page.getByRole("button", { name: "登录系统", exact: true }).click();
    await expect(navigation(page)).toBeVisible();
    await navigation(page).getByRole("button", { name: "我的学习", exact: true }).click();
    await expect(page.getByRole("heading", { name: "学习进度与个人记录" })).toBeVisible();
    await expect(page).toHaveURL(/\/learning$/);
  });
}

test("登录后偏好尚未加载也能进入首页并切换页面", async ({ page }) => {
  let release;
  const pending = new Promise((resolve) => { release = resolve; });
  await mockApi(page, async (route) => {
    await pending;
    await route.fulfill({ json: { code: 200, data: { subjects: ["Java"] } } });
  });
  try {
    await login(page);
    await expect(page).toHaveURL(/\/home$/, { timeout: 3000 });
    await expect(loginHeading(page)).toHaveCount(0);
    await navigation(page).getByRole("button", { name: "AI 学伴", exact: true }).click();
    await expect(page.getByRole("heading", { name: "把学习记录转化为下一步行动" })).toBeVisible();
    release();
    await expect(page).toHaveURL(/\/agents$/);
  } finally {
    release();
  }
});

test("登录后偏好返回401不会被失败回调送回无登录状态的首页", async ({ page }) => {
  await mockApi(page, (route) => route.fulfill({ status: 401, json: { message: "Please log in first" } }));
  await login(page);
  await expect(loginHeading(page)).toBeVisible();
  await expect(page.getByRole("alert")).toContainText("登录状态已失效");
  await expect(page).toHaveURL(/\/login$/);
  await expect(navigation(page)).toHaveCount(0);
});

test("退出后迟到的偏好响应不会恢复旧账号的数据或跳转", async ({ page }) => {
  await restoreSession(page);
  let release;
  let requested = false;
  const pending = new Promise((resolve) => { release = resolve; });
  await mockApi(page, async (route) => {
    requested = true;
    await pending;
    await route.fulfill({ json: { code: 200, data: { subjects: ["旧账号方向"] } } });
  });
  try {
    await page.goto("/home");
    await expect.poll(() => requested).toBe(true);
    await page.getByRole("button", { name: /跳转测试学生/ }).click();
    await page.getByRole("menuitem", { name: /退出登录/ }).click();
    await expect(loginHeading(page)).toBeVisible();
    const response = page.waitForResponse("**/api/preferences");
    release();
    await response;
    await expect(loginHeading(page)).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
    await expect.poll(() => page.evaluate(() => localStorage.getItem("learning_preferences"))).toBeNull();
  } finally {
    release();
  }
});

for (const animationFailure of ["throws", "stalled"]) {
  test(`过渡动画异常（${animationFailure}）时导航、前进和后退仍切换实际内容`, async ({ page }) => {
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await restoreSession(page);
    await page.addInitScript((failure) => {
      document.startViewTransition = () => {
        if (failure === "throws") throw new Error("浏览器过渡动画初始化失败");
        const pending = new Promise(() => {});
        return { updateCallbackDone: pending, ready: pending, finished: pending, skipTransition() {} };
      };
    }, animationFailure);
    await mockApi(page);
    await page.goto("/home");
    await navigation(page).getByRole("button", { name: "我的学习", exact: true }).click();
    await expect(page).toHaveURL(/\/learning$/);
    await expect(page.getByRole("heading", { name: "学习进度与个人记录" })).toBeVisible();
    await navigation(page).getByRole("button", { name: "AI 学伴", exact: true }).click();
    await expect(page.getByRole("heading", { name: "把学习记录转化为下一步行动" })).toBeVisible();
    await page.goBack();
    await expect(page).toHaveURL(/\/learning$/);
    await expect(page.getByRole("heading", { name: "学习进度与个人记录" })).toBeVisible();
    await page.goForward();
    await expect(page).toHaveURL(/\/agents$/);
    await expect(page.getByRole("heading", { name: "把学习记录转化为下一步行动" })).toBeVisible();
    expect(errors).toEqual([]);
  });
}
