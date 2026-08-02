"""Runbook 知识库使用的 Chroma 持久化和中文向量化能力。"""

import os
import threading
from pathlib import Path
from typing import Any

import chromadb
from chromadb.config import Settings
from sentence_transformers import SentenceTransformer


# AI 服务根目录，用于构造与启动位置无关的持久化路径。
PROJECT_ROOT = Path(__file__).resolve().parents[2]
# Chroma 本地持久化目录，重启 AI 服务后仍可复用已导入向量。
CHROMA_DIR = PROJECT_ROOT / ".chroma"
# 存放 Runbook 切片的 Chroma collection 名。
COLLECTION_NAME = "opsmind_runbooks"
# 中文语义检索使用的 embedding 模型。
EMBEDDING_MODEL_NAME = "BAAI/bge-small-zh-v1.5"
# 本地作品不发送匿名产品遥测，避免离线环境产生无意义的网络请求和告警。
CHROMA_SETTINGS = Settings(anonymized_telemetry=False)

# 进程内懒加载模型缓存，避免每次检索重复加载大文件。
_embedding_model: SentenceTransformer | None = None
# FastAPI 会在线程池中执行同步路由；锁可防止冷启动时多个请求重复加载模型。
_embedding_model_lock = threading.Lock()


def get_embedding_model() -> SentenceTransformer:
    """首次使用时加载向量模型，后续请求复用同一个进程内实例。"""
    global _embedding_model

    if _embedding_model is None:
        with _embedding_model_lock:
            # 获取锁后再次检查，等待期间其他线程可能已经完成初始化。
            if _embedding_model is None:
                _embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)

    return _embedding_model


def build_embeddings(texts: list[str]) -> list[list[float]]:
    """把文本转换成归一化向量，供 Chroma 执行相似度检索。"""
    model = get_embedding_model()
    embeddings = model.encode(
        texts,
        normalize_embeddings=True,
    )
    return embeddings.tolist()


class ChromaRunbookStore:
    """封装 Runbook 集合的连接、写入、清空和向量检索操作。"""

    def __init__(self):
        """连接本地或远程 Chroma，并在集合不存在时自动创建。"""
        chroma_host = os.getenv("CHROMA_HOST")
        if chroma_host:
            # 容器环境连接独立 Chroma 服务，端口使用容器内部的 8000。
            self.client = chromadb.HttpClient(
                host=chroma_host,
                port=int(os.getenv("CHROMA_PORT", "8000")),
                settings=CHROMA_SETTINGS,
            )
        else:
            # 本地开发默认使用进程内持久化目录，不强制依赖 Docker Chroma。
            self.client = chromadb.PersistentClient(
                path=str(CHROMA_DIR),
                settings=CHROMA_SETTINGS,
            )
        # collection 是后续文档写入和相似度查询的实际对象。
        self.collection = self.client.get_or_create_collection(
            name=COLLECTION_NAME
        )

    def reset(self):
        """删除已索引的 Runbook，并重新创建空集合。"""
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
        """向量化并写入一一对应的文档 id、正文和来源元数据。"""
        if not ids:
            return

        self.collection.add(
            ids=ids,
            documents=documents,
            metadatas=metadatas,
            embeddings=build_embeddings(documents),
        )

    def search(self, query: str, n_results: int = 3) -> list[dict[str, Any]]:
        """返回最相近的 Runbook 片段、来源元数据和向量距离。"""
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
