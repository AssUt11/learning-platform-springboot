import { expect, test } from "@playwright/test";

async function setup(page, { eligible = true, advanced = false } = {}) {
  const state = { quiz: null, generated: 0, smartReads: 0, complete: false, eligible };
  const profile = () => ({
    eligible: state.eligible, studiedTextLessons: state.eligible ? 1 : 0, totalTextLessons: 2,
    sourceLessonId: 35, sourceLessonTitle: "事务与索引精讲", difficulty: advanced ? "进阶" : "基础",
    reason: !state.eligible ? "先学习本课程的图文讲义，再开始按学习情况出题。" : state.quiz?.submitted ? "最新作答已计入，本轮继续检查事务的应用。" : advanced ? "基础和巩固表现稳定，尝试进阶应用。" : "你在“事务与索引精讲”最近 4 题的正确率为 25%，本轮优先复习易错内容。",
    recentAccuracy: advanced ? 100 : state.quiz?.submitted ? 50 : 25, recentQuestionCount: 4,
    weakTopics: advanced || state.quiz?.submitted ? [] : [{ title: "事务与索引精讲", accuracy: 25 }]
  });
  const question = { stem: "转账操作应使用什么保证一致性？", options: ["事务", "字体", "颜色", "页面宽度"], answer: 0, explanation: "事务把相关变更组织为整体。", reference: "事务用于保证一致性。", sourceLessonId: 35, sourceLessonTitle: "事务与索引精讲" };
  const publicQuiz = () => state.quiz && ({ ...state.quiz, questions: [{ ...(state.quiz.submitted ? question : { stem: question.stem, options: question.options, sourceLessonTitle: question.sourceLessonTitle }), personalization: state.quiz.personalization }] });
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 51, name: "个性化测试学生", role: "student" }));
    localStorage.setItem("learning_token", "smart-test-token");
    localStorage.setItem("learning_preferences", JSON.stringify(["数据库"]));
  });
  await page.route("**/api/**", async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/courses/12") data = { id: 12, title: "数据库课程", subject: "数据库", outline: { chapters: [{ id: 1, title: "事务与索引", lessons: [
      { id: 34, title: "课程视频", lessonType: "video", durationSeconds: 106 },
      { id: 35, title: "事务与索引精讲", lessonType: "article", contentText: "## 核心讲解\n事务用于保证一致性。请先观察转账操作，再分析失败时如何保持两个账户金额一致。\n## 从基础到进阶\n基础：识别事务。巩固：选择索引。进阶：分析并发。" }
    ] }] } };
    else if (path === "/api/learning/courses/12/progress") data = { totalLessons: 2, completedLessons: state.complete ? 1 : 0, progressPercent: state.complete ? 50 : 1, lessons: [{ lessonId: 34, progressPercent: 0 }, { lessonId: 35, completed: state.complete, progressPercent: state.complete ? 100 : 1 }] };
    else if (path === "/api/learning/lessons/35/progress") { state.complete = req.postDataJSON().eventType === "completed" || state.complete; data = { lessonId: 35, completed: state.complete, progressPercent: state.complete ? 100 : 1 }; }
    else if (path === "/api/practice/courses/12/smart/latest") { state.smartReads++; data = { personalization: profile(), quiz: publicQuiz(), generating: false }; }
    else if (path === "/api/practice/courses/12/smart/generate") {
      state.generated++;
      state.quiz = { available: true, quizId: `smart-${state.generated}`, difficulty: advanced ? "进阶" : "基础", submitted: false, answers: [], scope: "smart", personalization: profile() };
      data = publicQuiz();
    } else if (/\/api\/practice\/smart-\d+\/submit/.test(path)) { state.quiz = { ...state.quiz, submitted: true, answers: req.postDataJSON().answers, score: 100 }; data = publicQuiz(); }
    else if (path === "/api/practice/courses/12/assessment") data = { unlocked: false, totalLessons: 2, completedLessons: 0, nextDifficulty: "基础", stages: [], quiz: null };
    else if (path.endsWith("/latest")) data = { available: false };
    else if (path === "/api/categories") data = [];
    else if (path === "/api/preferences") data = { subjects: ["数据库"] };
    await route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/learn/12/35");
  const region = page.getByRole("region", { name: "AI 课后练习", exact: true });
  await region.getByRole("button", { name: "按学习情况", exact: true }).click();
  return { state, region };
}

test("按近期错题选课时，保存后更新出题建议，刷新可恢复成绩", async ({ page }) => {
  const { state, region } = await setup(page);
  await expect(region.getByRole("region", { name: "个性化出题依据" })).toContainText("正确率为 25%");
  await region.getByRole("button", { name: "生成个性化练习" }).click();
  await expect(region).toContainText("考查课时：事务与索引精讲");
  await expect(region).not.toContainText("讲义依据");
  await region.getByRole("radio").first().check();
  await region.getByRole("button", { name: "提交并查看解析" }).click();
  await expect(region).toContainText("本次自测 100 分");
  await expect(region).toContainText("最新作答已计入");
  await expect(region).toContainText("讲义依据：事务用于保证一致性。");
  await page.reload();
  await region.getByRole("button", { name: "按学习情况", exact: true }).click();
  await expect(region).toContainText("本次自测 100 分");
  expect(state.generated).toBe(1);
  await expect(page.getByRole("region", { name: "AI 课程测试", exact: true }).getByRole("button", { name: "向 AI 索要基础题" })).toBeDisabled();
});

test("没有已学资料时说明原因并阻止生成，表现稳定时推荐进阶", async ({ page }) => {
  const { region } = await setup(page, { eligible: false });
  await expect(region).toContainText("先学习本课程的图文讲义");
  await expect(region.getByRole("button", { name: "生成个性化练习" })).toBeDisabled();
  await expect(region).not.toContainText("优先复习：");
});

test("刷新学习进度保留本轮选择，切换出题方式清除旧范围题目", async ({ page }) => {
  const { state, region } = await setup(page, { advanced: true });
  await expect(region).toContainText("建议难度进阶");
  await region.getByRole("button", { name: "生成个性化练习" }).click();
  await region.getByRole("radio").first().check();
  const reads = state.smartReads;
  await page.getByRole("button", { name: "完成本课", exact: true }).click();
  await expect.poll(() => state.smartReads).toBeGreaterThan(reads);
  await expect(region.getByRole("radio").first()).toBeChecked();
  await region.getByRole("button", { name: "当前课时", exact: true }).click();
  await expect(region.getByRole("radio")).toHaveCount(0);
  await region.getByRole("button", { name: "按学习情况", exact: true }).click();
  await expect(region.getByRole("radio")).toHaveCount(4);
  expect(state.generated).toBe(1);
});

test("出题依据和题目在三个屏幕宽度下可完整阅读", async ({ page }, testInfo) => {
  const { region } = await setup(page);
  await region.getByRole("button", { name: "生成个性化练习" }).click();
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await region.scrollIntoViewIfNeeded();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`smart-${width}.png`) });
  }
});
