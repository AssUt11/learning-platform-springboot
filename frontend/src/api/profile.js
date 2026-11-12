import request from "../utils/request";

export function getProfile(options = {}) {
  return request("/profile", options);
}

export function saveAvatar(file) {
  const form = new FormData();
  form.append("file", file);
  return request("/profile/avatar", { method: "PUT", body: form, timeout: 20000 });
}
