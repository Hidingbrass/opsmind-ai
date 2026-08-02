from pathlib import Path
from typing import Any

import chromadb
from sentence_transformers import SentenceTransformer


PROJECT_ROOT = Path(__file__).resolve().parents[2]
CHROMA_DIR = PROJECT_ROOT / ".chroma"
COLLECTION_NAME = "opsmind_runbooks"
EMBEDDING_MODEL_NAME = "BAAI/bge-small-zh-v1.5"

_embedding_model: SentenceTransformer | None = None


def get_embedding_model() -> SentenceTransformer:
    global _embedding_model

    if _embedding_model is None:
        _embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)

    return _embedding_model


def build_embeddings(texts: list[str]) -> list[list[float]]:
    model = get_embedding_model()
    embeddings = model.encode(
        texts,
        normalize_embeddings=True,
    )
    return embeddings.tolist()


class ChromaRunbookStore:
    def __init__(self):
        self.client = chromadb.PersistentClient(path=str(CHROMA_DIR))
        self.collection = self.client.get_or_create_collection(
            name=COLLECTION_NAME
        )

    def reset(self):
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
        if not ids:
            return

        self.collection.add(
            ids=ids,
            documents=documents,
            metadatas=metadatas,
            embeddings=build_embeddings(documents),
        )

    def search(self, query: str, n_results: int = 3) -> list[dict[str, Any]]:
        result = self.collection.query(
            query_embeddings=build_embeddings([query]),
            n_results=n_results,
        )

        documents = result.get("documents", [[]])[0]
        metadatas = result.get("metadatas", [[]])[0]
        distances = result.get("distances", [[]])[0]

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
