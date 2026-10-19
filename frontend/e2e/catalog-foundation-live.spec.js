import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";

test("新增30门基础课可查可读，考研筛选、课时完成及刷新恢复", async ({ page, request }, testInfo) => {
  test.skip(process.env.E2E_CATALOG_FLOW !== "1", "需要独立数据库和已导入的课程包");
  expect(process.env.E2E_DB_NAME).toMatch(/^learning_platform_e2e_/);
  test.setTimeout(120_000);
  const pack=JSON.parse(readFileSync(new URL("../../backend/src/main/resources/catalog/foundation-courses.json", import.meta.url),"utf8"));
  const listed=await (await request.get("/api/courses?pageSize=50")).json();
  for(const course of pack.courses) {
    const row=listed.data.list.find(item=>item.title===course.title);
    expect(row,course.title).toBeTruthy(); expect(row.lessonCount).toBe(2);
    const detail=await (await request.get(`/api/courses/${row.id}`)).json();
    expect(detail.data.outline.chapters).toHaveLength(1);
    expect(detail.data.outline.chapters[0].lessons.every(item=>item.lessonType==="article"&&item.assetReady)).toBe(true);
  }
  const email=`e2e-catalog-${Date.now()}@zhidu.local`, password="E2e-school-2026";
  const created=await (await request.post("/api/auth/register",{data:{name:"课程验证学生",email,password,role:"student"}})).json();
  await request.post("/api/preferences",{headers:{Authorization:`Bearer ${created.token}`},data:{subjects:["考研数学"]}});
  await page.goto("/login");
  await page.getByPlaceholder("name@example.com").fill(email); await page.getByPlaceholder("请输入密码").fill(password);
  await page.getByRole("button",{name:"登录系统",exact:true}).click();
  await page.getByRole("button",{name:"考研备考",exact:true}).click();
  await expect(page.locator(".resource-card")).toHaveCount(10);
  await page.screenshot({path:testInfo.outputPath("postgraduate-courses.png"),fullPage:true});
  const title=pack.courses[0].title;
  await page.locator(".resource-card").filter({has:page.getByRole("heading",{name:title,exact:true})}).getByRole("button",{name:"查看课程",exact:true}).click();
  await page.getByRole("button",{name:"开始学习",exact:true}).click();
  await expect(page.locator(".learn-article-copy")).toContainText("差商");
  await page.getByRole("button",{name:"完成本课",exact:true}).click();
  await expect(page.getByRole("button",{name:"本课时已完成",exact:true})).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button",{name:"本课时已完成",exact:true})).toBeVisible();
  await page.locator(".learn-controls button.next").click();
  await expect(page.locator(".learn-article-copy")).toContainText("有符号面积");
  await page.screenshot({path:testInfo.outputPath("postgraduate-lesson.png"),fullPage:true});
});
