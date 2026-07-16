"""Java 后端与 Python AI 服务之间的 Pydantic JSON 合同。"""

from typing import Optional

from pydantic import BaseModel


class IncidentPayload(BaseModel):
    """Fault event context that identifies what the AI should diagnose."""

    id: str  # 故障事件唯一 id。
    title: str  # 故障标题。
    serviceName: str  # 故障主要所属服务。
    severity: str  # Java 枚举序列化得到的严重程度。
    status: str  # Java 枚举序列化得到的事件状态。
    symptom: str  # 用户或监控观察到的故障现象。
    createdAt: str  # ISO-8601 创建时间。
    updatedAt: str  # ISO-8601 最后更新时间。


class LogEntryPayload(BaseModel):
    """Structured log evidence supplied by the observability module."""

    timestamp: str  # 日志时间。
    serviceName: str  # 产生日志的服务。
    level: str  # INFO、WARN 或 ERROR。
    traceId: str  # 关联分布式链路的 id。
    message: str  # 日志正文。


class MetricPointPayload(BaseModel):
    """Single monitoring metric sample used to quantify an anomaly."""

    timestamp: str  # 采样时间。
    serviceName: str  # 指标所属服务。
    metricName: str  # 指标名。
    value: float  # 指标数值。
    unit: str  # ms、percent 或 count 等单位。


class TraceSpanPayload(BaseModel):
    """One node in a distributed request trace."""

    traceId: str  # 整条调用链 id。
    spanId: str  # 当前节点 id。
    parentSpanId: Optional[str] = None  # 父节点 id，根节点为 None。
    serviceName: str  # 执行当前节点的服务。
    operationName: str  # HTTP、SQL 或其他操作名。
    durationMs: int  # 节点耗时，单位毫秒。
    status: str  # 节点状态。
    errorMessage: Optional[str] = None  # 节点失败原因。


class DiagnosisRequest(BaseModel):
    """Complete evidence bundle sent by Spring Boot for one diagnosis."""

    incident: IncidentPayload  # 待诊断故障。
    logs: list[LogEntryPayload]  # 与故障服务相关的日志。
    metrics: list[MetricPointPayload]  # 与故障服务相关的指标。
    traces: list[TraceSpanPayload]  # 由日志 traceId 延伸得到的链路。


class DiagnosisReport(BaseModel):
    """Structured diagnosis returned to Java and persisted as DiagnosisRecord."""

    incidentId: str  # 报告所属故障 id。
    summary: str  # 面向用户的诊断摘要。
    rootCause: str  # 根因判断。
    evidence: list[str]  # 支撑结论的证据和 Runbook 来源。
    recommendation: str  # 排查或修复建议。
    confidence: float  # 0 到 1 之间的置信度。
