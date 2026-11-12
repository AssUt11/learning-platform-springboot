import { useEffect, useRef, useState } from "react";
import { CheckCircle2, FileSearch, LoaderCircle, RefreshCcw } from "lucide-react";
import { confirmCourseware, extractCourseware, getCourseware } from "../api/courseware";
import { coursewareLocation } from "../utils/courseware";
import "../courseware.css";

const stateLabels = {
  NOT_STARTED: "尚未解析", QUEUED: "排队中", PROCESSING: "正在解析", READY: "待教师确认",
  CONFIRMED: "已确认", FAILED: "解析失败", STALE: "资料已变更", SUPERSEDED: "已有更新版本"
};
export default function CoursewareAssetPanel({ lessonId, asset, onChanged }) {
  const [expanded, setExpanded] = useState(false);
  const [document, setDocument] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const [reload, setReload] = useState(0);
  const generation = useRef(0);
  const supportedFile = /\.(pdf|pptx|docx|txt)$/i.test(asset.fileName || "");
  const status = document?.status || "NOT_STARTED";
  const pending = ["QUEUED", "PROCESSING"].includes(status);

  useEffect(() => {
    generation.current += 1;
    setDocument(null); setError(""); setShowAll(false); setBusy(false); setLoading(false);
    return () => { generation.current += 1; };
  }, [lessonId, asset.id]);

  useEffect(() => {
    if (!expanded) return undefined;
    const controller = new AbortController();
    const version = generation.current;
    let timer;
    const load = async (quiet = false) => {
      if (!quiet) setLoading(true);
      try {
        const result = await getCourseware(lessonId, asset.id, { signal: controller.signal });
        if (controller.signal.aborted || version !== generation.current) return;
        setDocument(result); setError("");
        if (["QUEUED", "PROCESSING"].includes(result.status)) timer = setTimeout(() => load(true), 1800);
      } catch (failure) {
        if (!controller.signal.aborted && version === generation.current) setError(failure.message || "无法读取解析状态。");
      } finally {
        if (!controller.signal.aborted && version === generation.current) setLoading(false);
      }
    };
    load();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [expanded, lessonId, asset.id, reload]);

  const mutate = async (action) => {
    if (busy || pending) return;
    const version = ++generation.current;
    setBusy(true); setError("");
    try {
      const result = await action();
      if (version !== generation.current) return;
      setDocument(result); setShowAll(false); setReload(value => value + 1);
      if (result.status === "CONFIRMED") onChanged?.();
    } catch (failure) {
      if (version === generation.current) setError(failure.message || "课件操作未完成，请重试。");
    } finally {
      if (version === generation.current) setBusy(false);
    }
  };

  const segments = Array.isArray(document?.segments) ? document.segments : [];
  const canExtract = document?.supported ?? supportedFile;
  return (
    <details className="courseware-asset-panel" onToggle={(event) => setExpanded(event.currentTarget.open)}>
      <summary><FileSearch size={17} /><span>AI 课件解析</span><small>{stateLabels[status] || status}</small></summary>
      {expanded && <div className="courseware-asset-body" aria-busy={loading || busy || pending}>
        <p>解析原文件并核对内容。确认后，AI 可检索本课时的资料片段，并展示本次参考来源。支持 20 MB 内的 PDF、PPTX、DOCX、UTF-8 TXT；PDF 和 PPT 最多 200 页或张。</p>
        {!canExtract && <p className="courseware-hint">支持 PDF、PPTX、DOCX、TXT。旧版 DOC/PPT 请先另存为新格式；扫描图片暂不自动识别。</p>}
        {loading && <p role="status">正在读取课件状态…</p>}
        {pending && <p className="courseware-task-status" role="status"><LoaderCircle size={17} />{stateLabels[status]}。离开页面后任务仍会继续，可稍后回来查看。</p>}
        {status === "STALE" && <p className="courseware-hint">原文件或资料内容已变更，旧解析已停止用于答疑，请重新解析并确认。</p>}
        {(error || document?.error) && <p className="courseware-error" role="alert">{error || document.error}<button type="button" onClick={() => setReload(value => value + 1)}>重新读取</button></p>}
        {segments.length > 0 && <>
          <div className="courseware-preview-heading"><strong>{asset.fileName}</strong><span>{document.segmentCount || segments.length} 个片段 · {document.characterCount ?? segments.reduce((sum, segment) => sum + segment.text.length, 0)} 字</span></div>
          <div className="courseware-segments" aria-label="解析内容预览">
            {(showAll ? segments : segments.slice(0, 3)).map(segment => <article key={segment.id}>
              <strong>{coursewareLocation(segment.locationType, segment.locationIndex)}</strong><p>{segment.text}</p>
            </article>)}
          </div>
          {segments.length > 3 && <button className="courseware-more" type="button" onClick={() => setShowAll(value => !value)}>{showAll ? "收起预览" : `查看全部 ${segments.length} 个片段`}</button>}
        </>}
        {status === "CONFIRMED" && <p className="courseware-confirmed" role="status"><CheckCircle2 size={17} />已确认。资料仍遵循原有课程发布与审核权限。</p>}
        <div className="courseware-actions">
          {canExtract && <button type="button" disabled={busy || pending || loading} onClick={() => mutate(() => extractCourseware(lessonId, asset.id))}><RefreshCcw size={16} />{document?.documentId ? "重新解析" : "开始解析"}</button>}
          {status === "READY" && <button className="courseware-confirm" type="button" disabled={busy || !segments.length} onClick={() => mutate(() => confirmCourseware(lessonId, asset.id, document.documentId, document.versionHash))}>{busy ? "正在确认…" : "确认并用于 AI 答疑"}</button>}
        </div>
      </div>}
    </details>
  );
}
