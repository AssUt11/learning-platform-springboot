import request from "../utils/request";
import { streamAiResponse } from "./aiTutor";

export const getAgentChatStatus = () => request("/agent-chat/status");
export const getAgentConversations = () => request("/agent-chat/conversations");
export const getAgentConversation = id => request(`/agent-chat/conversations/${id}`);
export const streamAgentChat = (payload, options) => streamAiResponse("/agent-chat/stream", payload, options);
export const cancelAgentChat = id => request(`/agent-chat/requests/${id}/cancel`, { method: "POST" });
