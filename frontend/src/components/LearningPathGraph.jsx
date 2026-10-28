import { useEffect, useId, useState } from "react";
import { ArrowRight, GitBranch, RefreshCcw } from "lucide-react";
import request from "../utils/request";
import { LEARNING_STAGES, inferLearningStage, stageLabel } from "../utils/learningStages";

export default function LearningPathGraph({ selection, setPage, children }) {
  const [path, setPath] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [activeId, setActiveId] = useState(null);
  const [syncing, setSyncing] = useState(false);
  const markerId = useId().replaceAll(":", "");
  const personal = selection.view !== "catalog";

  useEffect(() => {
    if (!personal) return;
    let cancelled = false;
    setPath(null);
    setLoading(true);
    setError("");
    request(`/learning/paths/${selection.pathId ? encodeURIComponent(selection.pathId) : "latest"}`)
      .then((data) => {
        if (cancelled) return;
        setPath(data?.available ? data : null);
        setActiveId(data?.nodes?.[0]?.id || null);
      })
      .catch((err) => { if (!cancelled) setError(err.message || "学习路径加载失败"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [personal, selection.pathId, reload]);

  const sync = async () => {
    if (!path || syncing) return;
    setSyncing(true);
    setError("");
    try {
      const data = await request(`/learning/paths/${encodeURIComponent(path.pathId)}/sync`, { method: "POST" });
      setPath(data);
      if (!data.neo4jSynced) setError("Neo4j 暂不可用，路径已保存，可稍后重试同步。");
    } catch (err) { setError(err.message || "同步失败"); }
    finally { setSyncing(false); }
  };
  const nodes = path?.nodes || [];
  const rows = Math.ceil(nodes.length / 3);
  const canvasHeight = Math.max(580, rows * 220 + 100);
  const positions = nodes.map((node, index) => {
    if (nodes.length <= 3) return [nodes.length === 1 ? 500 : nodes.length === 2 ? 300 + index * 400 : 170 + index * 330, 290];
    const row = Math.floor(index / 3), column = index % 3;
    return [170 + (row % 2 ? 2 - column : column) * 330, 150 + row * 220];
  });
  const placed = Object.fromEntries(nodes.map((node, i) => [node.id, positions[i]]));
  const active = nodes.find((node) => node.id === activeId) || nodes[0];

  return (
    <div className="learning-graph-hub">
      <nav className="learning-graph-tabs" aria-label="图谱视图">
        <button className={personal ? "active" : ""} onClick={() => setPage("graph", { view: "path", pathId: selection.pathId })}>我的学习路径</button>
        <button className={!personal ? "active" : ""} onClick={() => setPage("graph", { view: "catalog" })}>公共知识图谱</button>
      </nav>
      {!personal ? children : (
        <main className="content personal-path-page">
          <header className="personal-path-heading">
            <div><span className="eyebrow">个人学习路径 · 智慧图谱</span><h2>{path?.title || "我的学习路径"}</h2><p>{path?.summary || "将学习路径分析生成的步骤连接成你的学习路线。"}</p></div>
            <button disabled={loading} onClick={() => setReload((value) => value + 1)}><RefreshCcw size={16} />刷新路径</button>
            <button onClick={() => setPage("planning")}>调整学习目标<ArrowRight size={16} /></button>
          </header>
          {error && <div className="inline-error-state" role="alert">{error}<button disabled={loading} onClick={() => setReload((value) => value + 1)}>重新加载</button></div>}
          {loading && <p role="status">正在加载你的学习路径…</p>}
          {!loading && !error && !path && <section className="personal-path-empty"><GitBranch size={40} /><h3>还没有生成学习路径</h3><p>先在 AI 学伴中运行“学习路径分析”，生成后会自动保存到这里。</p><button className="primary-btn" onClick={() => setPage("agents")}>生成学习路径</button></section>}
          {path && <>
            <div className="personal-path-meta">
              <span>{path.source === "neo4j" ? "Neo4j 个人路径" : path.neo4jSynced ? "已同步 Neo4j · 当前显示保存的路径" : "路径已保存 · 待同步 Neo4j"}</span>
              <span>{nodes.length} 个学习步骤 · {new Date(path.createdAt).toLocaleString("zh-CN")}</span>
              {path.source !== "neo4j" && <button disabled={syncing} onClick={sync}>{syncing ? "同步中…" : "同步到 Neo4j"}</button>}
            </div>
            {path.nextAction && <section className="adaptive-next-action" aria-label="自适应学习建议"><div><span className="eyebrow">根据最新学习与测验表现</span><h3>下一步：{path.nextAction.title}</h3><p>{path.nextAction.reason}</p></div>{path.nextAction.courseId && <button className="primary-btn" onClick={() => setPage(path.nextAction.lessonId ? "learn" : "course", { courseId: path.nextAction.courseId, lessonId: path.nextAction.lessonId })}>开始这一步<ArrowRight size={16} /></button>}</section>}
            <div className="personal-path-workspace">
              <section className="personal-path-canvas" style={{ height: canvasHeight }} aria-label="个人学习路径图谱">
                <div className="personal-path-stages">{LEARNING_STAGES.map((stage) => <span key={stage.code}>{stage.index} {stage.label}</span>)}</div>
                <svg viewBox={`0 0 1000 ${canvasHeight}`} preserveAspectRatio="none" aria-hidden="true">
                  <defs><marker id={markerId} markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0,0 L10,5 L0,10 Z" fill="#16816b" /></marker></defs>
                  {(path.links || []).map((link) => {
                    const from = placed[link.source]; const to = placed[link.target];
                    if (!from || !to) return null;
                    const dx = to[0] - from[0]; const dy = to[1] - from[1];
                    const length = Math.hypot(dx, dy);
                    return <line key={`${link.source}-${link.target}`} x1={from[0] + dx / length * 108} y1={from[1] + dy / length * 68} x2={to[0] - dx / length * 118} y2={to[1] - dy / length * 78} stroke="#16816b" strokeWidth="3" markerEnd={`url(#${markerId})`} />;
                  })}
                </svg>
                {nodes.map((node) => <button key={node.id} className={`personal-path-node${active?.id === node.id ? " active" : ""}${node.progressPercent >= 100 ? " learning-completed" : ""}`} style={{ left: `${placed[node.id][0] / 10}%`, top: `${placed[node.id][1] * 100 / canvasHeight}%` }} aria-pressed={active?.id === node.id} onClick={() => setActiveId(node.id)}><span>步骤 {node.order}</span><strong>{node.title}</strong><small>{stageLabel(node.stage || inferLearningStage(node.title))}</small></button>)}
                <p className="personal-path-legend">箭头表示本次分析建议的学习顺序；进度和自测反馈以最新记录为准</p>
              </section>
              {active && <aside className="personal-path-detail"><span className="eyebrow">步骤 {active.order}</span><h3>{active.title}</h3>{active.learningState && <div className="adaptive-step-state"><strong>{active.learningState}</strong><p>课时学习进度 {active.progressPercent ?? 0}% · 自测{active.quizScore == null ? "暂无成绩" : `${active.quizScore}分`}</p><p>{active.recommendationReason}</p><small>平台进阶条件：关联课程全部课时完成，且各课时最近一次自测达到80分。</small></div>}<h4>学习目标</h4><p>{active.goal}</p><h4>AI 建议的完成标准</h4><p>{active.completionCriteria}</p>{active.courseId && active.learningState !== "课程已下架" ? <button className="primary-btn" onClick={() => setPage("course", { courseId: active.courseId })}>进入学习课程<ArrowRight size={16} /></button> : <p className="muted">本步骤暂无匹配课程，可结合学习目标自主练习。</p>}</aside>}
            </div>
            <details className="personal-path-evidence"><summary>查看路径分析与先修依据</summary><p className="personal-path-analysis">{path.analysis}</p><p>{path.graphUsed ? "本次分析参考了 Neo4j 中查询到的先修关系。" : "本次分析没有匹配到可用的 Neo4j 先修关系，学习顺序尚未完成图谱校验。"}</p>{(path.evidence || []).flatMap((item) => item.prerequisites || []).map((item, index) => <p key={index}>{item.title} → {item.target}（{Number(item.distance) > 1 ? "间接先修" : "直接先修"}）</p>)}</details>
          </>}
        </main>
      )}
    </div>
  );
}
