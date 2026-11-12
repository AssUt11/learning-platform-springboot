function settings(overrides = {}) {
  const baseUrl = String(overrides.baseUrl || process.env.LLAMAFACTORY_BASE_URL || "http://127.0.0.1:8000/v1").replace(/\/$/, "");
  const endpoint = new URL(baseUrl);
  if (!["http:", "https:"].includes(endpoint.protocol) || endpoint.username || endpoint.password || endpoint.search || endpoint.hash) {
    throw new Error("Use an HTTP(S) API base URL without URL credentials or query parameters.");
  }
  if (!["localhost", "127.0.0.1", "[::1]"].includes(endpoint.hostname) && process.env.ZHIDU_EVAL_ALLOW_REMOTE !== "1") {
    throw new Error("Remote evaluation requires ZHIDU_EVAL_ALLOW_REMOTE=1.");
  }
  return { baseUrl, model: overrides.model || process.env.LLAMAFACTORY_MODEL || "zhidu-qwen3-4b-lora",
    apiKey: overrides.apiKey ?? process.env.LLAMAFACTORY_API_KEY ?? "",
    timeoutMs: Math.max(1000, Math.min(Number(overrides.timeoutMs || process.env.ZHIDU_EVAL_TIMEOUT_MS || 120000), 180000)) };
}

/** Promptfoo's documented ESM provider interface, also used by the native runner. */
export default class ZhiduLocalModelProvider {
  constructor(options = {}) { this.config = settings(options.config); }
  id() { return "zhidu:llamafactory-courseware"; }
  async callApi(prompt) {
    let messages;
    try {
      messages = JSON.parse(prompt);
      if (!Array.isArray(messages) || messages.some(item => !["system", "user", "assistant"].includes(item.role) || typeof item.content !== "string")) throw new Error();
    } catch { return { error: "Evaluation prompt must be a JSON chat-message array." }; }
    const started = performance.now();
    try {
      const response = await fetch(`${this.config.baseUrl}/chat/completions`, {
        method: "POST", redirect: "error", signal: AbortSignal.timeout(this.config.timeoutMs),
        headers: { "Content-Type": "application/json", ...(this.config.apiKey ? { Authorization: `Bearer ${this.config.apiKey}` } : {}) },
        body: JSON.stringify({ model: this.config.model, messages, stream: false, temperature: 0, max_tokens: 600 })
      });
      if (!response.ok) return { error: `Model API returned HTTP ${response.status}.` };
      const payload = await response.json();
      const output = payload.choices?.[0]?.message?.content;
      if (typeof output !== "string" || !output.trim()) return { error: "Model API did not return a text completion." };
      return { output, tokenUsage: payload.usage ? { total: payload.usage.total_tokens, prompt: payload.usage.prompt_tokens, completion: payload.usage.completion_tokens } : undefined,
        metadata: { durationMs: Math.round(performance.now() - started), model: this.config.model } };
    } catch (error) {
      return { error: error?.name === "TimeoutError" ? "Model request timed out." : "Model request failed; verify that the configured local API is running." };
    }
  }
}
