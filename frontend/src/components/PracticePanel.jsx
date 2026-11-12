import { useEffect, useRef, useState } from "react";
import { Brain, CheckCircle2, RefreshCcw } from "lucide-react";
import request from "../utils/request";
import GenerationStatus from "./GenerationStatus";

export default function PracticePanel({ courseId, lessonId, setPage, courseAssessment = false, progressVersion = 0 }) {
  const [quiz, setQuiz] = useState(null);
  const [answers, setAnswers] = useState([]);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [assessment, setAssessment] = useState(null);
  const [difficulty, setDifficulty] = useState("");
  const [loadVersion, setLoadVersion] = useState(0);
  const [mode, setMode] = useState("lesson");
  const [smartPlan, setSmartPlan] = useState(null);
  const quizIdRef = useRef(null);
  const scopeRef = useRef("");
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const smartMode = !courseAssessment && mode === "smart";
  const base = courseAssessment ? `/practice/courses/${courseId}/assessment` : smartMode ? `/practice/courses/${courseId}/smart` : `/practice/courses/${courseId}/lessons/${lessonId}`;
  const latestUrl = courseAssessment ? base : `${base}/latest`;
  const restore = (data) => {
    setQuiz(data?.available ? data : null);
    const samePendingQuiz = data?.quizId && data.quizId === quizIdRef.current && !data.submitted;
    setAnswers(current => samePendingQuiz ? current : data?.answers || []);
    quizIdRef.current = data?.available ? data.quizId : null;
  };
  useEffect(() => {
    let active = true;
    let timer;
    if (scopeRef.current !== base) {
      scopeRef.current = base;
      quizIdRef.current = null;
      setQuiz(null);
      setAnswers([]);
      setSmartPlan(null);
      setGenerating(false);
    }
    setLoading(true);
    setError("");
    const load = () =>
      request(latestUrl)
        .then((data) => {
          if (!active) return;
          if (smartMode) {
            setSmartPlan(data?.personalization || null);
            restore(data?.quiz);
          } else if (courseAssessment) {
            setAssessment(data);
            restore(data?.quiz);
          } else restore(data);
          setGenerating(Boolean(data?.generating));
          if (data?.generating) timer = window.setTimeout(load, 3000);
        })
        .catch((err) => {
          if (active) setError(err.message);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    load();
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [base, progressVersion, loadVersion]);
  const perform = async (action) => {
    if (busy) return;
    setBusy(action);
    setError("");
    try {
      const data = await request(
        action === "generate" ? `${base}/generate` : `/practice/${quiz.quizId}/submit`,
        {
          method: "POST",
          ...(action === "submit" ? { body: JSON.stringify({ answers }) } : courseAssessment ? { body: JSON.stringify({ difficulty: difficulty || assessment?.nextDifficulty }) } : {}),
          timeout: 180000
        }
      );
      if (mounted.current) {
        restore(data);
        if (courseAssessment || smartMode) {
          // Keep the saved result visible even if refreshing the stage summary fails.
          const state = await request(latestUrl);
          if (mounted.current) {
            if (smartMode) setSmartPlan(state?.personalization || null);
            else { setAssessment(state); setDifficulty(""); }
          }
        }
      }
    } catch (err) {
      if (mounted.current) setError(err.message || "练习请求失败。");
    } finally {
      if (mounted.current) setBusy("");
    }
  };
  const profile = smartMode && (!quiz || quiz.submitted) ? smartPlan : quiz?.personalization;
  return (
    <section id={courseAssessment ? "course-assessment" : undefined} className={`practice-panel${courseAssessment ? " course-assessment-panel" : ""}`} aria-label={courseAssessment ? "AI 课程测试" : "AI 课后练习"} aria-busy={Boolean(busy)}>
      <header>
        <div>
          <h2><Brain size={19} /> {courseAssessment ? "AI 课程测试" : "智能出题"}</h2>
          {courseAssessment && <span className="assessment-sequence">基础 → 巩固 → 进阶</span>}
        </div>
        <button
          disabled={loading || Boolean(busy) || generating || (courseAssessment && !assessment?.unlocked) || (smartMode && !smartPlan?.eligible)}
          onClick={() => perform("generate")}
        >
          <RefreshCcw size={16} />
          {busy === "generate" || generating ? "正在生成…" : courseAssessment ? quiz && !quiz.submitted ? "继续本次测试" : `向 AI 索要${difficulty || assessment?.nextDifficulty || "基础"}题` : smartMode ? quiz && !quiz.submitted ? "继续本轮练习" : "生成个性化练习" : quiz ? "重新出题" : "生成课时练习"}
        </button>
      </header>
      {!courseAssessment && <div className="practice-mode-switch" role="group" aria-label="出题方式">
        <button type="button" aria-pressed={!smartMode} disabled={Boolean(busy) || generating} onClick={() => setMode("lesson")}>当前课时</button>
        <button type="button" aria-pressed={smartMode} disabled={Boolean(busy) || generating} onClick={() => setMode("smart")}>按学习情况</button>
      </div>}
      <p>
        {courseAssessment ? "全部视频和图文课时完成后，AI 根据整门课程讲义逐次出题，每轮 1–2 道。达到 80 分后开放下一阶段；提交后可查看答案、原文依据与解析。" : smartMode ? "结合本课程已学内容、近期答题表现和易错知识点选题。首次从基础开始，理解稳定后逐步提高难度。" : "根据当前课时文字资料和近期答题情况生成练习，提交后保存结果，用于后续学习建议。"}
      </p>
      {profile && <section className="practice-personalization" aria-label="个性化出题依据">
        <h3>{smartMode && quiz?.submitted ? "下一轮出题建议" : "本次出题依据"}</h3>
        <p>{profile.reason}</p>
        {profile.eligible && <>
          <dl>
            <div><dt>已学图文</dt><dd>{profile.studiedTextLessons}/{profile.totalTextLessons} 节</dd></div>
            <div><dt>近期正确率</dt><dd>{profile.recentAccuracy == null ? "尚未测评" : `${profile.recentAccuracy}%`}{profile.recentQuestionCount > 0 && <small>最近 {profile.recentQuestionCount} 题</small>}</dd></div>
            <div><dt>建议难度</dt><dd>{profile.difficulty}</dd></div>
          </dl>
          <p className="practice-focus">重点课时：{profile.sourceLessonTitle}</p>
          {profile.weakTopics?.length > 0 && <p>优先复习：{profile.weakTopics.map(topic => `${topic.title}（${topic.accuracy}%）`).join("、")}</p>}
        </>}
      </section>}
      {courseAssessment && assessment && <>
        <p className="assessment-eligibility" role="status">
          {assessment.unlocked ? "已完成全部学习，可以开始课程测试。" : `已完成 ${assessment.completedLessons || 0}/${assessment.totalLessons || 0} 个已发布视频与图文课时。`}
          {assessment.pendingVideos > 0 && `另有 ${assessment.pendingVideos} 个录播课时等待补充素材并发布，届时完成观看后开放测试。`}
        </p>
        <ol className="assessment-stages" aria-label="测试难度进度">
          {(assessment.stages || []).map(stage => <li key={stage.difficulty} className={stage.passed ? "passed" : ""}>
            <button type="button" aria-pressed={(difficulty || assessment.nextDifficulty) === stage.difficulty} disabled={!assessment.unlocked || !stage.unlocked || Boolean(busy) || generating || (quiz && !quiz.submitted)} onClick={() => setDifficulty(stage.difficulty)}>
              <strong>{stage.difficulty}</strong>
              <span>{stage.bestScore != null ? `最高 ${stage.bestScore} 分` : stage.unlocked ? "待测试" : "上一阶段达标后开放"}</span>
              {stage.passed && <CheckCircle2 size={15} />}
            </button>
          </li>)}
        </ol>
      </>}
      {loading && <p role="status">正在读取已保存的练习…</p>}
      {error && (
        <div className="inline-error-state" role="alert">
          {error}
          <button type="button" disabled={Boolean(busy)} onClick={() => setLoadVersion(value => value + 1)}>重新读取</button>
        </div>
      )}
      {(busy === "generate" || generating) && (
        <GenerationStatus
          className="practice-thinking"
          label={courseAssessment ? "正在生成课程测试" : smartMode ? "正在根据学习情况出题" : "正在生成课时练习"}
          detail={courseAssessment ? "AI 正在结合整门课程讲义出题，完成后自动保存。" : smartMode ? "正在结合讲义、进度和历史答题记录选题，生成后自动保存。" : "模型正在阅读讲义并出题，请稍候。离开后可返回本课时读取已保存的题目。"}
        />
      )}
      {quiz && (
        <>
          <div className="practice-meta">
            <span>难度：{quiz.difficulty}</span>
            <span>{courseAssessment ? "范围：整门课程" : smartMode ? "范围：本课程已学内容" : "范围：当前课时"}</span>
          </div>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              perform("submit");
            }}
          >
            {quiz.questions.map((question, index) => (
              <fieldset key={`${quiz.quizId}-${index}`} disabled={quiz.submitted || Boolean(busy) || (courseAssessment && !assessment?.unlocked)}>
                <legend>
                  {index + 1}. {question.stem}
                </legend>
                {question.sourceLessonTitle && <p className="practice-question-source">考查课时：{question.sourceLessonTitle}</p>}
                {question.options.map((option, value) => (
                  <label className="practice-option" key={value}>
                    <input
                      type="radio"
                      name={`question-${quiz.quizId}-${index}`}
                      checked={answers[index] === value}
                      required
                      onChange={() =>
                        setAnswers((current) => {
                          const next = [...current];
                          next[index] = value;
                          return next;
                        })
                      }
                    />
                    {String.fromCharCode(65 + value)}. {option}
                  </label>
                ))}
                {quiz.submitted && (
                  <p className="practice-explanation">
                    <strong>
                      {answers[index] === question.answer ? "回答正确" : "需要巩固"} · 正确答案{" "}
                      {String.fromCharCode(65 + question.answer)}
                    </strong>
                    <br />
                    {question.explanation}
                    {question.reference && (
                      <>
                        <br />
                        讲义依据：{question.reference}
                      </>
                    )}
                  </p>
                )}
              </fieldset>
            ))}
            {!quiz.submitted && (
              <button className="primary-btn" disabled={Boolean(busy) || (courseAssessment && !assessment?.unlocked)} type="submit">
                {busy === "submit" ? "正在保存…" : "提交并查看解析"}
              </button>
            )}
          </form>
          {quiz.submitted && (
            <div className="practice-result" role="status">
              <CheckCircle2 size={22} />
              <div>
                <strong>本次自测 {quiz.score} 分 · 已保存</strong>
                <p>
                  {courseAssessment ? quiz.score >= 80 ? quiz.difficulty === "进阶" ? "已通过进阶测试，可以继续索题巩固。" : "已达到本阶段目标，可以继续挑战下一难度。" : "本阶段尚未达标，请结合解析复习后再次索题。" : quiz.score < 60
                    ? "建议重新阅读本课时并做基础练习。"
                    : quiz.score < 80
                      ? "建议继续巩固，再尝试进阶内容。"
                      : "本次表现良好，可以结合课程进度继续学习。"}
                </p>
                <button onClick={() => setPage("graph", { view: "path" })}>
                  查看更新后的学习路径
                </button>
                <button onClick={() => setPage("agents")}>根据最新表现重新规划</button>
              </div>
            </div>
          )}
        </>
      )}
      <small>
        AI 生成的题目和解析可能有误，请结合教师讲解核对。视频和文档需有文字摘要才可出题。
      </small>
    </section>
  );
}
