import { expect, test } from "@playwright/test";

const identities = [
  { role: "student", label: "演示学生", email: "feature-demo-weak@zhidu.local", destination: "/home" },
  { role: "teacher", label: "演示老师", email: "feature-demo-teacher@zhidu.local", destination: "/teacher" },
  { role: "admin", label: "演示管理", email: "feature-demo-admin@zhidu.local", destination: "/admin" }
];

async function setup(page, { enabled = true, failCredentials = false, holdLogin = false } = {}) {
  const state = { credentials: [], logins: [], user: null, failCredentials, release: null };
  const loginGate = holdLogin ? new Promise(resolve => { state.release = resolve; }) : Promise.resolve();
  await page.route("**/api/**", async route => {
    const req = route.request(), pathname = new URL(req.url()).pathname;
    if (!pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (pathname === "/api/auth/demo-accounts") data = { enabled, accounts: enabled ? identities : [] };
    else if (pathname.startsWith("/api/auth/demo-accounts/")) {
      const role = pathname.split("/").at(-1); state.credentials.push(role);
      if (state.failCredentials) return route.fulfill({ status: 503, json: { code: 503, message: "该演示账号暂不可用，请稍后重试。" } });
      data = { email: identities.find(item => item.role === role).email, password: `Example-${role}-2026` };
    } else if (pathname === "/api/auth/login") {
      const body = req.postDataJSON(); state.logins.push(body); await loginGate;
      const identity = identities.find(item => item.email === body.email) || { role: "student" };
      state.user = { id: identities.findIndex(item => item.role === identity.role) + 100, name: identity.label || "普通学生", email: body.email, role: identity.role };
      return route.fulfill({ json: { user: state.user, token: `signed-${identity.role}`, needsInterestSelection: false } });
    } else if (pathname === "/api/profile") data = state.user;
    else if (pathname === "/api/preferences") data = { subjects: ["Java"] };
    else if (pathname === "/api/learning/profile") data = { needsPlanning: false, status: "completed" };
    else if (pathname === "/api/categories") data = [];
    else if (pathname === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    else if (pathname === "/api/teacher/resources") return route.fulfill({ json: { resources: [], total: 0 } });
    else if (pathname === "/api/teacher/analytics") return route.fulfill({ json: { summary: {}, resourceStats: [], warningStudents: [], weakNodes: [], reviews: [], questions: [] } });
    else if (pathname === "/api/admin/dashboard") return route.fulfill({ json: { summary: {}, pendingResources: [], resources: [], reviews: [], questions: [], health: {}, auditLogs: [] } });
    return route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/login");
  return state;
}

for (const identity of identities) test(`${identity.label}自动填写并按真实身份完成登录`, async ({ page }) => {
  const state = await setup(page, { holdLogin: true });
  await page.getByRole("button", { name: identity.label, exact: true }).click();
  await expect(page.getByLabel("邮箱", { exact: true })).toHaveValue(identity.email);
  await expect(page.getByLabel("密码", { exact: true })).toHaveValue(`Example-${identity.role}-2026`);
  await expect.poll(() => state.logins.length).toBe(1);
  expect(state.logins[0]).toEqual({ email: identity.email, password: `Example-${identity.role}-2026` });
  for (const item of identities) await expect(page.getByRole("button", { name: item.label, exact: true })).toBeDisabled();
  await expect(page.locator('.auth-panel form button[type="submit"]')).toBeDisabled();
  state.release();
  await expect(page).toHaveURL(new RegExp(`${identity.destination}$`));
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("learning_user"))?.role)).toBe(identity.role);
  expect(state.credentials).toEqual([identity.role]);
  expect(state.logins).toHaveLength(1);
});

test("演示账号不可用时显示错误并可重试，不会提前提交登录", async ({ page }) => {
  const state = await setup(page, { failCredentials: true });
  await page.getByRole("button", { name: "演示学生", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("该演示账号暂不可用");
  expect(state.logins).toHaveLength(0);
  state.failCredentials = false;
  await page.getByRole("button", { name: "演示学生", exact: true }).click();
  await expect(page).toHaveURL(/\/home$/);
  expect(state.logins).toHaveLength(1);
});

test("未开启演示时保持普通登录和注册入口", async ({ page }) => {
  const state = await setup(page, { enabled: false });
  await expect(page.getByRole("region", { name: "演示账号登录" })).toHaveCount(0);
  await page.getByRole("button", { name: "注册", exact: true }).click();
  await expect(page.getByLabel("姓名", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "演示管理", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.getByLabel("邮箱", { exact: true }).fill("normal@example.com");
  await page.getByLabel("密码", { exact: true }).fill("Normal-password-2026");
  await page.getByRole("button", { name: "登录系统", exact: true }).click();
  await expect(page).toHaveURL(/\/home$/);
  expect(state.logins).toEqual([{ email: "normal@example.com", password: "Normal-password-2026" }]);
  expect(state.credentials).toEqual([]);
});

test("三个入口在桌面、平板和手机都可点击且不横向溢出", async ({ page }, testInfo) => {
  await setup(page);
  for (const width of [1440, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    for (const identity of identities) await expect(page.getByRole("button", { name: identity.label, exact: true })).toBeVisible();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.locator(".auth-panel").screenshot({ path: testInfo.outputPath(`demo-login-${width}.png`) });
  }
});
