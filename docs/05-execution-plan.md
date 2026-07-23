# 执行规划

这份文档是 OpsMind AI 的实战推进计划。项目定位是一个面向 AI 应用开发面试的作品级项目，主线突出 Java 后端工程能力，AI Agent 能力作为高质量增强点。

目标不是堆技术名词，而是做出一个能运行、能演示、能讲清楚架构取舍的系统。

## 规划总览

最终项目按三层交付：

```text
MVP 版本：先做完整闭环，约 2-3 周。
增强版本：补齐后端工程化和 AI Agent 过程可观测，约 2-3 周。
冲刺版本：补齐监控、评测、部署和面试材料，约 1-2 周。
```

如果每天投入时间不同，可以按下面节奏估算：

```text
每天 2-3 小时：5-8 周完成作品级版本。
每天 4-5 小时：3-5 周完成作品级版本。
每天 6 小时以上：2-3 周完成作品级版本。
```

推荐推进顺序：

```text
先完成业务闭环。
再把 AI 调用变成异步任务。
再加入工具调用、RAG 和诊断审计。
再补 Redis、SSE、熔断限流和可观测性。
最后做前端演示、部署和面试包装。
```

## 当前进度快照

更新时间：2026-07-24。

已经完成：

- Spring Boot 后端骨架、统一响应结构和全局异常处理。
- Incident 故障事件模块。
- Fault Scenario 故障注入模块。
- 模拟日志、指标、链路追踪查询接口。
- Spring Boot 调用 Python FastAPI AI 服务。
- AI 诊断报告生成、保存到 MySQL、按故障查询历史诊断记录。
- Chroma 本地知识库基础结构。
- `payment-timeout` Runbook 文档。
- `redis-connection-failure` Runbook 文档。
- `database-slow-query` Runbook 文档。
- 使用 `BAAI/bge-small-zh-v1.5` 中文 embedding 模型写入 Chroma。
- Runbook 知识库检索接口：`GET /ai/runbooks/search`。
- `/ai/diagnose` 已接入 Runbook RAG 检索，诊断证据中包含知识库命中来源。
- 支付超时、Redis 连接失败、数据库慢查询三场景观测数据。
- `/ai/diagnose` 已能按三类故障场景返回不同根因报告。
- Spring Boot 端三场景诊断接口均能保存 RAG 增强报告。
- 异步诊断任务第一版闭环已跑通：创建任务立即返回 `taskId`，后台执行 AI 诊断，成功后写入 `diagnosisRecordId`，失败后写入 `failureReason`。
- SSE 后端闭环已跑通：支持按 `taskId` 订阅、当前状态快照、`RUNNING` / `CALL_AI` 过程事件和 `SUCCESS` / `FAILED` 终态事件。
- 已使用 curl 验证 SSE 成功与失败终态均与数据库一致，终态推送后连接自动关闭。
- Spring Boot Tool Gateway 已完成 `queryLogs` 白名单分发、通用参数校验、任务与故障归属校验和结构化失败返回。
- 工具调用审计写入闭环已完成：成功只保存结果摘要，失败保存原因，并记录请求、状态和耗时。
- 已用 curl 和 MySQL 验证 `queryLogs` 成功审计与未知工具失败审计均真实落库，数据库记录与接口响应一致。
- 工具审计查询接口已完成：支持按 `taskId` 倒序查询，并通过 DTO 隐藏原始请求参数。
- Java 已将异步诊断 `taskId` 传入 Python，Python Tool Client 支持环境变量地址、超时和结构化失败。
- Python 诊断流程已主动调用 `queryLogs`，工具失败时降级使用已有日志，不中断诊断。
- 已通过真实异步任务验收 Agent 工具闭环：任务成功保存报告，审计中的任务、故障和工具结果一致。

当前正在进行：

```text
增强版本阶段 7：Function Calling 和工具调用审计
```

当前验收重点：

```text
1. 在 `queryLogs` 模式上补充 `queryMetrics` 工具。
2. 继续补充 `queryTrace` 和 `searchRunbook` 工具。
3. 让诊断结论显式使用多种工具返回的结构化证据。
```

下一步：

```text
沿用已验收的 Tool Gateway、审计和 Python Client 边界，完成 `queryMetrics` 闭环。
然后继续补充链路和 Runbook 工具，让 Agent 按需获取多类证据。
```

## 架构升级方向

升级后的系统更像一个真实后端平台，而不是简单的 AI Demo：

```text
前端控制台
  -> Nginx
  -> Spring Boot 后端
       -> Incident / Fault / Observability 业务模块
       -> Diagnosis Task 异步诊断任务模块
       -> Tool Gateway 诊断工具网关
       -> Audit 诊断审计模块
       -> SSE 诊断过程推送模块
       -> Redis 缓存、限流、任务状态、事件流
       -> MySQL 持久化故障、诊断报告、工具调用记录
       -> Resilience4j 保护 AI 服务和下游工具调用
  -> Python AI Agent 服务
       -> 多步骤诊断工作流
       -> Runbook RAG 检索
       -> Function Calling / Tool Calling
       -> 结构化 JSON 诊断报告
       -> 模型调用成本和延迟统计
  -> Chroma 运维知识库
  -> Prometheus / Grafana / OpenTelemetry 可观测性增强
```

后端主线：

- 异步诊断任务。
- SSE 实时诊断进度。
- Redis 缓存、限流、任务状态和事件流。
- Resilience4j 熔断、重试、限流和超时控制。
- 诊断记录、工具调用、模型调用审计落库。
- Prometheus 指标和 OpenTelemetry TraceId 串联。

AI 主线：

- 多步骤诊断 Agent 工作流。
- 日志、指标、链路、Runbook、发布记录工具调用。
- 结构化诊断报告 JSON Schema。
- Runbook RAG 引用证据。
- AI 诊断质量评测集。
- 模型调用成本和延迟统计。

## MVP 版本：完成可演示闭环

目标：让项目从故障注入到 AI 诊断报告形成完整链路。这个版本是简历项目的最低可展示版本。

预计时间：

```text
每天 2-3 小时：2-3 周。
每天 4-5 小时：1-2 周。
每天 6 小时以上：5-8 天。
```

### 阶段 1：故障事件和场景注入

预计时间：2-3 天。

当前状态：

- 已完成 Spring Boot 后端骨架。
- 已完成 MySQL、Redis、Chroma 基础设施。
- 已完成 Incident 基础业务模块。
- 当前正在推进 Fault Scenario 故障注入模块。

要完成的内容：

- `FaultScenario`：故障场景模型。
- `FaultInjectionResponse`：故障注入返回对象。
- `FaultScenarioService`：维护场景列表并创建 Incident。
- `FaultScenarioController`：提供场景列表和注入接口。
- 为支付超时、Redis 异常、数据库慢查询准备固定场景。

核心接口：

```text
GET  /api/fault-scenarios
POST /api/fault-scenarios/{scenarioKey}/inject
GET  /api/incidents
GET  /api/incidents/{incidentId}
```

验收方式：

```bash
curl http://localhost:8080/api/fault-scenarios
curl -X POST http://localhost:8080/api/fault-scenarios/payment-timeout/inject
curl http://localhost:8080/api/incidents
```

学习重点：

- Spring Boot 分层架构。
- 一个业务模块如何调用另一个业务模块。
- 为什么 `fault` 模块不直接访问数据库，而是调用 `IncidentService`。

面试可讲点：

- 我先把 AI 之外的业务闭环做稳定，避免所有复杂度都堆到大模型调用上。
- 故障注入是为了让系统可演示、可测试、可复现。

### 阶段 2：模拟可观测性数据

预计时间：2-3 天。

目标：为每个故障场景准备日志、指标和链路追踪数据，让 AI 诊断有证据来源。

要完成的内容：

- 日志查询接口。
- 指标查询接口。
- 链路追踪查询接口。
- 每个故障场景绑定一组模拟观测数据。
- 每条观测数据带 `incidentId`、`serviceName`、`traceId`、`timestamp`。

核心接口：

```text
GET /api/observability/logs?incidentId=1
GET /api/observability/metrics?incidentId=1
GET /api/observability/traces/{traceId}
```

验收方式：

- 注入支付服务超时故障后，可以查到对应日志、指标和链路。
- 数据能回答“哪里慢、哪里错、证据是什么”。

学习重点：

- 日志回答“发生了什么”。
- 指标回答“异常程度有多大”。
- 链路追踪回答“请求卡在哪里”。

面试可讲点：

- AI 诊断不能凭空猜测，必须基于日志、指标、链路和知识库证据。

### 阶段 3：同步版基础 AI 诊断

预计时间：3-4 天。

目标：先让 Spring Boot 调用 Python AI 服务，生成一份可落库的诊断报告。

要完成的内容：

- Spring Boot 调用 Python FastAPI。
- Python AI 服务接收故障上下文。
- AI 输出根因、证据、修复建议。
- 后端保存诊断报告。
- 定义诊断报告 DTO 和数据库结构。

核心接口：

```text
POST /api/incidents/{incidentId}/diagnosis
GET  /api/incidents/{incidentId}/diagnosis
```

诊断报告建议结构：

```json
{
  "rootCause": "payment-service 下游调用超时",
  "confidence": 0.86,
  "evidences": [
    {
      "type": "metric",
      "source": "payment-service latency",
      "summary": "P95 延迟明显升高"
    }
  ],
  "recommendedActions": [
    "检查 payment-service 到第三方支付网关的连接池和超时配置"
  ]
}
```

学习重点：

- 后端服务之间如何调用。
- Prompt 如何组织上下文。
- 为什么诊断报告必须结构化。

面试可讲点：

- Java 后端负责业务状态和工具网关，Python 服务负责 AI 编排，这是为了让两种技术栈各自做擅长的事。

### 阶段 4：Runbook RAG 知识库

预计时间：3-5 天。

目标：让 AI 不只依赖上下文和模型记忆，而是能检索运维手册和历史故障案例。

要完成的内容：

- 运维手册文档格式。
- 文档切分。
- 向量化并写入 Chroma。
- 根据故障现象检索相关 Runbook。
- 诊断报告引用知识来源。

核心能力：

```text
ingest runbook -> split chunks -> embed -> store in Chroma
incident context -> search runbook -> cited evidence -> diagnosis report
```

验收方式：

- 支付超时故障可以检索到 `payment-timeout` 运维手册。
- 最终报告中包含 Runbook 标题或片段来源。

学习重点：

- RAG 的流程：切分、向量化、存储、检索、生成。
- 企业 AI 应用为什么需要私有知识库。
- 如何用引用来源降低幻觉。

面试可讲点：

- 这个项目不把 RAG 做成单独问答，而是把 RAG 放入故障诊断链路中，服务于真实业务决策。

## 增强版本：突出后端工程化和 Agent 过程

目标：让系统从“能调用 AI”升级为“可观测、可恢复、可审计、可保护”的后端平台。

预计时间：

```text
每天 2-3 小时：2-3 周。
每天 4-5 小时：1-2 周。
每天 6 小时以上：5-8 天。
```

### 阶段 5：异步诊断任务

预计时间：3-4 天。

目标：AI 调用通常耗时较长，后端不应该让 HTTP 请求一直阻塞，因此改造成异步任务模式。

要完成的内容：

- `DiagnosisTask` 任务模型。
- 任务状态：`PENDING`、`RUNNING`、`SUCCESS`、`FAILED`。
- 创建任务接口。
- 查询任务状态接口。
- 后台执行诊断逻辑。
- 成功时保存 `diagnosisRecordId`。
- 失败时保存 `failureReason`。

核心接口：

```text
POST /api/diagnosis-tasks/incidents/{incidentId}
GET  /api/diagnosis-tasks/{taskId}
```

验收方式：

- 创建诊断任务后立即返回 `taskId`。
- 前端或 curl 可以轮询任务状态。
- 任务成功后状态变为 `SUCCESS`，并保存 `diagnosisRecordId`。
- AI 服务异常时任务状态变为 `FAILED`，并保存 `failureReason`。

当前实现状态：

- 已完成 `DiagnosisTask`、`DiagnosisTaskStatus`、`DiagnosisTaskRepository`、`DiagnosisTaskResponse`。
- 已完成 `DiagnosisTaskController` 和 `DiagnosisTaskService`。
- 已通过 `@EnableAsync` + `DiagnosisTaskExecutor` 实现后台执行。
- 已验证异步诊断任务闭环可用。

学习重点：

- 为什么耗时任务要异步化。
- 任务状态机如何设计。
- 同步接口和异步任务的边界。

面试可讲点：

- 我没有让 AI 调用阻塞用户请求，而是把它抽象成诊断任务，提升系统稳定性和扩展性。

### 阶段 6：SSE 实时诊断过程

预计时间：2-3 天。

当前状态：后端 SSE 闭环已完成，前端时间线展示留到控制台阶段实现。

目标：让前端实时看到诊断过程，适合展示 AI Agent 的推理和工具调用步骤。

要完成的内容：

- SSE 订阅接口。
- 诊断过程事件模型。
- 后端推送任务状态、工具调用、阶段结果。
- 前端时间线展示事件。

核心接口：

```text
GET /api/diagnosis-tasks/{taskId}/events
```

事件示例：

```json
{
  "taskId": "diag-001",
  "stage": "QUERY_METRICS",
  "message": "正在查询 payment-service 延迟指标",
  "timestamp": "2026-07-08T10:00:00"
}
```

验收方式：

- `GET /api/diagnosis-tasks/{taskId}/events` 能建立 `text/event-stream` 连接。
- 订阅时立即推送数据库中的当前状态快照，避免快速任务在连接建立前丢失终态。
- 异步执行器能推送 `RUNNING`、`CALL_AI`、`SUCCESS` 和 `FAILED` 事件。
- curl 收到的成功或失败终态与任务表一致，推送后 SSE 连接自动关闭。

学习重点：

- SSE 和 WebSocket 的区别。
- AI 流式过程为什么适合用 SSE。
- 后端如何管理长连接。

面试可讲点：

- SSE 让 Agent 过程透明化，用户能看到系统不是黑盒输出一段话。

### 阶段 7：Function Calling 和工具调用审计

预计时间：4-6 天。

当前状态：`queryLogs` Tool Gateway、成功/失败审计、审计查询接口和 Python Agent 主动调用均已完成并通过真实异步任务验收。

目标：让 AI Agent 自己决定调用日志、指标、链路、Runbook 和发布记录工具，并记录每次工具调用。

要完成的工具：

```text
queryLogs()
queryMetrics()
queryTrace()
searchRunbook()
getRecentDeployments()
generateIncidentReport()
```

要完成的后端能力：

- Tool Gateway 统一执行工具。
- 工具入参校验。
- 工具调用结果脱敏。
- 工具调用耗时记录。
- 工具调用审计落库。
- 危险操作人工确认机制。

工具调用审计字段：

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

验收方式：

- Agent 能按需调用日志、指标、链路和 Runbook。
- 每一次工具调用都能在数据库和前端时间线中看到。
- 工具失败时，Agent 能继续使用已有证据生成谨慎结论。

学习重点：

- Function Calling 是让模型调用后端函数，不只是输出文字。
- 工具网关为什么要由后端控制。
- AI Agent 为什么需要审计。

面试可讲点：

- 我把工具调用做成可审计链路，避免模型直接拥有不可控权限。

### 阶段 8：Redis 工程化

预计时间：3-5 天。

目标：让 Redis 在项目中承担真实后端价值，而不是只写在技术栈里。

必做内容：

- 诊断结果缓存。
- 诊断请求限流。
- 诊断任务状态缓存。
- 重复任务去重。

进阶内容：

- Redis Stream 保存诊断过程事件。
- Redis Stream Consumer 处理异步诊断事件。
- 页面重连后从事件流恢复诊断过程。

验收方式：

- 重复诊断同一故障时可以命中缓存。
- 过于频繁的诊断请求会被友好拒绝。
- 页面刷新后仍能恢复任务状态。
- 进阶版本中，可以从 Redis Stream 读取历史诊断事件。

学习重点：

- Redis 缓存、限流、任务状态、轻量队列的不同用法。
- AI 应用为什么需要缓存和限流。
- Stream 和普通缓存的区别。

面试可讲点：

- AI 调用慢且成本高，所以我用 Redis 做去重、缓存、限流和任务状态管理。

### 阶段 9：Resilience4j 保护下游调用

预计时间：2-4 天。

目标：保护 Spring Boot 调用 Python AI 服务、Chroma 和外部模型接口时的稳定性。

要完成的内容：

- AI 服务调用超时控制。
- 失败重试。
- 熔断降级。
- 并发隔离。
- 接口级限流。
- 友好的 fallback 返回。

适用场景：

```text
Spring Boot -> Python AI Service
Python AI Service -> LLM Provider
Python AI Service -> Chroma
Spring Boot -> Observability Tool
```

验收方式：

- AI 服务停止时，后端不会长时间卡死。
- 连续失败后触发熔断。
- 熔断期间返回明确的降级提示。

学习重点：

- 分布式系统中的超时、重试、熔断和限流。
- 为什么重试必须有次数和退避策略。
- 熔断和限流解决的问题不同。

面试可讲点：

- 我把 AI 服务当成不稳定下游处理，通过熔断、超时和降级避免故障扩散。

## 冲刺版本：完善作品级展示

目标：补齐监控、评测、部署和面试材料，让项目更像真实团队交付。

预计时间：

```text
每天 2-3 小时：1-2 周。
每天 4-5 小时：4-7 天。
每天 6 小时以上：2-4 天。
```

### 阶段 10：Prometheus、Grafana 和 OpenTelemetry

预计时间：3-5 天。

目标：把项目自己的运行状态也纳入观测，让后端工程能力更完整。

要完成的内容：

- Spring Boot Actuator。
- Micrometer Prometheus 指标。
- 诊断任务数量、成功率、失败率、平均耗时。
- AI 调用次数、平均延迟、失败次数。
- OpenTelemetry TraceId 在请求、任务、工具调用之间传递。
- Grafana Dashboard。

验收方式：

- `/actuator/prometheus` 能暴露指标。
- Grafana 能看到接口耗时、任务状态、AI 调用统计。
- 日志、诊断任务和工具调用记录能通过 `traceId` 串联。

学习重点：

- Metrics、Logs、Traces 的区别。
- 为什么后端系统需要可观测性。
- TraceId 如何帮助排查跨服务问题。

面试可讲点：

- 项目本身是 SRE 场景，所以我也用可观测性方法监控这个 AI 诊断平台。

### 阶段 11：AI 诊断质量评测

预计时间：2-4 天。

目标：不只展示“AI 能回答”，还要展示“如何判断回答质量”。

要完成的内容：

- 固定故障评测集。
- 每个故障设置期望根因、关键证据、推荐动作。
- 自动运行诊断并记录结果。
- 评测维度：根因命中、证据引用、修复建议可执行性、是否幻觉。
- 输出评测报告。

评测集示例：

```text
payment-timeout: 期望根因是支付服务下游超时。
redis-connection-failed: 期望根因是 Redis 连接不可用。
db-slow-query: 期望根因是订单查询 SQL 慢查询。
```

验收方式：

- 可以一键运行一组诊断评测。
- 评测报告能展示每个场景是否命中根因和证据。
- 失败案例能反向指导 Prompt、工具和 Runbook 改进。

学习重点：

- AI 应用不能只靠肉眼看效果。
- 评测集是 Prompt 和 Agent 迭代的基础。
- 如何用工程方式减少幻觉。

面试可讲点：

- 我为 AI 诊断做了小型评测集，用固定场景评估根因、证据和建议质量。

### 阶段 12：前端控制台和演示体验

预计时间：4-7 天。

目标：让项目可被面试官快速理解，不需要靠大量口头解释。

要完成的页面：

- 故障列表。
- 故障详情。
- 故障注入面板。
- AI 诊断任务状态。
- 诊断过程时间线。
- 工具调用详情。
- 诊断报告展示。
- 复盘报告展示。

验收方式：

- 面试官 3 分钟内能看懂系统价值。
- 从注入故障到生成报告可以完整演示。
- 页面能清楚展示“后端任务 + AI Agent + 工具调用 + 证据引用”的链路。

学习重点：

- 前端如何呈现复杂后端过程。
- AI 应用为什么需要过程透明。
- 演示体验如何影响项目含金量。

面试可讲点：

- 控制台不是为了好看，而是为了展示系统诊断过程、工具调用证据和任务状态。

### 阶段 13：部署、文档和简历包装

预计时间：3-5 天。

目标：让项目达到 GitHub 作品集可展示状态。

要完成的内容：

- 后端 Dockerfile。
- AI 服务 Dockerfile。
- 前端构建。
- Nginx 反向代理。
- Docker Compose 编排所有服务。
- API 文档。
- 架构图。
- 演示脚本。
- 简历项目描述。
- 面试问答稿。

验收方式：

- 新机器拉取代码后，可以按照 README 启动基础环境。
- README 能解释架构、演示步骤和技术亮点。
- 简历描述能突出 Java 后端、AI Agent 和工程化能力。

学习重点：

- Docker Compose 如何管理多服务本地环境。
- Nginx 如何统一代理前端和后端接口。
- 项目展示不只是代码，还包括文档、演示和讲解。

面试可讲点：

- 我把项目按真实交付方式整理，包含部署、文档、演示脚本和架构说明。

## 必做与进阶取舍

如果时间有限，优先保证这些必做项：

```text
故障注入
观测数据查询
基础 AI 诊断
Runbook RAG
异步诊断任务
SSE 诊断过程推送
工具调用审计
Redis 缓存和限流
Resilience4j 超时和熔断
前端完整演示
```

如果时间充足，再做这些进阶项：

```text
Redis Stream 诊断事件流
OpenTelemetry TraceId 串联
Grafana Dashboard
AI 诊断质量评测集
模型调用成本统计
GitHub Actions 构建检查
Testcontainers 集成测试
```

## 项目盲点与防翻车清单

这部分用于每个阶段结束时自查。项目真正的风险不是技术不够多，而是范围失控、证据链不完整、AI 输出不可控，以及面试表达和真实实现不匹配。

### 盲点 1：范围膨胀

风险：

- 同时追求 Agent、RAG、Redis Stream、OpenTelemetry、Grafana、评测集和前端大屏，导致核心闭环迟迟做不完。

控制方式：

```text
必须做：故障注入、观测数据、AI 诊断、Runbook RAG、异步任务、SSE、Redis 限流缓存、工具调用审计。
加分做：Resilience4j、Prometheus、Grafana、AI 评测集。
展示即可：OpenTelemetry、Redis Stream、模型成本统计。
```

阶段验收时优先问：

- 这个功能是否服务于故障诊断闭环？
- 是否能被 curl、页面或数据库记录验证？
- 如果时间不够，它是否可以降级为文档设计或预留扩展点？

### 盲点 2：数据真实性不足

风险：

- 模拟数据互相对不上，导致项目看起来像写死的演示。

控制方式：

每个故障场景都必须统一这些字段：

```text
incidentId
traceId
serviceName
timestamp
errorMessage
metricSpike
runbookTitle
expectedRootCause
recommendedAction
```

验收标准：

- 日志、指标、链路、Runbook 能共同指向同一个根因。
- 面试官问“AI 为什么判断是这个根因”时，可以拿出完整证据链。

### 盲点 3：AI 输出不可控

风险：

- AI 只输出一段自然语言，看起来像简单调用大模型。

控制方式：

诊断报告必须结构化，至少包含：

```text
rootCause
confidence
evidences
recommendedActions
riskLevel
needHumanConfirmation
```

验收标准：

- 报告中的每条关键结论都能关联日志、指标、链路或 Runbook 证据。
- 证据不足时，Agent 要明确说明无法确定根因，而不是编造答案。

### 盲点 4：Java 和 Python 边界不清

风险：

- Python AI 服务绕过 Java 后端直接访问所有数据，削弱 Java 后端主线。

控制方式：

```text
Spring Boot：业务状态、任务编排、工具网关、审计、限流、熔断、接口边界。
Python AI Service：模型调用、RAG、Agent 工作流、结构化报告、评测。
```

验收标准：

- AI 工具调用必须经过 Spring Boot Tool Gateway。
- Python 服务不直接修改故障事件和诊断任务主状态。
- 数据库写入以 Java 后端为主要入口，AI 服务返回结构化结果。

### 盲点 5：测试和验收不足

风险：

- 文档写得很强，但每个能力缺少可验证方式。

控制方式：

每个核心能力都要留下一个可演示验证点：

```text
curl 能注入故障。
curl 能查观测数据。
curl 能创建诊断任务。
SSE 能看到过程事件。
数据库能查到诊断报告和工具调用记录。
Redis 能证明限流或缓存生效。
```

验收标准：

- 每个阶段结束前，至少有一个 curl、页面操作、数据库查询或日志输出作为证据。

### 盲点 6：安全和权限意识不足

风险：

- Agent 看起来能执行很多动作，但没有权限边界和人工确认机制。

控制方式：

- 模型不能直接执行危险操作。
- 工具调用必须经过 Tool Gateway。
- 重启、回滚、扩容等动作需要人工确认。
- 工具入参要校验。
- 工具结果要脱敏。

验收标准：

- `generateIncidentReport` 可以自动执行。
- `restartService`、`rollbackDeployment` 等危险动作只能生成建议或等待人工确认。

### 盲点 7：前端展示没有突出价值

风险：

- 前端只展示最终报告，无法体现后端任务和 Agent 过程。

控制方式：

前端必须优先展示：

```text
故障是什么。
AI 正在做什么。
调用了哪些工具。
证据来自哪里。
最终判断是什么。
建议怎么修。
```

验收标准：

- 诊断时间线是前端展示核心。
- 工具调用和证据引用不能只藏在后端日志里。

### 盲点 8：简历表达过满

风险：

- 简历写成“完整实现生产级 SRE 平台”，但实际只是本地模拟项目。

控制方式：

- 已实现的能力写“实现”。
- 只做了设计的能力写“设计”或“预留扩展”。
- 只接入基础指标的能力不要写成完整可观测性平台。

推荐表达：

```text
实现异步诊断任务、SSE 过程推送、工具调用审计和 Redis 缓存限流。
接入基础 Prometheus 指标，预留 OpenTelemetry TraceId 链路扩展。
设计 Redis Stream 诊断事件流，用于后续恢复诊断过程。
```

最终优先级：

```text
证据链
异步任务
工具审计
SSE 展示
```

这四件事做扎实后，再继续补监控、评测和部署增强。

## 当前下一步

当前正在进行：

```text
增强版本阶段 7：Function Calling 和工具调用审计
```

下一步应完成：

```text
沿用 queryLogs 的网关、校验、审计和客户端模式实现 queryMetrics
让 Python 诊断流程消费指标工具结果，并在失败时安全降级
继续扩展 queryTrace 和 searchRunbook
```

后续进入：

```text
增强版本阶段 8：Redis 工程化与稳定性保护
```
