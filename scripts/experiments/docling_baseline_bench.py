#!/usr/bin/env python3
"""Baseline benchmark using docling-serve HTTP API.

Measures current docling-serve performance for comparison with
FastAPI and subprocess approaches.

Usage:
    python scripts/experiments/docling_baseline_bench.py

Requirements:
    - docling-serve running on localhost:5001
    - requests package installed
"""

import json
import sys
import time
from pathlib import Path

import requests

# Configuration
DOCLING_URL = "http://localhost:5001/v1/convert/file"
PDF_DIR = Path(__file__).parent.parent.parent / "tests" / "benchmark" / "pdfs"
RESULTS_DIR = Path(__file__).parent.parent.parent / "docs" / "hybrid" / "experiments"
RESULTS_FILE = RESULTS_DIR / "baseline_results.json"


def convert_pdf(pdf_path: Path) -> dict:
    """Convert a single PDF using docling-serve API."""
    with open(pdf_path, "rb") as f:
        files = {"files": (pdf_path.name, f, "application/pdf")}
        data = {
            "to_formats": "md",
            "do_ocr": "true",
            "do_table_structure": "true",
        }

        start_time = time.perf_counter()
        response = requests.post(DOCLING_URL, files=files, data=data, timeout=300)
        elapsed = time.perf_counter() - start_time

        return {
            "filename": pdf_path.name,
            "status": "success" if response.status_code == 200 else "error",
            "elapsed": elapsed,
            "status_code": response.status_code,
        }


def main():
    """Run baseline benchmark."""
    # Check server health
    try:
        health = requests.get("http://localhost:5001/health", timeout=5)
        if health.status_code != 200:
            print("ERROR: docling-serve is not healthy", file=sys.stderr)
            sys.exit(1)
    except requests.RequestException as e:
        print(f"ERROR: Cannot connect to docling-serve: {e}", file=sys.stderr)
        sys.exit(1)

    print("=" * 60)
    print("Docling-serve Baseline Benchmark")
    print("=" * 60)
    print(f"PDF directory: {PDF_DIR}")
    print(f"Server URL: {DOCLING_URL}")
    print()

    # Get PDF files
    pdf_files = sorted(PDF_DIR.glob("*.pdf"))
    total_files = len(pdf_files)
    print(f"Found {total_files} PDF files")
    print()

    # Process each PDF
    results = []
    total_start = time.perf_counter()

    for i, pdf_path in enumerate(pdf_files, 1):
        print(f"[{i:3d}/{total_files}] Processing {pdf_path.name}...", end=" ", flush=True)

        try:
            result = convert_pdf(pdf_path)
            results.append(result)
            print(f"{result['elapsed']:.2f}s ({result['status']})")
        except Exception as e:
            results.append({
                "filename": pdf_path.name,
                "status": "error",
                "elapsed": 0,
                "error": str(e),
            })
            print(f"ERROR: {e}")

    total_elapsed = time.perf_counter() - total_start

    # Calculate statistics
    successful = [r for r in results if r["status"] == "success"]
    failed = [r for r in results if r["status"] != "success"]

    if successful:
        elapsed_times = [r["elapsed"] for r in successful]
        avg_time = sum(elapsed_times) / len(elapsed_times)
        min_time = min(elapsed_times)
        max_time = max(elapsed_times)
    else:
        avg_time = min_time = max_time = 0

    # Print summary
    print()
    print("=" * 60)
    print("RESULTS SUMMARY")
    print("=" * 60)
    print(f"Total documents:     {total_files}")
    print(f"Successful:          {len(successful)}")
    print(f"Failed:              {len(failed)}")
    print()
    print(f"Total elapsed:       {total_elapsed:.1f}s")
    print(f"Average per doc:     {avg_time:.3f}s")
    print(f"Min:                 {min_time:.3f}s")
    print(f"Max:                 {max_time:.3f}s")
    print("=" * 60)

    # Save results
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    summary = {
        "approach": "baseline",
        "description": "docling-serve HTTP API",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "config": {
            "do_ocr": True,
            "do_table_structure": True,
            "server_url": DOCLING_URL,
        },
        "statistics": {
            "total_documents": total_files,
            "successful": len(successful),
            "failed": len(failed),
            "total_elapsed": round(total_elapsed, 2),
            "elapsed_per_doc": round(avg_time, 4),
            "min_elapsed": round(min_time, 4),
            "max_elapsed": round(max_time, 4),
        },
        "details": results,
    }

    with open(RESULTS_FILE, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\nResults saved to: {RESULTS_FILE}")

    return avg_time


if __name__ == "__main__":
    main()
