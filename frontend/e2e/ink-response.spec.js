import { expect, test } from "@playwright/test";

const course = {
  id: 12, title: "数据库原理", outline: { chapters: [{ id: 1, title: "第一章", lessons: [
    { id: 34, title: "事务与一致性", lessonType: "article", assetReady: true, contentText: "事务保证数据一致性。提交确认修改，回滚撤销修改。" },
    { id: 35, title: "索引与查询", lessonType: "article", assetReady: true, contentText: "索引帮助缩小查询范围。根据访问模式选择合适的索引。" }
  ] }] }
};

async function setup(page, { ignoreAbort = false } = {}) {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.addInitScript(({ ignoreAbort }) => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 41, name: "交互验证学生", role: "student" }));
    localStorage.setItem("learning_token", "ink-test-token");
    const original = window.fetch;
    window.testInkStreams = [];
    window.fetch = (url, options = {}) => {
      const path = new URL(url, location.href).pathname;
      if (!["/api/agent-chat/stream", "/api/ai/tutor/chat/stream"].includes(path)) return original(url, options);
      const entry = { closed: false };
      const stream = new ReadableStream({ start(controller) {
        entry.emit = (name, data) => { if (!entry.closed) controller.enqueue(new TextEncoder().encode(`event: ${name}\ndata: ${JSON.stringify(data)}\n\n`)); };
        entry.close = () => { if (!entry.closed) { entry.closed = true; controller.close(); } };
        if (!ignoreAbort) options.signal?.addEventListener("abort", () => { if (!entry.closed) { entry.closed = true; controller.error(new DOMException("Stopped", "AbortError")); } });
      } });
      window.testInkStreams.push(entry);
      return Promise.resolve(new Response(stream, { headers: { "Content-Type": "text/event-stream" } }));
    };
  }, { ignoreAbort });
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/preferences") data = { subjects: ["数据库"] };
    else if (path === "/api/categories") data = [];
    else if (path === "/api/courses") data = { list: [], total: 0, totalPages: 1 };
    else if (path === "/api/courses/12") data = course;
    else if (path === "/api/learning/courses/12/progress") data = { totalLessons: 2, lessons: [] };
    else if (path === "/api/agent-chat/status") data = { available: true, model: "zhidu-qwen3-4b-lora" };
    else if (path === "/api/agent-chat/conversations") data = [];
    await route.fulfill({ json: { code: 200, data } });
  });
}
async function emit(page, index, name, data) {
  await page.evaluate(({ index, name, data }) => window.testInkStreams[index].emit(name, data), { index, name, data });
}
async function start(page, index = 0) {
  await expect.poll(() => page.evaluate(() => window.testInkStreams.length)).toBe(index + 1);
  await emit(page, index, "conversation.created", { conversationId: 1, requestId: `ink-${index}`, messageId: index + 10, model: "zhidu-qwen3-4b-lora" });
  await emit(page, index, "message.started", { requestId: `ink-${index}` });
}
async function finish(page, index = 0) {
  await emit(page, index, "message.completed", { messageId: index + 10 });
  await page.evaluate(index => window.testInkStreams[index].close(), index);
}

test("流式文字水墨渐显，输入框与思考框宽度保持稳定，尾字和emoji不丢失", async ({ page }) => {
  await setup(page); await page.goto("/chat");
  await page.getByRole("textbox", { name: "发送给 Agent 的问题" }).fill("请解释事务");
  await page.getByRole("button", { name: "发送", exact: true }).click(); await start(page);
  const answer = page.locator(".agent-chat-message.assistant .ink-response");
  await expect(answer.getByRole("status")).toContainText("正在整理回答");
  const box = await answer.boundingBox(); const compose = await page.locator(".agent-chat-compose").boundingBox();
  const text = "事务保证一致性。\n分清提交和回滚，再用一个例子检验理解。🧠👩‍💻最后一字：好";
  await emit(page, 0, "message.delta", { content: text });
  await expect(answer.locator(".ink-arrival")).not.toBeEmpty();
  await expect(answer.locator(".ink-arrival")).toHaveCSS("animation-name", "ink-arrive");
  await page.locator(".agent-chat-main").screenshot({ path: "../.runtime/ink-thinking-preview-20261022.png" });
  await expect.poll(async () => (await answer.boundingBox()).width).toBe(box.width);
  expect(Math.abs((await page.locator(".agent-chat-compose").boundingBox()).y - compose.y)).toBeLessThan(1);
  await finish(page);
  await expect(answer.locator(".ink-response-copy")).toHaveText(text);
  await expect(answer).toHaveAttribute("data-state", "completed");
  await page.screenshot({ path: "../.runtime/ink-chat-preview-20261022.png", fullPage: true });
});

test("长回复仅在消息区滚动，上翻阅读后不被后续文字拉到底部", async ({ page }) => {
  await setup(page); await page.goto("/chat");
  await page.getByRole("textbox", { name: "发送给 Agent 的问题" }).fill("请详细解释");
  await page.getByRole("button", { name: "发送", exact: true }).click(); await start(page);
  const viewport = page.locator(".agent-chat-transcript");
  await emit(page, 0, "message.delta", { content: "事务需要结合实际例子理解。\n".repeat(40) });
  await expect.poll(() => viewport.evaluate(node => node.scrollHeight - node.clientHeight)).toBeGreaterThan(160);
  await viewport.evaluate(node => { node.scrollTop = 0; node.dispatchEvent(new Event("scroll")); });
  await emit(page, 0, "message.delta", { content: "继续补充：".repeat(20) + "https://example.test/" + "a".repeat(500) });
  await expect.poll(() => viewport.evaluate(node => node.scrollTop)).toBe(0);
  await finish(page);
  expect(await viewport.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
  expect(await page.evaluate(() => window.scrollY)).toBe(0);
  expect(await viewport.evaluate(node => node.scrollTop)).toBe(0);
});

test("停止生成立即显示已接收全文，保留未完成的最后一段", async ({ page }) => {
  await setup(page); await page.goto("/chat");
  await page.getByRole("textbox", { name: "发送给 Agent 的问题" }).fill("给我一段回答");
  await page.getByRole("button", { name: "发送", exact: true }).click(); await start(page);
  const text = "停止时也要保留每个已经收到的字。".repeat(12);
  await emit(page, 0, "message.delta", { content: text });
  await page.getByRole("button", { name: "停止生成", exact: true }).click();
  await expect(page.locator(".ink-response-copy")).toHaveText(text);
  await expect(page.locator(".ink-response")).toHaveAttribute("data-state", "cancelled");
  await expect(page.getByRole("textbox", { name: "发送给 Agent 的问题" })).toBeEnabled();
});

test("减少动态效果设置关闭水墨与打字延迟", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await setup(page); await page.goto("/chat");
  await page.getByRole("textbox", { name: "发送给 Agent 的问题" }).fill("你好");
  await page.getByRole("button", { name: "发送", exact: true }).click(); await start(page);
  const text = "减少动态效果时，完整文字直接显示。".repeat(12);
  await emit(page, 0, "message.delta", { content: text });
  await expect(page.locator(".ink-response-copy")).toHaveText(text);
  await expect(page.locator(".ink-arrival")).toHaveCSS("animation-name", "none");
  await finish(page);
});

test("课时答疑固定消息区高度，生成时输入框不下移，旧课时回复不串入新问题", async ({ page }) => {
  await setup(page, { ignoreAbort: true }); await page.goto("/learn/12/34");
  const panel = page.getByRole("region", { name: "AI 伴学", exact: true });
  await panel.locator("textarea").fill("解释事务"); await panel.getByRole("button", { name: "发送", exact: true }).click(); await start(page);
  const position = await panel.locator(".ai-tutor-form").boundingBox();
  await emit(page, 0, "message.delta", { content: "先理解一致性。".repeat(20) });
  await expect(panel.locator(".ink-response-copy")).toContainText("先理解");
  expect(Math.abs((await panel.locator(".ai-tutor-form").boundingBox()).y - position.y)).toBeLessThan(1);
  await page.locator(".learn-controls button.next").click();
  await panel.locator("textarea").fill("解释索引"); await panel.getByRole("button", { name: "发送", exact: true }).click(); await start(page, 1);
  await emit(page, 0, "message.delta", { content: "旧课时的迟到回答" }); await finish(page, 0);
  await expect(panel.getByRole("button", { name: "停止", exact: true })).toBeVisible();
  await expect(panel).not.toContainText("旧课时的迟到回答");
  await emit(page, 1, "message.delta", { content: "索引帮助缩小查询范围。" }); await finish(page, 1);
  await expect(panel.locator(".ink-response-copy")).toHaveText("索引帮助缩小查询范围。");
  await panel.screenshot({ path: "../.runtime/ink-lesson-preview-20261022.png" });
});

test("分析从待命到生成和长结果，卡片与相邻卡片不跳动", async ({ page }) => {
  await setup(page);
  let pending;
  await page.route("**/api/agents/guide", route => { pending = route; });
  await page.goto("/agents");
  const card = page.locator(".agent-card").first();
  const initial = await card.boundingBox();
  await card.getByRole("button", { name: "开始分析", exact: true }).click();
  await expect(card.getByRole("status")).toContainText("正在整理学习分析");
  expect(Math.abs((await card.boundingBox()).height - initial.height)).toBeLessThan(1);
  await expect.poll(() => Boolean(pending)).toBe(true);
  await pending.fulfill({ json: { result: "先理解概念，再结合课时练习。".repeat(150), toolsUsed: [], sources: [] } });
  await expect(card.locator(".agent-output")).toBeVisible();
  expect(Math.abs((await card.boundingBox()).height - initial.height)).toBeLessThan(1);
  expect(await card.locator(".agent-output").evaluate(node => node.scrollHeight > node.clientHeight)).toBe(true);
  await page.screenshot({ path: "../.runtime/ink-analysis-preview-20261022.png", fullPage: true });
});

test("手机宽度下长回复不横向溢出，生成期间页面不被自动滚动", async ({ page }) => {
  await setup(page); await page.setViewportSize({ width: 390, height: 844 }); await page.goto("/chat");
  await page.getByRole("textbox", { name: "发送给 Agent 的问题" }).fill("解释一个学习概念");
  await page.getByRole("button", { name: "发送", exact: true }).click(); await start(page);
  const scrollY = await page.evaluate(() => window.scrollY);
  await emit(page, 0, "message.delta", { content: "长链接也应自动换行：https://example.test/" + "a".repeat(600) });
  await finish(page);
  await expect(page.locator(".ink-response-copy")).toContainText("长链接也应自动换行");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(Math.abs(await page.evaluate(() => window.scrollY) - scrollY)).toBeLessThan(1);
});
