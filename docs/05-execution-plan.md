# 执行规划

这份文档是 OpsMind AI 的实战推进计划。目标是在较短时间内做出能放进简历的 AI 应用项目，同时保证用户真正理解每一步。

## 总体节奏

推荐节奏：

```text
每天 2-3 小时：3-4 周完成可展示版本。
每天 4-5 小时：约 2 周完成可展示版本。
每天 6 小时以上：7-10 天完成 MVP。
```

项目推进原则：

```text
先做后端闭环。
再做 AI 能力。
再做前端展示。
最后做部署和简历包装。
```

不要一开始就追求完整大而全。每个阶段都要做到“能运行、能演示、能讲清楚”。

## 阶段 1：后端故障事件管理

目标：让系统能创建、保存、查询故障事件。

当前状态：

- 已完成 Spring Boot 后端骨架。
- 已完成 MySQL、Redis、Chroma 基础设施。
- 已完成 Incident 基础业务模块。

核心理解：

- `Controller` 接收 HTTP 请求。
- `Service` 放业务逻辑。
- `Repository` 负责数据库访问。
- `Entity / Model` 表示业务数据结构。

验收方式：

```bash
curl http://localhost:8080/api/incidents
```

## 阶段 2：模拟故障注入

目标：让系统可以通过预设场景一键生成故障事件。

要完成的内容：

- `FaultScenario`：故障场景模型。
- `FaultInjectionResponse`：故障注入返回对象。
- `FaultScenarioService`：维护场景列表并创建 Incident。
- `FaultScenarioController`：提供场景列表和注入接口。

核心接口：

```text
GET  /api/fault-scenarios
POST /api/fault-scenarios/{scenarioKey}/inject
```

学习重点：

- 一个模块如何调用另一个模块。
- 为什么 `fault` 不直接访问数据库，而是调用 `IncidentService`。
- 如何把手动创建故障变成可演示的产品动作。

## 阶段 3：模拟可观测性数据

目标：为每个故障场景准备日志、指标和链路追踪数据。

要完成的内容：

- 日志查询接口。
- 指标查询接口。
- 链路追踪查询接口。
- 每个故障场景绑定一组模拟观测数据。

核心接口示例：

```text
GET /api/observability/logs?serviceName=payment-service
GET /api/observability/metrics?serviceName=payment-service
GET /api/observability/traces/{traceId}
```

学习重点：

- 日志回答“发生了什么”。
- 指标回答“系统状态是否异常”。
- 链路追踪回答“请求卡在哪里”。

## 阶段 4：基础 AI 诊断

目标：让 AI 服务根据故障上下文生成诊断报告。

要完成的内容：

- Spring Boot 调用 Python AI 服务。
- Python FastAPI 接收故障上下文。
- AI 生成根因、证据和修复建议。
- 后端保存诊断报告。

学习重点：

- 后端服务之间如何调用。
- Prompt 如何组织上下文。
- 为什么诊断报告必须带证据。

## 阶段 5：Chroma + RAG 运维知识库

目标：让 AI 不只靠模型记忆，而是能检索运维手册和历史故障案例。

要完成的内容：

- 运维手册文档格式。
- 文档切分。
- 向量化并写入 Chroma。
- 根据故障现象检索相关 Runbook。
- 诊断报告引用知识来源。

学习重点：

- RAG 的流程：切分、向量化、存储、检索、生成。
- 为什么企业 AI 应用需要私有知识库。
- 如何减少大模型幻觉。

## 阶段 6：Function Calling 工具调用

目标：让 AI Agent 自己决定调用日志、指标、链路、知识库等工具。

要完成的工具：

```text
queryLogs()
queryMetrics()
queryTrace()
searchRunbook()
getRecentDeployments()
generateIncidentReport()
```

学习重点：

- Function Calling 是让模型调用后端函数，不只是输出文字。
- AI 先决定“要查什么”，系统再执行工具，最后 AI 综合结果。
- 这一步是普通聊天机器人和 AI Agent 的分界线。

## 阶段 7：Redis 工程化

目标：让 Redis 在项目中承担真实后端价值。

要完成的内容：

- 缓存重复诊断结果。
- 限制频繁诊断请求。
- 存储诊断任务状态。
- 可选：使用 Redis Stream 保存诊断过程事件。

学习重点：

- Redis 不只是缓存，也能做限流、状态存储和轻量队列。
- AI 调用慢且贵，因此缓存和任务状态很重要。

## 阶段 8：React 前端控制台

目标：把 curl 操作变成可视化产品界面。

要完成的页面：

- 故障列表。
- 故障详情。
- 故障注入按钮。
- AI 诊断过程时间线。
- 诊断报告展示。

学习重点：

- React 负责构建网页界面。
- 前端通过 HTTP 调用 Spring Boot 接口。
- 控制台让项目更容易被面试官看懂。

## 阶段 9：Nginx + Docker Compose 部署

目标：让整个系统可以一键启动和统一访问。

要完成的内容：

- 后端 Dockerfile。
- AI 服务 Dockerfile。
- 前端构建。
- Nginx 反向代理。
- Docker Compose 编排所有服务。

学习重点：

- Nginx 统一代理前端和后端接口。
- Docker Compose 管理多服务本地环境。
- 部署能力会显著增强项目工程感。

## 阶段 10：简历与面试包装

目标：把项目成果变成简历可写、面试可讲、GitHub 可展示的材料。

要完成的内容：

- README 完整化。
- 架构图。
- 演示步骤。
- API 文档。
- 简历项目描述。
- 面试问答稿。

学习重点：

- 简历不能只堆技术名词，要写清楚解决了什么问题。
- 面试重点讲架构选择、模块协作、AI 落地链路和工程取舍。

## 当前下一步

当前正在进行：

```text
阶段 2：模拟故障注入
```

下一步应完成：

```text
FaultScenarioService
FaultScenarioController
mvn test
curl 验证故障注入接口
Git 提交
```

