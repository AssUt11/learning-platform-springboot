import { expect, test } from "@playwright/test";

async function setup(page, { role = "student", holdFirst = false, failFirst = false } = {}) {
  const calls = [];
  const chats = [];
  let release;
  await page.addInitScript(({ role }) => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 41, name: "聊天测试用户", role }));
    localStorage.setItem("learning_token", "chat-test-token");
  }, { role });
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (url.pathname === "/api/agent-chat/status") data = { available: true, provider: "llamafactory", model: "zhidu-qwen3-4b-lora" };
    else if (url.pathname === "/api/agent-chat/conversations") data = chats.map(({ messages, ...chat }) => chat);
    else if (/\/api\/agent-chat\/conversations\/\d+$/.test(url.pathname)) data = chats.find(item => item.id === Number(url.pathname.split("/").at(-1)));
    else if (url.pathname === "/api/agent-chat/stream") {
      const body = route.request().postDataJSON();
      calls.push(body);
      if (failFirst && calls.length === 1) return route.fulfill({ status: 503, json: { message: "模型暂时离线，请稍后重试。" } });
      let chat = chats.find(item => item.id === body.conversationId);
      if (!chat) { chat = { id: chats.length + 1, title: body.message, messages: [] }; chats.unshift(chat); }
      const reply = calls.length === 1 ? "这是第一轮的回答。" : "这是带有历史记忆的第二轮回答。";
      const finish = async () => {
        const messageId = calls.length * 2;
        chat.messages.push({ id: messageId - 1, role: "user", content: body.message, status: "completed" },
          { id: messageId, role: "assistant", content: reply, status: "completed" });
        chat.preview = reply;
        const events = [["conversation.created", { conversationId: chat.id, requestId: `chat-${messageId}`, messageId, model: "zhidu-qwen3-4b-lora" }],
          ["message.started", { messageId }], ["message.delta", { content: reply.slice(0, 4) }],
          ["message.delta", { content: reply.slice(4) }], ["message.completed", { messageId }]];
        await route.fulfill({ contentType: "text/event-stream", body: events.map(([name, value]) => `event: ${name}\ndata: ${JSON.stringify(value)}\n\n`).join("") });
      };
      if (holdFirst && calls.length === 1) { release = finish; return; }
      return finish();
    } else if (url.pathname === "/api/preferences") data = { subjects: ["Java"] };
    else if (url.pathname === "/api/categories") data = [];
    else if (url.pathname === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    await route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/chat");
  return { calls, release: () => release() };
}

for (const role of ["student", "teacher"]) {
  test(`${role} 导航在消息后显示 Agent 对话，可填写建议问题`, async ({ page }) => {
    await setup(page, { role });
    const labels = await page.getByRole("navigation", { name: "主导航" }).getByRole("button").allTextContents();
    expect(labels[labels.indexOf("消息") + 1]).toBe("Agent 对话");
    await expect(page.getByRole("heading", { name: "Agent 对话", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "如何制定一周学习计划？" }).click();
    await expect(page.getByRole("textbox", { name: "发送给 Agent 的问题" })).toHaveValue("如何制定一周学习计划？");
  });
}

test("独立聊天流式回答、切页继续、多轮追问、历史和刷新恢复", async ({ page }, testInfo) => {
  const state = await setup(page, { holdFirst: true });
  const input = page.getByRole("textbox", { name: "发送给 Agent 的问题" });
  await input.fill("怎样学习Java变量？");
  await page.getByRole("button", { name: "发送", exact: true }).click();
  await expect.poll(() => state.calls.length).toBe(1);
  const nav = page.getByRole("navigation", { name: "主导航" });
  await nav.getByRole("button", { name: "首页", exact: true }).click();
  await state.release();
  await nav.getByRole("button", { name: "Agent 对话", exact: true }).click();
  await expect(page.locator(".agent-chat-message.assistant")).toContainText("这是第一轮的回答。");
  await expect(input).toBeEnabled();
  await input.fill("请接着举一个例子。");
  await input.press("Enter");
  await expect(page.locator(".agent-chat-message.assistant")).toHaveCount(2);
  await expect(page.locator(".agent-chat-transcript")).toContainText("带有历史记忆的第二轮回答");
  expect(state.calls[1].conversationId).toBe(1);
  expect(state.calls[0]).not.toHaveProperty("courseId");
  await page.getByRole("button", { name: "新建对话", exact: true }).click();
  await expect(page.getByRole("heading", { name: "你好，有什么想一起探讨的？" })).toBeVisible();
  await page.locator(".agent-chat-history").getByRole("button", { name: /怎样学习Java变量/ }).click();
  await expect(page.locator(".agent-chat-message")).toHaveCount(4);
  await page.reload();
  await expect(page.locator(".agent-chat-message")).toHaveCount(4);
  await expect(page.locator(".agent-chat-transcript")).toContainText("带有历史记忆的第二轮回答");
  await page.screenshot({ path: testInfo.outputPath("agent-chat.png"), fullPage: true });
});

test("模型离线显示错误并可重试发送", async ({ page }) => {
  const state = await setup(page, { failFirst: true });
  const input = page.getByRole("textbox", { name: "发送给 Agent 的问题" });
  await input.fill("你好"); await page.getByRole("button", { name: "发送", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("模型暂时离线");
  await expect(input).toBeEnabled();
  await input.fill("请再试一次"); await page.getByRole("button", { name: "发送", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.locator(".agent-chat-transcript")).toContainText("第二轮回答");
  expect(state.calls).toHaveLength(2);
});
