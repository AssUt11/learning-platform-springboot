/** 只展示生成状态，不展示或伪造模型的内部思考过程。 */
export default function GenerationStatus({ active = true, label = "正在整理回答", detail, className = "" }) {
  return <div className={`generation-status ${active ? "is-active" : "is-settled"} ${className}`} role={active ? "status" : undefined}>
    <div className="generation-status-line"><span className="generation-ink-mark" aria-hidden="true"><i /><i /><i /></span><span>{label}</span></div>
    {detail && <p className="generation-status-detail">{detail}</p>}
  </div>;
}
