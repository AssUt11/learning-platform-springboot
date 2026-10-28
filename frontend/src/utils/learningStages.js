export const LEARNING_STAGES = [
  { code: "foundation", label: "基础课程", detail: "建立概念，打好学习基础", index: "01" },
  { code: "intermediate", label: "中期课程", detail: "连接知识，在实践中巩固", index: "02" },
  { code: "advanced", label: "进阶课程", detail: "深入原理，完成综合应用", index: "03" }
];
export const stageLabel = (code) => LEARNING_STAGES.find((stage) => stage.code === code)?.label || "基础课程";
export function inferLearningStage(title = "", level = "") {
  const text = String(title).toLowerCase();
  if (/(高级|进阶|性能|调优|并发|多线程|jvm|微服务|分布式|kubernetes|深度学习|transformer)/.test(text)) return "advanced";
  if (/(实战|项目|框架|spring|jdbc|集合|io流|io 流|异常|flask|django|事务|索引|部署|spark)/.test(text)) return "intermediate";
  return level === "项目实战" ? "advanced" : ["进阶", "中级"].includes(level) ? "intermediate" : "foundation";
}
