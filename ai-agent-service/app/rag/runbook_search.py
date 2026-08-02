"""供 HTTP 路由和诊断流程共同使用的 Runbook 检索入口。"""

from typing import Any

from app.rag.chroma_store import ChromaRunbookStore
from app.rag.hybrid_search import HybridRunbookSearcher


def search_runbooks(query: str, n_results: int = 3) -> list[dict[str, Any]]:
    """使用 dense、BM25 和 RRF 返回可解释的 Runbook 检索结果。"""
    store = ChromaRunbookStore()
    return HybridRunbookSearcher(store).search(
        query=query,
        n_results=n_results,
    )
