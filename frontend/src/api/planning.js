import request from "../utils/request";
export const getLearningProfile = () => request("/learning/profile");
export const saveLearningProfile = (answers) => request("/learning/profile", { method: "PUT", body: JSON.stringify(answers) });
export const deferLearningPlan = () => request("/learning/profile/defer", { method: "POST" });
export const generateLearningPlan = (version) => request("/learning/planning/preview", { method: "POST", body: JSON.stringify({ version }), timeout: 180000 });
export const adoptLearningPlan = (previewId) => request("/learning/planning/adopt", { method: "POST", body: JSON.stringify({ previewId }), timeout: 30000 });
export const getCurrentLearningPlan = () => request("/learning/planning/current");
