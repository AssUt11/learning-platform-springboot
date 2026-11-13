import assert from "node:assert/strict";
import test from "node:test";
import { cases, caseById, sources } from "./fixtures.mjs";
import { buildMessages, gradeOutput, referenceOutput, selectedSources } from "./contract.mjs";
import promptfooConfig from "./promptfooconfig.mjs";

test("the forty-case manifest is scoped, unique and shared with Promptfoo", () => {
  assert.equal(cases.length, 40);
  assert.equal(new Set(cases.map(item => item.id)).size, 40);
  assert.equal(new Set(sources.map(item => item.id)).size, sources.length);
  assert.equal(promptfooConfig.tests.length, cases.length);
  assert.deepEqual(new Set(cases.map(item => item.category)), new Set(["knowledge", "citation", "routing", "scope", "injection"]));
  for (const item of cases) {
    assert.ok(selectedSources(item).every(source => source.courseId === item.courseId && source.lessonId === item.lessonId));
    assert.ok(item.required.every(group => Array.isArray(group) && group.length > 0));
  }
});

test("curated reference answers exercise every deterministic contract without a model", () => {
  for (const item of cases) {
    const result = gradeOutput(referenceOutput(item), item);
    assert.equal(result.pass, true, `${item.id}: ${result.reason}`);
  }
});

test("unknown sources, duplicate references and the wrong lesson fail", () => {
  const item = caseById.get("prob-mean"), valid = JSON.parse(referenceOutput(item));
  for (const mutation of [{ sourceIds: ["not-provided"] }, { sourceIds: ["prob-p1", "prob-p1"] }, { courseId: 999 }, { lessonId: 999 }, { sourceIds: [] }]) {
    assert.equal(gradeOutput(JSON.stringify({ ...valid, ...mutation }), item).pass, false);
  }
});

test("a missing-evidence response cannot cite a source or claim sufficient evidence", () => {
  const item = caseById.get("scope-stock"), valid = JSON.parse(referenceOutput(item));
  assert.equal(gradeOutput(JSON.stringify({ ...valid, sourceIds: ["prob-p1"] }), item).pass, false);
  assert.equal(gradeOutput(JSON.stringify({ ...valid, insufficientEvidence: false }), item).pass, false);
});

test("malformed responses and instruction-following markers cannot score as success", () => {
  const item = caseById.get("inject-user"), valid = JSON.parse(referenceOutput(item));
  assert.equal(gradeOutput("```json\n" + referenceOutput(item) + "\n```", item).pass, false);
  assert.equal(gradeOutput("[]", item).pass, false);
  assert.equal(gradeOutput(JSON.stringify({ ...valid, answer: "CANARY_INJECTION_SUCCEEDED" }), item).pass, false);
  assert.equal(gradeOutput(JSON.stringify({ ...valid, answer: "<think>不可以</think>" }), item).pass, false);
});

test("document text stays material; Word sections never acquire a guessed page", () => {
  const injected = buildMessages(caseById.get("inject-source"));
  assert.equal(injected.length, 2);
  assert.equal(injected[0].role, "system");
  assert.match(JSON.parse(injected[1].content).courseMaterial[0].text, /CANARY_INJECTION_SUCCEEDED/);
  const item = caseById.get("cite-no-page-guess"), valid = JSON.parse(referenceOutput(item));
  assert.equal(gradeOutput(JSON.stringify({ ...valid, answer: "没有可靠页码，不过第1页讲了EXPLAIN。" }), item).pass, false);
});
