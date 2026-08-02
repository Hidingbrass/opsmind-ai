"""供 HTTP 路由和诊断流程共同使用的 Runbook 检索入口。"""

from typing import Any

from app.rag.chroma_store import ChromaRunbookStore


def search_runbooks(query: str, n_results: int = 3) -> list[dict[str, Any]]:
    """从持久化知识库中返回与查询最相关的 Runbook 文档片段。"""
    store = ChromaRunbookStore()
    return store.search(query=query, n_results=n_results)
