import { expect, test } from "@playwright/test";

const questions = [
  { stem: "事务用于保证什么？", options: ["一致性", "字体大小", "页面宽度", "图片颜色"], answer: 0, explanation: "事务将相关变更组织为整体。", reference: "事务用于保证一致性。" },
  { stem: "索引用于改善哪项性能？", options: ["图片渲染", "数据查找", "语音播放", "文本排版"], answer: 1, explanation: "索引可以减少查找扫描。", reference: "索引用于改善数据查找。" }
];

async function setup(page, { complete = false, pendingVideos = 0, failLoad = false, temporaryVideo = false } = {}) {
  const state = { complete, pendingVideos, failLoad, quiz: null, best: {}, generated: [], submitted: [], videoSaves: [] };
  const stages = ["基础", "巩固", "进阶"];
  const publicQuiz = () => state.quiz && { ...state.quiz, questions: questions.map(q => state.quiz.submitted ? q : { stem: q.stem, options: q.options }) };
  const assessment = () => {
    const nextDifficulty = stages.find(name => (state.best[name] ?? -1) < 80) || "进阶";
    return {
      totalLessons: 2, completedLessons: state.complete ? 2 : 1, pendingVideos: state.pendingVideos,
      unlocked: state.complete && state.pendingVideos === 0, nextDifficulty, generating: false,
      stages: stages.map((difficulty, index) => ({ difficulty, unlocked: index === 0 || stages.slice(0, index).every(name => state.best[name] >= 80), passed: state.best[difficulty] >= 80, bestScore: state.best[difficulty] ?? null })),
      quiz: publicQuiz()
    };
  };
  await page.addInitScript(() => {
    localStorage.setItem("learning_user", JSON.stringify({ id: 51, name: "课程测试学生", role: "student" }));
    localStorage.setItem("learning_token", "course-assessment-token");
    localStorage.setItem("learning_preferences", JSON.stringify(["数据库"]));
  });
  await page.route("**/api/**", async route => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    if (!path.startsWith("/api/")) return route.continue();
    let data = {};
    if (path === "/api/courses/12") data = {
      id: 12, title: "数据库：视频与图文学习", subject: "数据库",
      outline: { chapters: [{ id: 1, title: "事务与索引", lessons: [
        { id: 34, title: "事务视频", lessonType: "video", durationSeconds: 106, assets: temporaryVideo ? [{ id: 9, assetType: "video", fileName: "临时演示视频 A.mp4", transcodingStatus: "ready" }] : [] },
        { id: 35, title: "事务与索引精讲", lessonType: "article", contentText: "学习目标：理解事务与索引。\n\n## 学习脉络\n流程图：定义事务 → 观察执行 → 核对结果\n\n## 分步案例\n事务用于保证一致性。索引用于改善数据查找。\n\n## 从基础到进阶\n基础：识别事务。巩固：选择索引。进阶：分析并发。" }
      ] }] }
    };
    else if (path === "/api/learning/courses/12/progress") data = {
      totalLessons: 2, completedLessons: state.complete ? 2 : 1, progressPercent: state.complete ? 100 : 50,
      lessons: [{ lessonId: 34, completed: true, progressPercent: 100 }, { lessonId: 35, completed: state.complete, progressPercent: state.complete ? 100 : 0 }]
    };
    else if (path === "/api/learning/lessons/34/playback") data = { lessonId: 34, assetId: 9, playable: true, videoUrl: "/recording-fixture.mp4" };
    else if (path === "/api/learning/lessons/34/progress") {
      const body = req.postDataJSON();
      state.videoSaves.push(body);
      data = { lessonId: 34, completed: body.positionSeconds >= 106, progressPercent: Math.min(100, Math.floor(body.positionSeconds / 106 * 100)) };
    } else if (path === "/api/learning/lessons/35/progress") {
      const body = req.postDataJSON();
      if (body.eventType === "completed") state.complete = true;
      data = { lessonId: 35, completed: state.complete, progressPercent: state.complete ? 100 : 1 };
    } else if (path === "/api/practice/courses/12/assessment") {
      if (state.failLoad) return route.fulfill({ status: 503, json: { code: 503, message: "测试状态读取失败" } });
      data = assessment();
    } else if (path === "/api/practice/courses/12/assessment/generate") {
      const { difficulty } = req.postDataJSON();
      state.generated.push(difficulty);
      state.quiz = { available: true, quizId: `quiz-${state.generated.length}`, difficulty, submitted: false, scope: "course", answers: [] };
      data = publicQuiz();
    } else if (/^\/api\/practice\/quiz-\d+\/submit$/.test(path)) {
      const { answers } = req.postDataJSON();
      state.submitted.push(answers);
      const score = questions.filter((q, index) => q.answer === answers[index]).length * 50;
      state.quiz = { ...state.quiz, submitted: true, answers, score };
      state.best[state.quiz.difficulty] = Math.max(state.best[state.quiz.difficulty] || 0, score);
      data = publicQuiz();
    } else if (path === "/api/preferences") data = { subjects: ["数据库"] };
    else if (path.includes("/latest")) data = { available: false, generating: false };
    else if (path === "/api/categories") data = [];
    return route.fulfill({ json: { code: 200, data } });
  });
  await page.goto("/learn/12/35");
  return state;
}

const panel = page => page.getByRole("region", { name: "AI 课程测试", exact: true });

test("视频图文全部完成后才开放整课测试，未交卷刷新可恢复", async ({ page }) => {
  const state = await setup(page);
  await expect(page.getByRole("figure")).toContainText("定义事务");
  await page.getByRole("link", { name: "课程测试 · 基础到进阶" }).click();
  await expect(panel(page).getByRole("button", { name: "向 AI 索要基础题" })).toBeDisabled();
  await page.getByRole("button", { name: "完成本课", exact: true }).click();
  await expect(panel(page).getByRole("button", { name: "向 AI 索要基础题" })).toBeEnabled();
  await panel(page).getByRole("button", { name: "向 AI 索要基础题" }).click();
  await expect(panel(page).getByRole("group")).toHaveCount(2);
  await expect(panel(page)).not.toContainText("讲义依据");
  await page.reload();
  await expect(panel(page).getByRole("group")).toHaveCount(2);
  expect(state.generated).toEqual(["基础"]);
});

test("按基础、巩固、进阶逐级开放，低分需重练，成绩与解析保存", async ({ page }) => {
  const state = await setup(page, { complete: true });
  const region = panel(page);
  await expect(region.getByRole("button", { name: /进阶.*上一阶段/ })).toBeDisabled();
  await region.getByRole("button", { name: "向 AI 索要基础题" }).click();
  await region.getByRole("radio").nth(0).check();
  await region.getByRole("radio").nth(4).check();
  await region.getByRole("button", { name: "提交并查看解析" }).click();
  await expect(region).toContainText("本次自测 50 分");
  await expect(region.getByRole("button", { name: /巩固.*上一阶段/ })).toBeDisabled();
  await expect(region).toContainText("讲义依据：事务用于保证一致性。");
  for (const difficulty of ["基础", "巩固", "进阶"]) {
    await region.getByRole("button", { name: `向 AI 索要${difficulty}题` }).click();
    await region.getByRole("radio").nth(0).check();
    await region.getByRole("radio").nth(5).check();
    await region.getByRole("button", { name: "提交并查看解析" }).click();
    await expect(region).toContainText("本次自测 100 分");
    await expect(region.getByRole("button", { name: "提交并查看解析" })).toHaveCount(0);
  }
  expect(state.generated).toEqual(["基础", "基础", "巩固", "进阶"]);
  await expect(region).toContainText("已通过进阶测试");
  await page.reload();
  await expect(region).toContainText("本次自测 100 分");
});

test("素材缺失明确提示，网络状态可重新读取，三个宽度无横向溢出", async ({ page }, testInfo) => {
  const state = await setup(page, { complete: true, pendingVideos: 1, failLoad: true });
  const region = panel(page);
  await expect(region.getByRole("alert")).toContainText("测试状态读取失败");
  state.failLoad = false;
  await region.getByRole("button", { name: "重新读取" }).click();
  await expect(region).toContainText("1 个录播课时等待补充素材");
  await expect(region.getByRole("button", { name: "向 AI 索要基础题" })).toBeDisabled();
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await region.scrollIntoViewIfNeeded();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`assessment-${width}.png`) });
  }
});

test("带小数时长的视频正常播完可计为完成，播放中仍保留未完成状态", async ({ page }) => {
  const state = await setup(page);
  let recordingRequest;
  // Keep media loading pending; this regression exercises progress events, not the browser's decoder.
  await page.route("**/recording-fixture.mp4", route => { recordingRequest = route; });
  await page.goto("/learn/12/34");
  const video = page.locator("video");
  await expect(video).toHaveCount(1);
  await video.evaluate(element => {
    Object.defineProperty(element, "duration", { configurable: true, value: 105.511474 });
    Object.defineProperty(element, "currentTime", { configurable: true, value: 104.8 });
    element.dispatchEvent(new Event("timeupdate"));
  });
  await expect.poll(() => state.videoSaves.some(save => save.positionSeconds === 104)).toBe(true);
  await video.evaluate(element => {
    Object.defineProperty(element, "currentTime", { configurable: true, value: 105.511474 });
    element.dispatchEvent(new Event("ended"));
  });
  await expect.poll(() => state.videoSaves.some(save => save.eventType === "ended" && save.positionSeconds === 106)).toBe(true);
  await recordingRequest?.fulfill({ status: 204, body: "" });
});

test("复用视频明确标为临时演示，切到图文后不保留提示", async ({ page }) => {
  await setup(page, { temporaryVideo: true });
  let recordingRequest;
  await page.route("**/recording-fixture.mp4", route => { recordingRequest = route; });
  await page.goto("/learn/12/34");
  await expect(page.getByRole("note")).toHaveText("临时演示视频，后续替换为本课录播。");
  await page.getByRole("button", { name: /事务与索引精讲.*图文课时/ }).click();
  await expect(page.getByRole("note")).toHaveCount(0);
  await recordingRequest?.fulfill({ status: 204, body: "" });
});
