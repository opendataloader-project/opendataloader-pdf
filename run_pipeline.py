#!/usr/bin/env python3
"""
OpenDataLoader PDF - Ultimate Quality Turnkey Pipeline
Automates:
1. Cross-platform environment configuration (Java detection, PATH, OS-specific GPU/MPS accelerators)
2. Lifecycle management of the Hybrid Docling Server
3. Batch PDF conversion using maximal quality settings (tables, reading order, 300 DPI)
4. Post-processing & verification summary
"""

import json
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

# Cross-platform Java & Environment Setup
HOMEBREW_JDK = "/opt/homebrew/opt/openjdk@17/bin"
if os.path.exists(HOMEBREW_JDK) and HOMEBREW_JDK not in os.environ.get("PATH", ""):
    os.environ["PATH"] = f"{HOMEBREW_JDK}{os.pathsep}{os.environ.get('PATH', '')}"

if "JAVA_HOME" in os.environ:
    java_bin = os.path.join(os.environ["JAVA_HOME"], "bin")
    if os.path.exists(java_bin) and java_bin not in os.environ.get("PATH", ""):
        os.environ["PATH"] = f"{java_bin}{os.pathsep}{os.environ.get('PATH', '')}"

HYBRID_HOST = "127.0.0.1"
HYBRID_PORT = 5002
HYBRID_URL = f"http://{HYBRID_HOST}:{HYBRID_PORT}"


def is_server_ready(host=HYBRID_HOST, port=HYBRID_PORT, timeout=2.0) -> bool:
    """Check if the hybrid server is responding."""
    try:
        req = urllib.request.Request(f"http://{host}:{port}/health", headers={"User-Agent": "OpenDataLoader-Probe"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status == 200
    except Exception:
        return False


def detect_device() -> str:
    """Dynamically detect the fastest available hardware accelerator."""
    try:
        import torch
        if torch.cuda.is_available():
            return "cuda"
        if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
            return "mps"
    except Exception:
        pass
    return "cpu"


def start_hybrid_server() -> subprocess.Popen | None:
    """Start local docling hybrid server in the background."""
    if is_server_ready():
        print(f"[*] Hybrid server is already running on {HYBRID_URL}")
        return None

    # Automatically choose CUDA (NVIDIA GPU), MPS (Apple Silicon), or CPU
    device = detect_device()
    print(f"[*] Detected hardware accelerator: {device.upper()}")
    print(f"[*] Starting Hybrid AI Server on {HYBRID_URL} (device={device})...")

    cmd = [
        "opendataloader-pdf-hybrid",
        "--host", HYBRID_HOST,
        "--port", str(HYBRID_PORT),
        "--device", device,
        "--enrich-formula",
        "--enrich-picture-description",
        "--no-ocr",
    ]

    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    print("[*] Initializing AI server & ML models...")
    for _ in range(60):
        if is_server_ready():
            print("[+] Hybrid AI server is ready and listening!")
            return proc
        time.sleep(1)

    print("[!] Server process started. Proceeding...")
    return proc


def stop_hybrid_server(proc: subprocess.Popen | None) -> None:
    """Stop the hybrid server if it was started by this invocation."""
    if proc is None or proc.poll() is not None:
        return

    proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()


def run_conversion(input_path: str = "data", output_dir: str = "data/output_perfect"):
    """Run batch conversion with maximal quality parameters."""
    import opendataloader_pdf

    input_p = Path(input_path).resolve()
    output_p = Path(output_dir).resolve()
    output_p.mkdir(parents=True, exist_ok=True)

    print("\n=======================================================")
    print(f" Parsing PDF(s) from: {input_p}")
    print(f" Output Directory:    {output_p}")
    print(" Mode:                Hybrid (docling-fast, full AI processing)")
    print(" Acceleration:        CUDA / MPS / CPU (auto-detected)")
    print(" Table Handling:      Cluster + HTML tables in Markdown")
    print(" Image Quality:       300 DPI")
    print("=======================================================\n")

    start_time = time.perf_counter()

    opendataloader_pdf.convert(
        input_path=str(input_p),
        output_dir=str(output_p),
        format="markdown,json",
        hybrid="docling-fast",
        hybrid_mode="full",
        markdown_with_html=True,
        table_method="cluster",
        image_resolution="300.0",
        hybrid_url=HYBRID_URL,
        hybrid_timeout="0",
        hybrid_fallback=True
    )

    elapsed = time.perf_counter() - start_time
    print(f"\n[+] Conversion completed in {elapsed:.2f}s!")

    summarize_outputs(output_p)


def summarize_outputs(output_p: Path):
    """Summarize the converted results."""
    json_files = list(output_p.glob("*.json"))
    md_files = list(output_p.glob("*.md"))

    print("\n---------------- Extracted Files ----------------")
    for mf in md_files:
        size_kb = mf.stat().st_size / 1024
        print(f"  - Markdown: {mf.name} ({size_kb:.1f} KB)")
    for jf in json_files:
        size_kb = jf.stat().st_size / 1024
        print(f"  - JSON:     {jf.name} ({size_kb:.1f} KB)")

    print("\n---------------- Verification Summary ----------------")
    for jf in json_files:
        try:
            with open(jf, "r", encoding="utf-8") as f:
                doc = json.load(f)
            pages = doc.get("number of pages", 0)
            kids = doc.get("kids", [])
            formulas = sum(1 for k in kids if k.get("type") == "formula")
            pictures = sum(1 for k in kids if k.get("type") in ("image", "picture"))
            tables = sum(1 for k in kids if k.get("type") == "table")
            print(f"File: {jf.stem}")
            print(f"  • Pages: {pages}")
            print(f"  • Elements: {len(kids)}")
            print(f"  • Formulas: {formulas}")
            print(f"  • Tables: {tables}")
            print(f"  • Figures / Images: {pictures}")
        except Exception as e:
            print(f"  • Could not inspect {jf.name}: {e}")
    print("------------------------------------------------------\n")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in ("-h", "--help"):
        print("Usage: python run_pipeline.py [INPUT_PATH] [OUTPUT_DIR]")
        print("Default INPUT_PATH: data")
        print("Default OUTPUT_DIR: data/output_perfect")
        sys.exit(0)

    server_proc = start_hybrid_server()
    try:
        input_target = sys.argv[1] if len(sys.argv) > 1 else "data"
        out_target = sys.argv[2] if len(sys.argv) > 2 else "data/output_perfect"
        run_conversion(input_target, out_target)
    finally:
        stop_hybrid_server(server_proc)
