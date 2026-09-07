import { useEffect, useRef, useState } from "react";
import { Bot, BrainCircuit, Send, Square, ThumbsDown, ThumbsUp, UserRound } from "lucide-react";
import { cancelAiTutorRequest, sendAiTutorFeedback, streamAiTutor } from "../api/aiTutor";

export default function AiTutorPanel({ courseId, lessonId, courseTitle, lessonTitle }) {
  const [conversationId, setConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [requestId, setRequestId] = useState(null);
  const [error, setError] = useState("");
  const [knowledgeSource, setKnowledgeSource] = useState("mysql");
  const [feedback, setFeedback] = useState({});
  const abortRef = useRef(null);
  const completedRef = useRef(false);
  const requestIdRef = useRef(null);

  useEffect(() => {
    const activeRequest = requestIdRef.current;
    abortRef.current?.abort();
    if (activeRequest) cancelAiTutorRequest(activeRequest).catch(() => {});
    abortRef.current = null;
    requestIdRef.current = null;
    setConversationId(null);
    setMessages([]);
    setInput("");
    setError("");
    setStreaming(false);
    setRequestId(null);
  }, [courseId, lessonId]);

  useEffect(() => () => {
    const activeRequest = requestIdRef.current;
    abortRef.current?.abort();
    if (activeRequest) cancelAiTutorRequest(activeRequest).catch(() => {});
  }, []);

  const submit = async (event) => {
    event.preventDefault();
    const question = input.trim();
    if (!question || streaming || !courseId || !lessonId) return;
    setInput("");
    setError("");
    setStreaming(true);
    setRequestId(null);
    requestIdRef.current = null;
    completedRef.current = false;
    setMessages((current) => [
      ...current,
      { role: "user", content: question },
      { role: "assistant", content: "", generating: true }
    ]);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await streamAiTutor({
        conversationId,
        courseId,
        lessonId,
        message: question,
        mode: "GUIDED_EXPLANATION"
      }, {
        signal: controller.signal,
        onEvent: handleEvent
      });
      if (!completedRef.current) {
        setError("回答连接提前结束，请重新提问。");
        markAssistantStopped();
      }
    } catch (streamError) {
      if (streamError?.name !== "AbortError") {
        setError(streamError?.message || "AI 回答暂时不可用。");
        markAssistantStopped();
      }
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
      setStreaming(false);
    }
  };

  const handleEvent = (event, data) => {
    if (event === "conversation.created") {
      setConversationId(data.conversationId);
      if (data.requestId) {
        setRequestId(data.requestId);
        requestIdRef.current = data.requestId;
      }
    }
    if (event === "message.started") {
      setRequestId(data.requestId);
      requestIdRef.current = data.requestId;
      setKnowledgeSource(data.knowledgeSource || "mysql");
    }
    if (event === "message.delta") {
      setMessages((current) => updateLastAssistant(current, (message) => ({
        ...message,
        content: `${message.content || ""}${data.content || ""}`
      })));
    }
    if (event === "citation.added") {
      setMessages((current) => updateLastAssistant(current, (message) => ({
        ...message,
        sources: [...(message.sources || []), data]
      })));
    }
    if (event === "message.completed") {
      completedRef.current = true;
      requestIdRef.current = null;
      setMessages((current) => updateLastAssistant(current, (message) => ({
        ...message,
        generating: false,
        messageId: data.messageId,
        neo4jUsed: !!data.neo4jUsed
      })));
      setStreaming(false);
    }
    if (event === "error") {
      completedRef.current = true;
      requestIdRef.current = null;
      setError(data.message || "AI 回答生成失败。");
      markAssistantStopped();
      setStreaming(false);
    }
  };

  const stop = async () => {
    const activeRequest = requestIdRef.current || requestId;
    abortRef.current?.abort();
    if (activeRequest) await cancelAiTutorRequest(activeRequest).catch(() => null);
    requestIdRef.current = null;
    completedRef.current = true;
    markAssistantStopped();
    setStreaming(false);
  };

  const rate = async (messageId, rating) => {
    if (!messageId) return;
    try {
      await sendAiTutorFeedback(messageId, rating);
      setFeedback((current) => ({ ...current, [messageId]: rating }));
    } catch (feedbackError) {
      setError(feedbackError?.message || "反馈保存失败。");
    }
  };

  const markAssistantStopped = () => {
    setMessages((current) => updateLastAssistant(current, (message) => ({ ...message, generating: false })));
  };

  return (
    <section className="ai-tutor-panel" aria-label="AI 伴学">
      <header className="ai-tutor-head">
        <div className="ai-tutor-mark"><BrainCircuit size={20} /></div>
        <div><strong>AI 伴学</strong><span>围绕“{lessonTitle || "当前课时"}”答疑</span></div>
        <small className={knowledgeSource.includes("neo4j") ? "graph-on" : ""}>
          {knowledgeSource.includes("neo4j") ? "Neo4j 图谱增强" : "课程资料模式"}
        </small>
      </header>

      <div className="ai-tutor-messages" aria-live="polite">
        {!messages.length && (
          <div className="ai-tutor-empty">
            <Bot size={25} />
            <div><strong>有什么没听懂？</strong><p>我会结合 {courseTitle || "本课程"} 的当前课时和知识图谱给出提示与例子。</p></div>
          </div>
        )}
        {messages.map((message, index) => (
          <article className={`ai-tutor-message ${message.role}`} key={`${message.role}-${index}`}>
            <div>{message.role === "assistant" ? <Bot size={17} /> : <UserRound size={17} />}</div>
            <section>
              <strong>{message.role === "assistant" ? "AI 助教" : "我"}</strong>
              <p>{message.content || (message.generating ? "正在组织回答…" : "本次没有生成有效回答。")}</p>
              {!!message.sources?.length && (
                <div className="ai-tutor-sources">
                  {message.sources.map((source) => <span key={`${source.sourceType}-${source.sourceId}`}>{source.sourceType === "COURSE" ? "课程" : "课时"}：{source.title}</span>)}
                </div>
              )}
              {message.role === "assistant" && message.messageId && (
                <footer>
                  <button className={feedback[message.messageId] === 1 ? "active" : ""} onClick={() => rate(message.messageId, 1)} aria-label="回答有帮助"><ThumbsUp size={13} /></button>
                  <button className={feedback[message.messageId] === -1 ? "active" : ""} onClick={() => rate(message.messageId, -1)} aria-label="回答需改进"><ThumbsDown size={13} /></button>
                </footer>
              )}
            </section>
          </article>
        ))}
      </div>

      {error && <p className="ai-tutor-error">{error}</p>}
      <form className="ai-tutor-form" onSubmit={submit}>
        <textarea
          value={input}
          onChange={(event) => setInput(event.target.value.slice(0, 1000))}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
          placeholder="针对当前课时提问，Enter 发送，Shift+Enter 换行"
          disabled={streaming}
          maxLength={1000}
        />
        {streaming
          ? <button className="stop" type="button" onClick={stop}><Square size={15} />停止</button>
          : <button type="submit" disabled={!input.trim()}><Send size={15} />发送</button>}
      </form>
      <p className="ai-tutor-disclaimer">AI 内容可能有误，请结合课件和教师讲解判断；AI 不会修改成绩或学习记录。</p>
    </section>
  );
}

function updateLastAssistant(messages, updater) {
  const next = [...messages];
  for (let index = next.length - 1; index >= 0; index -= 1) {
    if (next[index].role === "assistant") {
      next[index] = updater(next[index]);
      break;
    }
  }
  return next;
}
