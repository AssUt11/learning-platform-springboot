import request from "../utils/request";

export function getKnowledgeCourseTargets({ category, direction, title }) {
  const query = new URLSearchParams({ category, direction, title });
  return request(`/knowledge/course-targets?${query}`);
}
