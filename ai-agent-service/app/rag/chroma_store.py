"""Chroma persistence and Chinese embedding helpers for the Runbook knowledge base."""

from pathlib import Path
from typing import Any

import chromadb
from sentence_transformers import SentenceTransformer


# AI 服务根目录，用于构造与启动位置无关的持久化路径。
PROJECT_ROOT = Path(__file__).resolve().parents[2]
# Chroma 本地持久化目录，重启 AI 服务后仍可复用已导入向量。
CHROMA_DIR = PROJECT_ROOT / ".chroma"
# 存放 Runbook 切片的 Chroma collection 名。
COLLECTION_NAME = "opsmind_runbooks"
# 中文语义检索使用的 embedding 模型。
EMBEDDING_MODEL_NAME = "BAAI/bge-small-zh-v1.5"

# 进程内懒加载模型缓存，避免每次检索重复加载大文件。
_embedding_model: SentenceTransformer | None = None


def get_embedding_model() -> SentenceTransformer:
    """Load the embedding model once and reuse it for later requests."""
    global _embedding_model

    if _embedding_model is None:
        _embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)

    return _embedding_model


def build_embeddings(texts: list[str]) -> list[list[float]]:
    """Convert text into normalized vectors suitable for cosine-like search."""
    model = get_embedding_model()
    embeddings = model.encode(
        texts,
        normalize_embeddings=True,
    )
    return embeddings.tolist()


class ChromaRunbookStore:
    """Small repository wrapper around the persistent Runbook collection."""

    def __init__(self):
        """Open the local Chroma database and create the collection when absent."""
        # client 管理本地 Chroma 数据库连接。
        self.client = chromadb.PersistentClient(path=str(CHROMA_DIR))
        # collection 是后续文档写入和相似度查询的实际对象。
        self.collection = self.client.get_or_create_collection(
            name=COLLECTION_NAME
        )

    def reset(self):
        """Delete all indexed Runbooks and recreate an empty collection."""
        try:
            self.client.delete_collection(COLLECTION_NAME)
        except Exception:
            pass

        self.collection = self.client.get_or_create_collection(
            name=COLLECTION_NAME
        )

    def add_documents(
            self,
            ids: list[str],
            documents: list[str],
            metadatas: list[dict[str, Any]],
    ):
        """Embed and add aligned document ids, texts, and metadata to Chroma."""
        if not ids:
            return

        self.collection.add(
            ids=ids,
            documents=documents,
            metadatas=metadatas,
            embeddings=build_embeddings(documents),
        )

    def search(self, query: str, n_results: int = 3) -> list[dict[str, Any]]:
        """Return the nearest Runbook chunks with metadata and vector distance."""
        result = self.collection.query(
            query_embeddings=build_embeddings([query]),
            n_results=n_results,
        )

        # Chroma 按“批次查询 -> 命中列表”返回二维数组，这里只取单个 query 的第一组。
        documents = result.get("documents", [[]])[0]
        metadatas = result.get("metadatas", [[]])[0]
        distances = result.get("distances", [[]])[0]

        # 把 Chroma 的并行数组整理成 API 更容易使用的命中对象。
        hits = []
        for index, document in enumerate(documents):
            hits.append(
                {
                    "content": document,
                    "metadata": metadatas[index],
                    "distance": distances[index] if index < len(distances) else None,
                }
            )

        return hits
