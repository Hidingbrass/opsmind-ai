from datetime import datetime, timezone

from fastapi import FastAPI

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
