import request from "../utils/request";

export function getKnowledgeGraph(subject, path = []) {
  const params = new URLSearchParams({ subject });
  if (path.length) params.set("path", path.join(" > "));
  return request(`/knowledge-graph?${params.toString()}`);
}
