# OpsMind AI 项目蓝图

## 一句话介绍

OpsMind AI 是一个面向微服务系统的 AI SRE 故障诊断平台。它通过 Spring Boot 后端任务编排、Python 双模式诊断 Agent、混合 Runbook RAG 和受控 Tool Calling，完成从故障注入到根因分析、修复建议和事故复盘的完整闭环。

## 简历定位

这个项目不是普通聊天机器人，而是一个偏后端工程落地的 AI 应用项目，可以展示：

- 使用 `Spring Boot` 设计业务后端、任务编排和工具网关。
- 使用异步任务处理耗时 AI 诊断流程。
- 使用 `SSE` 实时推送 Agent 诊断过程。
- 使用 `Redis` 完成任务缓存、结果复用、请求限流和重复任务去重。
- 使用 HTTP 超时与 `Resilience4j` 为 AI 服务调用提供重试、熔断和并发隔离。
- 使用 `Chroma + BM25 + RRF` 构建混合 Runbook RAG，并用固定数据集评估排序质量。
- 默认使用确定性工作流稳定演示，可选接入 OpenAI-compatible 模型自主选择只读工具。
- 使用受控 `Tool Calling` 实现日志、指标、链路、知识库和发布记录取证。
- 使用审计落库记录诊断报告、工具调用、执行模式、模型版本和 Token 用量。
- 使用 `Prometheus`、`Grafana` 和 `OpenTelemetry` 增强系统可观测性。
- 使用 `Nginx` 和 `Docker Compose` 完成本地多服务部署。

项目主线偏 Java 后端工程，AI 能力用于形成差异化亮点。

## 架构分层

```text
Frontend Dashboard
  展示故障、任务、诊断时间线、工具调用、报告和复盘

Nginx
  统一代理前端、后端 API 和 AI 服务健康检查

Spring Boot Backend
  业务控制面、任务编排、工具网关、审计、缓存、限流、SSE、熔断保护

Python AI Agent Service
  确定性/LLM 双执行模式、混合 Runbook RAG、工具取证编排、结构化报告生成

Data and Infra
  MySQL、Redis、Chroma、Prometheus、Grafana、OpenTelemetry
```

## MVP 范围

MVP 要证明一个完整的故障诊断闭环：

1. 用户向模拟服务注入故障。
2. 后端创建故障事件。
3. 后端返回对应日志、指标和链路追踪数据。
4. Spring Boot 调用 Python AI 服务。
5. AI 服务从 Chroma 检索相关 Runbook。
6. AI 服务生成带证据的诊断报告。
7. 后端持久化诊断报告。
8. 前端展示诊断过程和结果。

MVP 不强求一次做完所有工程化能力，但数据模型要为后续异步任务、审计、SSE 和评测预留空间。

## 增强版范围

增强版重点突出后端架构能力：

1. 同步诊断升级为异步诊断任务。
2. 使用 SSE 实时推送任务进度和工具调用过程。
3. 使用 Tool Gateway 统一执行 AI 工具调用。
4. 工具调用、Python AI 服务调用、诊断报告全部审计落库。
5. 使用 Redis 做诊断结果缓存、限流、任务状态和重复任务去重。
6. 使用 Resilience4j 保护 AI 服务和下游工具调用。
7. 可选使用 Redis Stream 保存诊断过程事件。

## 冲刺版范围

冲刺版用于提升作品完整度和面试表达：

1. 接入 Spring Boot Actuator 和 Micrometer。
2. 使用 Prometheus 采集接口耗时、诊断成功率、AI 调用延迟等指标。
3. 使用 Grafana 展示平台运行状态。
4. 使用 OpenTelemetry TraceId 串联请求、任务、工具调用和日志。
5. 建立小型 AI 诊断质量评测集。
6. 完成前端控制台、部署文档、架构图、演示脚本和面试材料。

## v1.1 当前状态

截至 2026-08-02，MVP、增强版和冲刺版已经完成，v1.1 进一步补齐：

- `deterministic` 与 `llm` 双执行模式，以及模型失败后的显式 `LLM_FALLBACK`。
- 五个模型可调用的只读工具；报告生成由受信代码完成，不向模型暴露写操作。
- 服务、事故和 trace 归属校验，Prompt Injection 提示隔离和工具调用上限。
- 9 份 Runbook、45 个切片、Dense + BM25 + RRF 混合检索。
- 24 条 RAG 排名评测、3 个端到端诊断场景和单元/集成回归测试。
- 执行模式、provider、model、promptVersion、Token 和工具调用数全链路审计与前端展示。

默认模式已经完成全栈实测；LLM 模式已完成客户端与工具循环的 mock 测试，仍需使用真实供应商 API Key 做上线前验收。

## 主要故障场景

先从三个可控场景开始：

- 支付服务超时。
- Redis 缓存连接失败。
- 数据库慢查询导致订单接口延迟升高。

每个场景都应包含：

- 服务名称。
- 故障现象。
- 日志。
- 指标。
- 类似链路追踪的调用链。
- 相关发布记录。
- 匹配的运维手册。
- 预期根因。
- 推荐修复方式。

## 系统模块

### Spring Boot 后端

职责：

- 故障事件增删改查。
- 故障注入 API。
- 模拟可观测性数据查询。
- 诊断任务编排。
- 诊断工具网关。
- 诊断报告持久化。
- 工具调用审计。
- Redis 缓存、限流和任务状态。
- SSE 诊断过程推送。
- HTTP 超时与 Resilience4j 重试、熔断和并发隔离。
- Prometheus 指标暴露。
- TraceId 贯穿请求、任务和工具调用。

### Python AI Agent 服务

职责：

- RAG 文档导入。
- Chroma Dense 检索、BM25 词法检索和 RRF 融合排序。
- 多步骤诊断 Agent 工作流。
- 确定性 Tool Calling 与可选的 OpenAI-compatible 模型 Tool Calling。
- 模型超时、协议错误或证据不足时显式回退到确定性路径。
- Prompt 编排。
- 结构化 JSON 诊断报告。
- Python AI 服务调用状态和延迟统计。
- AI 诊断质量评测。

### 模拟微服务

职责：

- 订单服务。
- 支付服务。
- 库存服务。
- 模拟故障模式。
- 输出结构化日志和指标接口。
- 产生可关联的 `traceId`。

### 前端控制台

职责：

- 故障事件列表。
- 故障注入面板。
- 诊断任务状态。
- 诊断过程时间线。
- 工具调用可视化。
- 证据引用展示。
- 最终报告和复盘报告展示。

### 基础设施

职责：

- Nginx 反向代理。
- Redis。
- Chroma。
- MySQL。
- Prometheus。
- Grafana。
- OpenTelemetry Collector。
- Docker Compose 一键启动。

## Tool Calling 工具

Python 诊断工作流通过 Spring Tool Gateway 调用这些工具：

```text
queryLogs(serviceName)
queryMetrics(serviceName)
queryTrace(traceId)
searchRunbook(query, nResults)
getRecentDeployments(serviceName)
generateIncidentReport(incidentId)
```

模型运行时只暴露前五个只读取证工具，`generateIncidentReport` 是诊断完成后的受信后处理步骤。每个工具都执行参数类型、`incidentId`、`serviceName` 和 `traceId` 归属校验。

工具调用必须经过后端 Tool Gateway，Python 服务不能直接操作业务数据库或基础设施。

每次工具调用都应该记录：

```text
taskId
incidentId
toolName
requestPayload
responseSummary
latencyMs
status
errorMessage
createdAt
```

## Redis 的使用方式

Redis 不能只是为了堆技术名词，而要承担真实后端价值：

- 缓存重复诊断结果。
- 存储 AI 诊断任务状态。
- 限制诊断请求频率。
- 对重复故障分析进行去重。
- 可选使用 Redis Stream 传递和恢复异步诊断事件。

## 混合 RAG 的使用方式

Chroma 用于存储 Dense embedding，BM25 索引用于补足错误码、服务名和缩写等精确词匹配；两个候选集通过 RRF 融合，避免直接比较不同量纲的分数。

知识库包含：

- 运维手册。
- 历史故障案例。
- 服务依赖说明。
- 故障排查指南。

当前知识库包含 9 份 Runbook、45 个切片。每个检索结果都带来源、Dense/BM25 排名和融合分数，最终诊断报告可引用可追溯的证据来源。

## 可观测性设计

平台需要观察两类对象：

- 被诊断的模拟微服务。
- OpsMind AI 平台自身。

平台自身应暴露这些指标：

```text
diagnosis_task_total
diagnosis_task_success_total
diagnosis_task_failed_total
diagnosis_task_duration_seconds
ai_model_call_total
ai_model_call_latency_seconds
tool_call_total
tool_call_failed_total
```

`traceId` 应贯穿：

```text
HTTP 请求 -> 诊断任务 -> 工具调用 -> AI 服务调用 -> 诊断报告
```

## 最终演示脚本

1. 打开控制台。
2. 点击“注入支付服务超时”。
3. 页面出现一条高优先级故障事件。
4. 点击“开始 AI 诊断”。
5. 后端创建异步诊断任务。
6. 时间线依次展示：
   - 查询指标。
   - 查询日志。
   - 查询链路追踪。
   - 检索运维手册。
   - 汇总工具调用证据。
   - 生成根因分析。
7. 最终报告指出订单结算延迟是由支付服务下游调用超时导致的。
8. 报告展示证据来源、置信度和推荐修复动作。
9. 点击“生成复盘”。
10. 展示事故复盘报告。
