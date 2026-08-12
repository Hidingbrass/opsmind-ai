"""验证暴露给 HTTP 和模型输出的文本及集合大小边界。"""

import unittest

from pydantic import ValidationError

from app.schemas import DiagnosisReport, DiagnosisRequest


class InputContractTest(unittest.TestCase):
    def _request_payload(self):
        return {
            "taskId": "task-1",
            "traceId": "0" * 32,
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
            "logs": [],
            "metrics": [],
            "traces": [],
        }

    def test_rejects_oversized_incident_title(self):
        payload = self._request_payload()
        payload["incident"]["title"] = "x" * 121

        with self.assertRaises(ValidationError):
            DiagnosisRequest.model_validate(payload)

    def test_rejects_excessive_observability_items(self):
        payload = self._request_payload()
        payload["logs"] = [
            {
                "timestamp": "2026-07-05T10:00:00Z",
                "serviceName": "payment-service",
                "level": "ERROR",
                "traceId": str(index),
                "message": "timeout",
            }
            for index in range(201)
        ]

        with self.assertRaises(ValidationError):
            DiagnosisRequest.model_validate(payload)

    def test_rejects_oversized_model_report(self):
        with self.assertRaises(ValidationError):
            DiagnosisReport(
                incidentId="incident-1",
                summary="x" * 1001,
                rootCause="root cause",
                evidence=["evidence"],
                recommendation="retry",
                confidence=0.5,
            )


if __name__ == "__main__":
    unittest.main()
