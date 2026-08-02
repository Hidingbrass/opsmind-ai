from typing import Optional

from pydantic import BaseModel


class IncidentPayload(BaseModel):
    id: str
    title: str
    serviceName: str
    severity: str
    status: str
    symptom: str
    createdAt: str
    updatedAt: str


class LogEntryPayload(BaseModel):
    timestamp: str
    serviceName: str
    level: str
    traceId: str
    message: str


class MetricPointPayload(BaseModel):
    timestamp: str
    serviceName: str
    metricName: str
    value: float
    unit: str


class TraceSpanPayload(BaseModel):
    traceId: str
    spanId: str
    parentSpanId: Optional[str] = None
    serviceName: str
    operationName: str
    durationMs: int
    status: str
    errorMessage: Optional[str] = None


class DiagnosisRequest(BaseModel):
    incident: IncidentPayload
    logs: list[LogEntryPayload]
    metrics: list[MetricPointPayload]
    traces: list[TraceSpanPayload]


class DiagnosisReport(BaseModel):
    incidentId: str
    summary: str
    rootCause: str
    evidence: list[str]
    recommendation: str
    confidence: float
