"""PDF to Markdown conversion runner.

This module iterates through a directory of PDF files and converts them to
Markdown using various parsing engines. For each engine, it records the
processing time and saves it to a summary.json file.

The script can be executed directly. By default, it processes all PDFs in the
'pdfs' directory with all available engines and stores the output under the
'prediction' directory.
"""

import argparse
import json
import logging
from pathlib import Path
import time
from typing import List, Optional

import cpuinfo

from engine_registry import ENGINES, ENGINE_DISPATCH

DEFAULT_INPUT_DIR = "pdfs"


def process_markdown(
    engine_name: str,
    input_dir_name: str,
    doc_id: Optional[str] = None,
):
    """Run PDF-to-Markdown conversion for a single engine.

    Creates an output directory, converts all PDFs from the input directory
    to Markdown, and writes a summary file with performance metrics.
    """
    project_root = Path(__file__).parent.parent.resolve()

    engine_version = ENGINES[engine_name]
    input_dir = Path(input_dir_name).resolve()
    output_dir = project_root / "prediction" / engine_name / "markdown"
    output_dir.mkdir(parents=True, exist_ok=True)

    if doc_id:
        candidate_path = input_dir / f"{doc_id.strip()}.pdf"
        if not candidate_path.exists():
            raise FileNotFoundError(f"'{doc_id.strip()}.pdf' not found in {input_dir}.")
        document_paths = [candidate_path]
        input_path = candidate_path
    else:
        document_paths = sorted(input_dir.glob("*.pdf"))
        input_path = input_dir
        if not document_paths:
            raise FileNotFoundError(f"No PDFs found in {input_dir}.")

    document_count = len(document_paths)
    logging.info(
        "Processing %d PDFs with %s %s...", document_count, engine_name, engine_version
    )

    start_time = time.time()

    to_markdown_func = ENGINE_DISPATCH.get(engine_name)
    if to_markdown_func:
        to_markdown_func(document_paths, input_path, output_dir)
    else:
        raise ValueError(f"Unknown engine: {engine_name}")

    end_time = time.time()
    total_elapsed = end_time - start_time

    elapsed_per_doc = total_elapsed / document_count if document_count > 0 else 0
    processor = cpuinfo.get_cpu_info()["brand_raw"]
    summary_data = {
        "engine_name": engine_name,
        "engine_version": engine_version,
        "processor": processor,
        "document_count": document_count,
        "total_elapsed": total_elapsed,
        "elapsed_per_doc": elapsed_per_doc,
        "date": time.strftime("%Y-%m-%d"),
    }

    summary_file_path = output_dir.parent / "summary.json"
    with open(summary_file_path, "w", encoding="utf-8") as f:
        json.dump(summary_data, f, indent=4)

    logging.info("Summary saved to %s", summary_file_path)


def _parse_args(argv: Optional[List[str]] = None):
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description="Parse PDFs and convert to Markdown.")
    parser.add_argument(
        "--input-dir",
        type=str,
        default=DEFAULT_INPUT_DIR,
        help="Directory containing PDFs to process (defaults to <project root>/pdfs)",
    )
    parser.add_argument(
        "--engine",
        type=str,
        default=None,
        choices=list(ENGINES.keys()),
        help="Name of the engine to use for parsing. If not specified, all engines are used.",
    )
    parser.add_argument(
        "--doc-id",
        type=str,
        default=None,
        help="Process only the specified document",
    )
    parser.add_argument(
        "--log-level",
        type=str,
        choices=list(logging.getLevelNamesMapping().keys()),
        default="INFO",
        help="Python logging level (e.g. INFO, DEBUG)",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> None:
    """Parse arguments and run the PDF-to-Markdown conversion."""
    args = _parse_args(argv)
    logging.basicConfig(level=getattr(logging, args.log_level.upper(), logging.INFO))

    if args.engine is None:
        engines = list(ENGINES.keys())
    else:
        engines = [args.engine]

    for engine_name in engines:
        process_markdown(engine_name, args.input_dir, args.doc_id)


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    main()
