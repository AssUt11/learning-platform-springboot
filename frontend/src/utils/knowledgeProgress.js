const normalize = (value) => String(value || "").replace(/\s+/g, "").toLowerCase();
const recordKey = (subject, title) => JSON.stringify([normalize(subject), normalize(title)]);
const pathKey = (path) => JSON.stringify(path.map(normalize));

export function knowledgeRecordForNode(node, graphPath = []) {
  const path = Array.isArray(node?.path) && node.path.length
    ? node.path
    : node?.type === "root" ? graphPath : [...graphPath, node?.label];
  const detail = node?.type === "point" && path.length >= 3 && path.at(-1) !== node.label;
  return {
    path,
    subject: path[1] || path[0] || "",
    knowledgeNode: detail ? [...path.slice(2), node.label].join(" > ") : node?.label || "",
    isKnowledgePoint: path.length >= 3
  };
}

function state(progress, completedCount = progress >= 100 ? 1 : 0, total = 1) {
  return { progress, completedCount, total,
    status: total > 0 && completedCount === total ? "completed" : progress > 0 ? "in-progress" : "not-started" };
}

export function summarizeKnowledgeProgress(items) {
  const total = items.reduce((sum, item) => sum + item.total, 0);
  if (!total) return state(0, 0, 0);
  const completed = items.reduce((sum, item) => sum + item.completedCount, 0);
  const average = items.reduce((sum, item) => sum + item.progress * item.total, 0) / total;
  return state(completed === total ? 100 : average > 0 ? Math.max(1, Math.min(99, Math.round(average))) : 0, completed, total);
}

export function createKnowledgeProgress(records = [], tree = []) {
  const saved = new Map();
  for (const record of records) {
    const key = recordKey(record.subject, record.knowledgeNode);
    const progress = Math.max(0, Math.min(100, Number(record.progress) || 0));
    saved.set(key, Math.max(saved.get(key) || 0, progress));
  }
  const branches = new Map();
  const visit = (item, parentPath) => {
    const path = [...parentPath, item.label];
    let result;
    if (item.children?.length) {
      const children = item.children.map((child) => visit(child, path));
      const total = children.reduce((sum, child) => sum + child.total, 0);
      const completed = children.reduce((sum, child) => sum + child.completedCount, 0);
      const average = children.reduce((sum, child) => sum + child.progress * child.total, 0) / total;
      result = state(completed === total ? 100 : average > 0 ? Math.max(1, Math.min(99, Math.round(average))) : 0, completed, total);
    } else {
      result = state(saved.get(recordKey(path[1] || path[0], item.label)) || 0);
    }
    branches.set(pathKey(path), result);
    return result;
  };
  tree.forEach((item) => visit(item, []));
  const read = (node, graphPath) => {
    if (node?.type === "stage") return summarizeKnowledgeProgress((node.memberNodes || []).map((member) => read(member, graphPath)));
    const record = knowledgeRecordForNode(node, graphPath);
    if (record.path.length < 3 && branches.has(pathKey(record.path))) return branches.get(pathKey(record.path));
    return state(saved.get(recordKey(record.subject, record.knowledgeNode)) || 0);
  };
  return read;
}

export const knowledgeStatusLabel = (status) => ({
  "not-started": "未开始", "in-progress": "学习中", completed: "已完成"
})[status] || "未开始";
