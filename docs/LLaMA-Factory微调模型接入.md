# LLaMA-Factory 微调模型接入

更新日期：2026-10-19。本文记录现有 AI 伴学的模型服务接入；LangChain4j 迁移仍是独立后续工作。

## 1. 当前接入结构

```text
学习页面 / AI 伴学
  → Spring Boot（llamafactory profile）
  → LlamaFactoryProvider
  → http://127.0.0.1:8000/v1/chat/completions
  → Qwen3-4B-Base + 知度课堂 LoRA
```

基础模型目录是用户提供的 Hugging Face 快照；微调效果来自另行加载的 LoRA，不能只加载基础模型后声称使用了微调权重。

本次选择已完成的最新训练运行：

| 项目 | 本机配置 |
|---|---|
| 基础模型 | `D:/LLaMA-Factory/cache/huggingface/models--Qwen--Qwen3-4B-Base/snapshots/906bfd4b4dc7f14ee4320094d8b41684abff8539` |
| LoRA | `D:/LLaMA-Factory/LlamaFactory/saves/Qwen3-4B-Base/lora/train_2026-09-22-21-30-44` |
| 训练记录 | `global_step=171`、`max_steps=171`、`epoch=3`；不代表质量评测通过 |
| 模板 | `default`，与该运行的 `training_args.yaml` 一致 |
| 推理环境 | `C:/Users/lenovo/miniconda3/envs/llamafactory/python.exe` |
| 推理方式 | Hugging Face 后端，bitsandbytes 4-bit，加载 LoRA；不合并或覆盖原权重 |
| 对外模型名称 | `zhidu-qwen3-4b-lora` |

模型配置见 [zhidu-local.yaml](../deploy/llamafactory/zhidu-local.yaml)。迁移到其他机器时修改配置中的绝对路径及脚本参数；模板必须与所选训练运行一致。

## 2. 本机启动

在项目根目录的 PowerShell 执行：

```powershell
# 启动模型 API（后台、仅监听本机）；模型加载需要时间。
.\scripts\start-llamafactory.ps1

# 确认加载完成后，应看到 zhidu-qwen3-4b-lora。
Invoke-RestMethod http://127.0.0.1:8000/v1/models

# 启动项目后端（前台，Ctrl+C 停止）。
.\scripts\start-backend-llamafactory.ps1
```

启动后访问 [知度课堂](http://127.0.0.1:8081)。后端仍使用原有数据库设置和账号鉴权；课程、会话、报告与路径的权限规则继续生效。

后端启动前需确认 MySQL 已运行。本机服务名为 `MySQL80`；若服务停止且普通终端无法启动，请在管理员 PowerShell 执行 `Start-Service MySQL80`，再运行后端脚本。

模型启动脚本会检查端口，不覆盖已有服务；日志与启动 PID 保存在 `.runtime/llamafactory/`，该目录已忽略提交。脚本只在子进程环境中设置本机监听、离线加载及模型名称，不安装依赖、不重新训练，也不下载权重。

如模型端口已被使用，先核对现有进程是否为目标模型；不要重复启动。使用 `-Port`、`-Python`、`-FactoryRoot`、`-Config` 参数可指定其他运行位置；端口改变后同步修改后端 API 地址。

使用 IDEA 时，在运行配置下拉列表中选择仓库提供的 `LearningPlatform-LLaMA-Factory`，它显式启用 `llamafactory` profile 和 AI。先停止占用 8081 的旧后端，再启动该配置。模型 API 仍需按上面的脚本单独启动；Spring Boot 不会自动加载 Python 模型进程。

已有的普通 `LearningPlatformApplication` 配置不会自动切换：未指定 profile 或 `AI_PROVIDER` 时，项目默认仍选择 Ollama。也可在原运行配置的程序参数中添加 `--spring.profiles.active=llamafactory`，并确保没有显式设置 `AI_TUTOR_ENABLED=false`。

## 3. 后端配置与回退

`application-llamafactory.yml` 激活后会选择 LLaMA-Factory 并默认启用 AI。也可以不使用 profile，直接设置以下进程环境变量再按原方式启动后端：

```powershell
$env:AI_TUTOR_ENABLED = 'true'
$env:AI_PROVIDER = 'llamafactory'
$env:LLAMAFACTORY_BASE_URL = 'http://127.0.0.1:8000/v1'
$env:LLAMAFACTORY_MODEL = 'zhidu-qwen3-4b-lora'
.\mvnw.cmd -f backend/pom.xml spring-boot:run
```

| 配置 | 默认值 | 含义 |
|---|---|---|
| `AI_PROVIDER` | `ollama` | 未启用专用 profile 时选择提供方 |
| `AI_TUTOR_ENABLED` | 普通启动为 false，专用 profile 为 true | 显式设置 false 仍会禁用 AI |
| `LLAMAFACTORY_BASE_URL` | `http://127.0.0.1:8000/v1` | 必须包含 API 前缀 `/v1` |
| `LLAMAFACTORY_MODEL` | `zhidu-qwen3-4b-lora` | 与服务的 `/v1/models` 一致 |
| `LLAMAFACTORY_API_KEY` | 空 | 若服务配置 `API_KEY`，后端填写相同密钥；不提交到仓库 |
| `LLAMAFACTORY_CONNECT_TIMEOUT_MS` | 3000 | 连接及健康探测预算 |
| `LLAMAFACTORY_RESPONSE_TIMEOUT_MS` | 120000 | 单次请求的排队与响应总预算 |
| `LLAMAFACTORY_MAX_TOKENS` | 600 | 单次输出上限 |
| `LLAMAFACTORY_CONTEXT_WINDOW` | 4096 | 应用提示词预算配置，需匹配部署容量；字符预算仅为近似 |
| `LLAMAFACTORY_TOOL_CALLING_ENABLED` | false | 四类任务默认先由后端收集证据 |

手动运行 Spring Boot 不会自动加载仓库 `.env`。`.env.example` 是配置示例，需设置进程环境变量或使用专用 profile。

回退：停止当前后端，移除 `llamafactory` profile，设置 `AI_PROVIDER=ollama` 和原 `OLLAMA_*` 后重启。专用 profile 明确选择 LLaMA-Factory，不能只在保留该 profile 的同时修改 `AI_PROVIDER` 来回退。数据库无需回滚。

Docker Compose 已传递相关变量，但推理进程未编排进 Compose。容器里的 `127.0.0.1` 指向容器本身；Docker Desktop 场景需单独配置宿主机可达地址。提供的本机模型脚本只监听 loopback，本次没有进行网络暴露或远程部署；容器可达性与 API 鉴权应作为独立部署验证。

## 4. 四类任务的实际工作方式

这版模型在简短测试中可以发出函数调用，但在现有完整诊断提示词下，连续请求仍未取得必要的 `get_learning_profile` 调用。因此默认不依赖模型自主选择工具：

1. 后端从当前场景的工具定义中执行固定查询，身份仍来自已鉴权的业务上下文。
2. 汇总学习概况、薄弱点、候选资源；路径场景另查询先修关系。
3. 把实际证据提供给微调模型，要求直接生成分析。
4. 沿用必要证据检查与路径解析。受控证据模式下，路径结构或课程引用校验失败时，最多请求模型修正一次；修正后仍未通过则返回错误，不保存无效路径。工具和来源元数据记录真实查询；不会把模型未调用工具描述为自主协作。

四卡片响应新增 `agentMode`：

- `prefetched-evidence`：后端收集证据，模型负责生成；当前 LLaMA-Factory 默认。
- `native-tools`：模型选择工具的原循环；Ollama 默认，LLaMA-Factory 可通过配置试验开启。

这次接入没有完成模型内容质量评估或所有业务校验升级。尤其推荐文字的依据、报告质量以及真实图谱先修数据仍需按实施方案验收。不要为了通过测试而关闭现有必要证据检查。

## 5. 流式、取消、用量与健康检查

- 原前端 SSE 事件名称不变；适配的是上游 OpenAI 风格 SSE。只发送 `delta.content`，不转发 `reasoning_content`。
- 网络断流、缺少完成事件和明确的长度截断不能被当作完整回答。
- 取消会关闭应用网络请求；模拟服务测试验证了阻塞读取退出和应用并发容量回收。模型端生成计算是否立即停止取决于 LLaMA-Factory，不能仅凭前端停止显示认定 GPU 已停止计算。
- 本机 LLaMA-Factory 的流式接口不返回 Token 用量，应用以 `-1` 保存未知 Token，完成事件中对应指标为 `null`；后续统计必须排除未知值，不能把它当作真实 0 或负成本。
- `GET /api/ai/health` 仍需登录。管理员响应新增 `modelService`；Ollama 模式继续保留原 `ollama` 字段。服务可用只表示模型列表可访问且名称匹配，不等于回答质量达标。

本地 LLaMA-Factory API 的流式结束实现始终发送 `stop`；它不提供可靠的流式截断原因。应用可识别上游明确报告的截断，但这项服务端限制需在长回答评估中额外检查。

## 6. 验证命令

```powershell
# 协议与应用单元测试，不要求运行真实模型。
.\mvnw.cmd -f backend/pom.xml '-Dtest=LlamaFactoryProviderTest,LearningAgentAiServiceTest,OllamaProviderTest,AiHealthControllerTest,TutorPromptServiceTest' test

# 先确认模型 API 已就绪。此测试仅用合成学生数据，不连接业务数据库。
$env:LLAMAFACTORY_SMOKE = 'true'
.\mvnw.cmd -f backend/pom.xml '-Dtest=LlamaFactoryLiveTest' test
Remove-Item Env:\LLAMAFACTORY_SMOKE
```

真实测试包含模型健康、中文 SSE、四类任务和路径结构解析；Neo4j 使用不可用的测试替身，验证显式降级，不证明真实图谱服务已经验收。测试报告位于 `backend/target/surefire-reports/`。

## 7. 实施记录

- 2026-10-12：加载基础模型和最终 LoRA 成功；中文问答与单个函数调用成功；协议测试通过；完整诊断原生工具循环未通过。
- 2026-10-14：增加默认受控证据模式，保留原生模式开关。真实模型的健康检查、中文 SSE、引导、推荐、学习路径与学情分析测试通过；路径返回成功解析。使用合成学生数据和不可用图谱替身，未验证真实学生数据或真实 Neo4j。
- 2026-10-14：后端全量测试共 72 项，71 项通过、1 项真实模型测试按默认开关跳过，失败与错误均为 0。另行开启真实模型测试时通过。
- 2026-10-14：后端使用 `llamafactory` profile 启动成功；首页 HTTP 200，未登录访问 AI 健康接口 HTTP 401。未进行登录后的浏览器全流程验收。
- 2026-10-15：重启模型服务后，健康、中文 SSE、引导和推荐复测通过，路径返回未通过结构校验；因此增加一次路径修正重试，并补充“修正成功”与“重复引用无效课程仍拒绝”的单元测试。
- 2026-10-15：本机 `MySQL80` 已停止，当前进程启动该服务被系统拒绝。项目后端尚未恢复运行，需先在管理员 PowerShell 启动数据库，再执行本节启动脚本。
- 2026-10-15：启用真实模型测试后，后端全量 74 项全部通过，0 失败、0 错误、0 跳过。真实模型覆盖健康、中文 SSE、四类任务及路径解析；仍使用合成学生数据和图谱降级替身。模型 API 当前监听 `127.0.0.1:8000`。本次日志为 `.runtime/backend-tests-20261015.log`，测试报告位于 `backend/target/surefire-reports/`。
- 2026-10-16：重新启动前端与模型服务，分别监听 `127.0.0.1:5173`、`127.0.0.1:8000`。前端生产构建成功；登录页、空表单校验、注册切换及教师注册码字段经浏览器检查正常，当前页面控制台未出现警告或错误。
- 2026-10-16：后端再次运行包含真实模型的全量 74 项测试，全部通过；前端 `learning-path.spec.js` 与 `student-buttons.spec.js` 共 14 项交互测试全部通过，覆盖路径展示与恢复、图谱降级、筛选分页、重试及搜索响应顺序。前端测试使用模拟接口，不能代替真实数据库联调。日志为 `.runtime/backend-tests-20261016.log`、`.runtime/frontend-tests-20261016.log`，页面截图为 `.runtime/frontend/login-20261016.png`。
- 2026-10-16：后端实际启动失败，日志确认 MySQL 连接被拒绝；`MySQL80` 服务仍已停止，系统拒绝当前进程启动服务。用户暂时无法启动数据库，因此真实登录、课程接口及数据库业务流程仍待验证；未修改数据库或放宽鉴权来绕过该问题。后端启动日志为 `.runtime/backend/stdout-20261016.log`。
- 2026-10-16 后续：使用独立 MySQL 3307 和 Neo4j 社区版 7688，后端 8082、前端 5174 已启动。21 项浏览器业务与回归测试全部通过，覆盖真实注册登录、课程学习、路径写入 Neo4j、流式答疑与报告下载；原业务数据库仍未访问。详见 [全链路联调记录](全链路联调记录-2026-10-16.md)。

参考：[LLaMA-Factory 推理文档](https://llamafactory.readthedocs.io/en/latest/getting_started/inference.html)。本机 API 字段与模板选择同时依据已安装源码及训练配置核对。
