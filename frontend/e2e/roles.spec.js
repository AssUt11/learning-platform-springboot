import { expect, test } from "@playwright/test";
import mysql from "mysql2/promise";

const testPassword = process.env.E2E_ACCOUNT_PASSWORD || "E2e-school-2026";
const teacherCode = process.env.E2E_TEACHER_REGISTRATION_CODE || "teacher-dev-2026";

function uniqueEmail(role, workerIndex) {
  return `e2e-${role}-${Date.now()}-${workerIndex}@zhidu.local`;
}

async function cleanupUser(email) {
  const connection = await mysql.createConnection({
    host: process.env.E2E_DB_HOST || "127.0.0.1",
    port: Number(process.env.E2E_DB_PORT || 3306),
    user: process.env.E2E_DB_USERNAME || "root",
    password: process.env.E2E_DB_PASSWORD || "root",
    database: process.env.E2E_DB_NAME || "learning_platform"
  });
  try {
    await connection.execute("delete from users where email=? and email like 'e2e-%@zhidu.local'", [email]);
  } finally {
    await connection.end();
  }
}

function collectPageErrors(page) {
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

async function openRegistration(page) {
  await page.goto("/");
  await page.getByRole("button", { name: "注册", exact: true }).click();
  await expect(page.getByRole("heading", { name: "创建你的学习空间" })).toBeVisible();
}

test("学生注册、兴趣选择和首页主流程", async ({ page }, testInfo) => {
  const email = uniqueEmail("student", testInfo.workerIndex);
  const pageErrors = collectPageErrors(page);
  try {
    await openRegistration(page);
    await page.getByPlaceholder("请输入姓名").fill("端到端学生");
    await page.getByPlaceholder("name@example.com").fill(email);
    await page.getByPlaceholder("请输入密码").fill(testPassword);
    await page.getByRole("button", { name: "学生", exact: true }).click();
    await page.getByRole("button", { name: "创建账号" }).click();

    await expect(page.getByRole("heading", { name: "选择你感兴趣的学习方向" })).toBeVisible();
    await page.getByRole("button", { name: /软件工程/ }).click();
    await page.getByRole("button", { name: "保存选择，进入学习空间" }).click();
    await expect(page.getByRole("heading", { name: /让知识形成结构/ })).toBeVisible();
    expect(pageErrors).toEqual([]);
  } finally {
    await cleanupUser(email);
  }
});

test("教师注册后进入课程创作工作台", async ({ page }, testInfo) => {
  const email = uniqueEmail("teacher", testInfo.workerIndex);
  const pageErrors = collectPageErrors(page);
  try {
    await openRegistration(page);
    await page.getByPlaceholder("请输入姓名").fill("端到端教师");
    await page.getByPlaceholder("name@example.com").fill(email);
    await page.getByPlaceholder("请输入密码").fill(testPassword);
    await page.getByRole("button", { name: "教师", exact: true }).click();
    await page.getByPlaceholder("请输入教师注册码").fill(teacherCode);
    await page.getByRole("button", { name: "创建账号" }).click();

    await expect(page.getByRole("heading", { name: /让知识形成结构/ })).toBeVisible();
    await page.getByRole("button", { name: "课程上传/管理" }).click();
    await expect(page.getByRole("heading", { name: "课程创作、审核反馈与学习洞察" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "提交视频或文字课程" })).toBeVisible();
    expect(pageErrors).toEqual([]);
  } finally {
    await cleanupUser(email);
  }
});

test("管理员登录后进入内容治理中心", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto("/");
  await page.getByPlaceholder("name@example.com").fill(process.env.E2E_ADMIN_EMAIL || "admin@zhidu.local");
  await page.getByPlaceholder("请输入密码").fill(process.env.E2E_ADMIN_PASSWORD || "admin-dev-2026");
  await page.getByRole("button", { name: "登录系统" }).click();

  await expect(page.getByRole("heading", { name: "内容治理与站点运行中心" })).toBeVisible();
  await expect(page.getByRole("button", { name: /课程与视频/ })).toBeVisible();
  expect(pageErrors).toEqual([]);
});
