import axios from "axios";
import { isAuthExpiredError, notifyUnauthorized } from "./auth";

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || "/api",
  timeout: 10000
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem("learning_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload.code === "number") {
      if (payload.code !== 200) {
        const requestError = new Error(payload.message || "请求失败");
        requestError.status = payload.code;
        if (isAuthExpiredError(requestError) && !isAuthenticationRequest(response.config?.url)) {
          notifyUnauthorized(requestError);
        }
        return Promise.reject(requestError);
      }
      return payload.data;
    }
    return payload;
  },
  async (error) => {
    if (axios.isCancel(error)) return Promise.reject(error);
    const responseData = await readErrorPayload(error.response?.data);
    const message = responseData?.message
      || responseData?.data?.message
      || (error.code === "ECONNABORTED" ? "请求超时，请稍后重试" : "网络异常，请检查后端服务");
    const requestError = new Error(message);
    requestError.status = error.response?.status;
    if (isAuthExpiredError(requestError) && !isAuthenticationRequest(error.config?.url)) {
      notifyUnauthorized(requestError);
    }
    return Promise.reject(requestError);
  }
);

function isAuthenticationRequest(url = "") {
  return /^\/?auth\/(?:login|register)(?:\?|$)/.test(String(url));
}

async function readErrorPayload(payload) {
  if (typeof Blob !== "undefined" && payload instanceof Blob) {
    try {
      return JSON.parse(await payload.text());
    } catch {
      return null;
    }
  }
  return payload;
}

export default function request(path, options = {}) {
  const { method = "GET", body, headers, ...rest } = options;
  let data = body;
  if (typeof body === "string" && body.length) {
    try {
      data = JSON.parse(body);
    } catch {
      data = body;
    }
  }
  return client.request({ url: path, method, data, headers, ...rest });
}
