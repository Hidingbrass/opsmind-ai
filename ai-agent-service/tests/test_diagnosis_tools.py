"""验证诊断流程对 Tool Gateway 结果的解析与降级行为。"""

import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from app.diagnosis import (
    _resolve_deployments,
    _resolve_metrics,
    _resolve_runbooks,
    _resolve_traces,
)
from app.schemas import DiagnosisRequest, ToolExecutionResult


class DiagnosisToolResolutionTest(unittest.TestCase):
    """确保多工具证据不会因单个下游失败而中断整次诊断。"""

    def test_request_accepts_java_unix_timestamps(self):
        """Java 的 Instant 序列化为秒数时，合同仍应解析成时间对象。"""
        request = DiagnosisRequest.model_validate(
            {
                "taskId": "task-1",
                "traceId": "0123456789abcdef0123456789abcdef",
                "incident": {
                    "id": "incident-1",
                    "title": "支付超时",
                    "serviceName": "payment-service",
                    "severity": "HIGH",
                    "status": "OPEN",
                    "symptom": "支付接口超时",
                    "createdAt": 1784890959.329146,
                    "updatedAt": 1784890959.329146,
                },
                "logs": [
                    {
                        "timestamp": 1783245603.0,
                        "serviceName": "payment-service",
                        "level": "ERROR",
                        "traceId": "trace-1",
                        "message": "支付超时",
                    }
                ],
                "metrics": [
                    {
                        "timestamp": "2026-07-05T10:00:00Z",
                        "serviceName": "payment-service",
                        "metricName": "http_server_requests_p95",
                        "value": 2200,
                        "unit": "ms",
                    }
                ],
                "traces": [],
            }
        )

        self.assertEqual(
            request.incident.createdAt,
            datetime.fromtimestamp(1784890959.329146, tz=timezone.utc),
        )
        self.assertEqual(
            request.metrics[0].timestamp,
            datetime(2026, 7, 5, 10, 0, tzinfo=timezone.utc),
        )

    def setUp(self):
        self.request = DiagnosisRequest.model_validate(
            {
                "taskId": "task-1",
                "traceId": "0123456789abcdef0123456789abcdef",
                "incident": {
                    "id": "incident-1",
                    "title": "支付超时",
                    "serviceName": "payment-service",
                    "severity": "HIGH",
                    "status": "OPEN",
                    "symptom": "支付接口超时",
                    "createdAt": "2026-07-05T10:00:00Z",
                    "updatedAt": "2026-07-05T10:00:00Z",
                },
                "logs": [
                    {
                        "timestamp": "2026-07-05T10:00:00Z",
                        "serviceName": "payment-service",
                        "level": "ERROR",
                        "traceId": "trace-1",
                        "message": "gateway timeout",
                    }
                ],
                "metrics": [
                    {
                        "timestamp": "2026-07-05T10:00:00Z",
                        "serviceName": "payment-service",
                        "metricName": "fallback.metric",
                        "value": 1,
                        "unit": "count",
                    }
                ],
                "traces": [],
            }
        )

    @patch("app.diagnosis.call_tool")
    def test_metrics_use_structured_tool_data(self, call_tool):
        call_tool.return_value = ToolExecutionResult(
            toolName="queryMetrics",
            status="SUCCESS",
            data=[
                {
                    "timestamp": "2026-07-05T10:00:00Z",
                    "serviceName": "payment-service",
                    "metricName": "http.server.requests.p95",
                    "value": 5200,
                    "unit": "ms",
                }
            ],
            latencyMs=3,
        )

        metrics = _resolve_metrics(self.request)

        self.assertEqual("http.server.requests.p95", metrics[0].metricName)
        call_tool.assert_called_once_with(
            task_id="task-1",
            incident_id="incident-1",
            trace_id="0123456789abcdef0123456789abcdef",
            tool_name="queryMetrics",
            arguments={"serviceName": "payment-service"},
        )

    @patch("app.diagnosis.call_tool")
    def test_metric_failure_falls_back_to_request_data(self, call_tool):
        call_tool.return_value = ToolExecutionResult(
            toolName="queryMetrics",
            status="FAILED",
            errorMessage="timeout",
            latencyMs=5000,
        )

        metrics = _resolve_metrics(self.request)

        self.assertEqual("fallback.metric", metrics[0].metricName)

    @patch("app.diagnosis.call_tool")
    def test_trace_query_uses_trace_id_from_logs(self, call_tool):
        call_tool.return_value = ToolExecutionResult(
            toolName="queryTrace",
            status="SUCCESS",
            data=[
                {
                    "traceId": "trace-1",
                    "spanId": "span-1",
                    "parentSpanId": None,
                    "serviceName": "payment-service",
                    "operationName": "POST /pay",
                    "durationMs": 5000,
                    "status": "ERROR",
                    "errorMessage": "timeout",
                }
            ],
            latencyMs=2,
        )

        traces = _resolve_traces(self.request, self.request.logs)

        self.assertEqual("span-1", traces[0].spanId)
        self.assertEqual("trace-1", call_tool.call_args.kwargs["arguments"]["traceId"])

    @patch("app.diagnosis.call_tool")
    def test_runbook_tool_returns_only_dictionary_hits(self, call_tool):
        call_tool.return_value = ToolExecutionResult(
            toolName="searchRunbook",
            status="SUCCESS",
            data=[{"content": "排查支付网关", "metadata": {"source": "runbook.md"}}],
            latencyMs=4,
        )

        hits = _resolve_runbooks(self.request, "支付超时")

        self.assertEqual("runbook.md", hits[0]["metadata"]["source"])

    @patch("app.diagnosis.call_tool")
    def test_recent_deployments_are_parsed_as_structured_evidence(self, call_tool):
        call_tool.return_value = ToolExecutionResult(
            toolName="getRecentDeployments",
            status="SUCCESS",
            data=[
                {
                    "deployedAt": "2026-07-05T08:30:00Z",
                    "serviceName": "payment-service",
                    "version": "2.4.1",
                    "commitId": "8fd31a2",
                    "operator": "release-bot",
                    "status": "SUCCESS",
                    "summary": "调整支付网关连接参数",
                }
            ],
            latencyMs=2,
        )

        deployments = _resolve_deployments(self.request)

        self.assertEqual("2.4.1", deployments[0].version)
        self.assertEqual(
            "getRecentDeployments",
            call_tool.call_args.kwargs["tool_name"],
        )


if __name__ == "__main__":
    unittest.main()
