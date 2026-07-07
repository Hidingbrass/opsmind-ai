from pathlib import Path

from app.rag.chroma_store import ChromaRunbookStore


PROJECT_ROOT = Path(__file__).resolve().parents[1]
RUNBOOK_DIR = PROJECT_ROOT / "knowledge" / "runbooks"


def split_markdown_by_heading(content: str) -> list[str]:
    chunks = []
    current_lines = []

    for line in content.splitlines():
        if line.startswith("## ") and current_lines:
            chunks.append("\n".join(current_lines).strip())
            current_lines = []

        current_lines.append(line)

    if current_lines:
        chunks.append("\n".join(current_lines).strip())

    return [chunk for chunk in chunks if chunk]


def build_documents():
    ids = []
    documents = []
    metadatas = []

    for file_path in sorted(RUNBOOK_DIR.glob("*.md")):
        content = file_path.read_text(encoding="utf-8")
        chunks = split_markdown_by_heading(content)

        for index, chunk in enumerate(chunks):
            doc_id = f"{file_path.stem}-{index}"

            ids.append(doc_id)
            documents.append(chunk)
            metadatas.append(
                {
                    "source": str(file_path.relative_to(PROJECT_ROOT)),
                    "filename": file_path.name,
                    "chunk_index": index,
                    "doc_type": "runbook",
                }
            )

    return ids, documents, metadatas


def main():
    ids, documents, metadatas = build_documents()

    store = ChromaRunbookStore()
    store.reset()
    store.add_documents(
        ids=ids,
        documents=documents,
        metadatas=metadatas,
    )

    print(f"已导入 {len(ids)} 个 Runbook 文档片段到 Chroma")


if __name__ == "__main__":
    main()
