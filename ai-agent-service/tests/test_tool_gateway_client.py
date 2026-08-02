"""Tool Gateway HTTP 客户端的直连和失败降级测试。"""

import unittest
from unittest.mock import MagicMock, patch

import httpx

from app.tools.tool_gateway_client import call_tool


class ToolGatewayClientTest(unittest.TestCase):
    """验证内部 HTTP 调用不会被宿主代理接管，并始终返回结构化结果。"""

    @patch("app.tools.tool_gateway_client.httpx.Client")
    def test_success_uses_direct_client_and_extracts_inner_result(
        self,
        client_class: MagicMock,
    ) -> None:
        """成功响应应剥离 Spring 外层 Result，并禁用环境代理。"""
        response = MagicMock()
        response.json.return_value = {
            "code": 0,
            "message": "success",
            "data": {
                "toolName": "queryLogs",
                "status": "SUCCESS",
                "data": [{"message": "日志"}],
                "errorMessage": None,
                "latencyMs": 3,
            },
        }
        client = client_class.return_value.__enter__.return_value
        client.post.return_value = response

        result = call_tool(
            task_id="task-1",
            incident_id="incident-1",
            trace_id="0" * 32,
            tool_name="queryLogs",
            arguments={"serviceName": "payment-service"},
        )

        client_class.assert_called_once_with(trust_env=False, timeout=5.0)
        self.assertEqual(result.status, "SUCCESS")
        self.assertEqual(result.data, [{"message": "日志"}])

    @patch("app.tools.tool_gateway_client.httpx.Client")
    def test_connection_error_returns_structured_failure(
        self,
        client_class: MagicMock,
    ) -> None:
        """网络错误不应让诊断接口崩溃，而应返回可降级处理的 FAILED。"""
        request = httpx.Request("POST", "http://127.0.0.1:8080/api/tools/execute")
        client = client_class.return_value.__enter__.return_value
        client.post.side_effect = httpx.ConnectError("connection refused", request=request)

        result = call_tool(
            task_id="task-1",
            incident_id="incident-1",
            trace_id=None,
            tool_name="queryMetrics",
            arguments={"serviceName": "payment-service"},
        )

        self.assertEqual(result.status, "FAILED")
        self.assertEqual(result.errorMessage, "无法连接 Spring Tool Gateway")
        self.assertIsNone(result.data)


if __name__ == "__main__":
    unittest.main()
