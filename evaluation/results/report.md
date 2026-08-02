# OpsMind AI 诊断评测报告

- 生成时间：2026-07-24T18:40:18.019434+00:00
- 通过：3/3
- 通过率：100%

| 场景 | 结果 | 根因 | 证据 | 建议 | 工具覆盖 | 复盘 | 置信度 | 延迟 |
| --- | --- | --- | --- | --- | --- | --- | ---: | ---: |
| payment-timeout | PASS | PASS | PASS | PASS | PASS | PASS | 0.86 | 8270ms |
| redis-connection-failure | PASS | PASS | PASS | PASS | PASS | PASS | 0.84 | 549ms |
| database-slow-query | PASS | PASS | PASS | PASS | PASS | PASS | 0.85 | 551ms |

## 工具调用

- `payment-timeout`：queryLogs -> queryMetrics -> queryTrace -> getRecentDeployments -> searchRunbook -> generateIncidentReport
- `redis-connection-failure`：queryLogs -> queryMetrics -> queryTrace -> getRecentDeployments -> searchRunbook -> generateIncidentReport
- `database-slow-query`：queryLogs -> queryMetrics -> queryTrace -> queryTrace -> getRecentDeployments -> searchRunbook -> generateIncidentReport

> 首个场景延迟包含中文向量模型冷启动；后续场景复用进程内模型缓存。
