"""Java 后端与 Python AI 服务之间的 Pydantic JSON 合同。"""

from datetime import datetime
from typing import Any, Literal, Optional

from pydantic import BaseModel, Field


class IncidentPayload(BaseModel):
    """标识 AI 应诊断对象的故障事件上下文。"""

    id: str  # 故障事件唯一 id。
    title: str  # 故障标题。
    serviceName: str  # 故障主要所属服务。
    severity: str  # Java 枚举序列化得到的严重程度。
    status: str  # Java 枚举序列化得到的事件状态。
    symptom: str  # 用户或监控观察到的故障现象。
    # 使用 datetime 同时兼容 Java 发送的 Unix 时间戳和 ISO-8601 字符串。
    createdAt: datetime  # 创建时间。
    updatedAt: datetime  # 最后更新时间。


class LogEntryPayload(BaseModel):
    """可观测模块提供的结构化日志证据。"""

    timestamp: datetime  # 日志时间。
    serviceName: str  # 产生日志的服务。
    level: str  # INFO、WARN 或 ERROR。
    traceId: str  # 关联分布式链路的 id。
    message: str  # 日志正文。


class MetricPointPayload(BaseModel):
    """用于量化异常程度的单个监控指标样本。"""

    timestamp: datetime  # 采样时间。
    serviceName: str  # 指标所属服务。
    metricName: str  # 指标名。
    value: float  # 指标数值。
    unit: str  # ms、percent 或 count 等单位。


class TraceSpanPayload(BaseModel):
    """分布式请求链路中的单个节点。"""

    traceId: str  # 整条调用链 id。
    spanId: str  # 当前节点 id。
    parentSpanId: Optional[str] = None  # 父节点 id，根节点为 None。
    serviceName: str  # 执行当前节点的服务。
    operationName: str  # HTTP、SQL 或其他操作名。
    durationMs: int  # 节点耗时，单位毫秒。
    status: str  # 节点状态。
    errorMessage: Optional[str] = None  # 节点失败原因。


class DeploymentPayload(BaseModel):
    """发布平台返回的最近部署记录。"""

    deployedAt: datetime  # 发布时间。
    serviceName: str  # 发布服务。
    version: str  # 应用版本。
    commitId: str  # 源码提交短 id。
    operator: str  # 发布人或自动化账号。
    status: str  # 发布状态。
    summary: str  # 变更摘要。


class DiagnosisRequest(BaseModel):
    """Spring Boot 为一次诊断发送的完整任务与证据上下文。"""

    taskId: Optional[str] = None  # 异步诊断任务 id；旧同步诊断请求中允许为空。
    traceId: Optional[str] = None  # Spring 入口创建的 OpenTelemetry traceId。
    incident: IncidentPayload  # 待诊断故障。
    logs: list[LogEntryPayload]  # 与故障服务相关的日志。
    metrics: list[MetricPointPayload]  # 与故障服务相关的指标。
    traces: list[TraceSpanPayload]  # 由日志 traceId 延伸得到的链路。


class ToolExecutionResult(BaseModel):
    """从 Spring 统一响应中提取、供诊断流程消费的内层工具结果。"""

    toolName: str  # 实际尝试执行的工具名。
    status: Literal["SUCCESS", "FAILED"]  # 工具级状态，不是 Spring 外层 Result.code。
    data: Any = None  # 成功时的结构化工具结果，失败时通常为 None。
    errorMessage: Optional[str] = None  # 失败原因，成功时为 None。
    latencyMs: int  # Spring Tool Gateway 记录的执行耗时。


class AgentExecutionMetadata(BaseModel):
    """描述报告由哪一种诊断器生成，避免把确定性流程包装成外部模型。"""

    executionMode: Literal["DETERMINISTIC", "LLM", "LLM_FALLBACK"] = (
        "DETERMINISTIC"
    )
    provider: str = "opsmind"
    modelName: str = "deterministic-rag-agent"
    promptVersion: str = "deterministic-v1"
    inputTokens: int = Field(default=0, ge=0)
    outputTokens: int = Field(default=0, ge=0)
    toolCallCount: int = Field(default=0, ge=0)


class DiagnosisReport(BaseModel):
    """返回给 Java 并保存为 DiagnosisRecord 的结构化诊断报告。"""

    incidentId: str  # 报告所属故障 id。
    traceId: Optional[str] = None  # 串联任务、工具、AI 调用和报告的链路 id。
    summary: str  # 面向用户的诊断摘要。
    rootCause: str  # 根因判断。
    evidence: list[str]  # 支撑结论的证据和 Runbook 来源。
    recommendation: str  # 排查或修复建议。
    confidence: float = Field(ge=0, le=1)  # 0 到 1 之间的置信度。
    agentMetadata: AgentExecutionMetadata = Field(
        default_factory=AgentExecutionMetadata
    )
