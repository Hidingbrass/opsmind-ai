"""Public Runbook search function used by both HTTP routes and diagnosis logic."""

from typing import Any

from app.rag.chroma_store import ChromaRunbookStore


def search_runbooks(query: str, n_results: int = 3) -> list[dict[str, Any]]:
    """Search the persistent Runbook collection for the most relevant chunks."""
    store = ChromaRunbookStore()
    return store.search(query=query, n_results=n_results)
