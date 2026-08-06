import { useEffect, useState } from "react";
import { ArrowRight, BookOpen, FileText, GitBranch, Heart, PlayCircle, Users } from "lucide-react";
import request from "../utils/request";
import { handleActionError } from "../utils/auth";
import { buildResourceTextContent } from "../utils/format";
import { CourseVideoPreview } from "./CourseVideo";

export default function CourseCard({ item, onUpdate, onOpenGraph, onOpenCourse }) {
  const Icon = item.type === "video" ? PlayCircle : FileText;
  const [busyAction, setBusyAction] = useState("");
  const [status, setStatus] = useState("");
  const progress = Math.max(0, Math.min(Number(item.progress || 0), 100));
  const textContent = item.content || buildResourceTextContent(item);

  useEffect(() => {
    if (!status) return undefined;
    const timer = setTimeout(() => setStatus(""), 2600);
    return () => clearTimeout(timer);
  }, [status]);

  const toggleFavorite = async () => {
    if (!onUpdate || busyAction) return;
    setBusyAction("favorite");
    setStatus("");
    try {
      const data = await request(`/resources/${item.id}/favorite`, { method: "POST" });
      onUpdate(item.id, { favorited: data.favorited, favoriteCount: data.favoriteCount });
      setStatus(data.favorited ? "已加入收藏" : "已取消收藏");
    } catch (error) {
      handleActionError(error, "收藏操作失败");
    } finally {
      setBusyAction("");
    }
  };

  return (
    <article className="resource-card">
      <button className={`course-cover course-cover-button${item.type === "video" && item.fileUrl ? " has-video" : ""}`} onClick={() => onOpenCourse?.(item)} aria-label={`查看课程：${item.title}`}>
        {item.type === "video" && item.fileUrl && <CourseVideoPreview src={item.fileUrl} title={item.title} />}
        <span className="course-cover-tag">{item.type === "video" ? "视频课" : "学习资料"}</span>
        {(!item.fileUrl || item.type !== "video") && <Icon size={38} />}
        {(!item.fileUrl || item.type !== "video") && <small>{item.directionName || item.subject || "精选课程"}</small>}
      </button>
      <div className="course-card-body">
        <div className="course-level-row">
          <span>{item.difficulty || item.level || "入门"}</span>
          <span><Users size={14} /> {item.learnerCount || 0} 人在学</span>
        </div>
        <button className="course-title-button" onClick={() => onOpenCourse?.(item)}><h3>{item.title}</h3></button>
        <p>{item.teacher || "平台讲师"} · {item.minutes || 45} 分钟</p>
        <div className="course-card-progress" aria-label={`学习进度 ${progress}%`}>
          <span className="course-card-progress-track"><i style={{ width: `${progress}%` }} /></span>
          <b>{progress}%</b>
        </div>
        {status && <p className="resource-action-status">{status}</p>}
        <div className="resource-actions">
          <button className="course-start-button" disabled={!!busyAction} onClick={() => onOpenCourse?.(item)}>
            {progress > 0 ? "继续学习" : "查看课程"}<ArrowRight size={15} />
          </button>
          {onOpenGraph && <button className="graph-link-button" disabled={!!busyAction} onClick={() => onOpenGraph(item.directionName || item.subject)} title="查看关联知识图谱"><GitBranch size={16} /></button>}
          <button className={item.favorited ? "active icon-button" : "icon-button"} disabled={!!busyAction} onClick={toggleFavorite} title={item.favorited ? "取消收藏" : "收藏课程"}><Heart size={16} /></button>
        </div>
        <details className="resource-text-study">
          <summary><BookOpen size={15} />课程介绍</summary>
          <div>{textContent.split(/\n+/).slice(0, 2).map((paragraph, index) => <p key={`${item.id}-text-${index}`}>{paragraph}</p>)}</div>
        </details>
      </div>
    </article>
  );
}
