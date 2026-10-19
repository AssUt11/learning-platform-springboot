import { useMemo, useSyncExternalStore } from "react";
import { getAgentAnalysisStore } from "./agentAnalysisStore";

export default function useAgentAnalysis(user) {
  const token = localStorage.getItem("learning_token");
  const store = useMemo(() => getAgentAnalysisStore(user?.id, token), [user?.id, token]);
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot);
  return { ...snapshot, runAnalysis: store.run };
}
