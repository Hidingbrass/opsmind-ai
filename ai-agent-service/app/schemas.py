"""Java 后端与 Python AI 服务之间的 Pydantic JSON 合同。"""

from datetime import datetime
from typing import Annotated, Any, Literal, Optional

from pydantic import BaseModel, Field


Identifier = Annotated[str, Field(min_length=1, max_length=64)]
DatabaseId = Annotated[str, Field(min_length=1, max_length=36)]
TraceId = Annotated[str, Field(min_length=1, max_length=64)]
ServiceName = Annotated[str, Field(min_length=1, max_length=80)]
ShortText = Annotated[str, Field(min_length=1, max_length=120)]
SymptomText = Annotated[str, Field(min_length=1, max_length=1000)]
EvidenceText = Annotated[str, Field(min_length=1, max_length=500)]
ReportText = Annotated[str, Field(min_length=1, max_length=1000)]
RecommendationText = Annotated[str, Field(min_length=1, max_length=4000)]
MessageText = Annotated[str, Field(min_length=1, max_length=4000)]


class IncidentPayload(BaseModel):
    """标识 AI 应诊断对象的故障事件上下文。"""

    id: DatabaseId  # 故障事件唯一 id。
    title: ShortText  # 故障标题。
    serviceName: ServiceName  # 故障主要所属服务。
    severity: Annotated[str, Field(min_length=1, max_length=20)]
    status: Annotated[str, Field(min_length=1, max_length=20)]
    symptom: SymptomText  # 用户或监控观察到的故障现象。
    # 使用 datetime 同时兼容 Java 发送的 Unix 时间戳和 ISO-8601 字符串。
    createdAt: datetime  # 创建时间。
    updatedAt: datetime  # 最后更新时间。


class LogEntryPayload(BaseModel):
    """可观测模块提供的结构化日志证据。"""

    timestamp: datetime  # 日志时间。
    serviceName: ServiceName  # 产生日志的服务。
    level: Annotated[str, Field(min_length=1, max_length=20)]
    traceId: TraceId  # 关联分布式链路的 id。
    message: MessageText  # 日志正文。


class MetricPointPayload(BaseModel):
    """用于量化异常程度的单个监控指标样本。"""

    timestamp: datetime  # 采样时间。
    serviceName: ServiceName  # 指标所属服务。
    metricName: Identifier  # 指标名。
    value: float  # 指标数值。
    unit: Annotated[str, Field(min_length=1, max_length=30)]


class TraceSpanPayload(BaseModel):
    """分布式请求链路中的单个节点。"""

    traceId: TraceId  # 整条调用链 id。
    spanId: TraceId  # 当前节点 id。
    parentSpanId: Optional[TraceId] = None  # 父节点 id，根节点为 None。
    serviceName: ServiceName  # 执行当前节点的服务。
    operationName: Annotated[str, Field(min_length=1, max_length=160)]
    durationMs: int = Field(ge=0)  # 节点耗时，单位毫秒。
    status: Annotated[str, Field(min_length=1, max_length=30)]
    errorMessage: Optional[MessageText] = None  # 节点失败原因。


class DeploymentPayload(BaseModel):
    """发布平台返回的最近部署记录。"""

    deployedAt: datetime  # 发布时间。
    serviceName: ServiceName  # 发布服务。
    version: Identifier  # 应用版本。
    commitId: Identifier  # 源码提交短 id。
    operator: ShortText  # 发布人或自动化账号。
    status: Annotated[str, Field(min_length=1, max_length=30)]
    summary: MessageText  # 变更摘要。


class DiagnosisRequest(BaseModel):
    """Spring Boot 为一次诊断发送的完整任务与证据上下文。"""

    taskId: Optional[DatabaseId] = None
    traceId: Optional[TraceId] = None
    incident: IncidentPayload  # 待诊断故障。
    logs: list[LogEntryPayload] = Field(max_length=200)
    metrics: list[MetricPointPayload] = Field(max_length=500)
    traces: list[TraceSpanPayload] = Field(max_length=500)


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

    incidentId: DatabaseId  # 报告所属故障 id。
    traceId: Optional[TraceId] = None
    summary: ReportText  # 面向用户的诊断摘要。
    rootCause: ReportText  # 根因判断。
    evidence: list[EvidenceText] = Field(min_length=1, max_length=20)
    recommendation: RecommendationText  # 排查或修复建议。
    confidence: float = Field(ge=0, le=1)  # 0 到 1 之间的置信度。
    agentMetadata: AgentExecutionMetadata = Field(
        default_factory=AgentExecutionMetadata
    )
