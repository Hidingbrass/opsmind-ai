# 开发路线图

更新时间：2026-08-02。

这份文档记录 OpsMind AI 的实际完成状态和后续优先级。完成状态必须同时有代码、
自动验证或运行时证据；“计划接入”和“已经实现”不得写在同一列。

## 状态总览

| 能力 | 状态 | 主要验收证据 |
| --- | --- | --- |
| 项目骨架与 10 服务 Compose | 已完成 | Compose 配置、健康检查和整栈 smoke |
| 三种故障与模拟观测数据 | 已完成 | Incident、日志、指标、Trace、发布记录 |
| 异步诊断任务与 SSE | 已完成 | taskId、状态机、过程事件、刷新恢复 |
| Spring Tool Gateway | 已完成 | 六个白名单工具、双层参数/归属校验、审计 |
| Redis 与 Resilience4j | 已完成 | 缓存、复用、限流、去重、重试、熔断、Bulkhead |
| 确定性多工具诊断 | 已完成并整栈验收 | 三场景 `3/3 PASS` |
| OpenAI-compatible LLM Agent | 已实现，待真实供应商验收 | 正常循环、越权、注入和 fallback 单测 |
| 混合 Runbook RAG | 已完成 | 9 份 Runbook、45 个片段、Dense + BM25 + RRF |
| RAG 排名评测 | 已完成 | 24 条查询，Hit@1 / Hit@3 / MRR 均为 1.00 |
| 模型与 Prompt 审计 | 已完成 | 模式、供应商、模型、Prompt、Token、模型工具数 |
| Prometheus/Grafana/OTel/Tempo | 已完成 | 指标、Dashboard、跨服务 traceId |
| React 控制台 | 已完成 | 1440x1000 / 390x844 浏览器验收 |
| CI 与本地验证入口 | 已完成 | GitHub Actions、`verify.sh`、`smoke_http.py` |
| 生产数据源、RBAC、多租户 | 未进入当前版本 | 未来生产化方向 |

## v1.0 作品级闭环

v1.0 建立了可以独立演示的完整主链：

```text
故障注入 -> Incident -> 异步任务 -> SSE -> 多工具取证
         -> Runbook 检索 -> 诊断报告 -> 工具审计 -> 事故复盘
```

已完成：

- Spring Boot 3.3 / Java 21 控制面与 FastAPI/Python 3.11 诊断服务。
- MySQL、Redis、Chroma、Nginx、Prometheus、Grafana、OTel、Tempo。
- `PENDING / RUNNING / SUCCESS / FAILED` 状态机。
- SSE 初始快照、过程事件、终态和自动关闭。
- 六个 Tool Gateway 工具：
  `queryLogs`、`queryMetrics`、`queryTrace`、`searchRunbook`、
  `getRecentDeployments`、`generateIncidentReport`。
- 任务缓存、10 分钟结果复用、固定窗口限流和分布式去重锁。
- HTTP 超时、有限重试、熔断、Bulkhead 和友好失败边界。
- 任务、工具、AI 调用和诊断报告的 MySQL 审计。
- W3C Trace Context、Micrometer、Prometheus、Grafana 和 Tempo。
- React 运维控制台与 Docker Compose 一键启动。

## v1.1 AI 应用深度

v1.1 解决“工程链路完整，但 AI 应用深度不够”的问题。

### 双执行模式

- 默认 `deterministic` 继续承担无密钥演示和回归基线。
- 新增 `llm` 模式，直接调用兼容 `/chat/completions` 的模型服务。
- 模型可自主选择五个只读取证工具，但必须完成最低证据覆盖。
- 最多 6 轮、最多 18 次模型工具调用，最终输出通过 Pydantic 合同校验。
- 模型失败时按配置进入显式 `LLM_FALLBACK`。

### Agent 安全与审计

- 未知工具在 Python 边界拒绝。
- `serviceName` 强制绑定 Incident，Trace 必须来自当前证据。
- Runbook 参数、工具结果长度和最终证据长度均有限制。
- 工具输出按不可信数据处理，不能把其中的文字当作新指令。
- Spring Tool Gateway 再次校验任务、故障、服务、Trace 和白名单。
- 报告和 AI 审计贯通执行模式、模型、Prompt、Token 和模型工具调用数。
- Prometheus 只保留低基数模式标签，模型名留在数据库审计。

### 混合检索

- Runbook 从 3 份扩为 9 份，引入 6 个长尾干扰场景。
- 中文 embedding + Chroma 负责 Dense 召回。
- 中文 unigram/bigram 与英文词元 BM25 负责精确术语匹配。
- RRF 融合排名，并返回检索模式、Dense/BM25 排名和分数。
- 24 条中英文查询形成可失败退出的 RAG 回归门槛。

### 交付自动化

- GitHub Actions 覆盖 Compose、Java 21、Python 3.11 和 Node 22。
- `scripts/verify.sh` 作为本地与 CI 对齐的快速入口。
- `scripts/smoke_http.py` 检查健康并执行三场景真实 HTTP 评测。
- 后端 Docker 构建采用“实际 package 层产物 + Maven 仓库缓存”，支持可选镜像源。

## 2026-08-02 验收快照

| 验收 | 结果 |
| --- | --- |
| Compose 静态校验 | PASS |
| Java 测试 | 16 tests，0 failure |
| Python 测试 | 24 tests，0 failure |
| React 生产构建 | PASS |
| 后端容器构建 | PASS；修复冷缓存依赖问题后 97 秒产出镜像 |
| 完整服务健康 | 10 个服务启动，核心依赖健康 |
| 三场景 HTTP 评测 | 3/3 PASS，五个评分维度全部通过 |
| RAG 排名评测 | 24/24 首位命中，三指标 1.00 |
| 浏览器 | 桌面/手机无横向溢出，0 console error，移动导航可用 |

后端镜像时间是本机当次使用显式 Maven 镜像和已有部分缓存的结果，不是跨环境 SLA。

## 当前边界

已完成整栈验收的是默认确定性模式。LLM 运行时已通过模拟模型响应验证，但仓库不含
外部 API Key，本轮没有对具体供应商做真实调用。因此：

- 可以说“实现可插拔 OpenAI-compatible Tool Calling 运行时”。
- 不可以说“某真实模型已上线”或引用尚未采集的真实模型成功率、Token 和成本。
- 可以说“RAG 回归集 24 条查询全部首位命中”。
- 不可以把这个小型策划数据集表述为生产准确率 100%。

## 后续路线

### P0：按目标岗位完成真实模型验收

- 选择一个工具调用兼容模型。
- 关闭 fallback 分别运行三个场景，确认 `executionMode=LLM`。
- 记录成功率、P50/P95、Token、单任务成本和失败类型。
- 再开启 fallback 模拟超时，确认降级可审计。

### P1：真实数据适配与生产权限

- 为日志、指标、Trace 和发布记录定义 Adapter 接口。
- 接入至少一个真实只读数据源。
- 增加身份、租户、RBAC、脱敏和高风险动作审批。

### P1：扩大评测可信度

- 增加同义改写、拼写错误、证据缺失和相似故障样本。
- 分开衡量检索、事实一致性、工具选择和端到端成功率。
- 保存版本化基线与回归趋势。

### P2：按岗位选择性增强

- MCP：只有目标岗位明确要求工具协议时再做。
- Reranker：只有扩充语料后 Dense/BM25/RRF 出现可测瓶颈时再做。
- 多 Agent：只有真实诊断任务需要角色分工时再做，不为名词堆叠。
- Kubernetes：只有要证明部署和水平扩展时再进入范围。

## 最终验收清单

- [x] 三种故障具有一致的多源证据。
- [x] 异步任务、SSE、数据库和 Redis 状态可恢复。
- [x] 六个工具经过 Tool Gateway 与审计。
- [x] Agent 模式、模型、Prompt、Token 和降级可追踪。
- [x] 混合 RAG 有干扰文档、数据集、指标和门槛。
- [x] Redis、Resilience4j 和 Tool Gateway 失败边界已测试。
- [x] Prometheus、Grafana、OpenTelemetry、Tempo 已接通。
- [x] 统一验证、整栈 smoke 和浏览器验收通过。
- [x] README、架构、复盘和简历口径与代码一致。
- [ ] 使用真实模型供应商完成可选 LLM 模式验收。

项目已达到当前作品级交付标准；最后一项是岗位定向增强，不影响默认模式演示。
