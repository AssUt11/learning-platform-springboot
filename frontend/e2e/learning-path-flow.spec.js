import { expect, test } from "@playwright/test";
import mysql from "mysql2/promise";

test("真实接口保存个人路径，读取最新版本并隔离不同学生", async ({ page, request }) => {
  test.skip(process.env.E2E_PATH_FLOW !== "1", "需要隔离数据库和专用模型测试服务");
  expect(process.env.E2E_DB_NAME).toMatch(/^learning_platform_e2e_/);
  const prefix = `e2e-path-${Date.now()}`;
  const emails = [`${prefix}-one@zhidu.local`, `${prefix}-two@zhidu.local`];
  const password = "Path-test-2026";
  const users = [];
  try {
    for (const email of emails) {
      const response = await request.post("/api/auth/register", { data: { name: "路径测试学生", email, password, role: "student" } });
      expect(response.ok()).toBeTruthy();
      users.push(await response.json());
    }
    const auth = (index) => ({ Authorization: `Bearer ${users[index].token}` });
    await request.post("/api/preferences", { headers: auth(0), data: { subjects: ["Java"] } });
    await page.goto("/login");
    await page.getByPlaceholder("name@example.com").fill(emails[0]);
    await page.getByPlaceholder("请输入密码").fill(password);
    await page.getByRole("button", { name: "登录系统" }).click();
    await page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: "AI 学伴" }).click();
    const generatedResponse = page.waitForResponse((response) => response.url().endsWith("/api/agents/path") && response.request().method() === "POST");
    await page.locator(".agent-card").filter({ has: page.getByRole("heading", { name: "学习路径分析", exact: true }) }).getByRole("button", { name: "开始分析" }).click();
    const first = await (await generatedResponse).json();
    expect(first.learningPath?.available, JSON.stringify(first)).toBe(true);
    expect(first.learningPath.neo4jSynced).toBe(process.env.E2E_PATH_NEO4J === "1");
    await page.getByRole("button", { name: "查看我的路径图谱" }).click();
    await expect(page.locator(".personal-path-node")).toHaveCount(2);
    await page.reload();
    await expect(page.getByRole("heading", { name: first.learningPath.title })).toBeVisible();
    const other = await request.get(`/api/learning/paths/${first.learningPath.pathId}`, { headers: auth(1) });
    expect(other.status()).toBe(404);
    const otherSync = await request.post(`/api/learning/paths/${first.learningPath.pathId}/sync`, { headers: auth(1) });
    expect(otherSync.status()).toBe(404);
    const secondResponse = await request.post("/api/agents/path", { headers: auth(0), data: {}, timeout: 150000 });
    const second = await secondResponse.json();
    expect(second.learningPath?.pathId, JSON.stringify(second)).not.toBe(first.learningPath.pathId);
    const latest = await (await request.get("/api/learning/paths/latest", { headers: auth(0) })).json();
    expect(latest.data.pathId).toBe(second.learningPath.pathId);
    expect(latest.data.nodes).toEqual(second.learningPath.nodes);
    expect(latest.data.source).toBe(process.env.E2E_PATH_NEO4J === "1" ? "neo4j" : "snapshot");
    await page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: "智慧图谱" }).click();
    await expect(page.getByRole("heading", { name: second.learningPath.title })).toBeVisible();
  } finally {
    const c = await mysql.createConnection({ host: process.env.E2E_DB_HOST || "127.0.0.1", user: process.env.E2E_DB_USERNAME || "root", password: process.env.E2E_DB_PASSWORD || "root", database: process.env.E2E_DB_NAME });
    try { await c.execute("delete from users where email in (?,?)", emails); }
    finally { await c.end(); }
  }
});
