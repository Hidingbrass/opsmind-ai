"""Python 诊断流程调用 Spring Tool Gateway 的 HTTP 客户端边界。"""

import os
import time
from typing import Any

import httpx
from pydantic import ValidationError

from app.schemas import ToolExecutionResult


# 本地开发默认访问 8080；Docker 部署时可通过环境变量改成后端容器地址。
BACKEND_BASE_URL = os.getenv(
    "OPSMIND_BACKEND_BASE_URL",
    "http://127.0.0.1:8080",
)

# 单个工具调用最多等待 5 秒，避免下游异常长期占用诊断线程。
TOOL_TIMEOUT_SECONDS = 5.0


def _build_failed_result(
    tool_name: str,
    error_message: str,
    started_at: float,
) -> ToolExecutionResult:
    """将 Python 客户端边界异常转换成诊断流程可消费的工具失败结果。"""
    latency_ms = int((time.perf_counter() - started_at) * 1000)

    return ToolExecutionResult(
        toolName=tool_name,
        status="FAILED",
        data=None,
        errorMessage=error_message,
        latencyMs=latency_ms,
    )


def call_tool(
    task_id: str,
    incident_id: str,
    tool_name: str,
    arguments: dict[str, Any],
) -> ToolExecutionResult:
    """调用 Spring Tool Gateway，并只返回诊断流程需要的内层工具结果。"""
    # Python 参数是 snake_case，这里转成 Java DTO 约定的 camelCase JSON 字段。
    request_payload = {
        "taskId": task_id,
        "incidentId": incident_id,
        "toolName": tool_name,
        "arguments": arguments,
    }
    started_at = time.perf_counter()
    try:
        url = BACKEND_BASE_URL.rstrip("/") + "/api/tools/execute"

        response = httpx.post(
            url,
            json=request_payload,
            timeout=TOOL_TIMEOUT_SECONDS,
        )

        # HTTP 不是 2xx 时直接抛出 httpx 异常。
        response.raise_for_status()

        # body 是 Spring 的统一 Result：code、message、data。
        body = response.json()
        if not isinstance(body, dict):
            raise ValueError("Spring Tool Gateway 响应必须是 JSON 对象")

        if body.get("code") != 0:
            raise ValueError(
                f"Spring Tool Gateway 调用失败: {body.get('message')}"
            )

        tool_result_data = body.get("data")
        if tool_result_data is None:
            raise ValueError("Spring Tool Gateway 返回缺少 data")

        # 将普通字典校验并转换成诊断流程需要的结构化对象。
        return ToolExecutionResult.model_validate(tool_result_data)
    except httpx.TimeoutException:
        return _build_failed_result(
            tool_name,
            "调用 Spring Tool Gateway 超时",
            started_at,
        )
    except httpx.HTTPStatusError as exc:
        return _build_failed_result(
            tool_name,
            f"Spring Tool Gateway HTTP 状态异常: {exc.response.status_code}",
            started_at,
        )
    except httpx.RequestError:
        return _build_failed_result(
            tool_name,
            "无法连接 Spring Tool Gateway",
            started_at,
        )
    except (ValueError, ValidationError) as exc:
        return _build_failed_result(
            tool_name,
            f"Spring Tool Gateway 响应无效: {exc}",
            started_at,
        )
