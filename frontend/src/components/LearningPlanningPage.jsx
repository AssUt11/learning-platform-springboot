import { useEffect, useRef, useState } from "react";
import { ArrowLeft, ArrowRight, Check, CheckCircle2, GraduationCap, RotateCcw, Sparkles } from "lucide-react";
import { adoptLearningPlan, deferLearningPlan, generateLearningPlan, getLearningProfile, saveLearningProfile } from "../api/planning";
import { LEARNING_STAGES, stageLabel } from "../utils/learningStages";
import GenerationStatus from "./GenerationStatus";

const QUESTIONS = [
  { key: "subject", title: "你现在最想学习什么？", hint: "选一个当前最想投入的方向，我们先为它规划一条清晰的路线。" },
  { key: "background", title: "以前学过哪些相关内容？", hint: "按实际经历选择。你可以补充学过的知识点，规划会参考这些信息。", options: [["none", "完全没学过", "从第一步开始"], ["basics", "学过一些基础", "需要梳理与巩固"], ["projects", "做过相关项目", "希望进一步提升"]] },
  { key: "goal", title: "这次学习希望达到什么目标？", hint: "相同的方向，也可以有不同的学习重点。", options: [["interest", "兴趣入门", "先认识这个领域"], ["exam", "准备考试", "系统复习核心知识"], ["project", "完成项目", "把知识用到实践中"], ["job", "求职提升", "补齐岗位所需能力"]] },
  { key: "targetStage", title: "希望从哪个阶段开始？", hint: "起点可以调整；必要的先修内容会给出补充建议。", options: [["foundation", "基础阶段", "从概念与基础方法学起"], ["intermediate", "中期巩固", "连接知识，增加实践"], ["advanced", "进阶提升", "深入原理与综合应用"], ["auto", "请帮我判断", "结合我的经历推荐起点"]] },
  { key: "dailyMinutes", title: "每天大约能投入多久？", hint: "按能长期坚持的时间来选，学习安排会更贴近你的节奏。", options: [[15, "15 分钟", "每天迈出一小步"], [30, "30 分钟", "保持稳定学习"], [60, "1 小时", "留出练习与复习时间"], ["custom", "自定义时间", "设置适合自己的节奏"]] }
];
const optionText = (key, value) => QUESTIONS.find((q) => q.key === key)?.options?.find((option) => option[0] === value)?.[1] || value;

export function PlanStages({ path, onStudy }) {
  return <div className="plan-stage-list">{LEARNING_STAGES.map((stage) => {
    const nodes = (path?.nodes || []).filter((node) => (node.stage || "foundation") === stage.code);
    return <section className={`plan-stage stage-${stage.code}`} key={stage.code}>
      <header><span>{stage.index}</span><div><h3>{stage.label}</h3><p>{stage.detail}</p></div><small>{nodes.length} 个步骤</small></header>
      {nodes.length ? nodes.map((node) => <article key={node.id} className={node.progressPercent >= 100 ? "completed" : ""}>
        <div><span className="plan-step-number">{node.order}</span><div><h4>{node.title}</h4><p>{node.goal}</p></div></div>
        {node.reason && <p className="plan-step-reason">{node.reason}</p>}
        <small>{node.estimatedMinutes ? `预计 ${node.estimatedMinutes} 分钟 · ` : ""}{node.completionCriteria}</small>
        {onStudy && node.courseId && <button onClick={() => onStudy(node)}>{node.progressPercent >= 100 ? "复习课程" : "开始学习"}<ArrowRight size={15} /></button>}
        {!node.courseId && <small className="plan-unmatched">暂无匹配课程，可按学习目标自主练习。</small>}
      </article>) : <p className="plan-stage-empty">本次路径暂未安排此阶段，可在目标调整后重新规划。</p>}
    </section>;
  })}</div>;
}

export default function LearningPlanningPage({ user, categoryTree, onFinish, onLogout }) {
  const [answers, setAnswers] = useState({});
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [review, setReview] = useState(false);
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [customSubject, setCustomSubject] = useState(false);
  const [customTime, setCustomTime] = useState(false);
  const [reload, setReload] = useState(0);
  const endRef = useRef(null);
  const alive = useRef(true);
  const draftKey = `learning_planning_draft:${user.id}`;
  const directions = categoryTree.flatMap((category) => category.children || []);
  const selectedDirection = directions.find((direction) => direction.label === answers.subject);
  const question = QUESTIONS[step];
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    let active = true; setLoading(true); setError("");
    getLearningProfile().then((profile) => {
      if (!active) return;
      let local = null;
      try { local = JSON.parse(localStorage.getItem(draftKey)); } catch { /* A corrupt draft is ignored. */ }
      const resume = local?.baseVersion === profile.version;
      const saved = resume ? local.answers : profile.answers || {};
      const unanswered = QUESTIONS.findIndex((question) => !saved[question.key]);
      setAnswers(saved); setStep(resume ? Math.min(4, local.step || 0) : profile.version ? Math.max(0, unanswered < 0 ? 4 : unanswered) : 0);
      setCustomSubject(!!saved.subject && !directions.some((item) => item.label === saved.subject));
      setCustomTime(!!saved.dailyMinutes && ![15, 30, 60].includes(saved.dailyMinutes));
      setPreview(resume ? null : profile.preview || null);
      setReview((!!profile.preview && !resume) || unanswered < 0);
      profileVersion.current = profile.version;
    }).catch((err) => { if (active) setError(err.message || "学习档案加载失败，请重试。"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user.id, reload]);
  const profileVersion = useRef(0);
  useEffect(() => { endRef.current?.scrollIntoView({ block: "nearest", behavior: "smooth" }); }, [step, review, preview, busy]);

  const update = (patch) => {
    const next = { ...answers, ...patch }; setAnswers(next); setError("");
    localStorage.setItem(draftKey, JSON.stringify({ answers: next, step, baseVersion: profileVersion.current }));
  };
  const persist = async () => {
    const profile = await saveLearningProfile(answers); profileVersion.current = profile.version;
    localStorage.removeItem(draftKey); return profile;
  };
  const valid = question.key === "dailyMinutes" ? Number(answers.dailyMinutes) >= 1 && Number(answers.dailyMinutes) <= 480 : !!answers[question.key];
  const next = async () => {
    if (!valid || busy) return;
    setBusy("save"); setError("");
    try { await persist(); if (step === 4) setReview(true); else setStep(step + 1); }
    catch (err) { setError(err.message); } finally { setBusy(""); }
  };
  const generate = async () => {
    setBusy("generate"); setError(""); setPreview(null);
    try { const profile = await persist(); const generated = await generateLearningPlan(profile.version); if (alive.current) setPreview(generated); }
    catch (err) { if (alive.current) setError(err.message || "生成失败，回答已保留，请重试。"); }
    finally { if (alive.current) setBusy(""); }
  };
  const adopt = async () => {
    setBusy("adopt"); setError("");
    try { const path = await adoptLearningPlan(preview.previewId); if (!alive.current) return; localStorage.removeItem(draftKey); onFinish({ answers, path }); }
    catch (err) { setError(err.message); } finally { if (alive.current) setBusy(""); }
  };
  const defer = async () => {
    setBusy("defer"); setError("");
    try { await persist(); await deferLearningPlan(); if (alive.current) onFinish({ answers, deferred: true }); }
    catch (err) { setError(err.message || "暂未保存成功，请重试。"); } finally { if (alive.current) setBusy(""); }
  };
  return <main className="planning-page">
    <header className="planning-topbar"><div><GraduationCap size={25} /><strong>知度课堂</strong><span>为每次出发，找到方向</span></div><button onClick={onLogout}>退出登录</button></header>
    <div className="planning-layout">
      <aside className="planning-intro"><span className="planning-kicker">A PATH THAT FITS YOU</span><h1>先聊一聊，<br />再开始你的<span>学习旅程。</span></h1><p>五个简单选择，让课程与知识图谱从你的目标出发。</p><div className="planning-milestones">{LEARNING_STAGES.map((stage) => <div key={stage.code}><span>{stage.index}</span><div><strong>{stage.label}</strong><small>{stage.detail}</small></div></div>)}</div><p className="planning-note">已有学习记录会继续保留。你也可以稍后调整目标。</p></aside>
      <section className="planning-conversation" aria-label="学习规划对话">
        <header><div><Sparkles size={20} /><strong>你的学习规划师</strong></div><span>{preview ? "路径预览" : review ? "确认学习档案" : `第 ${step + 1} / 5 问`}</span></header>
        <div className="planning-progress">{QUESTIONS.map((q, index) => <i key={q.key} className={review || index <= step ? "active" : ""} />)}</div>
        {loading ? <p role="status">正在读取你的学习档案…</p> : <>
          {!review && <div className="planning-chat-log">{QUESTIONS.slice(0, step).map((q) => <div className="planning-exchange" key={q.key}><p>{q.title}</p><div>{q.key === "dailyMinutes" ? `${answers[q.key]} 分钟 / 天` : optionText(q.key, answers[q.key])}</div></div>)}
            <div className="planning-current-question"><span className="planning-avatar"><Sparkles size={18} /></span><div><h2>{question.title}</h2><p>{question.hint}</p></div></div>
            <div className={`planning-options ${question.key === "subject" ? "subject-options" : ""}`} role="group" aria-label={question.title}>
              {question.key === "subject" ? <>{directions.map((direction) => <button type="button" key={direction.value || direction.label} aria-pressed={!customSubject && answers.subject === direction.label} onClick={() => { setCustomSubject(false); update({ subject: direction.label, knownTopics: [] }); }}><strong>{direction.label}</strong>{!customSubject && answers.subject === direction.label && <Check size={17} />}</button>)}<button type="button" aria-pressed={customSubject} onClick={() => { setCustomSubject(true); update({ subject: "" }); }}>其他方向</button></> : question.options.map(([value, label, detail]) => {
                const selected = question.key === "dailyMinutes" ? value === "custom" ? customTime : !customTime && answers.dailyMinutes === value : answers[question.key] === value;
                return <button type="button" key={value} aria-pressed={selected} onClick={() => {
                  if (question.key === "dailyMinutes") { setCustomTime(value === "custom"); update({ dailyMinutes: value === "custom" ? "" : value }); }
                  else update({ [question.key]: value, ...(question.key === "background" && value === "none" ? { knownTopics: [] } : {}) });
                }}><div><strong>{label}</strong><small>{detail}</small></div>{selected && <CheckCircle2 size={20} />}</button>;
              })}
            </div>
            {question.key === "subject" && customSubject && <label className="planning-custom">想学习的方向<input maxLength={60} aria-label="自定义学习方向" value={answers.subject || ""} onChange={(event) => update({ subject: event.target.value })} placeholder="例如：数据分析" /></label>}
            {question.key === "dailyMinutes" && customTime && <label className="planning-custom">每天学习分钟数<input type="number" min="1" max="480" aria-label="每天学习分钟数" value={answers.dailyMinutes || ""} onChange={(event) => update({ dailyMinutes: event.target.value ? Number(event.target.value) : "" })} /></label>}
            {question.key === "background" && answers.background && answers.background !== "none" && <div className="planning-known-topics"><p>哪些内容你接触过？可以多选。</p><div role="group" aria-label="已学知识点">{(selectedDirection?.children || []).slice(0, 20).map((topic) => <button key={topic.label} aria-pressed={(answers.knownTopics || []).includes(topic.label)} onClick={() => update({ knownTopics: (answers.knownTopics || []).includes(topic.label) ? answers.knownTopics.filter((item) => item !== topic.label) : [...(answers.knownTopics || []), topic.label] })}>{topic.label}</button>)}</div><label>补充说明（选填）<textarea maxLength={600} value={answers.notes || ""} onChange={(event) => update({ notes: event.target.value })} placeholder="例如：学过语法，但还没独立做过项目" /></label></div>}
            <footer className="planning-question-actions"><button disabled={step === 0 || !!busy} onClick={() => setStep(step - 1)}><ArrowLeft size={16} />上一题</button><button className="primary-btn" disabled={!valid || !!busy} onClick={next}>{busy === "save" ? "正在保存…" : step === 4 ? "查看我的学习档案" : "继续"}<ArrowRight size={16} /></button></footer>
          </div>}
          {review && <><div className="planning-current-question"><span className="planning-avatar"><Sparkles size={18} /></span><div><h2>{preview ? "这条路线，向你的目标出发" : "这是我了解到的你"}</h2><p>{preview ? preview.path?.summary : "确认以下信息，我会结合已有课程与学习记录生成路径。"}</p></div></div>
            <dl className="planning-profile-review">{QUESTIONS.map((q) => <div key={q.key}><dt>{q.title}</dt><dd>{q.key === "dailyMinutes" ? `${answers[q.key]} 分钟 / 天` : optionText(q.key, answers[q.key])}</dd></div>)}{answers.knownTopics?.length > 0 && <div><dt>接触过的知识点</dt><dd>{answers.knownTopics.join("、")}</dd></div>}{answers.notes && <div><dt>补充说明</dt><dd>{answers.notes}</dd></div>}</dl>
            {busy === "generate" && <GenerationStatus label="正在规划你的学习路线" detail="正在结合目标、课程和先修关系安排学习步骤，回答已经保存。" />}
            {preview && <PlanStages path={preview.path} />}
            <footer className="planning-review-actions"><button disabled={!!busy} onClick={() => { setReview(false); setPreview(null); setStep(0); }}><ArrowLeft size={16} />修改回答</button>{preview ? <><button disabled={!!busy} onClick={generate}><RotateCcw size={16} />重新生成</button><button className="primary-btn" disabled={!!busy} onClick={adopt}>{busy === "adopt" ? "正在保存路径…" : "采用路径并进入首页"}<ArrowRight size={16} /></button></> : <button className="primary-btn" disabled={!!busy} onClick={generate}>{busy === "generate" ? "生成中…" : "生成我的学习路径"}<Sparkles size={17} /></button>}</footer>
          </>}
        </>}
        {error && <div className="planning-error" role="alert"><p>{error}</p><small>你的回答会保留，可以重试或稍后规划。</small>{loading === false && !Object.keys(answers).length && <button onClick={() => setReload((value) => value + 1)}>重新加载</button>}</div>}
        <button className="planning-defer" disabled={(!!busy && busy !== "generate") || loading} onClick={defer}>{busy === "defer" ? "正在保存…" : "稍后规划，先进入首页"}</button>
        <div ref={endRef} />
      </section>
    </div>
  </main>;
}
