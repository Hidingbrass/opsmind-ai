import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.rag.chroma_store import ChromaRunbookStore


RUNBOOK_DIR = PROJECT_ROOT / "knowledge" / "runbooks"


def split_markdown_by_heading(content: str) -> list[str]:
    """Split on level-two headings and keep the Runbook title in each chunk."""
    lines = content.splitlines()
    title_line = ""
    title_index = -1
    for index, line in enumerate(lines):
        if line.startswith("# "):
            title_line = line.strip()
            title_index = index
            break
    body_lines = [
        line
        for index, line in enumerate(lines)
        if index != title_index
    ]

    chunks = []
    current_lines = []

    for line in body_lines:
        if line.startswith("## ") and current_lines:
            chunk = "\n".join(current_lines).strip()
            if chunk:
                chunks.append(chunk)
            current_lines = []

        current_lines.append(line)

    if current_lines:
        chunk = "\n".join(current_lines).strip()
        if chunk:
            chunks.append(chunk)

    if not chunks:
        fallback = content.strip()
        return [fallback] if fallback else []

    if not title_line:
        return chunks
    return [f"{title_line}\n\n{chunk}" for chunk in chunks]


def heading_value(content: str, prefix: str) -> str:
    """Extract a heading value for retrieval metadata."""
    for line in content.splitlines():
        if line.startswith(prefix):
            return line[len(prefix):].strip()
    return ""


def build_documents():
    ids = []
    documents = []
    metadatas = []

    for file_path in sorted(RUNBOOK_DIR.glob("*.md")):
        content = file_path.read_text(encoding="utf-8")
        chunks = split_markdown_by_heading(content)
        title = heading_value(content, "# ")

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
                    "title": title,
                    "section": heading_value(chunk, "## "),
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
