import { useEffect, useRef, useState } from "react";
import {
  createCourseChapter, updateCourseChapter, deleteCourseChapter, reorderCourseChapters,
  createCourseLesson, updateCourseLesson, deleteCourseLesson, reorderCourseLessons,
  createLessonAsset, updateLessonAsset, deleteLessonAsset, getCourseOutline
} from "../api/course";

const TYPES = { article: "图文讲义", video: "视频", document: "文档 / PPT", exercise: "教师练习" };

/** 目录编排复用已有教师接口，发布仍需管理员审核。 */
export default function TeacherCourseEditor({ course, onChanged, onClose }) {
  const [outline, setOutline] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadVersion, setLoadVersion] = useState(0);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    let current = true;
    setLoading(true); setError("");
    getCourseOutline(course.id).then((data) => { if (current) setOutline(data); })
      .catch((err) => { if (current) setError(err.message); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [course.id, loadVersion]);

  const mutate = async (action) => {
    if (busy) return false;
    setBusy(true); setError(""); setNotice("");
    try {
      await action();
      if (!mounted.current) return true;
      setNotice("已保存，课程变更等待管理员审核；审核通过后向学生开放。");
      setLoadVersion((value) => value + 1); onChanged?.();
      return true;
    } catch (err) {
      if (mounted.current) setError(err.message || "课程目录保存失败。");
      return false;
    } finally { if (mounted.current) setBusy(false); }
  };
  const move = (items, index, offset, save) => {
    const ids = items.map((item) => item.id);
    [ids[index], ids[index + offset]] = [ids[index + offset], ids[index]];
    return mutate(() => save(ids));
  };
  return <section className="course-structure-editor" aria-label="课程目录编辑器" aria-busy={busy}>
    <header><div><span className="eyebrow">章节与课时管理</span><h2>{course.title}</h2></div><button disabled={busy} onClick={onClose}>收起目录</button></header>
    <p>先添加章节，再添加课时和对应资料。视频支持 MP4，文档支持 PDF、Word、PPT、TXT；每个课时可保存多个同类型资料。</p>
    {error && <p className="inline-error-state" role="alert">{error}<button disabled={busy || loading} onClick={() => setLoadVersion((value) => value + 1)}>重新加载</button></p>}
    {notice && <p className="inline-success-state" role="status">{notice}</p>}
    {loading && <p role="status">正在加载课程目录…</p>}
    <fieldset disabled={busy || loading}>
      {(outline?.chapters || []).map((chapter, index, chapters) => <article className="structure-chapter" key={chapter.id}>
        <TitleForm title={chapter.title} label={`章节 ${index + 1} 名称`} submitLabel="保存章节" onSave={(title) => mutate(() => updateCourseChapter(chapter.id, { title, description: chapter.description || "" }))} />
        <div className="structure-actions">
          <button disabled={busy || loading || index === 0} onClick={() => move(chapters, index, -1, (ids) => reorderCourseChapters(course.id, ids))}>章节上移</button>
          <button disabled={busy || loading || index === chapters.length - 1} onClick={() => move(chapters, index, 1, (ids) => reorderCourseChapters(course.id, ids))}>章节下移</button>
          <button onClick={() => { if (window.confirm("停用此章节及其课时？历史学习记录会保留。")) mutate(() => deleteCourseChapter(chapter.id)); }}>停用章节</button>
        </div>
        {(chapter.lessons || []).map((lesson, lessonIndex, lessons) => <section className="structure-lesson" key={lesson.id}>
          <TitleForm title={lesson.title} label={`课时 ${lessonIndex + 1} 名称`} submitLabel="保存课时" onSave={(title) => mutate(() => updateCourseLesson(lesson.id, { ...lesson, title }))} />
          <div className="structure-actions"><span>{TYPES[lesson.lessonType]} · {lesson.assetReady ? "资料已就绪" : "待补充资料"}</span>
            <button disabled={busy || loading || lessonIndex === 0} onClick={() => move(lessons, lessonIndex, -1, (ids) => reorderCourseLessons(chapter.id, ids))}>课时上移</button>
            <button disabled={busy || loading || lessonIndex === lessons.length - 1} onClick={() => move(lessons, lessonIndex, 1, (ids) => reorderCourseLessons(chapter.id, ids))}>课时下移</button>
            <button onClick={() => { if (window.confirm("停用此课时？历史学习记录会保留。")) mutate(() => deleteCourseLesson(lesson.id)); }}>停用课时</button>
          </div>
          {(lesson.assets || []).map((asset) => <details key={asset.id} className="structure-asset"><summary>{asset.fileName || "文字资料"} · {asset.transcodingStatus === "ready" ? "可用" : "待处理"}</summary>
            {asset.playbackUrl && <a href={asset.playbackUrl} target="_blank" rel="noreferrer">打开资料</a>}
            <TextAssetForm initial={asset.contentText || ""} label="讲义正文或资料摘要" submitLabel="更新文字" required={["article", "exercise"].includes(lesson.lessonType)} onSave={(contentText) => mutate(() => updateLessonAsset(lesson.id, asset.id, { assetType: lesson.lessonType, contentText, coverUrl: asset.coverUrl || "", transcodingStatus: asset.transcodingStatus }))} />
            <button onClick={() => { if (window.confirm("移除此资料？")) mutate(() => deleteLessonAsset(lesson.id, asset.id)); }}>移除资料</button>
          </details>)}
          <AssetForm lesson={lesson} onSave={(data) => mutate(() => createLessonAsset(lesson.id, data))} />
        </section>)}
        <NewLessonForm onSave={(lesson) => mutate(() => createCourseLesson(chapter.id, lesson))} />
      </article>)}
      {!loading && !outline?.chapters?.length && <p>还没有章节，从下方添加第一章。</p>}
      <TitleForm title="" label="新章节名称" submitLabel="添加章节" reset onSave={(title) => mutate(() => createCourseChapter(course.id, { title }))} />
    </fieldset>
  </section>;
}

function TitleForm({ title, label, submitLabel, onSave, reset }) {
  const [value, setValue] = useState(title);
  useEffect(() => setValue(title), [title]);
  return <form className="structure-row" onSubmit={async (event) => { event.preventDefault(); if (await onSave(value.trim())) { if (reset) setValue(""); } }}>
    <label>{label}<input aria-label={label} value={value} onChange={(event) => setValue(event.target.value)} required maxLength={160} /></label><button type="submit">{submitLabel}</button>
  </form>;
}

function NewLessonForm({ onSave }) {
  const [title, setTitle] = useState(""); const [type, setType] = useState("article");
  return <form className="structure-row" onSubmit={async (event) => { event.preventDefault(); if (await onSave({ title: title.trim(), lessonType: type, durationSeconds: 0 })) setTitle(""); }}>
    <label>新课时名称<input aria-label="新课时名称" required maxLength={180} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
    <label>课时类型<select aria-label="课时类型" value={type} onChange={(event) => setType(event.target.value)}>{Object.entries(TYPES).map(([id, text]) => <option key={id} value={id}>{text}</option>)}</select></label>
    <button type="submit">添加课时</button>
  </form>;
}

function TextAssetForm({ initial, label, onSave, submitLabel, required }) {
  const [content, setContent] = useState(initial);
  useEffect(() => setContent(initial), [initial]);
  return <form onSubmit={(event) => { event.preventDefault(); onSave(content); }}><label>{label}<textarea required={required} maxLength={1000000} value={content} onChange={(event) => setContent(event.target.value)} /></label><button type="submit">{submitLabel}</button></form>;
}

function AssetForm({ lesson, onSave }) {
  const ref = useRef(null); const [content, setContent] = useState("");
  const fileRequired = ["video", "document"].includes(lesson.lessonType);
  return <form className="structure-upload" ref={ref} onSubmit={async (event) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget); data.set("assetType", lesson.lessonType); data.set("contentText", content);
    if (await onSave(data)) { setContent(""); ref.current?.reset(); }
  }}>
    <h4>添加{TYPES[lesson.lessonType]}资料</h4>
    {fileRequired && <label>资料文件<input name="file" type="file" required accept={lesson.lessonType === "video" ? ".mp4" : ".pdf,.doc,.docx,.ppt,.pptx,.txt"} /></label>}
    <label>{fileRequired ? "文字摘要（用于 AI 答疑和出题）" : "学习正文"}<textarea required={!fileRequired} maxLength={1000000} value={content} onChange={(event) => setContent(event.target.value)} /></label>
    <button type="submit">保存资料</button>
  </form>;
}
