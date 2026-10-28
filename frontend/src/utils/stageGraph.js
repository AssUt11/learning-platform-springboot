import { LEARNING_STAGES, inferLearningStage } from "./learningStages";

function find(path, tree) {
  let items = tree, current;
  for (const label of path) { current = items.find((item) => item.label === label); if (!current) return null; items = current.children || []; }
  return current;
}
export function knowledgeLeavesForPath(tree, path) {
  const root = find(path, tree);
  const result = [];
  const visit = (item, parent) => {
    const fullPath = [...parent, item.label];
    if (item.children?.length) item.children.forEach((child) => visit(child, fullPath));
    else if (fullPath.length >= 3) result.push({ id: fullPath.join("/"), label: item.label, path: fullPath, type: "knowledge", learningStage: item.learningStage || inferLearningStage(item.label) });
  };
  if (root) visit(root, path.slice(0, -1));
  return result;
}
export function buildStageGraph(graph, path, tree, filter = "") {
  const nodes = graph.nodes.map((node) => ({ ...node, learningStage: node.learningStage || find(node.path || [], tree)?.learningStage || inferLearningStage(node.label) }));
  if (path.length !== 2) {
    const filtered = nodes.filter((node) => !filter || !["knowledge", "point"].includes(node.type) || node.learningStage === filter);
    const ids = new Set(filtered.map((node) => node.id));
    return { ...graph, nodes: filtered, links: graph.links.filter((link) => ids.has(link.source) && ids.has(link.target)) };
  }
  const root = nodes.find((node) => node.type === "root") || nodes[0];
  if (!root) return graph;
  const members = nodes.filter((node) => node.id !== root.id && ["knowledge", "point"].includes(node.type));
  // The authoritative taxonomy may contain newly published lessons absent from an older graph snapshot.
  for (const topic of knowledgeLeavesForPath(tree, path)) {
    if (!members.some((node) => node.label === topic.label)) members.push(topic);
  }
  const result = [root], links = [];
  for (const stage of LEARNING_STAGES.filter((item) => !filter || item.code === filter)) {
    const topics = members.filter((node) => node.learningStage === stage.code);
    const group = { id: `${root.id}:stage:${stage.code}`, label: stage.label, type: "stage", path,
      learningStage: stage.code, description: stage.detail, memberNodes: topics, parentId: root.id };
    result.push(group); links.push({ source: root.id, target: group.id });
    for (const topic of topics) {
      result.push({ ...topic, parentId: group.id }); links.push({ source: group.id, target: topic.id });
    }
  }
  return { ...graph, nodes: result, links };
}
