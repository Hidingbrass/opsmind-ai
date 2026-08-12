# OpsMind AI 工程协作约定

本文件适用于整个仓库。它只保存长期有效的工程规则；当前分支、完成进度、临时任务和
一次性测试结果不得写入本文件。

## 事实与项目边界

- 先读 `README.md`、相关 `docs/`、源码、配置和测试，再修改代码。
- MySQL 是任务事实来源，Redis 是可降级加速层，SSE 是实时通知通道。
- 当前产品运行时是单 Agent；研发过程中的多 Agent 协作不得包装成产品能力。
- 当前观测数据和故障场景是可重复模拟数据；未接入真实供应商或生产系统时必须明确说明。
- `deterministic` 是无密钥默认模式；`LLM` 与 `LLM_FALLBACK` 的实现和验收结论必须分开描述。
- LLM 五类证据工具只有返回 `SUCCESS` 才算完成取证。
- 当前异步执行器是单实例进程内执行。服务启动会把遗留的 `PENDING/RUNNING` 任务终止为
  `FAILED`，不会自动重放，也不得声称具备分布式任务恢复能力。

## 开始工作

1. 运行 `git status --short --branch`，确认基线和用户已有修改。
2. 定位入口、调用方、数据合同、异常路径和相关测试。
3. 写清目标、非目标、允许修改的文件和验收命令。
4. 优先增加能复现问题的失败测试，再做最小实现。

默认采用单任务、单写入者。只有用户显式设置 `MULTI_TASK=ON` 时，才拆分互不依赖且文件
所有权不重叠的子任务；主任务负责合同、集成、回归和事实口径。子任务的“完成”不等于
项目完成。

## 修改约束

- 保留用户未提交修改，不覆盖、回滚或清理无关文件。
- 沿用现有 Spring Boot、FastAPI、React 分层与 DTO 合同，不为小改动新增依赖或抽象层。
- 不弱化测试、白名单、Trace/Incident 归属校验、结构化输出或显式 fallback 状态。
- 同一任务允许多个 SSE 订阅者；单条连接失败不得影响其他订阅者或诊断主流程。
- 数据库状态必须先持久化，再更新 Redis，最后发送 SSE。
- 不提交密钥、真实凭证或包含敏感值的日志。
- 未经明确授权，不提交 Git、不推送、不发布，也不执行 `docker compose down -v` 等数据删除操作。

## 验证与完成定义

按改动范围先跑定向测试，再运行统一验证：

```bash
./scripts/verify.sh
```

`verify.sh` 只覆盖 Compose 静态校验、Java/Python 测试和 React 构建，不等于整栈运行验收。
服务实际启动后再运行：

```bash
./scripts/smoke_http.py --output evaluation/results
ai-agent-service/.venv/bin/python evaluation/run_rag_evaluation.py \
  --output evaluation/results/rag_report.json
```

完成报告必须区分已实现、已在当前检出验证、历史证据和未验证风险。没有运行的检查直接写
“未验证”，不得复用旧结果冒充当前结果。
