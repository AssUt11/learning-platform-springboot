const AGENT_IDS = ["guide", "analysis", "path", "recommend"];
const STORAGE_PREFIX = "learning_agent_analysis_v1:";
let activeStore = null;

function currentUserId() {
  try {
    return String(JSON.parse(localStorage.getItem("learning_user"))?.id ?? "");
  } catch {
    return "";
  }
}

function outputFrom(data) {
  return {
    text: data?.result || (data?.downloadUrl ? "学习分析报告已生成" : "分析已完成"),
    error: false,
    model: data?.model || "",
    tools: Array.isArray(data?.toolsUsed) ? data.toolsUsed : [],
    sources: Array.isArray(data?.sources) ? data.sources : [],
    graphUsed: Boolean(data?.graphUsed),
    learningPath: data?.learningPath || null
  };
}

function restoreOutputs(storageKey) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey));
    if (saved?.version !== 1) return {};
    return Object.fromEntries(AGENT_IDS.flatMap((id) => {
      const output = saved.outputs?.[id];
      if (typeof output?.text !== "string") return [];
      return [[id, {
        ...outputFrom({ result: output.text, model: output.model, toolsUsed: output.tools,
          sources: output.sources, graphUsed: output.graphUsed, learningPath: output.learningPath }),
        error: Boolean(output.error)
      }]];
    }));
  } catch {
    return {};
  }
}

function removeSavedOutputs(userId) {
  try {
    sessionStorage.removeItem(STORAGE_PREFIX + userId);
  } catch {
    // Storage may be unavailable; in-memory navigation still works.
  }
}

function createStore(userId, token) {
  const storageKey = STORAGE_PREFIX + userId;
  const listeners = new Set();
  let retired = false;
  let snapshot = { outputs: restoreOutputs(storageKey), busyAgents: {}, reportsVersion: 0 };
  const isCurrentSession = () => !retired && currentUserId() === userId
    && localStorage.getItem("learning_token") === token;
  const publish = (next) => {
    snapshot = next;
    try {
      // Only settled results survive reload. A lost HTTP connection must not restore a permanent spinner.
      sessionStorage.setItem(storageKey, JSON.stringify({ version: 1, outputs: snapshot.outputs }));
    } catch {
      // A full or disabled storage must not turn a successful analysis into a failure.
    }
    listeners.forEach((listener) => listener());
  };

  return {
    userId,
    token,
    getSnapshot: () => snapshot,
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    // The request belongs to this login session, not the mounted analysis page.
    async run(id, generate) {
      if (!AGENT_IDS.includes(id) || snapshot.busyAgents[id] || !isCurrentSession()) return;
      publish({ ...snapshot, outputs: { ...snapshot.outputs, [id]: null },
        busyAgents: { ...snapshot.busyAgents, [id]: true } });
      try {
        const data = await generate();
        // Logout or a different login must never receive this request's late response.
        if (!isCurrentSession()) return;
        publish({
          ...snapshot,
          outputs: { ...snapshot.outputs, [id]: outputFrom(data) },
          busyAgents: { ...snapshot.busyAgents, [id]: false },
          reportsVersion: snapshot.reportsVersion + (id === "analysis" ? 1 : 0)
        });
      } catch (error) {
        if (!isCurrentSession()) return;
        publish({ ...snapshot,
          outputs: { ...snapshot.outputs, [id]: { text: `分析失败：${error?.message || "请稍后重试"}`, error: true } },
          busyAgents: { ...snapshot.busyAgents, [id]: false } });
      }
    },
    retire() {
      retired = true;
      snapshot = { outputs: {}, busyAgents: {}, reportsVersion: 0 };
      removeSavedOutputs(userId);
      listeners.forEach((listener) => listener());
      listeners.clear();
    }
  };
}

export function getAgentAnalysisStore(userId, token) {
  const ownerId = String(userId ?? "");
  if (!activeStore || activeStore.userId !== ownerId || activeStore.token !== token) {
    activeStore?.retire();
    activeStore = createStore(ownerId, token);
  }
  return activeStore;
}

export function clearAgentAnalysisSession() {
  removeSavedOutputs(currentUserId());
  activeStore?.retire();
  activeStore = null;
}
