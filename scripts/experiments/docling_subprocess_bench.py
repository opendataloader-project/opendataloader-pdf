#!/usr/bin/env python3
"""Subprocess experiment benchmark using docling SDK directly.

Tests the subprocess approach where each PDF is processed by
invoking a Python worker script via subprocess.

This approach has overhead from:
1. Python interpreter startup
2. Model loading per-process (unless using persistent worker)

For this experiment, we test a persistent worker approach:
- Single Python process stays alive
- Receives PDF paths via stdin, outputs JSON via stdout

Usage:
    python scripts/experiments/docling_subprocess_bench.py

Requirements:
    - docling package installed
"""

import base64
import json
import subprocess
import sys
import tempfile
import time
from pathlib import Path

# Configuration
PDF_DIR = Path(__file__).parent.parent.parent / "tests" / "benchmark" / "pdfs"
RESULTS_DIR = Path(__file__).parent.parent.parent / "docs" / "hybrid" / "experiments"
RESULTS_FILE = RESULTS_DIR / "subprocess_results.json"

# Worker script inline - will be written to temp file
WORKER_SCRIPT = '''
import base64
import json
import sys
import time
import tempfile
import os

# Initialize docling once
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import (
    EasyOcrOptions,
    PdfPipelineOptions,
    TableFormerMode,
    TableStructureOptions,
)
from docling.document_converter import DocumentConverter, PdfFormatOption

print("WORKER_READY", file=sys.stderr, flush=True)

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

print("CONVERTER_READY", file=sys.stderr, flush=True)

# Process requests from stdin
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue

    try:
        request = json.loads(line)
        pdf_base64 = request.get("pdf_base64")
        filename = request.get("filename", "document.pdf")

        # Decode and write to temp file
        pdf_bytes = base64.b64decode(pdf_base64)
        with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
            tmp.write(pdf_bytes)
            tmp_path = tmp.name

        try:
            start = time.perf_counter()
            result = converter.convert(tmp_path)
            elapsed = time.perf_counter() - start

            md_content = result.document.export_to_markdown()

            response = {
                "status": "success",
                "filename": filename,
                "markdown": md_content,
                "processing_time": elapsed,
            }
        except Exception as e:
            response = {
                "status": "error",
                "filename": filename,
                "error": str(e),
            }
        finally:
            os.unlink(tmp_path)

        print(json.dumps(response), flush=True)

    except Exception as e:
        response = {
            "status": "error",
            "error": str(e),
        }
        print(json.dumps(response), flush=True)
'''


def convert_pdf(process: subprocess.Popen, pdf_path: Path) -> dict:
    """Convert a single PDF using subprocess worker."""
    # Read PDF and encode as base64
    with open(pdf_path, "rb") as f:
        pdf_bytes = f.read()
    pdf_base64 = base64.b64encode(pdf_bytes).decode("ascii")

    # Send request
    request = {
        "pdf_base64": pdf_base64,
        "filename": pdf_path.name,
    }

    start_time = time.perf_counter()
    process.stdin.write(json.dumps(request) + "\n")
    process.stdin.flush()

    # Read response
    response_line = process.stdout.readline()
    elapsed = time.perf_counter() - start_time

    if response_line:
        try:
            response = json.loads(response_line)
            response["client_elapsed"] = elapsed
            return response
        except json.JSONDecodeError as e:
            return {
                "filename": pdf_path.name,
                "status": "error",
                "error": f"JSON decode error: {e}",
                "client_elapsed": elapsed,
            }
    else:
        return {
            "filename": pdf_path.name,
            "status": "error",
            "error": "No response from worker",
            "client_elapsed": elapsed,
        }


def main():
    """Run subprocess benchmark."""
    print("=" * 60)
    print("Subprocess Experiment Benchmark")
    print("=" * 60)
    print(f"PDF directory: {PDF_DIR}")
    print()

    # Write worker script to temp file
    with tempfile.NamedTemporaryFile(mode="w", suffix=".py", delete=False) as f:
        f.write(WORKER_SCRIPT)
        worker_path = f.name

    print("Starting worker process...", flush=True)

    try:
        # Start worker process
        process = subprocess.Popen(
            [sys.executable, worker_path],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,  # Line buffered
        )

        # Wait for worker to be ready (read stderr for status messages)
        print("Waiting for worker to initialize (including model loading)...", flush=True)

        ready_count = 0
        while ready_count < 2:
            line = process.stderr.readline()
            if "WORKER_READY" in line:
                ready_count += 1
                print("  - Worker process started", flush=True)
            elif "CONVERTER_READY" in line:
                ready_count += 1
                print("  - DocumentConverter initialized", flush=True)
            elif process.poll() is not None:
                print("ERROR: Worker process died unexpectedly", file=sys.stderr)
                remaining_stderr = process.stderr.read()
                print(remaining_stderr, file=sys.stderr)
                sys.exit(1)

        print("Worker is ready.", flush=True)
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
                result = convert_pdf(process, pdf_path)
                results.append(result)
                server_time = result.get("processing_time", 0)
                client_time = result.get("client_elapsed", 0)
                print(f"{client_time:.2f}s (server: {server_time:.2f}s) ({result['status']})")
            except Exception as e:
                results.append({
                    "filename": pdf_path.name,
                    "status": "error",
                    "client_elapsed": 0,
                    "error": str(e),
                })
                print(f"ERROR: {e}")

        total_elapsed = time.perf_counter() - total_start

    finally:
        # Shutdown worker
        print("\nShutting down worker...", flush=True)
        if process.poll() is None:
            process.stdin.close()
            process.terminate()
            process.wait(timeout=5)

        # Clean up worker script
        import os
        os.unlink(worker_path)

    # Calculate statistics
    successful = [r for r in results if r["status"] == "success"]
    failed = [r for r in results if r["status"] != "success"]

    if successful:
        client_times = [r.get("client_elapsed", 0) for r in successful]
        server_times = [r.get("processing_time", 0) for r in successful]
        avg_client_time = sum(client_times) / len(client_times)
        avg_server_time = sum(server_times) / len(server_times)
        min_time = min(client_times)
        max_time = max(client_times)
    else:
        avg_client_time = avg_server_time = min_time = max_time = 0

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
    print(f"Average per doc:     {avg_client_time:.3f}s  (target: < 1.0s)")
    print(f"Avg server time:     {avg_server_time:.3f}s")
    print(f"Min:                 {min_time:.3f}s")
    print(f"Max:                 {max_time:.3f}s")
    print()

    # Success/Failure check
    if avg_client_time < 1.0:
        print("✅ SUCCESS: Average time is below 1.0s threshold!")
    else:
        print("❌ FAILURE: Average time exceeds 1.0s threshold")
        print("   Subprocess approach will be excluded.")

    print("=" * 60)

    # Save results
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    summary = {
        "approach": "subprocess",
        "description": "Persistent Python subprocess with docling SDK",
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "config": {
            "do_ocr": True,
            "do_table_structure": True,
            "worker_type": "persistent",
        },
        "statistics": {
            "total_documents": total_files,
            "successful": len(successful),
            "failed": len(failed),
            "total_elapsed": round(total_elapsed, 2),
            "elapsed_per_doc": round(avg_client_time, 4),
            "server_time_per_doc": round(avg_server_time, 4),
            "min_elapsed": round(min_time, 4),
            "max_elapsed": round(max_time, 4),
        },
        "threshold": {
            "target": 1.0,
            "passed": avg_client_time < 1.0,
        },
        "details": results,
    }

    with open(RESULTS_FILE, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\nResults saved to: {RESULTS_FILE}")

    return avg_client_time


if __name__ == "__main__":
    main()
