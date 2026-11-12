import { cases } from "./fixtures.mjs";
import { buildMessages } from "./contract.mjs";

export default {
  description: "知度课堂：中文课件答疑、来源与课时范围的独立模型回归",
  prompts: ["{{messages}}"],
  providers: [{ id: "file://provider.mjs" }],
  evaluateOptions: { maxConcurrency: 1 },
  defaultTest: { assert: [{ type: "javascript", value: "file://assertions.mjs" }] },
  tests: cases.map(testCase => ({ description: testCase.id, vars: { caseId: testCase.id, messages: JSON.stringify(buildMessages(testCase)) }, metadata: { category: testCase.category } }))
};
