from datetime import datetime, timezone

from fastapi import FastAPI

from app.diagnosis import generate_diagnosis
from app.rag.runbook_search import search_runbooks
from app.schemas import DiagnosisRequest, DiagnosisReport

app = FastAPI(
    title="OpsMind AI Agent Service",
    description="OpsMind AI 的 Python AI Agent 服务",
    version="0.1.0",
)


@app.get("/ai/health")
def health():
    return {
        "service": "opsmind-ai-agent",
        "status": "UP",
        "time": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/ai/diagnose", response_model=DiagnosisReport)
def diagnose(request: DiagnosisRequest):
    return generate_diagnosis(request)


@app.get("/ai/runbooks/search")
def search_runbook_knowledge(query: str, n_results: int = 3):
    return {
        "query": query,
        "results": search_runbooks(query=query, n_results=n_results),
    }
