#!/usr/bin/env python3
"""
Basic RAG Chunking Example - No External Dependencies

Demonstrates PDF-to-chunks conversion using only opendataloader-pdf
and Python standard library. Ready for integration with any embedding
model or vector store.

Usage:
    pip install opendataloader-pdf
    python basic_chunking.py
"""

import json
import tempfile
from pathlib import Path


import opendataloader_pdf


def convert_pdf_to_json(pdf_path: str, output_dir: str) -> Path:
    """Convert PDF to JSON and Markdown with reading order enabled."""
    opendataloader_pdf.convert(
        input_path=pdf_path,
        output_dir=output_dir,
        format="json,markdown",
        reading_order="xycut",
        quiet=True,
    )
    pdf_name = Path(pdf_path).stem
    return Path(output_dir) / f"{pdf_name}.json"


def load_document(json_path: Path) -> dict:
    """Load the JSON output from OpenDataLoader."""
    with open(json_path, encoding="utf-8") as f:
        return json.load(f)


def chunk_by_element(doc: dict) -> list[dict]:
    """
    Strategy 1: Chunk by semantic element.

    Creates one chunk per paragraph, heading, or list element.
    Best for: Fine-grained retrieval, precise citations.
    """
    chunks = []
    for element in doc.get("kids", []):
        if element.get("type") in ("paragraph", "heading", "list"):
            chunks.append({
                "text": element.get("content", ""),
                "metadata": {
                    "type": element["type"],
                    "page": element.get("page number"),
                    "bbox": element.get("bounding box"),
                    "source": doc.get("file name"),
                }
            })
    return chunks


def chunk_by_section(doc: dict) -> list[dict]:
    """
    Strategy 2: Chunk by heading/section.

    Groups content under headings into coherent sections.
    Best for: Context-rich retrieval, topic-based search.
    """
    chunks = []
    current_heading = None
    current_content: list[str] = []
    current_start_page = None

    for element in doc.get("kids", []):
        element_type = element.get("type")

        if element_type == "heading":
            # Save previous section
            if current_content:
                chunks.append({
                    "text": "\n".join(current_content),
                    "metadata": {
                        "heading": current_heading,
                        "page": current_start_page,
                        "source": doc.get("file name"),
                    }
                })
            current_heading = element.get("content", "")
            current_content = [current_heading]
            current_start_page = element.get("page number")
        elif element_type in ("paragraph", "list"):
            content = element.get("content", "")
            if content:
                current_content.append(content)

    # Save the last section
    if current_content:
        chunks.append({
            "text": "\n".join(current_content),
            "metadata": {
                "heading": current_heading,
                "page": current_start_page,
                "source": doc.get("file name"),
            }
        })

    return chunks


def chunk_with_min_size(doc: dict, min_chars: int = 200) -> list[dict]:
    """
    Strategy 3: Merge adjacent elements until minimum size.

    Combines small paragraphs to avoid overly fragmented chunks.
    Best for: Balanced chunk sizes, reducing noise.
    """
    chunks = []
    buffer_text = ""
    buffer_pages: list[int] = []

    for element in doc.get("kids", []):
        if element.get("type") in ("paragraph", "heading", "list"):
            content = element.get("content", "")
            page = element.get("page number")

            buffer_text += content + "\n"
            if page and page not in buffer_pages:
                buffer_pages.append(page)

            if len(buffer_text) >= min_chars:
                chunks.append({
                    "text": buffer_text.strip(),
                    "metadata": {
                        "pages": buffer_pages.copy(),
                        "source": doc.get("file name"),
                    }
                })
                buffer_text = ""
                buffer_pages = []

    # Save remaining buffer
    if buffer_text.strip():
        chunks.append({
            "text": buffer_text.strip(),
            "metadata": {
                "pages": buffer_pages,
                "source": doc.get("file name"),
            }
        })

    return chunks


def format_citation(metadata: dict) -> str:
    """Generate a citation string from chunk metadata."""
    source = metadata.get("source", "unknown")
    page = metadata.get("page") or (metadata.get("pages", [None]) or [None])[0]
    bbox = metadata.get("bbox")

    citation = f"Source: {source}"
    if page:
        citation += f", Page {page}"
    if bbox:
        citation += f", Position ({bbox[0]:.0f}, {bbox[1]:.0f})"

    return citation


def main():
    # Find sample PDF relative to this script
    # Using 1901.03003.pdf - a multi-page academic paper with complex layout
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent.parent
    sample_pdf = repo_root / "samples" / "pdf" / "1901.03003.pdf"

    if not sample_pdf.exists():
        print(f"Sample PDF not found at: {sample_pdf}")
        print("Make sure you're running from the repository.")
        return

    print(f"Processing: {sample_pdf.name}")
    print("=" * 50)

    # Convert PDF to JSON in a temp directory
    with tempfile.TemporaryDirectory() as temp_dir:
        json_path = convert_pdf_to_json(str(sample_pdf), temp_dir)
        doc = load_document(json_path)

        print(f"Document: {doc.get('file name')}")
        print(f"Pages: {doc.get('number of pages')}")
        print(f"Elements: {len(doc.get('kids', []))}")

        # Strategy 1: By element
        print("\n--- Strategy 1: Chunk by Element ---")
        element_chunks = chunk_by_element(doc)
        print(f"Created {len(element_chunks)} chunks")
        for i, chunk in enumerate(element_chunks[:3]):
            text_preview = chunk["text"][:60] + "..." if len(chunk["text"]) > 60 else chunk["text"]
            print(f"  [{i+1}] {text_preview}")
            print(f"      {format_citation(chunk['metadata'])}")

        # Strategy 2: By section
        print("\n--- Strategy 2: Chunk by Section ---")
        section_chunks = chunk_by_section(doc)
        print(f"Created {len(section_chunks)} chunks")
        for i, chunk in enumerate(section_chunks[:2]):
            heading = chunk["metadata"].get("heading", "No heading")
            print(f"  Section: {heading}")
            print(f"  Text: {chunk['text'][:60]}...")

        # Strategy 3: Merged
        print("\n--- Strategy 3: Merged Chunks (min 200 chars) ---")
        merged_chunks = chunk_with_min_size(doc, min_chars=200)
        print(f"Created {len(merged_chunks)} chunks")
        for i, chunk in enumerate(merged_chunks[:2]):
            print(f"  [{i+1}] {len(chunk['text'])} chars: {chunk['text'][:50]}...")

        # Show example chunk structure
        print("\n--- Example Chunk Structure ---")
        print("Each chunk has 'text' and 'metadata' ready for embedding:")
        if element_chunks:
            print(json.dumps(element_chunks[0], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
