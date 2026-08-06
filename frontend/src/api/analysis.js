import request from "../utils/request";

export const getTeacherAnalytics = () => request("/teacher/analytics");
export const getAgentReports = () => request("/agents/reports");
