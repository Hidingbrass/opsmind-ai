# OpsMind AI 诊断评测报告

- 生成时间：2026-08-12T09:19:18.932784+00:00
- 通过：3/3
- 通过率：100%

| 场景 | 结果 | 模式 | 模型 | 根因 | 证据 | 建议 | 工具覆盖 | 复盘 | Token | 置信度 | 延迟 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: | ---: |
| payment-timeout | PASS | DETERMINISTIC | deterministic-rag-agent | PASS | PASS | PASS | PASS | PASS | 0 | 0.86 | 608ms |
| redis-connection-failure | PASS | DETERMINISTIC | deterministic-rag-agent | PASS | PASS | PASS | PASS | PASS | 0 | 0.84 | 549ms |
| database-slow-query | PASS | DETERMINISTIC | deterministic-rag-agent | PASS | PASS | PASS | PASS | PASS | 0 | 0.85 | 566ms |

## 工具调用

- `payment-timeout`：queryLogs -> queryMetrics -> queryTrace -> getRecentDeployments -> searchRunbook -> generateIncidentReport
- `redis-connection-failure`：queryLogs -> queryMetrics -> queryTrace -> getRecentDeployments -> searchRunbook -> generateIncidentReport
- `database-slow-query`：queryLogs -> queryMetrics -> queryTrace -> queryTrace -> getRecentDeployments -> searchRunbook -> generateIncidentReport

> 若 AI 服务刚启动，首个场景延迟还会包含中文向量模型冷启动；后续请求复用进程内模型缓存。
