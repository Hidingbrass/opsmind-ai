# OpsMind AI 项目蓝图

## 一句话介绍

OpsMind AI 是一个 AI 驱动的 SRE 故障诊断助手，它通过可观测性数据、RAG 知识检索和函数调用，诊断模拟微服务系统中的故障。

## 简历定位

这个项目不是普通聊天机器人，而是一个偏工程落地的 AI 应用项目，可以展示：

- 使用 `Spring Boot` 进行后端系统设计。
- 设计 Agent 式 AI 工作流。
- 使用 `Chroma` 构建 RAG 知识库。
- 使用 `Function Calling` 进行工具调用。
- 在真实后端场景中使用 `Redis`。
- 使用 `Nginx` 和 `Docker Compose` 完成部署。
- 具备微服务和可观测性基础认知。

## MVP 范围

MVP 要证明一个完整的故障诊断闭环：

1. 用户向模拟服务注入故障。
2. 后端创建故障事件。
3. AI Agent 接收故障上下文。
4. AI Agent 调用诊断工具。
5. AI Agent 从知识库中检索相关运维手册。
6. AI Agent 生成诊断报告。
7. 前端展示诊断过程和结果。

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
- 匹配的运维手册。
- 预期根因。
- 推荐修复方式。

## 系统模块

### Spring Boot 后端

职责：

- 故障事件增删改查。
- 故障注入 API。
- 诊断工具 API。
- AI 任务编排。
- Redis 集成。
- 通过 SSE 向前端流式输出诊断过程。
- 如果时间充足，后续可以加入登录鉴权。

### AI Agent 服务

职责：

- RAG 文档导入。
- Chroma 向量检索。
- Prompt 编排。
- 根因推理。
- 报告生成。

### 模拟微服务

职责：

- 订单服务。
- 支付服务。
- 库存服务。
- 模拟故障模式。
- 输出结构化日志和指标接口。

### 前端控制台

职责：

- 故障事件列表。
- 故障注入面板。
- 诊断过程时间线。
- 工具调用可视化。
- 最终报告展示。

### 基础设施

职责：

- Nginx 反向代理。
- Redis。
- Chroma。
- MySQL 或 PostgreSQL。
- Docker Compose 一键启动。

## Function Calling 工具

模型应该能够调用这些工具：

```text
query_logs(service_name, keyword, time_range)
query_metrics(service_name, metric_name, time_range)
query_trace(trace_id)
search_runbook(problem_description)
get_recent_deployments(service_name)
recommend_action(incident_id)
generate_incident_report(incident_id)
```

## Redis 的使用方式

Redis 不能只是为了堆技术名词，而要承担真实后端价值：

- 缓存 RAG 检索结果。
- 存储 AI 诊断任务状态。
- 限制诊断请求频率。
- 对重复故障分析进行去重。
- 可选：使用 Redis Stream 传递异步诊断事件。

## Chroma 的使用方式

Chroma 用于存储：

- 运维手册。
- 历史故障案例。
- 服务依赖说明。
- 故障排查指南。

每个被检索出的文本片段都应包含来源元数据，这样最终诊断报告可以引用证据来源。

## 最终演示脚本

1. 打开控制台。
2. 点击“注入支付服务超时”。
3. 页面出现一条高优先级故障事件。
4. 点击“开始 AI 诊断”。
5. 时间线依次展示：
   - 查询指标。
   - 查询日志。
   - 查询链路追踪。
   - 检索运维手册。
   - 生成根因分析。
6. 最终报告指出订单结算延迟是由支付服务超时导致的。
7. 点击“生成复盘”。
8. 展示事故复盘报告。

