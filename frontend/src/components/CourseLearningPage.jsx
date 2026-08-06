import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Circle,
  Clock3,
  FileText,
  ListVideo,
  LoaderCircle,
  PlayCircle,
  RotateCcw
} from "lucide-react";
import { getCourseDetail } from "../api/course";
import {
  getCourseLearningProgress,
  getLessonPlayback,
  saveLessonProgress
} from "../api/learning";
import CourseVideo from "./CourseVideo";

const EMPTY_PROGRESS = {
  totalLessons: 0,
  completedLessons: 0,
  progressPercent: 0,
  totalDurationSeconds: 0,
  watchedSeconds: 0,
  lastLessonId: null,
  lessons: []
};

export default function CourseLearningPage({ courseId, lessonId, setPage }) {
  const [course, setCourse] = useState(null);
  const [progress, setProgress] = useState(EMPTY_PROGRESS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [playback, setPlayback] = useState({ status: "idle", videoUrl: null, assetId: null, reasonCode: null });
  const [saving, setSaving] = useState(false);
  const [playbackReload, setPlaybackReload] = useState(0);
  const videoRef = useRef(null);
  const playbackAbortRef = useRef(null);
  const playbackRequestRef = useRef(0);
  const activeLessonRef = useRef(null);
  const lastSavedSecondRef = useRef(0);
  const restoredLessonRef = useRef(null);
  const mountedRef = useRef(true);

  const chapters = Array.isArray(course?.outline?.chapters) ? course.outline.chapters : [];
  const lessons = useMemo(
    () => chapters.flatMap((chapter, chapterIndex) => (chapter.lessons || []).map((lesson, lessonIndex) => ({
      ...lesson,
      chapterId: chapter.id,
      chapterTitle: chapter.title,
      chapterIndex,
      lessonIndex
    }))),
    [course]
  );
  const activeIndex = lessons.findIndex((item) => Number(item.id) === Number(lessonId));
  const activeLesson = activeIndex >= 0 ? lessons[activeIndex] : null;
  const lessonProgress = progress.lessons.find((item) => Number(item.lessonId) === Number(activeLesson?.id));
  const previousLesson = activeIndex > 0 ? lessons[activeIndex - 1] : null;
  const nextLesson = activeIndex >= 0 && activeIndex < lessons.length - 1 ? lessons[activeIndex + 1] : null;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (!courseId) {
      setError("没有找到要学习的课程。");
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([getCourseDetail(courseId), getCourseLearningProgress(courseId)])
      .then(([courseData, progressData]) => {
        if (cancelled) return;
        setCourse(courseData);
        setProgress(progressData || EMPTY_PROGRESS);
      })
      .catch((loadError) => {
        if (!cancelled) setError(loadError?.message || "学习页加载失败，请稍后重试。");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [courseId]);

  useEffect(() => {
    if (loading || !lessons.length) return;
    if (activeLesson) return;
    const fallbackId = progress.lastLessonId || lessons[0].id;
    setPage("learn", { courseId, lessonId: fallbackId, replace: true });
  }, [loading, lessons.length, activeLesson?.id, progress.lastLessonId, courseId]);

  const clearVideo = () => {
    const video = videoRef.current;
    if (!video) return;
    video.pause();
    video.removeAttribute("src");
    video.load();
  };

  const cancelPlayback = () => {
    playbackRequestRef.current += 1;
    playbackAbortRef.current?.abort();
    playbackAbortRef.current = null;
  };

  const mergeSavedProgress = (saved) => {
    setProgress((current) => {
      const existing = Array.isArray(current.lessons) ? current.lessons : [];
      const nextLessons = existing.map((item) => Number(item.lessonId) === Number(saved.lessonId) ? saved : item);
      if (!nextLessons.some((item) => Number(item.lessonId) === Number(saved.lessonId))) nextLessons.push(saved);
      const completedLessons = nextLessons.filter((item) => item.completed).length;
      const progressPercent = nextLessons.length
        ? Math.round(nextLessons.reduce((sum, item) => sum + Number(item.progressPercent || 0), 0) / nextLessons.length)
        : 0;
      return {
        ...current,
        lessons: nextLessons,
        completedLessons,
        progressPercent,
        watchedSeconds: nextLessons.reduce((sum, item) => sum + Number(item.lastPositionSeconds || 0), 0),
        lastLessonId: saved.lessonId
      };
    });
  };

  const persistLesson = async (lesson, eventType = "heartbeat", silent = false) => {
    if (!lesson) return null;
    const video = videoRef.current;
    const isVideo = lesson.lessonType === "video";
    if (!isVideo && eventType !== "completed") return null;
    const positionSeconds = isVideo ? Math.max(0, Math.floor(video?.currentTime || 0)) : 1;
    const durationSeconds = isVideo
      ? Math.max(0, Math.floor(Number.isFinite(video?.duration) ? video.duration : lesson.durationSeconds || 0))
      : 1;
    if (isVideo && positionSeconds <= 0 && eventType !== "ended") return null;
    if (isVideo) lastSavedSecondRef.current = Math.max(lastSavedSecondRef.current, positionSeconds);
    if (!silent && mountedRef.current) setSaving(true);
    try {
      const saved = await saveLessonProgress(courseId, lesson.id, { positionSeconds, durationSeconds, eventType });
      lastSavedSecondRef.current = Math.max(lastSavedSecondRef.current, Number(saved.lastPositionSeconds || 0));
      if (mountedRef.current) mergeSavedProgress(saved);
      return saved;
    } catch (saveError) {
      if (!silent && mountedRef.current) setNotice(saveError?.message || "课时进度暂时无法保存。");
      return null;
    } finally {
      if (!silent && mountedRef.current) setSaving(false);
    }
  };

  useEffect(() => {
    if (!activeLesson) return;
    activeLessonRef.current = activeLesson;
    cancelPlayback();
    clearVideo();
    restoredLessonRef.current = null;
    lastSavedSecondRef.current = Number(lessonProgress?.lastPositionSeconds || 0);
    setNotice("");
    if (activeLesson.lessonType !== "video") {
      setPlayback({ status: "article", videoUrl: null, assetId: null, reasonCode: null });
      return;
    }
    const requestId = playbackRequestRef.current;
    const controller = new AbortController();
    playbackAbortRef.current = controller;
    setPlayback({ status: "loading", videoUrl: null, assetId: null, reasonCode: null });
    getLessonPlayback(courseId, activeLesson.id, { signal: controller.signal })
      .then((result) => {
        if (requestId !== playbackRequestRef.current || Number(result.lessonId) !== Number(activeLesson.id)) return;
        playbackAbortRef.current = null;
        if (result.playable && result.videoUrl) {
          setPlayback({ status: "playable", videoUrl: result.videoUrl, assetId: result.assetId, reasonCode: null });
          return;
        }
        setPlayback({
          status: result.reasonCode === "ASSET_NOT_READY" ? "processing" : "unavailable",
          videoUrl: null,
          assetId: null,
          reasonCode: result.reasonCode
        });
      })
      .catch((playbackError) => {
        if (playbackError?.code === "ERR_CANCELED" || requestId !== playbackRequestRef.current) return;
        playbackAbortRef.current = null;
        setPlayback({ status: "error", videoUrl: null, assetId: null, reasonCode: "PLAYBACK_REQUEST_FAILED" });
        setNotice(playbackError?.message || "本课时视频加载失败。");
      });
    return () => controller.abort();
  }, [courseId, activeLesson?.id, playbackReload]);

  useEffect(() => {
    const persistOnLeave = () => {
      persistLesson(activeLessonRef.current, "pagehide", true);
    };
    window.addEventListener("pagehide", persistOnLeave);
    window.addEventListener("beforeunload", persistOnLeave);
    return () => {
      window.removeEventListener("pagehide", persistOnLeave);
      window.removeEventListener("beforeunload", persistOnLeave);
      persistOnLeave();
      cancelPlayback();
      clearVideo();
    };
  }, [courseId]);

  const switchLesson = async (lesson) => {
    if (!lesson || Number(lesson.id) === Number(activeLesson?.id)) return;
    persistLesson(activeLesson, "switch", true);
    cancelPlayback();
    clearVideo();
    setPage("learn", { courseId, lessonId: lesson.id });
  };

  const restorePosition = (event) => {
    if (!activeLesson || restoredLessonRef.current === activeLesson.id) return;
    const video = event.currentTarget;
    const savedPosition = Number(lessonProgress?.lastPositionSeconds || 0);
    if (savedPosition > 0 && Number.isFinite(video.duration)) {
      video.currentTime = Math.min(savedPosition, Math.max(0, video.duration - 1));
      setNotice(`已从 ${formatDuration(savedPosition)} 继续播放。`);
    }
    restoredLessonRef.current = activeLesson.id;
  };

  const handleTimeUpdate = (event) => {
    const currentSecond = Math.floor(event.currentTarget.currentTime || 0);
    if (currentSecond - lastSavedSecondRef.current >= 12) persistLesson(activeLesson, "heartbeat", true);
  };

  const completeArticle = async () => {
    const saved = await persistLesson(activeLesson, "completed");
    if (saved?.completed) setNotice("本课时已完成，可以继续下一节。");
  };

  if (loading) {
    return <main className="course-learn-page learn-loading"><LoaderCircle className="spin" size={28} /><span>正在准备学习空间...</span></main>;
  }
  if (error || !course) {
    return (
      <main className="course-learn-page learn-error">
        <strong>{error || "课程不存在或尚未发布。"}</strong>
        <button onClick={() => setPage("home")}><ArrowLeft size={16} />返回课程中心</button>
      </main>
    );
  }
  if (!lessons.length) {
    return (
      <main className="course-learn-page learn-error">
        <strong>这门课程还没有已发布课时。</strong>
        <button onClick={() => setPage("course", { courseId })}><ArrowLeft size={16} />返回课程详情</button>
      </main>
    );
  }

  return (
    <main className="course-learn-page">
      <header className="learn-topbar">
        <button className="learn-back" onClick={() => setPage("course", { courseId })}><ArrowLeft size={17} />课程详情</button>
        <div className="learn-course-title"><span>正在学习</span><strong>{course.title}</strong></div>
        <div className="learn-course-progress">
          <span>{progress.completedLessons || 0}/{progress.totalLessons || lessons.length} 节完成</span>
          <div><i style={{ width: `${progress.progressPercent || 0}%` }} /></div>
          <strong>{progress.progressPercent || 0}%</strong>
        </div>
      </header>

      <div className="learn-workspace">
        <aside className="learn-outline">
          <div className="learn-outline-head"><ListVideo size={18} /><div><strong>课程目录</strong><span>{lessons.length} 个课时</span></div></div>
          <div className="learn-chapter-list">
            {chapters.map((chapter, chapterIndex) => (
              <section key={chapter.id}>
                <header><span>第 {chapterIndex + 1} 章</span><strong>{chapter.title}</strong></header>
                {(chapter.lessons || []).map((lesson) => {
                  const saved = progress.lessons.find((item) => Number(item.lessonId) === Number(lesson.id));
                  const selected = Number(lesson.id) === Number(activeLesson?.id);
                  return (
                    <button className={selected ? "active" : ""} key={lesson.id} onClick={() => switchLesson(lesson)}>
                      {saved?.completed ? <CheckCircle2 className="done" size={17} /> : lesson.lessonType === "video" ? <PlayCircle size={17} /> : <FileText size={17} />}
                      <span><strong>{lesson.title}</strong><small>{lesson.lessonType === "video" ? formatDuration(lesson.durationSeconds) : "图文课时"}{saved?.progressPercent ? ` · ${saved.progressPercent}%` : ""}</small></span>
                    </button>
                  );
                })}
              </section>
            ))}
          </div>
        </aside>

        <section className="learn-stage">
          <div className="learn-stage-head">
            <div><span>{activeLesson?.chapterTitle}</span><h1>{activeLesson?.title}</h1></div>
            <span className={`learn-save-state${saving ? " is-saving" : ""}`}>{saving ? <LoaderCircle className="spin" size={14} /> : <CheckCircle2 size={14} />}{saving ? "正在保存" : "进度自动保存"}</span>
          </div>

          <div className="learn-content-stage">
            {playback.status === "loading" && <div className="learn-stage-state"><LoaderCircle className="spin" size={30} /><strong>正在获取本课时视频...</strong></div>}
            {playback.status === "playable" && playback.videoUrl && (
              <CourseVideo
                ref={videoRef}
                key={`${activeLesson.id}-${playback.assetId}`}
                className="learn-video"
                src={playback.videoUrl}
                title={activeLesson.title}
                autoPlay
                onLoadedMetadata={restorePosition}
                onTimeUpdate={handleTimeUpdate}
                onPause={() => persistLesson(activeLesson, "pause", true)}
                onEnded={async () => {
                  const saved = await persistLesson(activeLesson, "ended");
                  if (saved?.completed) setNotice(nextLesson ? "本课时已完成，可以进入下一节。" : "恭喜完成本课程最后一节！");
                }}
              />
            )}
            {["processing", "unavailable", "error"].includes(playback.status) && (
              <div className="learn-stage-state">
                <RotateCcw size={30} />
                <strong>{playback.status === "processing" ? "视频正在处理中" : playback.status === "error" ? "视频加载失败" : "本课时暂无可播放视频"}</strong>
                <p>已停止上一课时的视频，不会继续播放其他章节的内容。</p>
                <button onClick={() => {
                  setPlayback({ status: "idle", videoUrl: null, assetId: null, reasonCode: null });
                  setPlaybackReload((value) => value + 1);
                }}>重新加载</button>
              </div>
            )}
            {playback.status === "article" && (
              <article className="learn-article">
                <div className="learn-article-label"><FileText size={17} />图文课时</div>
                <div className="learn-article-copy">{activeLesson?.contentText || course.content || "本课时内容正在整理，请先结合课程目标完成自主学习与笔记。"}</div>
                <button disabled={lessonProgress?.completed || saving} onClick={completeArticle}>
                  {lessonProgress?.completed ? <><CheckCircle2 size={17} />本课时已完成</> : <><Circle size={17} />完成本课</>}
                </button>
              </article>
            )}
          </div>

          {notice && <p className="learn-notice" role="status">{notice}</p>}
          <footer className="learn-controls">
            <button disabled={!previousLesson} onClick={() => switchLesson(previousLesson)}><ChevronLeft size={18} /><span><small>上一节</small>{previousLesson?.title || "已经是第一节"}</span></button>
            <div><Clock3 size={15} /><span>本节 {activeLesson?.lessonType === "video" ? formatDuration(activeLesson.durationSeconds) : "图文学习"}</span></div>
            <button className="next" disabled={!nextLesson} onClick={() => switchLesson(nextLesson)}><span><small>下一节</small>{nextLesson?.title || "已经是最后一节"}</span><ChevronRight size={18} /></button>
          </footer>
        </section>
      </div>
    </main>
  );
}

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0));
  const minutes = Math.floor(value / 60);
  const remain = Math.floor(value % 60);
  return `${minutes}:${String(remain).padStart(2, "0")}`;
}
