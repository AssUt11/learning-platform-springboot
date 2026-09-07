import request from "../utils/request";

const apiBase = (import.meta.env.VITE_API_BASE || "/api").replace(/\/$/, "");

export async function streamAiTutor(payload, { signal, onEvent } = {}) {
  const token = localStorage.getItem("learning_token");
  const response = await fetch(`${apiBase}/ai/tutor/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload),
    signal
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    const requestError = new Error(error?.message || error?.data?.message || "AI 伴学请求失败");
    requestError.status = response.status;
    throw requestError;
  }
  if (!response.body) throw new Error("浏览器不支持流式回答。");

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() || "";
    blocks.forEach((block) => dispatchBlock(block, onEvent));
    if (done) break;
  }
  if (buffer.trim()) dispatchBlock(buffer, onEvent);
}

function dispatchBlock(block, onEvent) {
  let event = "message";
  const data = [];
  block.split(/\r?\n/).forEach((line) => {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
  });
  if (!data.length || !onEvent) return;
  const raw = data.join("\n");
  let payload = raw;
  try {
    payload = JSON.parse(raw);
  } catch {
    // Keep plain-text events readable if a proxy changes the payload.
  }
  onEvent(event, payload);
}

export const cancelAiTutorRequest = (requestId) => request(`/ai/requests/${requestId}/cancel`, {
  method: "POST"
});

export const sendAiTutorFeedback = (messageId, rating, reason = "") => request(
  `/ai/messages/${messageId}/feedback`,
  { method: "POST", body: JSON.stringify({ rating, reason }) }
);
