"""验证 OpenAI-compatible 响应解析和外部错误边界。"""

import unittest
from unittest.mock import MagicMock, patch

import httpx

from app.agent.config import AgentSettings
from app.agent.llm_client import LlmClientError, OpenAiCompatibleClient


def _settings() -> AgentSettings:
    return AgentSettings(
        mode="llm",
        provider="test-provider",
        model="test-model",
        base_url="https://model.example/v1",
        api_key="secret-value",
        prompt_version="prompt-v1",
        max_steps=6,
        timeout_seconds=10,
        fallback_enabled=True,
    )


class LlmClientTest(unittest.TestCase):
    @patch("app.agent.llm_client.httpx.Client")
    def test_parses_tool_calls_and_usage(self, client_class):
        response = MagicMock()
        response.json.return_value = {
            "choices": [
                {
                    "message": {
                        "role": "assistant",
                        "content": None,
                        "tool_calls": [
                            {
                                "id": "call-1",
                                "type": "function",
                                "function": {
                                    "name": "queryLogs",
                                    "arguments": '{"serviceName":"payment-service"}',
                                },
                            }
                        ],
                    }
                }
            ],
            "usage": {"prompt_tokens": 23, "completion_tokens": 7},
        }
        context_client = MagicMock()
        context_client.post.return_value = response
        client_class.return_value.__enter__.return_value = context_client

        completion = OpenAiCompatibleClient(_settings()).complete(
            [{"role": "user", "content": "diagnose"}],
            [],
        )

        self.assertEqual("queryLogs", completion.tool_calls[0].name)
        self.assertEqual(23, completion.input_tokens)
        self.assertEqual(7, completion.output_tokens)
        _, kwargs = context_client.post.call_args
        self.assertEqual(
            "Bearer secret-value",
            kwargs["headers"]["Authorization"],
        )

    @patch("app.agent.llm_client.httpx.Client")
    def test_request_error_becomes_safe_client_error(self, client_class):
        request = httpx.Request("POST", "https://model.example/v1/chat/completions")
        context_client = MagicMock()
        context_client.post.side_effect = httpx.ConnectError(
            "connection details",
            request=request,
        )
        client_class.return_value.__enter__.return_value = context_client

        with self.assertRaisesRegex(LlmClientError, "无法连接"):
            OpenAiCompatibleClient(_settings()).complete([], [])


if __name__ == "__main__":
    unittest.main()
