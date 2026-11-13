import assert from "node:assert/strict";
import { createServer } from "node:http";
import test from "node:test";
import ZhiduLocalModelProvider from "./provider.mjs";

async function fakeApi(context, handler) {
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  context.after(() => { server.closeAllConnections(); return new Promise(resolve => server.close(resolve)); });
  return `http://127.0.0.1:${server.address().port}/v1`;
}

const prompt = JSON.stringify([{ role: "system", content: "Synthetic fixture only." }, { role: "user", content: "测试" }]);

test("OpenAI-compatible transport sends deterministic scoped messages and records usage", async context => {
  let recorded;
  const baseUrl = await fakeApi(context, async (request, response) => {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    recorded = { path: request.url, token: request.headers.authorization, body: JSON.parse(Buffer.concat(chunks)) };
    response.setHeader("Content-Type", "application/json");
    response.end(JSON.stringify({ choices: [{ message: { content: "测试完成" } }], usage: { total_tokens: 12, prompt_tokens: 8, completion_tokens: 4 } }));
  });
  const provider = new ZhiduLocalModelProvider({ config: { baseUrl, model: "synthetic", apiKey: "test-key-not-a-real-secret" } });
  const result = await provider.callApi(prompt);
  assert.equal(result.output, "测试完成");
  assert.equal(recorded.path, "/v1/chat/completions");
  assert.equal(recorded.body.temperature, 0);
  assert.equal(recorded.body.stream, false);
  assert.equal(recorded.body.max_tokens, 600);
  assert.equal(recorded.token, "Bearer test-key-not-a-real-secret");
  assert.deepEqual(recorded.body.messages, JSON.parse(prompt));
  assert.deepEqual(result.tokenUsage, { total: 12, prompt: 8, completion: 4 });
  assert.ok(result.metadata.durationMs >= 0);
});

test("HTTP errors do not echo response bodies or credentials into reports", async context => {
  const baseUrl = await fakeApi(context, (_request, response) => { response.writeHead(503); response.end("private diagnostic text"); });
  const result = await new ZhiduLocalModelProvider({ config: { baseUrl } }).callApi(prompt);
  assert.equal(result.error, "Model API returned HTTP 503.");
  assert.equal(result.output, undefined);
});

test("invalid model payloads and invalid prompt formats are failures", async context => {
  const baseUrl = await fakeApi(context, (_request, response) => { response.setHeader("Content-Type", "application/json"); response.end("{}"); });
  const provider = new ZhiduLocalModelProvider({ config: { baseUrl } });
  assert.match((await provider.callApi(prompt)).error, /text completion/);
  assert.match((await provider.callApi("not a message array")).error, /JSON chat-message array/);
});

test("the default provider rejects credential-bearing URLs and remote hosts before requests", () => {
  assert.throws(() => new ZhiduLocalModelProvider({ config: { baseUrl: "http://name:password@127.0.0.1:8000/v1" } }), /URL credentials/);
  assert.throws(() => new ZhiduLocalModelProvider({ config: { baseUrl: "file:///etc/passwd" } }), /HTTP/);
  if (process.env.ZHIDU_EVAL_ALLOW_REMOTE !== "1") {
    assert.throws(() => new ZhiduLocalModelProvider({ config: { baseUrl: "https://example.com/v1" } }), /Remote evaluation/);
  }
});
