"""Evaluate Runbook retrieval over HTTP without changing existing reports."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any

import httpx


ROOT = Path(__file__).resolve().parent
DEFAULT_DATASET = ROOT / "rag_dataset.json"


def source_name(hit: Any) -> str:
    """Read a stable source name from a possibly incomplete retrieval hit."""
    if not isinstance(hit, dict):
        return ""
    metadata = hit.get("metadata")
    if not isinstance(metadata, dict):
        return ""
    filename = metadata.get("filename")
    if isinstance(filename, str) and filename:
        return filename
    source = metadata.get("source")
    return Path(source).name if isinstance(source, str) else ""


def first_relevant_rank(
    hits: list[Any],
    relevant_sources: set[str],
) -> int | None:
    """Return the one-based rank of the first relevant Runbook hit."""
    for rank, hit in enumerate(hits, start=1):
        if source_name(hit) in relevant_sources:
            return rank
    return None


def evaluate_case(
    client: httpx.Client,
    case: dict[str, Any],
    n_results: int,
) -> dict[str, Any]:
    response = client.get(
        "/ai/runbooks/search",
        params={"query": case["query"], "n_results": n_results},
    )
    response.raise_for_status()
    body = response.json()
    hits = body.get("results", []) if isinstance(body, dict) else []
    if not isinstance(hits, list):
        hits = []

    rank = first_relevant_rank(hits, set(case["relevantSources"]))
    return {
        "id": case["id"],
        "query": case["query"],
        "firstRelevantRank": rank,
        "hitAt1": rank == 1,
        "hitAt3": rank is not None and rank <= 3,
        "topSources": [source_name(hit) for hit in hits[:3]],
        "topRetrievalModes": [
            hit.get("retrievalMode") if isinstance(hit, dict) else None
            for hit in hits[:3]
        ],
    }


def summarise(results: list[dict[str, Any]]) -> dict[str, Any]:
    total = len(results)
    reciprocal_rank_sum = sum(
        1.0 / result["firstRelevantRank"]
        for result in results
        if result["firstRelevantRank"] is not None
    )
    modes = Counter(
        mode
        for result in results
        for mode in result.get("topRetrievalModes", [])
        if isinstance(mode, str)
    )
    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "total": total,
        "hitAt1": (
            sum(result["hitAt1"] for result in results) / total
            if total
            else 0.0
        ),
        "hitAt3": (
            sum(result["hitAt3"] for result in results) / total
            if total
            else 0.0
        ),
        "mrr": reciprocal_rank_sum / total if total else 0.0,
        "retrievalModes": dict(sorted(modes.items())),
        "results": results,
    }


def load_dataset(path: Path) -> list[dict[str, Any]]:
    cases = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(cases, list) or not cases:
        raise ValueError("RAG dataset must be a non-empty JSON array")
    for case in cases:
        if not isinstance(case, dict):
            raise ValueError("Each RAG case must be a JSON object")
        required = {"id", "query", "relevantSources"}
        if not required.issubset(case):
            raise ValueError(f"RAG case is missing fields: {case}")
        if not isinstance(case["relevantSources"], list):
            raise ValueError("relevantSources must be a JSON array")
    return cases


def main() -> None:
    parser = argparse.ArgumentParser(description="Run OpsMind RAG retrieval evaluation")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--n-results", type=int, default=3)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-hit-at-1", type=float, default=0.8)
    parser.add_argument("--min-hit-at-3", type=float, default=1.0)
    parser.add_argument("--min-mrr", type=float, default=0.85)
    args = parser.parse_args()

    if args.n_results < 3:
        parser.error("--n-results must be at least 3 to calculate Hit@3")
    for name, value in {
        "--min-hit-at-1": args.min_hit_at_1,
        "--min-hit-at-3": args.min_hit_at_3,
        "--min-mrr": args.min_mrr,
    }.items():
        if value < 0 or value > 1:
            parser.error(f"{name} must be between 0 and 1")

    cases = load_dataset(args.dataset)
    with httpx.Client(
        base_url=args.base_url.rstrip("/") + "/",
        timeout=args.timeout,
        trust_env=False,
    ) as client:
        results = [
            evaluate_case(client, case, args.n_results)
            for case in cases
        ]

    summary = summarise(results)
    serialized = json.dumps(summary, ensure_ascii=False, indent=2)
    print(serialized)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized + "\n", encoding="utf-8")

    passed = (
        summary["hitAt1"] >= args.min_hit_at_1
        and summary["hitAt3"] >= args.min_hit_at_3
        and summary["mrr"] >= args.min_mrr
    )
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
