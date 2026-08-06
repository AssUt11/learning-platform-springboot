import request from "../utils/request";

export function getCourses(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") query.set(key, String(value));
  });
  return request(`/courses?${query.toString()}`);
}

export const getCourseDetail = (courseId) => request(`/courses/${courseId}`);
export const getCourseOutline = (courseId) => request(`/courses/${courseId}/outline`);
export const toggleCourseFavorite = (courseId) => request(`/resources/${courseId}/favorite`, { method: "POST" });

export const createCourseChapter = (courseId, chapter) => request(`/teacher/courses/${courseId}/chapters`, {
  method: "POST",
  body: JSON.stringify(chapter)
});
export const updateCourseChapter = (chapterId, chapter) => request(`/teacher/chapters/${chapterId}`, {
  method: "PUT",
  body: JSON.stringify(chapter)
});
export const deleteCourseChapter = (chapterId) => request(`/teacher/chapters/${chapterId}`, { method: "DELETE" });
export const reorderCourseChapters = (courseId, ids) => request(`/teacher/courses/${courseId}/chapters/reorder`, {
  method: "PUT",
  body: JSON.stringify({ ids })
});
export const createCourseLesson = (chapterId, lesson) => request(`/teacher/chapters/${chapterId}/lessons`, {
  method: "POST",
  body: JSON.stringify(lesson)
});
export const updateCourseLesson = (lessonId, lesson) => request(`/teacher/lessons/${lessonId}`, {
  method: "PUT",
  body: JSON.stringify(lesson)
});
export const deleteCourseLesson = (lessonId) => request(`/teacher/lessons/${lessonId}`, { method: "DELETE" });
export const reorderCourseLessons = (chapterId, ids) => request(`/teacher/chapters/${chapterId}/lessons/reorder`, {
  method: "PUT",
  body: JSON.stringify({ ids })
});
export const createLessonAsset = (lessonId, formData) => request(`/teacher/lessons/${lessonId}/assets`, {
  method: "POST",
  body: formData
});
export const updateLessonAsset = (lessonId, assetId, asset) => request(`/teacher/lessons/${lessonId}/assets/${assetId}`, {
  method: "PUT",
  body: JSON.stringify(asset)
});
export const deleteLessonAsset = (lessonId, assetId) => request(`/teacher/lessons/${lessonId}/assets/${assetId}`, {
  method: "DELETE"
});

export const getCourseCommunity = (courseId) => request(`/courses/${courseId}/community`);

export const addCourseQuestion = (courseId, content) => request(`/courses/${courseId}/questions`, {
  method: "POST",
  body: JSON.stringify({ content })
});

export const addCourseNote = (courseId, note) => request(`/courses/${courseId}/notes`, {
  method: "POST",
  body: JSON.stringify(note)
});

export const deleteCourseNote = (courseId, noteId) => request(`/courses/${courseId}/notes/${noteId}`, {
  method: "DELETE"
});

export const submitCourseReview = (courseId, review) => request(`/courses/${courseId}/reviews`, {
  method: "POST",
  body: JSON.stringify(review)
});
