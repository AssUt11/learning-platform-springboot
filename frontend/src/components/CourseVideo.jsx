import { forwardRef, useEffect, useRef, useState } from "react";
import { AlertCircle, Play } from "lucide-react";

function safeVideoUrl(src) {
  return src ? encodeURI(src) : "";
}

const CourseVideo = forwardRef(function CourseVideo({
  src,
  title,
  autoPlay = false,
  className = "",
  onLoadedMetadata,
  onTimeUpdate,
  onPause,
  onEnded,
  onPlay,
  onError
}, ref) {
  const [failed, setFailed] = useState(false);

  useEffect(() => setFailed(false), [src]);

  if (failed) {
    return (
      <div className={`course-video-error ${className}`} role="status">
        <AlertCircle size={30} />
        <div>
          <strong>视频暂时无法播放</strong>
          <p>请确认本地视频文件仍在上传目录中，然后刷新页面重试。</p>
        </div>
      </div>
    );
  }

  return (
    <video
      ref={ref}
      className={className}
      controls
      autoPlay={autoPlay}
      playsInline
      preload="metadata"
      src={safeVideoUrl(src)}
      aria-label={title ? `${title} 课程视频` : "课程视频"}
      onLoadedMetadata={onLoadedMetadata}
      onTimeUpdate={onTimeUpdate}
      onPause={onPause}
      onEnded={onEnded}
      onPlay={onPlay}
      onError={(event) => {
        setFailed(true);
        onError?.(event);
      }}
    >
      当前浏览器不支持视频播放，请升级浏览器后重试。
    </video>
  );
});

export default CourseVideo;

export function CourseVideoPreview({ src, title }) {
  const videoRef = useRef(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);

  const playPreview = () => {
    if (!videoRef.current || failed) return;
    videoRef.current.play().catch(() => {});
  };

  const pausePreview = () => {
    videoRef.current?.pause();
  };

  return (
    <span
      className={`course-video-preview${ready ? " is-ready" : ""}${failed ? " is-error" : ""}`}
      onMouseEnter={playPreview}
      onMouseLeave={pausePreview}
      onFocus={playPreview}
      onBlur={pausePreview}
      aria-hidden="true"
    >
      {!failed && (
        <video
          ref={videoRef}
          muted
          loop
          playsInline
          preload="metadata"
          src={safeVideoUrl(src)}
          onLoadedData={() => setReady(true)}
          onError={() => setFailed(true)}
        />
      )}
      <span className="course-video-preview-shade" />
      <span className="course-video-preview-play"><Play size={18} fill="currentColor" /></span>
      <small>{failed ? "本地视频未找到" : ready ? "悬停预览 · 点击学习" : `正在加载 ${title || "课程视频"}`}</small>
    </span>
  );
}
