# 学习地图

你不需要在开始前掌握很深的 SRE 知识。这个项目的学习方式是：只学项目真正用得到的内容。

## 必要的 SRE 知识

### 日志

要能回答：

- 发生了什么？
- 哪个服务报错了？
- 出现了什么错误信息或异常？

项目中的用法：

- 存储结构化模拟日志。
- 让 AI 按服务名和关键词查询日志。

### 指标

要能回答：

- 接口延迟是否升高？
- 错误率是否升高？
- CPU 或内存是否异常？

项目中的用法：

- 创建 Prometheus 风格的模拟指标。
- 让 AI 对比正常值和异常值。

### 链路追踪

要能回答：

- 哪个服务调用最慢？
- 哪个下游服务导致请求失败？

项目中的用法：

- 为每个故障创建一个简单的调用链。
- 让 AI 定位最慢或失败的调用节点。

### 运维手册

要能回答：

- 出现这个现象时，工程师应该检查什么？
- 常见原因有哪些？
- 允许执行哪些修复动作？

项目中的用法：

- 把运维手册存入 Chroma。
- 使用 RAG 检索相关排查步骤。

## 后端知识

必须掌握：

- Spring Boot REST API。
- 分层架构。
- DTO 和实体分离。
- RedisTemplate 或 Spring Cache。
- SSE 流式输出。
- Docker Compose 基础。

可以后续补充：

- Spring Security 和 JWT。
- OpenAPI 文档。
- Testcontainers。

## AI 知识

必须掌握：

- Prompt 模板。
- RAG 流程：切分、向量化、存储、检索、生成。
- Tool Calling / Function Calling。
- 基于证据减少幻觉。

可以后续补充：

- Agent 记忆。
- 多步骤规划。
- RAG 效果评估指标。

## 建议学习顺序

1. Spring Boot CRUD 和分层架构。
2. Redis 缓存和限流基础。
3. 日志、指标、链路追踪分别是什么。
4. 使用 Chroma 做 RAG。
5. Function Calling。
6. SSE 流式输出。
7. Docker Compose 和 Nginx。

