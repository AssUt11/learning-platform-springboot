import { useEffect, useRef, useState } from "react";
import { FileText, X } from "lucide-react";
import { readCoursewareSource } from "../api/courseware";
import { coursewareLocation } from "../utils/courseware";
import "../courseware.css";

export default function CoursewareSources({ sources, courseId, lessonId }) {
  const [selected, setSelected] = useState(null);
  const [sourceText, setSourceText] = useState(null);
  const [error, setError] = useState("");
  const dialog = useRef(null);
  const list = [...new Map((sources || []).map(source => [`${source.sourceType}-${source.sourceId}`, source])).values()];
  useEffect(() => {
    if (!selected) return undefined;
    const controller = new AbortController();
    setSourceText(null); setError("");
    dialog.current?.showModal();
    readCoursewareSource(selected, courseId, lessonId, { signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setSourceText(data); })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message || "资料已更新或暂时无法访问，请重新提问获取当前来源。"); });
    return () => { controller.abort(); dialog.current?.close(); };
  }, [selected, courseId, lessonId]);
  if (!list.length) return null;
  return (
    <div className="courseware-sources">
      <p>本次参考资料<span>用于提供回答上下文，具体结论请结合原文核对。</span></p>
      <ul>{list.map(source => <li key={`${source.sourceType}-${source.sourceId}`}>
        {source.sourceType === "COURSEWARE_CHUNK" ? <details>
          <summary><FileText size={15} /><span>{source.referenceNumber ? `资料 ${source.referenceNumber} · ` : ""}{source.title || source.fileName || "课件片段"}</span></summary>
          {source.excerpt && <blockquote>{source.excerpt}</blockquote>}
          <button type="button" onClick={() => setSelected(source)}>查看参考原文</button>
        </details> : <span className="courseware-context-source">{source.sourceType === "COURSE" ? "课程" : "课时"}：{source.title}</span>}
      </li>)}</ul>
      {selected && <dialog className="courseware-source-dialog" ref={dialog} aria-labelledby="courseware-source-title" onCancel={(event) => { event.preventDefault(); setSelected(null); }}>
        <header><div><h2 id="courseware-source-title">参考原文</h2><p>{selected.title}</p></div><button type="button" aria-label="关闭参考原文" onClick={() => setSelected(null)}><X size={20} /></button></header>
        {error ? <p role="alert" className="courseware-error">{error}</p> : sourceText ? <>
          <p className="courseware-source-location">{sourceText.fileName || selected.fileName} · {coursewareLocation(sourceText.locationType || selected.locationType, sourceText.locationIndex || selected.locationIndex)}</p>
          <div className="courseware-source-text">{sourceText.text || sourceText.excerpt}</div>
        </> : <p role="status">正在校验资料版本并读取原文…</p>}
      </dialog>}
    </div>
  );
}
