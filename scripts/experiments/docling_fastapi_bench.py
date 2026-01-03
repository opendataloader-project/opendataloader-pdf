#!/usr/bin/env python3
"""FastAPI experiment benchmark using docling SDK directly.

Tests the hypothesis that a lightweight FastAPI server with
DocumentConverter singleton is faster than docling-serve.

This script:
1. Starts an embedded FastAPI server (port 5002)
2. Converts all 200 benchmark PDFs
3. Measures and reports performance

Usage:
    python scripts/experiments/docling_fastapi_bench.py

Requirements:
    - docling package installed
    - fastapi, uvicorn packages installed
"""

import json
import multiprocessing
import os
import sys
import tempfile
import time
from pathlib import Path

import requests

# Configuration
FASTAPI_PORT = 5002
FASTAPI_URL = f"http://localhost:{FASTAPI_PORT}/convert"
PDF_DIR = Path(__file__).parent.parent.parent / "tests" / "benchmark" / "pdfs"
RESULTS_DIR = Path(__file__).parent.parent.parent / "docs" / "hybrid" / "experiments"
RESULTS_FILE = RESULTS_DIR / "fastapi_results.json"


def run_server():
    """Run FastAPI server in a subprocess."""
    import uvicorn
    from fastapi import FastAPI, File, UploadFile
    from fastapi.responses import JSONResponse

    # Import docling after fork to avoid issues
    from docling.datamodel.base_models import InputFormat
    from docling.datamodel.pipeline_options import (
        EasyOcrOptions,
        OcrOptions,
        PdfPipelineOptions,
        TableFormerMode,
        TableStructureOptions,
    )
    from docling.document_converter import DocumentConverter, PdfFormatOption

    app = FastAPI()

    # Create singleton DocumentConverter with warm-up
    print("Initializing DocumentConverter...", flush=True)

    pipeline_options = PdfPipelineOptions(
        do_ocr=True,
        do_table_structure=True,
        ocr_options=EasyOcrOptions(force_full_page_ocr=False),
        table_structure_options=TableStructureOptions(
            mode=TableFormerMode.ACCURATE
        ),
    )

    converter = DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)
        }
    )
    print("DocumentConverter initialized.", flush=True)

    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.post("/convert")
    async def convert(file: UploadFile = File(...)):
        # Save uploaded file to temp location
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name

        try:
            start = time.perf_counter()
            result = converter.convert(tmp_path)
            elapsed = time.perf_counter() - start

            md_content = result.document.export_to_markdown()

            return JSONResponse({
                "status": "success",
                "markdown": md_content,
                "processing_time": elapsed,
            })
        except Exception as e:
            return JSONResponse({
                "status": "error",
                "error": str(e),
            }, status_code=500)
        finally:
            os.unlink(tmp_path)

    uvicorn.run(app, host="0.0.0.0", port=FASTAPI_PORT, log_level="warning")


def convert_pdf(pdf_path: Path) -> dict:
    """Convert a single PDF using FastAPI server."""
    with open(pdf_path, "rb") as f:
        files = {"file": (pdf_path.name, f, "application/pdf")}

        start_time = time.perf_counter()
        response = requests.post(FASTAPI_URL, files=files, timeout=300)
        elapsed = time.perf_counter() - start_time

        if response.status_code == 200:
            data = response.json()
            return {
                "filename": pdf_path.name,
                "status": "success",
                "elapsed": elapsed,
                "server_time": data.get("processing_time", 0),
            }
        else:
            return {
                "filename": pdf_path.name,
                "status": "error",
                "elapsed": elapsed,
                "error": response.text,
            }


def wait_for_server(max_retries=60, delay=1.0):
    """Wait for server to be ready."""
    for i in range(max_retries):
        try:
            resp = requests.get(f"http://localhost:{FASTAPI_PORT}/health", timeout=5)
            if resp.status_code == 200:
                return True
        except requests.RequestException:
            pass
        time.sleep(delay)
    return False


def main():
    """Run FastAPI benchmark."""
    print("=" * 60)
    print("FastAPI Experiment Benchmark")
    print("=" * 60)
    print(f"PDF directory: {PDF_DIR}")
    print(f"Server URL: {FASTAPI_URL}")
    print()

    # Start server in subprocess
    print("Starting FastAPI server...", flush=True)
    server_process = multiprocessing.Process(target=run_server, daemon=True)
    server_process.start()

    # Wait for server to be ready
    print("Waiting for server to initialize (including model loading)...", flush=True)
    if not wait_for_server(max_retries=120, delay=1.0):
        print("ERROR: Server failed to start", file=sys.stderr)
        server_process.terminate()
        sys.exit(1)

    print("Server is ready.", flush=True)
    print()

    # Get PDF files
    pdf_files = sorted(PDF_DIR.glob("*.pdf"))
    total_files = len(pdf_files)
    print(f"Found {total_files} PDF files")
    print()

    # Process each PDF
    results = []
    total_start = time.perf_counter()

    try:
        for i, pdf_path in enumerate(pdf_files, 1):
            print(f"[{i:3d}/{total_files}] Processing {pdf_path.name}...", end=" ", flush=True)

            try:
                result = convert_pdf(pdf_path)
                results.append(result)
                server_time = result.get("server_time", 0)
                print(f"{result['elapsed']:.2f}s (server: {server_time:.2f}s) ({result['status']})")
            except Exception as e:
                results.append({
                    "filename": pdf_path.name,
                    "status": "error",
                    "elapsed": 0,
                    "error": str(e),
                })
                print(f"ERROR: {e}")

        total_elapsed = time.perf_counter() - total_start

    finally:
        # Shutdown server
        print("\nShutting down server...", flush=True)
        server_process.terminate()
        server_process.join(timeout=5)

    # Calculate statistics
    successful = [r for r in results if r["status"] == "success"]
    failed = [r for r in results if r["status"] != "success"]

    if successful:
        elapsed_times = [r["elapsed"] for r in successful]
        server_times = [r.get("server_time", 0) for r in successful]
        avg_time = sum(elapsed_times) / len(elapsed_times)
        avg_server_time = sum(server_times) / len(server_times)
        min_time = min(elapsed_times)
        max_time = max(elapsed_times)
    else:
        avg_time = avg_server_time = min_time = max_time = 0

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
    print(f"Average per doc:     {avg_time:.3f}s  (target: < 0.8s)")
    print(f"Avg server time:     {avg_server_time:.3f}s")
    print(f"Min:                 {min_time:.3f}s")
    print(f"Max:                 {max_time:.3f}s")
    print()

    # Success/Failure check
    if avg_time < 0.8:
        print("✅ SUCCESS: Average time is below 0.8s threshold!")
    else:
        print("❌ FAILURE: Average time exceeds 0.8s threshold")
        print("   Plan may need to be discarded.")

    print("=" * 60)

    # Save results
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    summary = {
        "approach": "fastapi",
        "description": "FastAPI server with docling SDK singleton",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "config": {
            "do_ocr": True,
            "do_table_structure": True,
            "server_port": FASTAPI_PORT,
        },
        "statistics": {
            "total_documents": total_files,
            "successful": len(successful),
            "failed": len(failed),
            "total_elapsed": round(total_elapsed, 2),
            "elapsed_per_doc": round(avg_time, 4),
            "server_time_per_doc": round(avg_server_time, 4),
            "min_elapsed": round(min_time, 4),
            "max_elapsed": round(max_time, 4),
        },
        "threshold": {
            "target": 0.8,
            "passed": avg_time < 0.8,
        },
        "details": results,
    }

    with open(RESULTS_FILE, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\nResults saved to: {RESULTS_FILE}")

    return avg_time


if __name__ == "__main__":
    # Required for multiprocessing on macOS
    multiprocessing.set_start_method("spawn", force=True)
    main()
