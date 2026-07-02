# OpsMind AI

OpsMind AI 是一个面向微服务系统的 AI SRE 智能故障诊断平台。

这个项目用于展示如何把 `Spring Boot`、`Redis`、`LangChain`、`Chroma`、`Nginx`、可观测性数据、`RAG` 和 `Function Calling` 组合成一个真实感较强的 AI 应用项目。

## 项目目标

构建一个平台，使它能够：

- 采集和管理模拟微服务故障。
- 分析日志、指标、链路追踪和发布记录。
- 从 `Chroma` 知识库中检索运维手册和历史故障案例。
- 通过 `Function Calling` 调用诊断工具。
- 生成根因分析、修复建议和事故复盘报告。

## 为什么做这个项目

AI 应用正在从简单聊天机器人，转向能够理解上下文并执行任务的智能 Agent。SRE 和故障响应是很适合 AI Agent 落地的场景，因为生产系统会产生大量日志、指标、链路追踪和告警信息，而工程师需要快速定位根因。

这个项目的定位是一个适合写进 AI 应用开发简历的作品级平台。它不是要替代真实生产环境中的 SRE 系统，而是用可控的模拟场景展示完整的 AI 工程能力。

## 核心技术栈

- 后端：`Spring Boot 3`
- AI 编排：`LangChain4j` 和 Python `LangChain`
- 向量数据库：`Chroma`
- 缓存和异步状态：`Redis`
- 网关：`Nginx`
- 可观测性：`OpenTelemetry` 概念、Prometheus 风格指标、结构化日志、模拟链路追踪
- 部署：`Docker Compose`
- 前端：`React` 或 `Vue`

## 目标演示流程

1. 使用 `Docker Compose` 启动全部服务。
2. 打开 OpsMind 控制台。
3. 注入一个故障，例如支付服务超时、Redis 连接异常或数据库慢查询。
4. 平台创建一条故障事件。
5. AI Agent 调用工具查询日志、指标、链路追踪、运维手册和最近发布记录。
6. 控制台实时展示诊断过程。
7. AI Agent 输出根因、证据、修复方案和事故复盘。

## 仓库结构

```text
opsmind-ai/
  backend-springboot/       Spring Boot 业务后端和 AI 工具网关
  ai-agent-service/         Python LangChain 服务，负责 RAG 和 Agent 工作流
  frontend-dashboard/       Web 控制台
  mock-microservices/       模拟订单、支付、库存等微服务
  nginx/                    反向代理配置
  docs/                     路线图、架构说明、简历材料
  docker-compose.yml        本地完整运行环境
```

## 开发原则

每次只构建一个可见能力，保证项目持续可演示：

1. 故障事件数据模型和 API。
2. 模拟微服务故障注入。
3. 不带 RAG 的基础 AI 诊断。
4. 基于 `Chroma` 的运维手册 RAG。
5. `Function Calling` 工具调用链路。
6. `Redis` 缓存、限流和异步状态。
7. `Nginx` 与 `Docker Compose` 部署。
8. 前端控制台和最终演示打磨。

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
