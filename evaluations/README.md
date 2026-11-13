# 知度课堂模型评测

这套评测包含 **40 条中文固定样例**：20 条基础知识、6 条来源引用、2 条课程/课时定位、6 条资料不足或越权请求、6 条提示注入。样例与课程片段均为仓库内公开、合成的测试资料，不读取业务数据库，不需要学生 Token，不建立账号。

它验证“给定课件后的独立模型回答契约”。**它不等同于课件解析正确、检索排序正确、引用接口授权正确或整个课堂功能已验收**；这些由后端单测和浏览器回归分别验证。自然语言关键词只作初步门槛，最终教学正确性仍需要教师抽查。

## 环境

使用 Node.js **22.22.0+**，推荐项目现有 Node 24。默认 `node` 若是旧版 Node 18，请切换后再运行。本目录的原生运行器与测试使用 Node 内置模块，**无需安装 npm 依赖**。

从项目根目录运行：

```powershell
node --test evaluations/contract.test.mjs evaluations/provider.test.mjs
node evaluations/run.mjs --mode=offline
```

离线模式只验证评测契约、人工参考答案和断言，不调用模型，报告明确标记 `offline-contract`、`modelInvoked=false`。不能将其报告为“模型 40 道题通过”。

## 真实本地模型

先按项目的 `scripts/start-llamafactory.ps1` 启动模型。运行器直接调用 OpenAI-compatible `/v1/chat/completions`，不会经过课堂后台，也不会保存课堂对话。

```powershell
$env:LLAMAFACTORY_BASE_URL = 'http://127.0.0.1:8000/v1'
$env:LLAMAFACTORY_MODEL = 'zhidu-qwen3-4b-lora'
node evaluations/run.mjs --mode=live --limit=3
node evaluations/run.mjs --mode=live
```

可用 `--case=prob-mean` 精确选择一题；`--output=路径.json` 指定报告位置。默认逐题串行，适合本机单并发模型。每题最多输出 600 tokens，超时默认 120 秒，可用 `ZHIDU_EVAL_TIMEOUT_MS` 设置（上限 180 秒）。首次先跑 3 题，检查服务、输出格式与耗时，再跑全量。

可选密钥只通过本地 `LLAMAFACTORY_API_KEY` 环境变量提供，不写入配置和报告。默认仅允许回环地址；明确需要远程测试时才设置 `ZHIDU_EVAL_ALLOW_REMOTE=1`。固定样例不包含学生资料。

结果在 `evaluations/results/`，已忽略 Git。报告保留数据集版本、Prompt 版本、逐题断言、耗时、服务端 Token 使用量，以及 live 模式的合成测试回答。失败退出码为 1；网络不可达、空回答和格式错误均记为失败，不算跳过或成功。

## 确定性验收项

- 一个有效 JSON 对象，不接受思考标签、Markdown 代码围栏或空回答。
- `courseId`、`lessonId` 与当前样例一致，不能被问题里的其他 ID 改写。
- `sourceIds` 只能引用本次提供且属于当前课时的片段；不得引用未知来源。
- 资料不足时明确说明，设置 `insufficientEvidence=true` 且不伪造引用。
- 检查关键知识点、注入标记、虚假成绩修改声明和伪造页码。

PDF 页、PPT 幻灯片、Word 正文段落是不同定位类型。未提取真实页码的 Word 不能编造“第几页”。这里的 `sourceIds` 是固定评测标签，不是生产数据库 ID。

模型没有通过时，应保留失败报告，区分输出格式、事实、引用、范围和权限错误，再调整 Prompt/检索/LoRA。不要直接放宽断言来把结果变成绿色。

首次执行记录见[2026-11-08 基线](BASELINE-2026-11-08.md)：离线契约 40/40 通过，真实本地模型 smoke 0/3 通过（JSON 后追加句号），两者分开记录。

## 可选 Promptfoo

已核验官方发布 **Promptfoo 0.125.1**（2026-10-25）及其 Node `>=22.22.0` 要求。本目录提供同一组样例、同一套断言的 [ESM custom provider](https://github.com/promptfoo/promptfoo/blob/main/site/docs/providers/custom-api.md) 和配置；无需另写另一套模型接入。它使用本项目可审查的本地 JavaScript 文件，不调用 LLM 裁判。

若需要 Promptfoo 对比表/报告，在 `evaluations` 目录用固定版本运行（这一步会从 npm 获取 Promptfoo）：

```powershell
cd evaluations
$env:PROMPTFOO_DISABLE_TELEMETRY = '1'
npm exec --yes --package=promptfoo@0.125.1 -- promptfoo eval --config promptfooconfig.mjs --no-cache --max-concurrency 1 --output results/promptfoo.json
```

Promptfoo 是可选工具；本项目没有将它的整套依赖加入前端。原生 runner 是已经离线可运行的交付路径，使用同一 provider/fixtures/grader。真正使用 Promptfoo CLI 时也需检查命令输出，不把配置文件存在当作其运行已通过。

官方核验来源：[发布版本](https://github.com/promptfoo/promptfoo/releases/tag/0.125.1)、[Node 要求](https://github.com/promptfoo/promptfoo/blob/0.125.1/package.json)、[JavaScript 断言](https://github.com/promptfoo/promptfoo/blob/main/site/docs/configuration/expected-outputs/javascript.md)。
