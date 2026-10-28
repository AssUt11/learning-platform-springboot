import { useEffect, useState } from "react";
import { ArrowRight, Route, RefreshCcw } from "lucide-react";
import { getCurrentLearningPlan } from "../api/planning";
import { LEARNING_STAGES, stageLabel } from "../utils/learningStages";

export default function LearningPlanSummary({ user, setPage }) {
  const [data, setData] = useState(null), [error, setError] = useState(""), [reload, setReload] = useState(0);
  useEffect(() => {
    if (user?.role !== "student") return;
    let active = true;
    getCurrentLearningPlan().then((result) => { if (active) { setData(result); setError(""); } }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [user?.id, reload]);
  if (user?.role !== "student") return null;
  const path = data?.path;
  const next = path?.nextAction || path?.nodes?.find((node) => Number(node.progressPercent || 0) < 100);
  const stage = path?.nodes?.find((node) => node.id === next?.nodeId)?.stage || next?.stage || "foundation";
  return <section className="home-learning-plan" aria-label="我的学习计划">
    <div className="home-plan-heading"><div><Route size={21} /><div><span>YOUR NEXT STEP</span><h2>我的学习计划</h2></div></div><button onClick={() => setPage("planning")}>{path?.available ? "调整学习目标" : "开始规划"}<ArrowRight size={16} /></button></div>
    {error ? <div role="alert">学习计划暂未加载：{error}<button onClick={() => setReload((value) => value + 1)}><RefreshCcw size={15} />重试</button></div> : path?.available ? <>
      <div className="home-plan-stages">{LEARNING_STAGES.map((item) => <span className={stage === item.code ? "current" : ""} key={item.code}>{item.index} {item.label}</span>)}</div>
      <div className="home-plan-next"><div><small>{next ? `当前阶段 · ${stageLabel(stage)}` : "本次路线已学完"}</small><h3>{next?.title || path.title}</h3><p>{next?.reason || path.summary}</p></div><div><button onClick={() => setPage("graph", { view: "path", pathId: path.pathId })}>查看完整路径</button>{next?.courseId && <button className="primary-btn" onClick={() => setPage(next.lessonId ? "learn" : "course", { courseId: next.courseId, lessonId: next.lessonId })}>继续学习<ArrowRight size={15} /></button>}</div></div>
    </> : <p>用五个简单选择，找到适合你的起点。规划会保留你已经取得的学习进度。</p>}
  </section>;
}
