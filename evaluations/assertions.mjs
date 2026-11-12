import { caseById } from "./fixtures.mjs";
import { gradeOutput } from "./contract.mjs";

export default function assertCoursewareResponse(output, context) {
  const testCase = caseById.get(context.vars.caseId);
  return testCase ? gradeOutput(output, testCase) : { pass: false, score: 0, reason: "Unknown evaluation fixture." };
}
