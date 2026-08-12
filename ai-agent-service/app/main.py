"""FastAPI 应用入口，将健康检查、RAG 检索和诊断能力暴露为 HTTP API。"""

from datetime import datetime, timezone
from typing import Annotated

from fastapi import FastAPI, Query

from app.agent.config import load_agent_settings
from app.agent.orchestrator import diagnose_with_config
from app.rag.runbook_search import search_runbooks
from app.schemas import DiagnosisRequest, DiagnosisReport

# Uvicorn 通过 app.main:app 找到这个应用对象并启动 HTTP 服务。
app = FastAPI(
    title="OpsMind AI Agent Service",
    description="OpsMind AI 的 Python AI Agent 服务",
    version="1.1.0",
)


@app.get("/ai/health")
def health():
    """返回轻量存活状态；健康检查不会触发体积较大的向量模型加载。"""
    settings = load_agent_settings()
    return {
        "service": "opsmind-ai-agent",
        "status": "UP",
        "diagnosisMode": settings.mode,
        "llmConfigured": settings.llm_ready,
        "model": settings.model or "deterministic-rag-agent",
        "time": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/ai/diagnose", response_model=DiagnosisReport)
def diagnose(request: DiagnosisRequest):
    """校验 Java 请求合同，并把请求交给多工具诊断流程。"""
    return diagnose_with_config(request)


@app.get("/ai/runbooks/search")
def search_runbook_knowledge(
    query: Annotated[str, Query(min_length=1, max_length=500)],
    n_results: Annotated[int, Query(ge=1, le=5)] = 3,
):
    """直接检索 Runbook 文档片段，供 RAG 调试和闭环验收使用。"""
    return {
        "query": query,
        "results": search_runbooks(query=query, n_results=n_results),
    }
