import { expect, test } from "@playwright/test";

test("真实微调模型独立对话、多轮追问、数据库历史、账号隔离和停止生成", async ({ page, request }, testInfo) => {
  test.skip(process.env.E2E_AGENT_CHAT_FLOW !== "1", "需要隔离数据库和真实模型");
  expect(process.env.E2E_DB_NAME).toMatch(/^learning_platform_e2e_/);
  test.setTimeout(240_000);
  const prefix = `e2e-freechat-${Date.now()}`;
  const password = "E2e-school-2026";
  const create = async suffix => {
    const response = await request.post("/api/auth/register", { data: { name: `对话测试${suffix}`, email: `${prefix}-${suffix}@zhidu.local`, password, role: "student" } });
    expect(response.ok()).toBeTruthy();
    return response.json();
  };
  const first = await create("one"); const other = await create("two");
  const auth = token => ({ Authorization: `Bearer ${token}` });
  await request.post("/api/preferences", { headers: auth(first.token), data: { subjects: ["Java"] } });
  await page.goto("/login");
  await page.getByPlaceholder("name@example.com").fill(`${prefix}-one@zhidu.local`);
  await page.getByPlaceholder("请输入密码").fill(password);
  await page.getByRole("button", { name: "登录系统", exact: true }).click();
  await page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: "Agent 对话", exact: true }).click();
  await expect(page.getByText("已连接微调模型", { exact: true })).toBeVisible();
  const input = page.getByRole("textbox", { name: "发送给 Agent 的问题" });
  for (const question of ["我在学习Java，请用两句话介绍变量。", "请接着给一个简短的代码例子。"]) {
    await input.fill(question); await page.getByRole("button", { name: "发送", exact: true }).click();
    await expect(input).toBeEnabled({ timeout: 150_000 });
    await expect(page.getByRole("alert")).toHaveCount(0);
  }
  const listed = await (await request.get("/api/agent-chat/conversations", { headers: auth(first.token) })).json();
  expect(listed.data).toHaveLength(1);
  const id = listed.data[0].id;
  const history = await (await request.get(`/api/agent-chat/conversations/${id}`, { headers: auth(first.token) })).json();
  expect(history.data.messages).toHaveLength(4);
  expect(history.data.messages.filter(row => row.role === "assistant" && row.status === "completed" && row.content.trim())).toHaveLength(2);
  expect((await request.get(`/api/agent-chat/conversations/${id}`, { headers: auth(other.token) })).status()).toBe(404);
  await page.reload();
  await expect(page.locator(".agent-chat-message")).toHaveCount(4);
  await page.screenshot({ path: testInfo.outputPath("agent-chat-live.png"), fullPage: true });
  const started = page.waitForResponse(response => response.url().endsWith("/api/agent-chat/stream") && response.status() === 200);
  await input.fill("请详细分十点解释Java并发编程中的线程同步、锁、死锁和排查方法。");
  await page.getByRole("button", { name: "发送", exact: true }).click();
  await started;
  await page.getByRole("button", { name: "停止生成", exact: true }).click();
  await expect(input).toBeEnabled();
  await expect.poll(async () => {
    const saved = await (await request.get(`/api/agent-chat/conversations/${id}`, { headers: auth(first.token) })).json();
    return saved.data.messages.at(-1).status;
  }).toBe("cancelled");
});
