import request from "../utils/request";

const assetPath = (lessonId, assetId) => `/teacher/lessons/${lessonId}/assets/${assetId}/courseware`;
export const getCourseware = (lessonId, assetId, options = {}) => request(assetPath(lessonId, assetId), options);
export const extractCourseware = (lessonId, assetId) => request(`${assetPath(lessonId, assetId)}/extract`, { method: "POST" });
export const confirmCourseware = (lessonId, assetId, documentId, versionHash) => request(
  `${assetPath(lessonId, assetId)}/${encodeURIComponent(documentId)}/confirm`,
  { method: "POST", body: JSON.stringify({ versionHash }) }
);

/** Only the application's authorized excerpt endpoint may be opened as a source. */
export function readCoursewareSource(source, courseId, lessonId, options = {}) {
  const chunkId = Number(source.chunkId || source.sourceId);
  if (!Number.isSafeInteger(chunkId) || chunkId <= 0) return Promise.reject(new Error("这份引用缺少有效的资料片段编号。"));
  const query = new URLSearchParams({ courseId: String(courseId), lessonId: String(lessonId) });
  return request(`/learning/courseware/chunks/${chunkId}?${query}`, options);
}
