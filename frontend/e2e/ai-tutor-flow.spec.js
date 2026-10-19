import { expect, test } from "@playwright/test";

// Retain synthetic fixtures in the disposable database for manual verification.
test("真实模型伴学：流式回答、会话、反馈、分析报告和学生隔离", async ({ page, request }, testInfo) => {
  test.skip(process.env.E2E_AI_FLOW !== "1", "需要真实模型和隔离数据库");
  expect(process.env.E2E_DB_NAME).toMatch(/^learning_platform_e2e_/);
  test.setTimeout(360_000);
  const prefix = `e2e-ai-${Date.now()}`;
  const password = "E2e-school-2026";
  const api = async (path, options = {}, token) => {
    const response = await request.fetch(`/api${path}`, {
      ...options, timeout: 150_000,
      headers: { ...options.headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) }
    });
    const payload = await response.json();
    expect(response.ok(), JSON.stringify(payload)).toBeTruthy();
    return payload.code === 200 ? payload.data : payload;
  };
  const createUser = role => api("/auth/register", { method: "POST", data: {
    email: `${prefix}-${role}@zhidu.local`, name: `AI流程${role}`, password, role,
    ...(role === "teacher" ? { teacherRegistrationCode: process.env.E2E_TEACHER_REGISTRATION_CODE || "teacher-dev-2026" } : {})
  } });
  const teacher = await createUser("teacher");
  const student = await createUser("student");
  const other = await api("/auth/register", { method: "POST", data: {
    email: `${prefix}-other@zhidu.local`, name: "其他学生", password, role: "student"
  } });
  await api("/preferences", { method: "POST", data: { subjects: ["Java"] } }, student.token);
  const created = await api("/teacher/resources", { method: "POST", multipart: {
    title: `${prefix} Java变量入门`, subject: "Java", type: "material", level: "入门",
    description: "真实模型联调课程", content: "Java变量需要先声明类型，再赋值。例如 int count = 3; 表示整数变量count的值为3。"
  } }, teacher.token);
  const admin = await api("/auth/login", { method: "POST", data: {
    email: process.env.E2E_ADMIN_EMAIL || "admin@zhidu.local", password: process.env.E2E_ADMIN_PASSWORD || "admin-dev-2026"
  } });
  await api(`/admin/resources/${created.courseId}/review`, { method: "POST", data: { action: "approve" } }, admin.token);
  const detail = await api(`/courses/${created.courseId}`, {}, student.token);
  const lessonId = detail.outline.chapters[0].lessons[0].id;
  const health = await api("/ai/health", {}, admin.token);
  expect(health.modelService.available).toBe(true);
  expect(health.neo4j.available).toBe(true);
  const sync = await api("/admin/knowledge/prerequisites/sync", { method: "POST", data: {} }, admin.token);
  expect(sync.available).toBe(true);
  expect(sync.syncedCount).toBe(sync.relationCount);
  expect(sync.syncedCount).toBeGreaterThan(0);

  await page.goto("/login");
  await page.getByPlaceholder("name@example.com").fill(`${prefix}-student@zhidu.local`);
  await page.getByPlaceholder("请输入密码").fill(password);
  await page.getByRole("button", { name: "登录系统" }).click();
  await expect(page.getByRole("heading", { name: /让知识形成结构/ })).toBeVisible();
  await page.goto(`/learn/${created.courseId}/${lessonId}`);
  const panel = page.getByRole("region", { name: "AI 伴学", exact: true });
  await panel.getByPlaceholder("针对当前课时提问，Enter 发送，Shift+Enter 换行").fill("请根据本课用两句话解释Java变量，并举一个简短例子。");
  await panel.getByRole("button", { name: "发送", exact: true }).click();
  await expect(panel.getByRole("button", { name: "回答有帮助" })).toBeVisible({ timeout: 150_000 });
  await expect(panel.locator(".ai-tutor-message.assistant p")).not.toHaveText("");
  await expect(panel.locator(".ai-tutor-error")).toHaveCount(0);
  await panel.getByRole("button", { name: "回答有帮助" }).click();
  await expect(panel.getByRole("button", { name: "回答有帮助" })).toHaveClass(/active/);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: testInfo.outputPath("real-ai-tutor.png"), fullPage: true });
  const conversations = await api("/ai/conversations", {}, student.token);
  expect(conversations).toHaveLength(1);
  const conversation = await api(`/ai/conversations/${conversations[0].id}`, {}, student.token);
  expect(conversation.messages.filter(message => message.role === "ASSISTANT" && message.status === "completed")).toHaveLength(1);
  expect((await request.get(`/api/ai/conversations/${conversations[0].id}`, { headers: { Authorization: `Bearer ${other.token}` } })).status()).toBe(404);

  for (const scene of ["guide", "recommend", "analysis"]) {
    const result = await api(`/agents/${scene}`, { method: "POST", data: {} }, student.token);
    expect(result.result).toBeTruthy();
    expect(result.provider).toBe("llamafactory");
    expect(result.agentMode).toBe("prefetched-evidence");
    if (scene === "analysis") {
      const report = await request.get(result.downloadUrl, { headers: { Authorization: `Bearer ${student.token}` } });
      expect(report.ok()).toBeTruthy();
      expect((await report.body()).subarray(0, 2).toString()).toBe("PK");
      expect((await request.get(result.downloadUrl, { headers: { Authorization: `Bearer ${other.token}` } })).status()).toBe(404);
      const reports = await api("/agents/reports", {}, student.token);
      expect(reports.reports.some(row => row.id === result.reportId)).toBe(true);
    }
  }
});
