import { useEffect, useRef, useState } from "react";
import GenerationStatus from "./GenerationStatus";

const segmenter = typeof Intl.Segmenter === "function" ? new Intl.Segmenter("zh", { granularity: "grapheme" }) : null;
function graphemes(text) {
  if (segmenter) return Array.from(segmenter.segment(text));
  let index = 0;
  return Array.from(text, (segment) => { const item = { segment, index }; index += segment.length; return item; });
}

/** 仅让新收到的一小段文字渐显；正文原位排版，历史回答不重新播放。 */
export default function InkResponse({ content = "", generating = false, status = "completed", emptyMessage = "本次没有生成有效回答。" }) {
  const text = String(content || "");
  const [frame, setFrame] = useState({ settled: text, arriving: "", sequence: 0 });
  const rendered = useRef(text);
  const latest = useRef(text);
  const [reducedMotion, setReducedMotion] = useState(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  const stopped = status === "cancelled";
  const failed = status === "failed";

  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReducedMotion(media.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    latest.current = text;
    if (!generating || reducedMotion || !text.startsWith(rendered.current)) {
      rendered.current = text;
      setFrame((current) => ({ settled: text, arriving: "", sequence: current.sequence + 1 }));
    }
  }, [text, generating, reducedMotion]);

  useEffect(() => {
    if (!generating || reducedMotion) return;
    const flush = () => {
      // 不显示尚未收到低代理项的半个 emoji；多码点字符保持在同一个动画片段。
      const next = latest.current.replace(/[\uD800-\uDBFF]$/, "");
      if (next === rendered.current) return;
      const previous = rendered.current;
      const pieces = graphemes(next);
      const firstUnread = next.startsWith(previous) ? pieces.findIndex((piece) => piece.index + piece.segment.length > previous.length) : 0;
      if (firstUnread < 0) return;
      const batchSize = Math.max(3, Math.min(24, Math.ceil((pieces.length - firstUnread) / 10)));
      const settled = next.slice(0, pieces[firstUnread].index);
      const arriving = pieces.slice(firstUnread, firstUnread + batchSize).map((piece) => piece.segment).join("");
      rendered.current = settled + arriving;
      setFrame((current) => ({ settled, arriving, sequence: current.sequence + 1 }));
    };
    const timer = window.setInterval(flush, 64);
    return () => window.clearInterval(timer);
  }, [generating, reducedMotion]);

  // 停止、完成和异常时立即展示已接收的全文，不让动画队列吞掉尾字。
  const shown = generating && !reducedMotion ? frame : { settled: text, arriving: "", sequence: 0 };
  const label = generating ? (text ? "正在生成回答" : "正在整理回答")
    : stopped ? "已停止生成" : failed || status !== "completed" ? "本次生成未完成" : text ? "回答已生成" : "暂无有效回答";
  return <div className={`ink-response${generating ? " is-generating" : ""}`} data-state={generating ? "generating" : status}>
    <GenerationStatus active={generating} label={label} />
    <p className="ink-response-copy" aria-busy={generating}>
      {shown.settled}<span className="ink-arrival" key={shown.sequence}>{shown.arriving}</span>
      {!text && !generating && emptyMessage}
    </p>
  </div>;
}
