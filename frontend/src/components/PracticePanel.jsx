import { useEffect, useRef, useState } from "react";
import { Brain, CheckCircle2, RefreshCcw } from "lucide-react";
import request from "../utils/request";
import GenerationStatus from "./GenerationStatus";

export default function PracticePanel({ courseId, lessonId, setPage }) {
  const [quiz, setQuiz] = useState(null);
  const [answers, setAnswers] = useState([]);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const base = `/practice/courses/${courseId}/lessons/${lessonId}`;
  const restore = (data) => { setQuiz(data?.available ? data : null); setAnswers(data?.answers || []); };
  useEffect(() => {
    let active = true;
    let timer;
    const load = () => request(`${base}/latest`).then((data) => {
      if (!active) return;
      restore(data); setGenerating(Boolean(data?.generating));
      if (data?.generating) timer = window.setTimeout(load, 3000);
    }).catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    load();
    return () => { active = false; window.clearTimeout(timer); };
  }, [base]);
  const perform = async (action) => {
    if (busy) return;
    setBusy(action); setError("");
    try {
      const data = await request(action === "generate" ? `${base}/generate` : `/practice/${quiz.quizId}/submit`, {
        method: "POST", ...(action === "submit" ? { body: JSON.stringify({ answers }) } : {}), timeout: 180000
      });
      if (mounted.current) restore(data);
    } catch (err) { if (mounted.current) setError(err.message || "练习请求失败。"); }
    finally { if (mounted.current) setBusy(""); }
  };
  return <section className="practice-panel" aria-label="AI 课后练习" aria-busy={Boolean(busy)}>
    <header><div><span className="eyebrow"><Brain size={17} /> AI 辅导 · 课后自测</span><h2>学完以后，检验是否理解</h2></div>
      <button disabled={loading || Boolean(busy) || generating} onClick={() => perform("generate")}><RefreshCcw size={16} />{busy === "generate" || generating ? "正在生成…" : quiz ? "重新出题" : "生成课时练习"}</button>
    </header>
    <p>根据当前课时文字资料生成选择题，参考上次成绩调整难度。提交后保存结果，用于 AI 学习建议；不计入教师正式成绩。</p>
    {loading && <p role="status">正在读取已保存的练习…</p>}
    {error && <p className="inline-error-state" role="alert">{error}</p>}
    {(busy === "generate" || generating) && <GenerationStatus className="practice-thinking" label="正在生成课时练习" detail="模型正在阅读讲义并出题，请稍候。离开后可返回本课时读取已保存的题目。" />}
    {quiz && <><div className="practice-meta"><span>难度：{quiz.difficulty}</span><span>模型：{quiz.model}</span></div>
      <form onSubmit={(event) => { event.preventDefault(); perform("submit"); }}>
        {quiz.questions.map((question, index) => <fieldset key={`${quiz.quizId}-${index}`} disabled={quiz.submitted || Boolean(busy)}>
          <legend>{index + 1}. {question.stem}</legend>
          {question.options.map((option, value) => <label className="practice-option" key={value}><input type="radio" name={`question-${index}`} checked={answers[index] === value} required onChange={() => setAnswers((current) => { const next = [...current]; next[index] = value; return next; })} />{String.fromCharCode(65 + value)}. {option}</label>)}
          {quiz.submitted && <p className="practice-explanation"><strong>{answers[index] === question.answer ? "回答正确" : "需要巩固"} · 正确答案 {String.fromCharCode(65 + question.answer)}</strong><br />{question.explanation}{question.reference && <><br />讲义依据：{question.reference}</>}</p>}
        </fieldset>)}
        {!quiz.submitted && <button className="primary-btn" disabled={Boolean(busy)} type="submit">{busy === "submit" ? "正在保存…" : "提交并查看解析"}</button>}
      </form>
      {quiz.submitted && <div className="practice-result" role="status"><CheckCircle2 size={22} /><div><strong>本次自测 {quiz.score} 分 · 已保存</strong><p>{quiz.score < 60 ? "建议重新阅读本课时并做基础练习。" : quiz.score < 80 ? "建议继续巩固，再尝试进阶内容。" : "本次表现良好，可以结合课程进度继续学习。"}</p><button onClick={() => setPage("graph", { view: "path" })}>查看更新后的学习路径</button><button onClick={() => setPage("agents")}>根据最新表现重新规划</button></div></div>}
    </>}
    <small>AI 生成的题目和解析可能有误，请结合教师讲解核对。视频和文档需有文字摘要才可出题。</small>
  </section>;
}
