"""Unit tests for hybrid Runbook retrieval without a real embedding model."""

import unittest
from unittest.mock import patch

from app.rag.chroma_store import ChromaRunbookStore
from app.rag.hybrid_search import HybridRunbookSearcher, MAX_RESULTS
from app.rag.runbook_search import search_runbooks


def make_hit(
    document_id: str,
    content: str,
    filename: str,
    distance: float | None = None,
) -> dict:
    return {
        "id": document_id,
        "content": content,
        "metadata": {
            "filename": filename,
            "source": f"knowledge/runbooks/{filename}",
            "chunk_index": 0,
        },
        "distance": distance,
    }


class FakeStore:
    def __init__(self, corpus: list[dict], dense_hits: list[dict]) -> None:
        self.corpus = corpus
        self.dense_hits = dense_hits
        self.search_calls: list[tuple[str, int]] = []

    def list_documents(self) -> list[dict]:
        return self.corpus

    def search(self, query: str, n_results: int) -> list[dict]:
        self.search_calls.append((query, n_results))
        return self.dense_hits[:n_results]


class EmptyCollection:
    def count(self) -> int:
        return 0


class SparseQueryCollection:
    def count(self) -> int:
        return 1

    def query(self, **kwargs) -> dict:
        return {"ids": [["sparse-0"]]}


class HybridRunbookSearchTest(unittest.TestCase):
    def test_rrf_promotes_dense_and_lexical_overlap(self) -> None:
        payment = make_hit(
            "payment-0",
            "payment gateway timeout",
            "payment-timeout.md",
            0.1,
        )
        redis = make_hit(
            "redis-0",
            "Redis connection refused and connection pool exhausted",
            "redis-connection-failure.md",
            0.2,
        )
        database = make_hit(
            "database-0",
            "database slow query and full table scan",
            "database-slow-query.md",
            0.3,
        )
        store = FakeStore(
            corpus=[payment, redis, database],
            dense_hits=[payment, redis, database],
        )

        results = HybridRunbookSearcher(store).search(
            "Redis connection refused",
            n_results=3,
        )

        self.assertEqual("redis-connection-failure.md", results[0]["metadata"]["filename"])
        self.assertEqual("hybrid", results[0]["retrievalMode"])
        self.assertEqual(2, results[0]["ranks"]["dense"])
        self.assertEqual(1, results[0]["ranks"]["lexical"])
        self.assertGreater(results[0]["scores"]["rrf"], results[1]["scores"]["rrf"])

    def test_chinese_tokens_participate_in_fusion(self) -> None:
        payment = make_hit(
            "payment-0",
            "支付网关响应超时",
            "payment-timeout.md",
            0.1,
        )
        database = make_hit(
            "database-0",
            "数据库慢查询可能由全表扫描和索引缺失导致",
            "database-slow-query.md",
            0.2,
        )
        store = FakeStore(
            corpus=[payment, database],
            dense_hits=[payment, database],
        )

        results = HybridRunbookSearcher(store).search(
            "数据库全表扫描缺少索引",
            n_results=2,
        )

        self.assertEqual("database-slow-query.md", results[0]["metadata"]["filename"])
        self.assertEqual("hybrid", results[0]["retrievalMode"])
        self.assertEqual(1, results[0]["ranks"]["lexical"])

    def test_empty_corpus_does_not_request_dense_embeddings(self) -> None:
        store = FakeStore(corpus=[], dense_hits=[])

        self.assertEqual([], HybridRunbookSearcher(store).search("redis", 3))
        self.assertEqual([], store.search_calls)

    def test_non_positive_result_limit_returns_empty(self) -> None:
        redis = make_hit(
            "redis-0",
            "Redis connection refused",
            "redis-connection-failure.md",
        )
        store = FakeStore(corpus=[redis], dense_hits=[redis])

        self.assertEqual([], HybridRunbookSearcher(store).search("redis", 0))
        self.assertEqual([], HybridRunbookSearcher(store).search("redis", -1))
        self.assertEqual([], store.search_calls)

    def test_result_limit_is_capped(self) -> None:
        corpus = [
            make_hit(
                f"redis-{index}",
                f"Redis connection issue {index}",
                "redis-connection-failure.md",
                float(index),
            )
            for index in range(MAX_RESULTS + 5)
        ]
        store = FakeStore(corpus=corpus, dense_hits=corpus)

        results = HybridRunbookSearcher(store).search("redis connection", 1000)

        self.assertEqual(MAX_RESULTS, len(results))
        self.assertEqual(len(corpus), store.search_calls[0][1])

    def test_missing_fields_keep_legacy_response_contract(self) -> None:
        incomplete = {"id": "incomplete-0"}
        store = FakeStore(corpus=[incomplete], dense_hits=[incomplete])

        result = HybridRunbookSearcher(store).search("unknown", 1)[0]

        self.assertEqual("", result["content"])
        self.assertEqual({}, result["metadata"])
        self.assertIsNone(result["distance"])
        self.assertIn("retrievalMode", result)
        self.assertIn("ranks", result)
        self.assertIn("scores", result)

    @patch("app.rag.runbook_search.ChromaRunbookStore")
    def test_public_search_function_remains_compatible(self, store_class) -> None:
        redis = make_hit(
            "redis-0",
            "Redis connection refused",
            "redis-connection-failure.md",
            0.12,
        )
        store_class.return_value = FakeStore([redis], [redis])

        result = search_runbooks("redis", 1)[0]

        self.assertEqual("Redis connection refused", result["content"])
        self.assertEqual(0.12, result["distance"])

    @patch("app.rag.chroma_store.build_embeddings")
    def test_empty_chroma_collection_skips_embedding(self, build_embeddings) -> None:
        store = ChromaRunbookStore.__new__(ChromaRunbookStore)
        store.collection = EmptyCollection()

        self.assertEqual([], store.search("redis", 3))
        build_embeddings.assert_not_called()

    @patch("app.rag.chroma_store.build_embeddings", return_value=[[0.1]])
    def test_sparse_chroma_response_fills_compatibility_fields(
        self,
        build_embeddings,
    ) -> None:
        store = ChromaRunbookStore.__new__(ChromaRunbookStore)
        store.collection = SparseQueryCollection()

        result = store.search("redis", 3)[0]

        self.assertEqual("sparse-0", result["id"])
        self.assertEqual("", result["content"])
        self.assertEqual({}, result["metadata"])
        self.assertIsNone(result["distance"])
        build_embeddings.assert_called_once_with(["redis"])


if __name__ == "__main__":
    unittest.main()
