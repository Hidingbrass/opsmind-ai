"""从环境变量读取 Agent 模式和外部模型配置。"""

from dataclasses import dataclass
import os


SUPPORTED_MODES = {"deterministic", "llm"}


def _read_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _read_positive_int(name: str, default: int, maximum: int) -> int:
    raw_value = os.getenv(name, str(default))
    try:
        value = int(raw_value)
    except ValueError as exc:
        raise ValueError(f"{name} 必须是整数") from exc
    if value <= 0 or value > maximum:
        raise ValueError(f"{name} 必须在 1 到 {maximum} 之间")
    return value


@dataclass(frozen=True)
class AgentSettings:
    """Agent 运行时配置；API Key 只在进程内使用，不进入报告或日志。"""

    mode: str
    provider: str
    model: str
    base_url: str
    api_key: str | None
    prompt_version: str
    max_steps: int
    timeout_seconds: int
    fallback_enabled: bool

    @property
    def llm_ready(self) -> bool:
        return bool(self.base_url and self.model)


def load_agent_settings() -> AgentSettings:
    """读取并校验配置，默认保持无需密钥的确定性演示模式。"""
    mode = os.getenv("OPSMIND_DIAGNOSIS_MODE", "deterministic").strip().lower()
    if mode not in SUPPORTED_MODES:
        raise ValueError(
            "OPSMIND_DIAGNOSIS_MODE 只支持 deterministic 或 llm"
        )

    return AgentSettings(
        mode=mode,
        provider=os.getenv(
            "OPSMIND_LLM_PROVIDER", "openai-compatible"
        ).strip(),
        model=os.getenv("OPSMIND_LLM_MODEL", "").strip(),
        base_url=os.getenv("OPSMIND_LLM_BASE_URL", "").strip(),
        api_key=os.getenv("OPSMIND_LLM_API_KEY") or None,
        prompt_version=os.getenv(
            "OPSMIND_LLM_PROMPT_VERSION", "opsmind-agent-v1"
        ).strip(),
        max_steps=_read_positive_int("OPSMIND_LLM_MAX_STEPS", 6, 12),
        timeout_seconds=_read_positive_int(
            "OPSMIND_LLM_TIMEOUT_SECONDS", 45, 180
        ),
        fallback_enabled=_read_bool("OPSMIND_LLM_FALLBACK_ENABLED", True),
    )
