import request from "../utils/request";

export const getCategories = () => request("/categories");
export const getDirections = (categoryId) => request(`/categories/${categoryId}/directions`);
export const getKnowledgeCategoryTree = () => request("/knowledge/categories/tree");
