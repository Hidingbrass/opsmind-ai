"""FastAPI 应用入口，将健康检查、RAG 检索和诊断能力暴露为 HTTP API。"""

from datetime import datetime, timezone

from fastapi import FastAPI

from app.diagnosis import generate_diagnosis
from app.rag.runbook_search import search_runbooks
from app.schemas import DiagnosisRequest, DiagnosisReport

# Uvicorn 通过 app.main:app 找到这个应用对象并启动 HTTP 服务。
app = FastAPI(
    title="OpsMind AI Agent Service",
    description="OpsMind AI 的 Python AI Agent 服务",
    version="0.1.0",
)


@app.get("/ai/health")
def health():
    """Return a lightweight liveness response without loading the embedding model."""
    return {
        "service": "opsmind-ai-agent",
        "status": "UP",
        "time": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/ai/diagnose", response_model=DiagnosisReport)
def diagnose(request: DiagnosisRequest):
    """Validate the Java payload and delegate the actual diagnosis workflow."""
    return generate_diagnosis(request)


@app.get("/ai/runbooks/search")
def search_runbook_knowledge(query: str, n_results: int = 3):
    """Search Runbook chunks directly for RAG debugging and acceptance tests."""
    return {
        "query": query,
        "results": search_runbooks(query=query, n_results=n_results),
    }
