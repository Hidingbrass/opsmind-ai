from pathlib import Path
from typing import Any

import chromadb


PROJECT_ROOT = Path(__file__).resolve().parents[2]
CHROMA_DIR = PROJECT_ROOT / ".chroma"
COLLECTION_NAME = "opsmind_runbooks"


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
        )

    def search(self, query: str, n_results: int = 3) -> list[dict[str, Any]]:
        result = self.collection.query(
            query_texts=[query],
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