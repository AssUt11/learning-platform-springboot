export function clearAuthStorage() {
  localStorage.removeItem("learning_user");
  localStorage.removeItem("learning_token");
  localStorage.removeItem("learning_preferences");
  localStorage.removeItem("learning_needs_interest_setup");
}

export function isAuthExpiredError(error) {
  const message = String(error?.message || "");
  return error?.status === 401
    || message.includes("Please log in first")
    || message.includes("sign in again");
}

export function notifyUnauthorized(error) {
  if (typeof window === "undefined" || error?.unauthorizedNotified) return;
  if (error && typeof error === "object") error.unauthorizedNotified = true;
  window.dispatchEvent(new CustomEvent("learning:unauthorized", {
    detail: {
      status: error?.status,
      message: error?.message
    }
  }));
}

export function handleActionError(error, fallbackMessage) {
  if (isAuthExpiredError(error)) {
    notifyUnauthorized(error);
    return true;
  }
  alert(`${fallbackMessage}：${error?.message || "请稍后重试"}`);
  return false;
}
