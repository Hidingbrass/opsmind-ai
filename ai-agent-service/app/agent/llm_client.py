"""最小 OpenAI-compatible Chat Completions 客户端。"""

from dataclasses import dataclass
from typing import Any

import httpx

from app.agent.config import AgentSettings


class LlmClientError(RuntimeError):
    """外部模型网络、HTTP 或响应合同错误。"""


@dataclass(frozen=True)
class LlmToolCall:
    """模型请求执行的一次函数工具调用。"""

    id: str
    name: str
    arguments_json: str


@dataclass(frozen=True)
class LlmCompletion:
    """Agent 运行时需要的模型响应子集。"""

    content: str | None
    tool_calls: list[LlmToolCall]
    assistant_message: dict[str, Any]
    input_tokens: int
    output_tokens: int


class OpenAiCompatibleClient:
    """调用支持 `/chat/completions` 的模型服务，不绑定具体供应商 SDK。"""

    def __init__(self, settings: AgentSettings):
        self.settings = settings

    def complete(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> LlmCompletion:
        """发送一轮 Agent 对话并校验最小响应合同。"""
        url = self.settings.base_url.rstrip("/") + "/chat/completions"
        headers = {"Content-Type": "application/json"}
        if self.settings.api_key:
            headers["Authorization"] = f"Bearer {self.settings.api_key}"

        try:
            with httpx.Client(timeout=self.settings.timeout_seconds) as client:
                response = client.post(
                    url,
                    headers=headers,
                    json={
                        "model": self.settings.model,
                        "messages": messages,
                        "tools": tools,
                        "tool_choice": "auto",
                        "temperature": 0,
                    },
                )
            response.raise_for_status()
            body = response.json()
        except httpx.TimeoutException as exc:
            raise LlmClientError("外部模型调用超时") from exc
        except httpx.HTTPStatusError as exc:
            raise LlmClientError(
                f"外部模型返回 HTTP {exc.response.status_code}"
            ) from exc
        except httpx.RequestError as exc:
            raise LlmClientError("无法连接外部模型服务") from exc
        except ValueError as exc:
            raise LlmClientError("外部模型没有返回合法 JSON") from exc

        try:
            message = body["choices"][0]["message"]
            raw_tool_calls = message.get("tool_calls") or []
            tool_calls = [
                LlmToolCall(
                    id=item["id"],
                    name=item["function"]["name"],
                    arguments_json=item["function"].get("arguments", "{}"),
                )
                for item in raw_tool_calls
            ]
            usage = body.get("usage") or {}
            return LlmCompletion(
                content=message.get("content"),
                tool_calls=tool_calls,
                assistant_message=message,
                input_tokens=max(0, int(usage.get("prompt_tokens", 0))),
                output_tokens=max(0, int(usage.get("completion_tokens", 0))),
            )
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            raise LlmClientError("外部模型响应缺少 choices/message") from exc
