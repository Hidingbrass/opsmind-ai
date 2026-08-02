"""在确定性演示和真实模型 Agent 之间选择，并提供可审计降级。"""

import logging

from app.agent.config import AgentSettings, load_agent_settings
from app.agent.llm_client import LlmClientError
from app.agent.runtime import AgentRuntimeError, run_llm_diagnosis
from app.diagnosis import generate_diagnosis
from app.schemas import AgentExecutionMetadata, DiagnosisReport, DiagnosisRequest


logger = logging.getLogger(__name__)


def _fallback_report(
    request: DiagnosisRequest,
    settings: AgentSettings,
) -> DiagnosisReport:
    report = generate_diagnosis(request)
    return report.model_copy(
        update={
            "agentMetadata": AgentExecutionMetadata(
                executionMode="LLM_FALLBACK",
                provider=settings.provider,
                modelName=settings.model or "unconfigured-llm",
                promptVersion=settings.prompt_version,
                inputTokens=0,
                outputTokens=0,
                toolCallCount=0,
            )
        }
    )


def diagnose_with_config(
    request: DiagnosisRequest,
    settings: AgentSettings | None = None,
) -> DiagnosisReport:
    """根据显式模式执行诊断；只有配置允许时才自动降级。"""
    runtime_settings = settings or load_agent_settings()
    if runtime_settings.mode == "deterministic":
        return generate_diagnosis(request)

    try:
        return run_llm_diagnosis(request, runtime_settings)
    except (AgentRuntimeError, LlmClientError) as exc:
        if not runtime_settings.fallback_enabled:
            raise
        logger.warning(
            "LLM Agent 失败，降级到确定性诊断: %s",
            exc,
        )
        return _fallback_report(request, runtime_settings)
