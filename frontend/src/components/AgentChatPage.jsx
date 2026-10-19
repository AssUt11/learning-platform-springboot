import { useEffect, useRef, useState } from "react";
import { Bot, Check, ChevronRight, MessageSquare, Plus, Send, Sparkles, Square, UserRound } from "lucide-react";
import { cancelAgentChat, getAgentChatStatus, getAgentConversation, getAgentConversations, streamAgentChat } from "../api/agentChat";
import { isAuthExpiredError, notifyUnauthorized } from "../utils/auth";

const suggestions = ["帮我解释一个难懂的知识点", "如何制定一周学习计划？", "用一个例子解释数据库索引"];

export default function AgentChatPage({ user, active }) {
  const [conversations, setConversations] = useState([]);
  const [conversationId, setConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [connection, setConnection] = useState(null);
  const initialized = useRef(false);
  const mounted = useRef(true);
  const running = useRef(false);
  const requestId = useRef(null);
  const abort = useRef(null);
  const complete = useRef(false);
  const cancelled = useRef(false);
  const bottom = useRef(null);
  const selection = useRef(0);
  const draftKey = `learning_agent_chat_selected:${user.id}`;

  const showError = cause => {
    if (isAuthExpiredError(cause)) notifyUnauthorized(cause);
    else if (mounted.current) setError(cause?.message || "对话加载失败，请稍后重试。");
  };
  const refreshList = async () => {
    const list = await getAgentConversations();
    if (mounted.current) setConversations(Array.isArray(list) ? list : []);
  };
  const selectConversation = async id => {
    if (running.current) return;
    const version = ++selection.current;
    setLoading(true);
    setError("");
    try {
      const data = await getAgentConversation(id);
      if (!mounted.current || version !== selection.current) return;
      setConversationId(data.id);
      setMessages(Array.isArray(data.messages) ? data.messages : []);
      try { sessionStorage.setItem(draftKey, String(data.id)); } catch { /* Optional navigation memory. */ }
    } catch (cause) { if (version === selection.current) showError(cause); }
    finally { if (mounted.current && version === selection.current) setLoading(false); }
  };

  useEffect(() => {
    if (!active || initialized.current) return;
    initialized.current = true;
    refreshList().catch(showError);
    getAgentChatStatus().then(data => { if (mounted.current) setConnection(data); }).catch(showError);
    let saved;
    try { saved = Number(sessionStorage.getItem(draftKey)); } catch { /* New chat if storage is disabled. */ }
    if (saved > 0) selectConversation(saved);
  }, [active]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      abort.current?.abort();
      if (requestId.current) cancelAgentChat(requestId.current).catch(() => {});
    };
  }, []);

  useEffect(() => {
    if (active) bottom.current?.scrollIntoView({ block: "nearest" });
  }, [messages, active]);

  const updateAssistant = patch => setMessages(current => {
    const next = [...current];
    const index = next.findLastIndex(item => item.role === "assistant");
    if (index >= 0) next[index] = typeof patch === "function" ? patch(next[index]) : { ...next[index], ...patch };
    return next;
  });
  const newConversation = () => {
    if (running.current) return;
    ++selection.current;
    setConversationId(null); setMessages([]); setInput(""); setError(""); setLoading(false);
    try { sessionStorage.removeItem(draftKey); } catch { /* Optional navigation memory. */ }
  };
  const send = async event => {
    event.preventDefault();
    const question = input.trim();
    if (!question || running.current || loading) return;
    running.current = true;
    complete.current = false;
    cancelled.current = false;
    requestId.current = null;
    const controller = new AbortController();
    abort.current = controller;
    setStreaming(true); setError(""); setInput("");
    setMessages(current => [...current, { role: "user", content: question, status: "completed" },
      { role: "assistant", content: "", status: "queued" }]);
    try {
      await streamAgentChat({ conversationId, message: question }, {
        signal: controller.signal,
        onEvent: (name, data) => {
          if (!mounted.current) return;
          if (name === "conversation.created") {
            requestId.current = data.requestId;
            setConversationId(data.conversationId);
            updateAssistant({ id: data.messageId });
            try { sessionStorage.setItem(draftKey, String(data.conversationId)); } catch { /* Optional navigation memory. */ }
            setConnection(current => ({ ...current, available: true, model: data.model }));
          } else if (name === "message.started") updateAssistant({ status: "generating" });
          else if (name === "message.delta") updateAssistant(message => ({ ...message, content: message.content + (data.content || "") }));
          else if (name === "message.completed") {
            complete.current = true;
            updateAssistant({ id: data.messageId, status: "completed" });
          } else if (name === "error") {
            complete.current = true;
            const stopped = data.code === "AI_REQUEST_CANCELLED";
            updateAssistant({ status: stopped ? "cancelled" : "failed" });
            if (!stopped) setError(data.message || "回答生成失败，请重新发送。");
          }
        }
      });
      if (!complete.current && !cancelled.current && mounted.current) {
        setError("连接提前结束，已保存的对话可在历史中查看。");
        updateAssistant({ status: "failed" });
      }
    } catch (cause) {
      if (mounted.current && !cancelled.current && cause?.name !== "AbortError") {
        updateAssistant({ status: "failed" }); showError(cause);
      }
    } finally {
      running.current = false;
      requestId.current = null;
      if (mounted.current) {
        setStreaming(false);
        refreshList().catch(showError);
      }
    }
  };
  const stop = async () => {
    cancelled.current = true;
    abort.current?.abort();
    updateAssistant({ status: "cancelled" });
    if (requestId.current) {
      try { await cancelAgentChat(requestId.current); } catch (cause) { showError(cause); }
    }
  };

  return (
    <main className="agent-chat-page content">
      <div className="agent-chat-title"><span className="eyebrow">你的学习搭档</span><h1>Agent 对话</h1><p>和微调模型聊问题、理思路，让每一次追问都有回应。</p></div>
      <div className="agent-chat-workspace">
        <aside className="agent-chat-sidebar" aria-label="历史对话">
          <button className="agent-chat-new" onClick={newConversation} disabled={streaming}><Plus size={18} />新建对话</button>
          <h2>历史对话 <span>{conversations.length}</span></h2>
          <div className="agent-chat-history">
            {conversations.map(item => <button key={item.id} disabled={streaming || loading} aria-current={item.id === conversationId ? "true" : undefined}
              className={item.id === conversationId ? "active" : ""} onClick={() => selectConversation(item.id)}>
              <MessageSquare size={17} /><span><strong>{item.title}</strong><small>{item.preview || "继续这段对话"}</small></span><ChevronRight size={14} />
            </button>)}
            {!conversations.length && <p className="agent-chat-history-empty">第一段对话，从你的问题开始。<br />回答会自动保存到这里。</p>}
          </div>
          <div className="agent-chat-model"><Sparkles size={17} /><div><strong>知度课堂微调模型</strong><span>{connection?.model || "LLaMA-Factory"}</span></div></div>
        </aside>
        <section className="agent-chat-main" aria-label="Agent 对话窗口">
          <header><div className="agent-chat-avatar"><Bot size={23} /></div><div><strong>知度 Agent</strong><span>可以连续追问，也可以从一个新问题开始</span></div>
            <small className={connection?.available ? "online" : ""}>{connection?.available ? <><Check size={13} />已连接微调模型</> : connection ? "模型未连接" : "正在检查连接"}</small></header>
          <div className="agent-chat-transcript" aria-live="polite" aria-busy={loading || streaming}>
            {loading ? <p className="agent-chat-loading">正在加载对话…</p> : messages.length ? messages.map((message, index) => (
              <article className={`agent-chat-message ${message.role}`} key={message.id || `${message.role}-${index}`}>
                <div className="agent-chat-avatar">{message.role === "assistant" ? <Bot size={19} /> : <UserRound size={19} />}</div>
                <section><strong>{message.role === "assistant" ? "知度 Agent" : "我"}</strong>
                  <p>{message.content || (["queued", "generating"].includes(message.status) && streaming ? "正在组织回答…" : message.status === "cancelled" ? "本次回答已停止。" : "本次回答未完成，请重新提问。")}</p>
                  {message.status === "cancelled" && message.content && <small>已停止生成</small>}
                </section>
              </article>
            )) : <div className="agent-chat-welcome"><span><Sparkles size={30} /></span><h2>你好，有什么想一起探讨的？</h2><p>一个概念、一段思路，或下一步的学习计划。<br />把问题交给我，我们一步一步聊清楚。</p><div>{suggestions.map(text => <button key={text} onClick={() => setInput(text)}>{text}<ChevronRight size={15} /></button>)}</div></div>}
            <div ref={bottom} />
          </div>
          <div className="agent-chat-compose">
            {(error || (connection && !connection.available)) && <p className="agent-chat-error" role="alert">{error || connection.message || "模型服务暂不可用。"}</p>}
            <form onSubmit={send}><textarea aria-label="发送给 Agent 的问题" placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
              value={input} disabled={streaming || loading} maxLength={1000} onChange={event => setInput(event.target.value)}
              onKeyDown={event => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }} />
              <div><span>{input.length}/1000</span>{streaming ? <button type="button" className="stop" onClick={stop}><Square size={15} />停止生成</button>
                : <button type="submit" disabled={!input.trim() || loading}><Send size={16} />发送</button>}</div>
            </form><p className="agent-chat-note">回答由 AI 生成，请结合事实核对。对话仅对当前账号可见。</p>
          </div>
        </section>
      </div>
    </main>
  );
}
