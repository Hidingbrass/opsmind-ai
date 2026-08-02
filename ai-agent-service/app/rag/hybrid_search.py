"""Hybrid dense and lexical Runbook retrieval with explainable RRF scores."""

import json
from typing import Any

from app.rag.bm25 import BM25Index


MAX_RESULTS = 20
MIN_DENSE_CANDIDATES = 10
DENSE_CANDIDATE_MULTIPLIER = 4
RRF_K = 60


def _result_limit(n_results: int) -> int:
    try:
        requested = int(n_results)
    except (TypeError, ValueError):
        return 0
    if requested <= 0:
        return 0
    return min(requested, MAX_RESULTS)


def _normalise_hit(hit: Any) -> dict[str, Any]:
    source = hit if isinstance(hit, dict) else {}
    content = source.get("content")
    metadata = source.get("metadata")
    distance = source.get("distance")
    return {
        "id": source.get("id"),
        "content": content if isinstance(content, str) else "",
        "metadata": metadata if isinstance(metadata, dict) else {},
        "distance": distance if isinstance(distance, (int, float)) else None,
    }


def _hit_key(hit: dict[str, Any]) -> str:
    document_id = hit.get("id")
    if document_id is not None:
        return f"id:{document_id}"

    metadata = hit.get("metadata", {})
    identity = {
        "source": metadata.get("source"),
        "filename": metadata.get("filename"),
        "chunk_index": metadata.get("chunk_index"),
        "content": hit.get("content", ""),
    }
    return json.dumps(identity, ensure_ascii=False, sort_keys=True)


class HybridRunbookSearcher:
    """Fuse Chroma dense candidates with BM25 results using RRF."""

    def __init__(self, store: Any) -> None:
        self.store = store

    def search(self, query: str, n_results: int = 3) -> list[dict[str, Any]]:
        """Return ranked hits while preserving the legacy response fields."""
        limit = _result_limit(n_results)
        if limit == 0 or not isinstance(query, str) or not query.strip():
            return []

        corpus = [
            _normalise_hit(hit)
            for hit in self.store.list_documents()
        ]
        if not corpus:
            return []

        dense_candidate_count = min(
            len(corpus),
            max(MIN_DENSE_CANDIDATES, limit * DENSE_CANDIDATE_MULTIPLIER),
        )
        dense_hits = [
            _normalise_hit(hit)
            for hit in self.store.search(query, dense_candidate_count)
        ]

        lexical_scores = BM25Index(
            [hit["content"] for hit in corpus]
        ).score(query)
        lexical_order = sorted(
            (
                (score, _hit_key(corpus[index]), corpus[index])
                for index, score in enumerate(lexical_scores)
                if score > 0.0
            ),
            key=lambda item: (-item[0], item[1]),
        )

        fused: dict[str, dict[str, Any]] = {}
        for rank, hit in enumerate(dense_hits, start=1):
            key = _hit_key(hit)
            if key in fused:
                continue
            fused[key] = {
                "hit": hit,
                "dense_rank": rank,
                "lexical_rank": None,
                "lexical_score": None,
                "rrf_score": 1.0 / (RRF_K + rank),
            }

        for rank, (lexical_score, key, hit) in enumerate(
            lexical_order,
            start=1,
        ):
            state = fused.get(key)
            if state is None:
                state = {
                    "hit": hit,
                    "dense_rank": None,
                    "lexical_rank": None,
                    "lexical_score": None,
                    "rrf_score": 0.0,
                }
                fused[key] = state
            state["lexical_rank"] = rank
            state["lexical_score"] = lexical_score
            state["rrf_score"] += 1.0 / (RRF_K + rank)

        ordered = sorted(
            fused.items(),
            key=lambda item: (
                -item[1]["rrf_score"],
                item[1]["dense_rank"] or float("inf"),
                item[1]["lexical_rank"] or float("inf"),
                item[0],
            ),
        )

        results: list[dict[str, Any]] = []
        for _, state in ordered[:limit]:
            dense_rank = state["dense_rank"]
            lexical_rank = state["lexical_rank"]
            if dense_rank is not None and lexical_rank is not None:
                retrieval_mode = "hybrid"
            elif dense_rank is not None:
                retrieval_mode = "dense"
            else:
                retrieval_mode = "lexical"

            hit = state["hit"]
            results.append(
                {
                    "content": hit["content"],
                    "metadata": hit["metadata"],
                    "distance": hit["distance"],
                    "retrievalMode": retrieval_mode,
                    "ranks": {
                        "dense": dense_rank,
                        "lexical": lexical_rank,
                    },
                    "scores": {
                        "rrf": round(state["rrf_score"], 8),
                        "denseDistance": hit["distance"],
                        "lexicalBm25": (
                            round(state["lexical_score"], 8)
                            if state["lexical_score"] is not None
                            else None
                        ),
                    },
                }
            )

        return results
