#!/usr/bin/env python3
"""
Batch Processing Example

Demonstrates processing multiple PDFs in a single invocation to avoid
repeated Java JVM startup overhead. This is the recommended approach
for large-scale document pipelines.

Requires Python 3.9+.

Usage:
    pip install opendataloader-pdf
    python batch_processing.py
"""

from __future__ import annotations

import json
import tempfile
import time
from pathlib import Path

import opendataloader_pdf


def batch_convert(pdf_paths: list[str], output_dir: str) -> list[Path]:
    """Convert multiple PDFs in a single JVM invocation."""
    opendataloader_pdf.convert(
        input_path=pdf_paths,
        output_dir=output_dir,
        format="json,markdown",
        quiet=True,
    )
    # Collect output JSON files
    return sorted(Path(output_dir).glob("*.json"))


def convert_directory(directory: str, output_dir: str) -> list[Path]:
    """Convert all PDFs in a directory (recursive)."""
    opendataloader_pdf.convert(
        input_path=directory,
        output_dir=output_dir,
        format="json,markdown",
        quiet=True,
    )
    return sorted(Path(output_dir).glob("*.json"))


def summarize_results(json_files: list[Path]) -> None:
    """Print a summary of all converted documents."""
    total_pages = 0
    total_elements = 0

    print(f"\n{'Document':<40} {'Pages':>6} {'Top-level':>9}")
    print("-" * 58)

    for json_path in json_files:
        with open(json_path, encoding="utf-8") as f:
            doc = json.load(f)
        pages = doc.get("number of pages", 0)
        elements = len(doc.get("kids", []))
        total_pages += pages
        total_elements += elements
        print(f"{json_path.stem:<40} {pages:>6} {elements:>9}")

    print("-" * 58)
    print(f"{'Total':<40} {total_pages:>6} {total_elements:>9}")
    print(f"\nProcessed {len(json_files)} documents")


def main():
    # Find sample PDFs relative to this script
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent.parent
    samples_dir = repo_root / "samples" / "pdf"

    pdf_files = sorted(samples_dir.glob("*.pdf"))
    if not pdf_files:
        print(f"No sample PDFs found at: {samples_dir}")
        return

    print(f"Found {len(pdf_files)} PDFs in {samples_dir.name}/")
    for p in pdf_files:
        print(f"  - {p.name}")

    # --- Method 1: Pass a list of files ---
    print("\n" + "=" * 58)
    print("Method 1: Batch convert with file list")
    print("=" * 58)

    with tempfile.TemporaryDirectory() as temp_dir:
        start = time.perf_counter()
        json_files = batch_convert(
            [str(p) for p in pdf_files],
            temp_dir,
        )
        elapsed = time.perf_counter() - start

        summarize_results(json_files)
        print(f"Time: {elapsed:.2f}s (single JVM invocation)")

    # --- Method 2: Pass a directory ---
    # Note: directory input recursively finds PDFs in subdirectories,
    # so the file count may differ from Method 1 (which uses top-level glob).
    print("\n" + "=" * 58)
    print("Method 2: Convert entire directory")
    print("=" * 58)

    with tempfile.TemporaryDirectory() as temp_dir:
        start = time.perf_counter()
        json_files = convert_directory(str(samples_dir), temp_dir)
        elapsed = time.perf_counter() - start

        summarize_results(json_files)
        print(f"Time: {elapsed:.2f}s (single JVM invocation)")


if __name__ == "__main__":
    main()
