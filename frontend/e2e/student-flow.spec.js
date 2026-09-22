import { expect, test } from "@playwright/test";
import mysql from "mysql2/promise";

// This flow creates and removes a published fixture course. Require a disposable database.
test("学生收藏、笔记、评价、课时切换和继续学习的真实接口流程", async ({ page, request }) => {
  test.skip(!/^learning_platform_(buttons|e2e)_/.test(process.env.E2E_DB_NAME || ""), "请使用隔离测试数据库");
  test.setTimeout(90_000);
  const prefix = `e2e-buttons-${Date.now()}`;
  const password = process.env.E2E_ACCOUNT_PASSWORD || "E2e-school-2026";
  const emails = [`${prefix}-teacher@zhidu.local`, `${prefix}-student@zhidu.local`];
  let courseId;
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const api = async (path, options = {}, token) => {
    const response = await request.fetch(`/api${path}`, {
      ...options, headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    const payload = await response.json();
    expect(response.ok(), JSON.stringify(payload)).toBeTruthy();
    return payload.code === 200 ? payload.data : payload;
  };
  try {
    const teacher = await api("/auth/register", { method: "POST", data: {
      email: emails[0], name: "按钮测试教师", password, role: "teacher",
      teacherRegistrationCode: process.env.E2E_TEACHER_REGISTRATION_CODE || "teacher-dev-2026"
    } });
    const student = await api("/auth/register", { method: "POST", data: {
      email: emails[1], name: "按钮测试学生", password, role: "student"
    } });
    await api("/preferences", { method: "POST", data: { subjects: ["软件工程"] } }, student.token);
    const created = await api("/teacher/resources", { method: "POST", multipart: {
      title: `${prefix} Java学习课程`, subject: "Java", type: "material", level: "入门",
      description: "按钮测试课程", content: "第一节：学习Java的基础概念并完成阅读。"
    } }, teacher.token);
    courseId = created.courseId;
    const detail = await api(`/courses/${courseId}`, {}, teacher.token);
    const chapter = detail.outline.chapters[0];
    const firstLesson = chapter.lessons[0];
    const second = await api(`/teacher/chapters/${chapter.id}/lessons`, { method: "POST", data: {
      title: "第二节：练习与巩固", lessonType: "article", durationSeconds: 60
    } }, teacher.token);
    await api(`/teacher/lessons/${second.lessonId}/assets`, { method: "POST", multipart: {
      assetType: "article", contentText: "第二节：用自己的话总结学习收获。"
    } }, teacher.token);
    const document = await api(`/teacher/chapters/${chapter.id}/lessons`, { method: "POST", data: {
      title: "第三节：学习文档", lessonType: "document", durationSeconds: 60
    } }, teacher.token);
    await api(`/teacher/lessons/${document.lessonId}/assets`, { method: "POST", multipart: {
      assetType: "document", file: { name: "button-test.txt", mimeType: "text/plain", buffer: Buffer.from("Java practice document") }
    } }, teacher.token);
    const admin = await api("/auth/login", { method: "POST", data: {
      email: process.env.E2E_ADMIN_EMAIL || "admin@zhidu.local",
      password: process.env.E2E_ADMIN_PASSWORD || "admin-dev-2026"
    } });
    await api(`/admin/resources/${courseId}/review`, { method: "POST", data: { action: "approve" } }, admin.token);

    await page.goto("/login");
    await page.getByPlaceholder("name@example.com").fill(emails[1]);
    await page.getByPlaceholder("请输入密码").fill(password);
    await page.getByRole("button", { name: "登录系统" }).click();
    await expect(page.getByRole("heading", { name: /让知识形成结构/ })).toBeVisible();
    const card = page.locator(".resource-card").filter({ hasText: `${prefix} Java学习课程` });
    await card.getByRole("button", { name: "收藏课程", exact: true }).click();
    await expect(card.getByRole("button", { name: "取消收藏", exact: true })).toBeVisible();
    await page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: "我的学习" }).click();
    await page.getByRole("button", { name: `查看收藏课程：${prefix} Java学习课程` }).click();
    await expect(page).toHaveURL(new RegExp(`/course/${courseId}$`));

    const tabs = page.getByRole("navigation", { name: "课程内容导航" });
    await tabs.getByRole("button", { name: /^笔记/ }).click();
    await page.getByPlaceholder("记录关键概念、代码思路或还需要验证的问题...").fill("按钮测试：笔记需要持久保存");
    await page.getByRole("button", { name: "保存笔记" }).click();
    await expect(page.locator(".note-list")).toContainText("按钮测试：笔记需要持久保存");
    await page.reload();
    await tabs.getByRole("button", { name: /^笔记/ }).click();
    await expect(page.locator(".note-list")).toContainText("按钮测试：笔记需要持久保存");
    await page.locator(".note-list").getByRole("button", { name: "删除", exact: true }).click();
    await expect(page.getByText("开始记录第一条笔记")).toBeVisible();

    await tabs.getByRole("button", { name: /^评价/ }).click();
    await page.locator(".rating-picker button").nth(3).click();
    await page.getByPlaceholder("分享课程最有价值的部分，以及你希望继续改进的地方。").fill("按钮测试：课程评价提交成功");
    await page.getByRole("button", { name: "提交评价" }).click();
    await expect(page.locator(".review-list")).toContainText("按钮测试：课程评价提交成功");
    await expect(page.locator(".review-list .filled")).toHaveCount(4);

    await page.getByRole("button", { name: "开始学习", exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/learn/${courseId}/${firstLesson.id}$`));
    await page.getByRole("button", { name: "完成本课", exact: true }).click();
    await expect(page.getByRole("button", { name: "本课时已完成", exact: true })).toBeDisabled();
    await page.locator(".learn-controls").getByRole("button", { name: /下一节/ }).click();
    await expect(page.getByRole("heading", { name: "第二节：练习与巩固" })).toBeVisible();
    await page.getByRole("button", { name: "完成本课", exact: true }).click();
    await expect(page.getByRole("button", { name: "本课时已完成", exact: true })).toBeDisabled();
    await page.locator(".learn-controls").getByRole("button", { name: /上一节/ }).click();
    await expect(page).toHaveURL(new RegExp(`/learn/${courseId}/${firstLesson.id}$`));
    await page.locator(".learn-outline").getByRole("button", { name: /第三节：学习文档/ }).click();
    const documentLink = page.getByRole("link", { name: "打开课程文档" });
    await expect(documentLink).toBeVisible();
    const documentResponse = await request.get(await documentLink.getAttribute("href"));
    expect(documentResponse.ok()).toBeTruthy();
    expect(await documentResponse.text()).toContain("Java practice document");
    await page.getByRole("button", { name: "完成本课", exact: true }).click();
    await expect(page.locator(".learn-course-progress")).toContainText("100%");
    await page.reload();
    await expect(page.locator(".learn-course-progress")).toContainText("100%");
    await page.getByRole("navigation", { name: "主导航" }).getByRole("button", { name: "我的学习" }).click();
    await page.getByRole("button", { name: "继续：第三节：学习文档" }).click();
    await expect(page).toHaveURL(new RegExp(`/learn/${courseId}/${document.lessonId}$`));
    expect(errors).toEqual([]);
  } finally {
    const connection = await mysql.createConnection({
      host: process.env.E2E_DB_HOST || "127.0.0.1", port: Number(process.env.E2E_DB_PORT || 3306),
      user: process.env.E2E_DB_USERNAME || "root", password: process.env.E2E_DB_PASSWORD || "root",
      database: process.env.E2E_DB_NAME
    });
    try {
      await connection.beginTransaction();
      await connection.execute("delete from users where email in (?,?)", emails);
      if (courseId) {
        await connection.execute("delete from course_legacy_migration where resource_id=?", [courseId]);
        await connection.execute("delete a from lesson_assets a join course_lessons l on l.id=a.lesson_id join course_chapters ch on ch.id=l.chapter_id join courses c on c.id=ch.course_id where c.legacy_resource_id=?", [courseId]);
        await connection.execute("delete l from course_lessons l join course_chapters ch on ch.id=l.chapter_id join courses c on c.id=ch.course_id where c.legacy_resource_id=?", [courseId]);
        await connection.execute("delete ch from course_chapters ch join courses c on c.id=ch.course_id where c.legacy_resource_id=?", [courseId]);
        await connection.execute("delete from resources where id=?", [courseId]);
        await connection.execute("delete from courses where legacy_resource_id=?", [courseId]);
      }
      await connection.commit();
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      await connection.end();
    }
  }
});
