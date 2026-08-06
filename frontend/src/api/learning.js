import request from "../utils/request";

export const getLearningSummary = () => request("/learning/summary");
export const saveLearningProgress = (payload) => request("/learning/records", {
  method: "POST",
  body: JSON.stringify(payload)
});

export const getLessonPlayback = (courseId, lessonId, options = {}) => request(
  `/learning/lessons/${lessonId}/playback?courseId=${courseId}`,
  options
);

export const saveLessonProgress = (courseId, lessonId, payload, options = {}) => request(
  `/learning/lessons/${lessonId}/progress`,
  {
    method: "PUT",
    body: JSON.stringify({ courseId, ...payload }),
    ...options
  }
);

export const getCourseLearningProgress = (courseId, options = {}) => request(
  `/learning/courses/${courseId}/progress`,
  options
);

export const getResumeLearning = (options = {}) => request("/learning/resume", options);
