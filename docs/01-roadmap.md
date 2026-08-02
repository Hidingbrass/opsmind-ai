# 开发路线图

更新时间：2026-07-25。

这份文档记录 OpsMind AI 从 MVP 到作品级版本的实际完成状态。实现证据以代码、
自动化测试、三场景评测报告和运行时验收为准。

## 状态总览

| 阶段 | 状态 | 主要验收证据 |
| --- | --- | --- |
| 基础设施与项目骨架 | 已完成 | MySQL、Redis、Chroma、Spring Boot、FastAPI |
| 故障事件与注入 | 已完成 | 三种场景均能创建独立 Incident |
| 模拟可观测数据 | 已完成 | 日志、指标、Trace、发布记录 |
| 同步诊断与报告落库 | 已完成 | `/ai/diagnose` 与 Spring 诊断接口 |
| Runbook RAG | 已完成 | 三份 Runbook、Chroma、中文 embedding、来源引用 |
| 异步诊断任务 | 已完成 | taskId、状态机、成功报告 id、失败原因 |
| SSE 过程推送 | 已完成 | 过程事件、终态快照、自动关闭 |
| 多工具工作流与审计 | 已完成 | 六个白名单工具、成功/失败审计 |
| Redis 工程化 | 已完成 | 缓存、结果复用、限流、分布式去重锁 |
| Resilience4j | 已完成 | 超时、重试、熔断、Bulkhead、fallback |
| Prometheus/Grafana | 已完成 | 业务指标、Dashboard 和数据源配置 |
| OpenTelemetry/Tempo | 已完成 | Docker 环境中按新任务 traceId 查询到完整链路 |
| 三场景自动评测 | 已完成 | `evaluation/results/report.md` 为 3/3 PASS |
| React 控制台 | 已完成 | 注入、SSE、审计、报告、复盘、刷新恢复 |
| Docker 与交付文档 | 已完成 | 10 个服务启动、健康检查、README、OpenAPI |

## MVP 版本

### 1. 项目骨架和基础设施

已完成：

- Spring Boot 3.3 / Java 21 后端。
- Python 3.11 / FastAPI 诊断服务。
- MySQL、Redis、Chroma。
- 统一 JSON 响应和全局异常处理。
- `/api/health`、`/ai/health` 和 Actuator。

### 2. 三种故障场景

已完成：

- `payment-timeout`
- `redis-connection-failure`
- `database-slow-query`

每个场景均具有故障事件、日志、指标、Trace、发布记录、Runbook、预期根因和处置建议。

### 3. RAG 诊断闭环

已完成：

- Markdown Runbook 切片和导入脚本。
- `BAAI/bge-small-zh-v1.5` 中文向量。
- Chroma 本地持久化与容器服务两种连接方式。
- `/ai/runbooks/search` 检索接口。
- 诊断证据保留 `knowledge/runbooks/*.md` 来源。
- Spring Boot 保存结构化诊断记录。

## 增强版本

### 4. 异步任务和 SSE

已完成：

- 创建任务立即返回 `taskId`。
- 状态机：`PENDING / RUNNING / SUCCESS / FAILED`。
- 数据库先保存状态，再向前端推送事件。
- SSE 订阅时立即发送当前快照，避免快速任务丢失终态。
- 成功保存 `diagnosisRecordId`，失败保存可读 `failureReason`。
- 页面刷新后按 `incidentId` 恢复最近任务、报告和审计。

### 5. Tool Gateway

已完成六个工具：

```text
queryLogs
queryMetrics
queryTrace
searchRunbook
getRecentDeployments
generateIncidentReport
```

边界：

- Python 不直接访问 MySQL 或观测服务。
- 所有工具先校验 `taskId` 与 `incidentId` 归属。
- 精确白名单分发，不允许反射执行任意方法。
- 成功只保存结果摘要，失败保存原因。
- 对外审计 DTO 隐藏原始请求参数。

当前默认采用确定性工具调用顺序，不声称外部大模型自主选择工具。

### 6. Redis 和稳定性

已完成：

- 任务状态缓存 1 小时。
- 同一故障成功任务 10 分钟复用。
- 分布式创建锁，封住并发重复任务窗口。
- 固定窗口限流，默认每客户端每分钟 10 次。
- Redis 故障时回源 MySQL或 fail-open，避免缓存变成单点。
- AI 服务调用具有连接/读取超时、2 次有限重试、熔断和并发隔离。
- 限流返回 HTTP 429，并发冲突返回 HTTP 409。

## 冲刺版本

### 7. 可观测性

已完成并通过运行时验收：

- Actuator 与 Prometheus。
- 任务、工具、AI 调用数量和耗时指标。
- 固定低基数标签，避免把 taskId 写入 Prometheus。
- Grafana 自动配置 Prometheus、Tempo 和 OpsMind Dashboard。
- W3C Trace Context 从 HTTP 请求传播到异步线程和 Python 工具回调。
- `traceId` 保存到任务、报告、工具审计和 AI 审计。
- OpenTelemetry Collector 接收 OTLP 并输出到 Tempo。

运行时已经验证相同 `traceId` 出现在任务、报告、工具审计和 AI 审计中；全量
Compose 环境中也能按新任务 `traceId` 从 Tempo 查询到入口请求、AI 调用、
五次取证工具回调和 Runbook 检索 Span。

### 8. 质量评测

已完成：

- 固定三场景数据集。
- 真实 HTTP 注入、异步等待、报告查询和事故复盘。
- 根因、证据、建议、六工具覆盖和复盘一致性五项评分。
- JSON 和 Markdown 双格式报告。
- 当前结果 `3/3 PASS`。

### 9. 前端和部署

已完成：

- React + Vite 运维控制台。
- 桌面端和 390px 移动端布局检查。
- 无水平溢出，按钮文字不越界。
- SSE 时间线、工具与 AI 审计、TraceId、诊断报告和事故复盘。
- 三个 Dockerfile、Nginx 代理和完整 Compose。
- MySQL、Redis、Chroma、AI、后端健康检查和启动依赖。

## 最终验收清单

- [x] 三种故障均有一致的多源证据。
- [x] RAG 检索命中对应 Runbook。
- [x] 异步任务成功和失败均能落库。
- [x] SSE 终态与数据库一致。
- [x] 六个工具均经过 Tool Gateway 和审计。
- [x] Redis 结果复用、限流和去重已实现。
- [x] Prometheus 指标、TraceId 和 OpenAPI 已实现。
- [x] 三场景评测 100% 通过。
- [x] React 控制台完成真实浏览器验收。
- [x] 完整 Compose 镜像构建、Tempo 查询和容器端到端最终复验。

最终验收清单已经全部通过，项目达到当前规划的作品级版本交付标准。
