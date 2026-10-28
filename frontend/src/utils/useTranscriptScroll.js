import { useCallback, useEffect, useRef } from "react";

/** 只滚动消息容器；用户上翻阅读后，不再被新 token 拉回底部。 */
export default function useTranscriptScroll(active = true) {
  const viewportRef = useRef(null);
  const contentRef = useRef(null);
  const following = useRef(true);
  const onScroll = useCallback(() => {
    const viewport = viewportRef.current;
    if (viewport) following.current = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight < 64;
  }, []);
  const followLatest = useCallback(() => {
    following.current = true;
    const viewport = viewportRef.current;
    if (viewport) viewport.scrollTop = viewport.scrollHeight;
  }, []);
  useEffect(() => {
    if (!active) return;
    const viewport = viewportRef.current;
    const content = contentRef.current;
    if (!viewport || !content) return;
    let frame;
    const follow = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        if (following.current) viewport.scrollTop = viewport.scrollHeight;
      });
    };
    const observer = new ResizeObserver(follow);
    observer.observe(content);
    follow();
    return () => { observer.disconnect(); window.cancelAnimationFrame(frame); };
  }, [active]);
  return { viewportRef, contentRef, onScroll, followLatest };
}
