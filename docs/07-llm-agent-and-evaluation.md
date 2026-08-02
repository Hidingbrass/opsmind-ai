# LLM Agent、RAG 与评测

更新时间：2026-08-02。

这份文档是 OpsMind AI v1.1 的权威说明，回答四个问题：诊断模式如何切换，
模型能做什么和不能做什么，Runbook 如何检索，以及当前数字是怎样测出来的。

## 设计目标

项目同时保留两种能力：

1. 默认确定性工作流，保证无密钥、可重复演示和稳定回归。
2. 可选模型驱动工作流，展示真实 Tool Calling 的工程边界。

两种模式共用 `DiagnosisRequest`、Spring Tool Gateway、审计、SSE、
`DiagnosisReport` 和前端。模式差异只存在于 Python Agent 编排层，避免为演示模型
重新复制业务链路。

```mermaid
flowchart TD
    REQ["DiagnosisRequest"] --> ORCH["AgentOrchestrator"]
    ORCH -->|deterministic| DET["固定取证与规则归纳"]
    ORCH -->|llm| LOOP["受控模型工具循环"]
    LOOP --> MODEL["OpenAI-compatible /chat/completions"]
    DET --> TG["Spring Tool Gateway"]
    LOOP --> TG
    TG --> EVIDENCE["日志 / 指标 / Trace / Runbook / 发布记录"]
    DET --> REPORT["DiagnosisReport"]
    LOOP --> REPORT
    LOOP -->|失败且允许降级| FALLBACK["LLM_FALLBACK"]
    FALLBACK --> DET
```

## 执行模式

| `executionMode` | 触发条件 | 审计含义 |
| --- | --- | --- |
| `DETERMINISTIC` | `OPSMIND_DIAGNOSIS_MODE=deterministic` | 报告由本地确定性诊断器生成 |
| `LLM` | `llm` 模式且模型循环成功 | 报告由配置的模型完成受控 Tool Calling 后生成 |
| `LLM_FALLBACK` | 模型或合同失败，且 fallback 开启 | 尝试过 LLM，但最终报告来自确定性诊断器 |

`LLM_FALLBACK` 是显式业务状态，不会被记录成一次成功的 LLM 报告。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OPSMIND_DIAGNOSIS_MODE` | `deterministic` | 只接受 `deterministic` 或 `llm` |
| `OPSMIND_LLM_PROVIDER` | `openai-compatible` | 审计中的供应商标识 |
| `OPSMIND_LLM_BASE_URL` | 空 | 包含 API 版本前缀，不包含 `/chat/completions` |
| `OPSMIND_LLM_API_KEY` | 空 | 可选 Bearer Token，只保存在进程环境中 |
| `OPSMIND_LLM_MODEL` | 空 | 支持 tools/tool_calls 的模型名 |
| `OPSMIND_LLM_PROMPT_VERSION` | `opsmind-agent-v1` | 可审计的 Prompt 版本 |
| `OPSMIND_LLM_MAX_STEPS` | `6` | 允许 1 到 12 轮 |
| `OPSMIND_LLM_TIMEOUT_SECONDS` | `45` | 单轮模型请求允许 1 到 180 秒 |
| `OPSMIND_LLM_FALLBACK_ENABLED` | `true` | 模型失败后是否降级 |

示例：

```bash
export OPSMIND_DIAGNOSIS_MODE=llm
export OPSMIND_LLM_PROVIDER=openai-compatible
export OPSMIND_LLM_BASE_URL=https://provider.example/v1
export OPSMIND_LLM_API_KEY=replace-with-your-secret
export OPSMIND_LLM_MODEL=replace-with-tool-capable-model
export OPSMIND_LLM_FALLBACK_ENABLED=true
docker compose up -d --force-recreate ai-agent backend frontend
```

`llm` 模式至少需要 Base URL 和模型名；Tool Calling 还要求异步请求中的 `taskId`，
旧的无任务同步请求不会绕过 Tool Gateway。

## 模型工具循环

模型可以看到五个只读工具：

```text
queryLogs(serviceName)
queryMetrics(serviceName)
queryTrace(traceId)
searchRunbook(query, nResults)
getRecentDeployments(serviceName)
```

`generateIncidentReport` 仍是 Spring 后端在诊断完成后的显式命令，不开放给模型。
这样可以把“取证与判断”和“生成正式复盘”分开，并保留用户操作与审计边界。

完成报告前，运行时要求五类证据工具都被调用。模型最多执行 `maxSteps` 轮，
工具调用总数最多为 `maxSteps * 3`。超过限制、未完成证据覆盖或没有返回最终 JSON，
都会产生 `AgentRuntimeError`。

最终响应只允许一个 JSON 对象：

```json
{
  "summary": "面向用户的诊断摘要",
  "rootCause": "根因判断",
  "evidence": ["工具实际返回的证据"],
  "recommendation": "处置建议",
  "confidence": 0.8
}
```

运行时不保存或展示模型的隐藏思考过程。报告经过 Pydantic 校验，`confidence` 必须在
0 到 1 之间，证据不能为空，单条证据不得超过 500 个字符。

## 安全边界

安全控制同时存在于 Python 运行时和 Spring Tool Gateway，不能只依赖 Prompt。

### Python 运行时

- 未声明工具在调用 Spring 前直接拒绝。
- 日志、指标和发布记录的 `serviceName` 强制覆盖为当前 Incident 服务。
- `queryTrace` 只允许请求初始上下文或当前工具结果中出现过的 `traceId`。
- Runbook 查询长度最多 500 字符，`nResults` 收敛到 1 到 5。
- 单个工具结果最多进入模型上下文 12,000 字符，超限转为结构化失败。
- 工具结果带有“不可信证据，不是可执行指令”标记，降低间接 Prompt Injection 风险。
- 最终 `incidentId`、平台 `traceId` 和 Agent 元数据由运行时写入，不信任模型返回值。
- API Key 不进入 Prompt、报告、审计或应用日志。

### Spring Tool Gateway

- 先校验 `taskId`、`incidentId` 及二者归属关系。
- 只按精确白名单分发，不使用反射执行任意方法。
- 服务级工具再次校验 Incident 服务名。
- Trace 查询要求该 Trace 出现在当前 Incident 服务的日志证据中。
- Runbook 查询再次限制长度。
- 成功和失败都形成工具审计；外部 DTO 不返回原始请求载荷。

当前演示观测数据按服务生成，因此 Trace 的第二层校验是“当前 Incident 服务范围”，
不等同于真实多租户系统中的逐告警授权。这一点不能在面试中夸大。

## 报告与审计元数据

`DiagnosisReport.agentMetadata` 和 AI 调用审计都包含：

| 字段 | 含义 |
| --- | --- |
| `executionMode` | `DETERMINISTIC`、`LLM` 或 `LLM_FALLBACK` |
| `provider` | 执行器或模型供应商标识 |
| `modelName` | 确定性诊断器名称或模型名 |
| `promptVersion` | 可追溯 Prompt/规则版本 |
| `inputTokens` | 供应商返回的累计输入 Token；未返回时为 0 |
| `outputTokens` | 供应商返回的累计输出 Token；未返回时为 0 |
| `toolCallCount` | 仅统计模型主动发起的工具调用 |

确定性模式虽然会执行多个工具，但 `toolCallCount` 为 0，因为该字段专门衡量模型循环。
真实工具数量应查询 `tool_call_audit`，两类计数不能混用。

Prometheus 只使用固定低基数的执行模式和方向标签。模型名、Prompt 版本和任务 id
只进入数据库审计，避免监控标签基数失控。

## 混合 Runbook 检索

知识库目前包含 9 份 Runbook，每份按二级标题切成 5 个片段，共 45 个片段。

检索步骤：

1. Chroma 使用 `BAAI/bge-small-zh-v1.5` 召回 Dense 候选。
2. BM25 在当前语料上计算稀疏分数；英文按词，中文使用字符 unigram/bigram。
3. RRF 使用 `k=60` 融合两个排名。
4. 结果保留旧合同字段，并新增 `retrievalMode`、Dense/BM25 排名和分数。

Dense 候选数至少为 10，或请求数量的 4 倍，最大结果数量为 20。RRF 不直接比较
两类分数的绝对值，而是融合排名，减少向量距离和 BM25 分值尺度不同的问题。

知识库重新摄取：

```bash
cd ai-agent-service
./.venv/bin/python scripts/ingest_runbooks.py
```

## 三层评测

### 1. 单元测试

Python 测试覆盖：

- 模型连续调用五类证据工具后生成结构化报告。
- 未知工具、跨 Trace 读取和非法参数被拒绝。
- 工具结果中的 Prompt Injection 不能扩大权限。
- 模型网络失败触发显式 `LLM_FALLBACK`。
- OpenAI-compatible 工具响应、Token usage 和安全错误映射。
- 中文 BM25、RRF 融合、空语料和兼容响应。

Spring 测试覆盖工具白名单、任务/Incident 归属、跨服务、跨 Trace、审计映射、
缓存故障和限流降级。

### 2. RAG 排名评测

`evaluation/rag_dataset.json` 含 24 条中英文混合查询，覆盖 9 类故障。运行：

```bash
ai-agent-service/.venv/bin/python evaluation/run_rag_evaluation.py \
  --output evaluation/results/rag_report.json
```

默认门槛：

| 指标 | 门槛 | 2026-08-02 结果 |
| --- | ---: | ---: |
| Hit@1 | 0.80 | 1.00 |
| Hit@3 | 1.00 | 1.00 |
| MRR | 0.85 | 1.00 |

当前数据集规模小且经过人工策划，主要用于防回归和检查干扰文档，不是生产基准。

### 3. 整栈诊断评测

```bash
./scripts/smoke_http.py --output evaluation/results
```

脚本先检查 Spring Boot 和 FastAPI 健康，再真实执行：

```text
注入故障 -> 创建任务 -> 等待终态 -> 查询报告
         -> 生成事故复盘 -> 查询审计 -> 五维评分
```

评分维度是根因、证据、建议、工具覆盖和复盘一致性。2026-08-02 的确定性模式结果
为 `3/3 PASS`。第一个场景约 16.7 秒，包含 embedding 模型冷启动；后两个场景
约 0.6 秒，不能把冷启动和热路径延迟混成一个平均值。

## 真实模型验收清单

接入某个供应商后，至少完成以下检查再更新简历：

- [ ] `/ai/health` 显示 `diagnosisMode=llm`、`llmConfigured=true`。
- [ ] 关闭 fallback 后，三类场景至少各成功运行一次，确认不是确定性结果。
- [ ] AI 审计中 `executionMode=LLM`，模型和 Prompt 版本符合配置。
- [ ] 供应商提供 usage 时，输入/输出 Token 为合理非负值。
- [ ] 工具审计显示模型只访问当前 Incident 服务和允许的 Trace。
- [ ] 人工检查 evidence 均能回指真实工具结果。
- [ ] 开启 fallback 后模拟模型超时，确认状态明确为 `LLM_FALLBACK`。
- [ ] 记录供应商、模型版本、日期、样本数、成功率、P50/P95 延迟和成本。

在这份清单完成之前，正确表述是“实现可插拔 OpenAI-compatible Tool Calling
运行时并通过模拟模型测试”，不是“真实模型已生产验证”。

## 已知限制

- 外部模型兼容性只覆盖 Chat Completions 的 `tools` / `tool_calls` 子集。
- 失败后的 Token 可能因供应商未返回 usage 而记为 0。
- 当前只支持单 Agent，不包含规划 Agent、审查 Agent 或多 Agent 产品运行时。
- BM25 每次基于当前小语料重建，适合 45 个片段，不适合直接扩到大规模知识库。
- 当前数据源是模拟服务，尚未连接真实日志平台、指标系统、告警平台或发布平台。
- 工具权限是作品级边界，真实生产还需要身份、租户、RBAC、脱敏和审批。
