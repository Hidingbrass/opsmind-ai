# OpsMind AI

OpsMind AI 是一个面向微服务系统的 AI SRE 智能故障诊断平台。

这个项目用于展示如何把 `Spring Boot` 后端工程、Python AI Agent、可观测性数据、RAG、Function Calling、Redis 工程化、熔断限流和 Docker 部署组合成一个真实感较强的 AI 应用项目。

## 项目定位

OpsMind AI 不是普通聊天机器人，而是一个以后端系统为主线的 AI 应用项目：

- Java 后端负责故障事件、任务编排、工具网关、审计、缓存、限流、SSE 推送和系统稳定性。
- Python AI 服务负责多步骤 Agent 工作流、Runbook RAG、模型调用和结构化诊断报告生成。
- 前端控制台负责展示故障注入、诊断任务、工具调用过程、证据引用和最终报告。

项目面向 AI 应用开发岗位，同时兼顾 Java 后端工程能力和 Python AI 调用能力。

## 项目目标

构建一个平台，使它能够：

- 注入和管理模拟微服务故障。
- 分析日志、指标、链路追踪和发布记录。
- 从 `Chroma` 知识库中检索运维手册和历史故障案例。
- 通过 `Function Calling` 调用诊断工具。
- 以异步任务方式执行 AI 诊断。
- 通过 `SSE` 实时展示诊断过程。
- 记录诊断报告、工具调用和模型调用审计。
- 使用 `Redis` 完成缓存、限流、任务状态和事件流增强。
- 使用 `Resilience4j` 保护 AI 服务和下游工具调用。
- 使用 `Prometheus`、`Grafana` 和 `OpenTelemetry` 增强系统可观测性。

## 为什么做这个项目

AI 应用正在从简单聊天机器人，转向能够理解上下文、调用工具并执行任务的智能 Agent。SRE 和故障响应是很适合 AI Agent 落地的场景，因为生产系统会产生大量日志、指标、链路追踪和告警信息，而工程师需要快速定位根因。

这个项目的价值在于：它把 AI 能力放进一个真实后端工程场景中，而不是只做一个问答页面。面试时可以围绕后端架构、异步任务、工具网关、缓存限流、可观测性、RAG、Agent 工作流和工程取舍展开讨论。

## 核心技术栈

- 后端：`Spring Boot 3`
- 后端工程化：异步任务、`SSE`、`Resilience4j`、审计落库、OpenAPI
- AI 编排：Python `FastAPI`、`LangChain` / Agent 工作流
- AI 能力：`RAG`、`Function Calling`、结构化 JSON 报告、评测集
- 向量数据库：`Chroma`
- 缓存和事件：`Redis`、可选 `Redis Stream`
- 数据库：`MySQL`
- 可观测性：`Spring Boot Actuator`、`Micrometer`、`Prometheus`、`Grafana`、`OpenTelemetry`
- 网关：`Nginx`
- 部署：`Docker Compose`
- 前端：`React` 或 `Vue`

## 目标架构

```text
Frontend Dashboard
  -> Nginx
  -> Spring Boot Backend
       -> Incident / Fault / Observability API
       -> Diagnosis Task Orchestrator
       -> Tool Gateway
       -> SSE Event Publisher
       -> Audit Log
       -> Redis
       -> MySQL
       -> Resilience4j
  -> Python AI Agent Service
       -> Agent Workflow
       -> Tool Calling
       -> Runbook RAG
       -> Structured Diagnosis Report
       -> Evaluation Dataset
  -> Chroma
  -> Prometheus / Grafana / OpenTelemetry
```

## 目标演示流程

1. 使用 `Docker Compose` 启动全部服务。
2. 打开 OpsMind 控制台。
3. 注入一个故障，例如支付服务超时、Redis 连接异常或数据库慢查询。
4. 平台创建一条故障事件。
5. 用户点击“开始 AI 诊断”。
6. 后端创建异步诊断任务并返回 `taskId`。
7. 控制台通过 `SSE` 实时展示诊断过程。
8. AI Agent 调用工具查询日志、指标、链路追踪、运维手册和最近发布记录。
9. 后端记录工具调用审计、任务状态和模型调用耗时。
10. AI Agent 输出根因、证据、修复方案和事故复盘。

## 仓库结构

```text
opsmind-ai/
  backend-springboot/       Spring Boot 业务后端、任务编排和工具网关
  ai-agent-service/         Python AI Agent 服务，负责 RAG、工具调用和报告生成
  frontend-dashboard/       Web 控制台
  mock-microservices/       模拟订单、支付、库存等微服务
  nginx/                    反向代理配置
  docs/                     路线图、架构说明、简历材料
  docker-compose.yml        本地完整运行环境
```

## 交付路线

项目按三层推进：

```text
MVP 版本：故障注入 -> 观测数据 -> 基础 AI 诊断 -> Runbook RAG。
增强版本：异步任务 -> SSE -> Function Calling -> Redis 工程化 -> Resilience4j。
冲刺版本：Prometheus/Grafana -> OpenTelemetry -> AI 评测集 -> 前端演示 -> 部署和简历材料。
```

时间估算：

```text
每天 2-3 小时：5-8 周完成作品级版本。
每天 4-5 小时：3-5 周完成作品级版本。
每天 6 小时以上：2-3 周完成作品级版本。
```

详细执行计划见 [docs/05-execution-plan.md](docs/05-execution-plan.md)。

## 开发原则

每次只构建一个可见能力，保证项目持续可演示：

1. 先完成故障事件和故障注入。
2. 再准备日志、指标、链路追踪等证据数据。
3. 先做同步 AI 诊断，再升级为异步任务。
4. 先完成 Runbook RAG，再扩展多工具 Agent。
5. Redis、SSE、审计、熔断限流服务于真实工程问题。
6. 可观测性和评测集放在冲刺阶段，不阻塞 MVP。
7. 最后集中打磨前端演示、部署和简历表达。

## 本地基础设施启动

`docker-compose.yml` 已显式设置项目名为 `opsmind`，因此即使仓库目录包含中文，也可以直接启动：

```bash
docker compose up -d redis mysql chroma
```

检查服务状态：

```bash
docker compose ps
```

验证 Redis：

```bash
docker exec opsmind-redis redis-cli ping
```

验证 MySQL：

```bash
docker exec opsmind-mysql mysql -uopsmind -popsmind_password -e "SHOW DATABASES;"
```

验证 Chroma：

```bash
curl http://127.0.0.1:8001/api/v1/heartbeat
```
