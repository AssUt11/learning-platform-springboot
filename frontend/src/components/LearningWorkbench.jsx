import { useEffect, useState } from "react";
import { ArrowRight, BookOpen, CheckCircle2, GitBranch, Heart, MessageCircle, Route, Wand2 } from "lucide-react";
import { getLearningSummary, getResumeLearning } from "../api/learning";

const analysisPrompts = [
  { icon: Wand2, title: "我应该先补哪块知识？", detail: "课程诊断与薄弱点分析" },
  { icon: Route, title: "下一阶段该怎么学？", detail: "结合掌握度安排学习路径" },
  { icon: BookOpen, title: "有哪些适合我的资料？", detail: "从现有课程中寻找学习资源" }
];

/** These previews use the catalogue and the signed-in account, never sample progress. */
export default function LearningWorkbench({ user, subject, branches = [], onOpenGraph, setPage }) {
  const [learning, setLearning] = useState(null);
  const [resume, setResume] = useState(null);
  const [unavailable, setUnavailable] = useState(false);
  useEffect(() => {
    let active = true;
    setLearning(null);
    setResume(null);
    setUnavailable(false);
    Promise.allSettled([getLearningSummary(), getResumeLearning()]).then(([summary, recent]) => {
      if (!active) return;
      if (summary.status === "fulfilled") setLearning(summary.value?.summary || {});
      else setUnavailable(true);
      if (recent.status === "fulfilled" && recent.value?.available) setResume(recent.value);
    });
    return () => { active = false; };
  }, [user?.id]);
  const count = (camel, lower) => learning ? Number(learning[camel] ?? learning[lower] ?? 0) : "—";
  const stats = [
    { icon: BookOpen, label: "已学课程", value: count("learnedResourceCount", "learnedresourcecount") },
    { icon: CheckCircle2, label: "已完成", value: count("completedResourceCount", "completedresourcecount") },
    { icon: Heart, label: "收藏课程", value: count("favoriteCount", "favoritecount") }
  ];
  return (
    <section className="learning-workbench" aria-labelledby="learning-workbench-title">
      <header>
        <h2 id="learning-workbench-title">每一次学习，都有下一步。</h2>
        <p>找到知识之间的联系，把学习中的问题和进度接起来。</p>
      </header>
      <div className="learning-workbench-grid">
        <article className="workbench-graph">
          <div className="workbench-map-preview" aria-label={`${subject}的课程方向预览`}>
            <div className="workbench-map-root"><GitBranch size={22} /><strong>{subject}</strong></div>
            <div className="workbench-map-branches">
              {(branches.length ? branches.slice(0, 3) : ["前置知识", "课程章节", "关联能力"]).map((branch) => (
                <button key={branch} type="button" onClick={() => onOpenGraph(branch)}>{branch}</button>
              ))}
            </div>
          </div>
          <h3>知识关系可视化</h3>
          <p>从方向到具体知识点，查看前置关系，直接进入对应课时。</p>
          <button className="workbench-link" type="button" onClick={() => onOpenGraph(subject)}>展开智慧图谱<ArrowRight size={16} /></button>
        </article>
        <article className="workbench-analysis">
          <div className="workbench-analysis-preview" aria-label="可进行的学习分析">
            {analysisPrompts.map(({ icon: Icon, title, detail }) => (
              <button key={title} type="button" onClick={() => setPage("agents")}>
                <Icon size={18} /><span><strong>{title}</strong><small>{detail}</small></span><ArrowRight size={15} />
              </button>
            ))}
          </div>
          <h3>AI 学习分析</h3>
          <p>结合实际学习记录生成建议，也可以去 Agent 对话继续追问。</p>
          <button className="workbench-link" type="button" onClick={() => setPage("agents")}>打开 AI 学伴<ArrowRight size={16} /></button>
        </article>
        <article className="workbench-growth">
          <div className="workbench-growth-preview" aria-label="我的真实学习概况" aria-busy={!learning && !unavailable}>
            <dl>{stats.map(({ icon: Icon, label, value }) => <div key={label}><dt><Icon size={15} />{label}</dt><dd>{value}</dd></div>)}</dl>
            <div className="workbench-recent">
              <MessageCircle size={17} />
              <div><small>{resume ? "最近学习" : unavailable ? "学习记录暂未加载" : "你的学习档案"}</small><strong>{resume?.lessonTitle || "课程进度、笔记与提问，留在自己的账号里。"}</strong></div>
            </div>
          </div>
          <h3>持续成长档案</h3>
          <p>保留每一节课的学习进度，下次回来接着学。</p>
          <button className="workbench-link" type="button" onClick={() => setPage("learning")}>查看我的学习<ArrowRight size={16} /></button>
        </article>
      </div>
    </section>
  );
}
