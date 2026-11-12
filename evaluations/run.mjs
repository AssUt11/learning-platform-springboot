import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { cases, DATASET_VERSION } from "./fixtures.mjs";
import { buildMessages, EVALUATION_PROMPT_VERSION, gradeOutput, referenceOutput } from "./contract.mjs";
import ZhiduLocalModelProvider from "./provider.mjs";

const args = new Map(process.argv.slice(2).map(item => {
  const match = /^--([a-z-]+)=(.+)$/.exec(item);
  if (!match || !["mode", "limit", "case", "output"].includes(match[1])) throw new Error("Use --mode=offline|live [--limit=N] [--case=ID] [--output=PATH].");
  return [match[1], match[2]];
}));
const mode = args.get("mode") || "offline";
if (!["offline", "live"].includes(mode)) throw new Error("mode must be offline or live.");
const limit = args.has("limit") ? Number(args.get("limit")) : cases.length;
if (!Number.isInteger(limit) || limit < 1 || limit > cases.length) throw new Error(`limit must be 1–${cases.length}.`);
let selected = cases.filter(item => !args.has("case") || item.id === args.get("case")).slice(0, limit);
if (!selected.length) throw new Error("No matching evaluation case.");
const provider = mode === "live" ? new ZhiduLocalModelProvider() : null;
const results = [];
const startedAt = new Date().toISOString();
const started = performance.now();
for (const testCase of selected) {
  const response = provider ? await provider.callApi(JSON.stringify(buildMessages(testCase))) : { output: referenceOutput(testCase) };
  const grade = response.error ? { pass: false, score: 0, reason: response.error } : gradeOutput(response.output, testCase);
  results.push({ id: testCase.id, category: testCase.category, ...grade, durationMs: response.metadata?.durationMs,
    tokenUsage: response.tokenUsage, ...(mode === "live" ? { output: response.output || null } : {}) });
  console.log(`${grade.pass ? "PASS" : "FAIL"} ${testCase.id}${grade.pass ? "" : `: ${grade.reason}`}`);
}
const passed = results.filter(item => item.pass).length;
const durationMs = Math.round(performance.now() - started);
const report = { startedAt, createdAt: new Date().toISOString(), durationMs, requestsPerMinute: durationMs ? Number((results.length * 60000 / durationMs).toFixed(2)) : null,
  datasetVersion: DATASET_VERSION, promptVersion: EVALUATION_PROMPT_VERSION,
  mode: mode === "live" ? "live-model" : "offline-contract", modelInvoked: mode === "live", model: provider?.config.model,
  scope: "Synthetic standalone model contract; not production retrieval or end-to-end classroom acceptance.",
  total: results.length, passed, failed: results.length - passed, results };
const defaultOutput = fileURLToPath(new URL(`./results/${mode}-${new Date().toISOString().replace(/[:.]/g, "-")}.json`, import.meta.url));
const outputPath = resolve(args.get("output") || defaultOutput);
await mkdir(resolve(outputPath, ".."), { recursive: true });
await writeFile(outputPath, JSON.stringify(report, null, 2) + "\n");
console.log(`${passed}/${results.length} passed (${report.mode}); report: ${outputPath}`);
if (passed !== results.length) process.exitCode = 1;
