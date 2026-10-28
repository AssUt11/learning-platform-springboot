import { expect, test } from "@playwright/test";

const tree = [{ label: "软件工程", children: [{ label: "Java", children: [
  { label: "Java基础", learningStage: "foundation" }, { label: "流程控制", learningStage: "foundation" },
  { label: "JDBC", learningStage: "intermediate" }, { label: "集合框架", learningStage: "intermediate" },
  { label: "多线程", learningStage: "advanced" }
] }, { label: "Python", children: [{ label: "Python基础", learningStage: "foundation" }] }] }];
const courses = [
  { id: 7, title: "Java基础入门", subject: "Java", learningStage: "foundation", stageLabel: "基础课程", type: "material", minutes: 30 },
  { id: 8, title: "JDBC应用", subject: "Java", learningStage: "intermediate", stageLabel: "中期课程", type: "material", minutes: 45 },
  { id: 9, title: "Java多线程", subject: "Java", learningStage: "advanced", stageLabel: "进阶课程", type: "material", minutes: 60 }
];
const path = { title: "Java项目学习路线", summary: "先夯实基础，再进行接口与并发实践。", available: true, pathId: "plan-1", createdAt: "2026-10-26T08:00:00Z", source: "snapshot", neo4jSynced: false,
  nodes: courses.map((course, index) => ({ id: `step-${index+1}`, order: index+1, title: course.title, stage: course.learningStage, courseId: course.id, goal: "完成讲解与实践", completionCriteria: "课时学习达到100%", reason: "结合学习目标安排", estimatedMinutes: course.minutes, progressPercent: 0 })),
  links: [{ source: "step-1", target: "step-2" }, { source: "step-2", target: "step-3" }], nextAction: { nodeId: "step-1", title: courses[0].title, courseId: 7, reason: "先完成必要基础" }
};

async function setup(page, { firstTime = true } = {}) {
  const state = { answers: {}, version: 0, status: firstTime ? "draft" : "deferred", activePathId: null, preview: null, generated: 0, adopted: 0, failModel: false, queries: [] };
  const profile = () => ({ answers: state.answers, version: state.version, status: state.status, activePathId: state.activePathId,
    needsPlanning: state.status === "draft" && !state.activePathId,
    ...(state.preview?.version === state.version ? { preview: state.preview } : {}) });
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 63, name: "规划测试学生", role: "student" }));
    localStorage.setItem("learning_token", "planning-token"); localStorage.setItem("learning_preferences", JSON.stringify(["Java"]));
  });
  await page.route("**/api/**", async (route) => {
    const request = route.request(), url = new URL(request.url()), method = request.method();
    if (!url.pathname.startsWith("/api/")) return route.continue();
    let data = {};
    if (url.pathname === "/api/learning/profile") {
      if (method === "PUT") { state.answers = request.postDataJSON(); state.version++; }
      data = profile();
    } else if (url.pathname === "/api/learning/profile/defer") { state.status = "deferred"; data = profile(); }
    else if (url.pathname === "/api/learning/planning/preview") {
      state.generated++;
      if (state.failModel) return route.fulfill({ status: 503, json: { code: 503, message: "模型暂不可用，请稍后重试" } });
      state.preview = { previewId: "plan-1", path, version: state.version }; data = state.preview;
    } else if (url.pathname === "/api/learning/planning/adopt") { state.adopted++; state.activePathId = "plan-1"; state.status = "completed"; data = path; }
    else if (url.pathname === "/api/learning/planning/current") data = { profile: profile(), path: state.activePathId ? path : { available: false } };
    else if (url.pathname.startsWith("/api/learning/paths/")) data = state.activePathId ? path : { available: false };
    else if (url.pathname === "/api/preferences") data = { subjects: [state.answers.subject || "Java"] };
    else if (url.pathname === "/api/knowledge/categories/tree") data = { categories: tree };
    else if (url.pathname === "/api/categories") data = [];
    else if (url.pathname === "/api/knowledge-graph") data = { source: "mysql", nodes: [{ id: "root", label: "Java", type: "root" }], links: [] };
    else if (url.pathname === "/api/learning/knowledge-progress") data = [];
    else if (url.pathname === "/api/courses") {
      state.queries.push(url.searchParams);
      const list = courses.filter((course) => !url.searchParams.get("learningStage") || course.learningStage === url.searchParams.get("learningStage"));
      data = { list, total: list.length, totalPages: 1 };
    }
    await route.fulfill({ json: { code: 200, data } });
  });
  return state;
}

async function answerFive(page) {
  await page.getByRole("button", { name: "Java", exact: true }).click();
  await page.getByRole("button", { name: "继续", exact: true }).click();
  await page.getByRole("button", { name: /学过一些基础/ }).click();
  await page.getByRole("group", { name: "已学知识点" }).getByRole("button", { name: "流程控制", exact: true }).click();
  await page.getByRole("button", { name: "继续", exact: true }).click();
  await page.getByRole("button", { name: /完成项目/ }).click();
  await page.getByRole("button", { name: "继续", exact: true }).click();
  await page.getByRole("button", { name: /中期巩固/ }).click();
  await page.getByRole("button", { name: "继续", exact: true }).click();
  await page.getByRole("button", { name: /30 分钟/ }).click();
  await page.getByRole("button", { name: "查看我的学习档案", exact: true }).click();
}

test("首次进入首页前显示五问选项，预览确认后才采用路径并进入首页", async ({ page }, testInfo) => {
  const state = await setup(page);
  await page.goto("/home");
  await expect(page).toHaveURL(/\/planning$/);
  await expect(page.getByRole("heading", { name: "你现在最想学习什么？" })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("planning-choices.png"), fullPage: true });
  await answerFive(page);
  expect(state.answers).toMatchObject({ subject: "Java", background: "basics", goal: "project", targetStage: "intermediate", dailyMinutes: 30, knownTopics: ["流程控制"] });
  await page.getByRole("button", { name: "生成我的学习路径", exact: true }).click();
  await expect(page.getByRole("button", { name: "采用路径并进入首页", exact: true })).toBeVisible();
  expect(state.adopted).toBe(0); expect(state.activePathId).toBeNull();
  await expect(page.locator(".plan-stage")).toHaveCount(3);
  await page.screenshot({ path: testInfo.outputPath("planning-preview.png"), fullPage: true });
  await page.getByRole("button", { name: "采用路径并进入首页", exact: true }).click();
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByRole("region", { name: "我的学习计划" })).toContainText("Java基础入门");
  expect(state.adopted).toBe(1);
  await page.reload(); await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByRole("region", { name: "我的学习计划" })).toContainText("Java基础入门");
});

test("回答自动保存，刷新后从未回答的问题继续", async ({ page }) => {
  await setup(page); await page.goto("/planning");
  await page.getByRole("button", { name: "Python", exact: true }).click();
  await page.getByRole("button", { name: "继续", exact: true }).click();
  await page.getByRole("button", { name: /完全没学过/ }).click();
  await page.getByRole("button", { name: "继续", exact: true }).click();
  await expect(page.getByRole("heading", { name: "这次学习希望达到什么目标？" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "这次学习希望达到什么目标？" })).toBeVisible();
  await expect(page.locator(".planning-chat-log")).toContainText("Python");
});

test("模型失败保留答案，用户可重试或稍后规划，不会卡在首页前", async ({ page }) => {
  const state = await setup(page); state.failModel = true;
  await page.goto("/planning"); await answerFive(page);
  await page.getByRole("button", { name: "生成我的学习路径", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("模型暂不可用");
  expect(state.answers.subject).toBe("Java");expect(state.adopted).toBe(0);
  await page.getByRole("button", { name: "稍后规划，先进入首页", exact: true }).click();
  await expect(page).toHaveURL(/\/home$/); await page.reload(); await expect(page).toHaveURL(/\/home$/);
});

test("自定义时间需要有效分钟数，选项可返回修改", async ({ page }) => {
  await setup(page); await page.goto("/planning"); await answerFive(page);
  await page.getByRole("button", { name: "修改回答", exact: true }).click();
  for (let i=0;i<4;i++) await page.getByRole("button", { name: "继续", exact: true }).click();
  await page.getByRole("button", { name: /自定义时间/ }).click();
  await page.getByRole("spinbutton", { name: "每天学习分钟数" }).fill("0");
  await expect(page.getByRole("button", { name: "查看我的学习档案", exact: true })).toBeDisabled();
  await page.getByRole("spinbutton", { name: "每天学习分钟数" }).fill("45");
  await page.getByRole("button", { name: "查看我的学习档案", exact: true }).click();
  await expect(page.locator(".planning-profile-review")).toContainText("45 分钟 / 天");
});

test("课程按基础、中期、进阶筛选并显示对应标签", async ({ page }) => {
  const state = await setup(page, { firstTime: false }); await page.goto("/home");
  await page.getByRole("group", { name: "课程学习阶段" }).getByRole("button", { name: "中期课程", exact: true }).click();
  await expect(page.locator(".studio-course-grid .resource-card")).toHaveCount(1);
  await expect(page.locator(".studio-course-grid")).toContainText("JDBC应用");
  expect(state.queries.at(-1).get("learningStage")).toBe("intermediate");
  await page.getByRole("group", { name: "课程学习阶段" }).getByRole("button", { name: "进阶课程", exact: true }).click();
  await expect(page.locator(".studio-course-grid")).toContainText("Java多线程");
});

test("方向图谱显示三个阶段分组，筛选后只显示相应知识点", async ({ page }, testInfo) => {
  await setup(page, { firstTime: false }); await page.goto("/graph?view=catalog");
  await expect(page.locator(".graph-node.stage-node")).toHaveCount(3);
  await page.getByRole("group", { name: "图谱学习阶段" }).getByRole("button", { name: "中期课程", exact: true }).click();
  await expect(page.locator('.graph-node[data-knowledge-name="JDBC"]')).toBeVisible();
  await expect(page.locator('.graph-node[data-knowledge-name="Java基础"]')).toHaveCount(0);
  await expect(page.locator('.graph-node[data-knowledge-name="多线程"]')).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath("staged-knowledge-graph.png"), fullPage: true });
});
