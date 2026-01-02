#!/usr/bin/env python3
"""Benchmark runner for opendataloader-pdf.

Executes PDF parsing, evaluation, and optional regression checking.
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path
from typing import List, Optional, Sequence

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent / "src"))

from evaluator import (
    DEFAULT_GT_DIR,
    DEFAULT_OUTPUT_FILENAME,
    DEFAULT_PREDICTION_ROOT,
    run as evaluate_run,
)
from evaluator_table_detection import evaluate_table_detection_batch
from evaluator_triage import evaluate_triage_batch, print_triage_summary
from pdf_parser import DEFAULT_INPUT_DIR, process_markdown


def _resolve_path(value: str, project_root: Path) -> Path:
    """Return value as an absolute Path anchored at the project root."""
    path = Path(value)
    return path if path.is_absolute() else project_root / path


def run_benchmark(args: argparse.Namespace) -> dict:
    """Execute parsing and evaluation pipeline.

    Returns:
        Dictionary with evaluation results
    """
    project_root = Path(__file__).parent.resolve()
    input_dir = _resolve_path(args.input_dir, project_root)
    ground_truth_dir = _resolve_path(args.ground_truth_dir, project_root)
    prediction_root = _resolve_path(args.prediction_root, project_root)

    # Determine engine based on hybrid mode
    if args.hybrid and args.hybrid != "off":
        engine_name = f"opendataloader-hybrid-{args.hybrid}"
    else:
        engine_name = "opendataloader"

    # Step 1: Parse PDFs
    logging.info("Starting PDF parsing with %s...", engine_name)
    process_markdown(engine_name, str(input_dir), doc_id=args.doc_id)

    # Step 2: Run evaluation
    logging.info("Running evaluation...")
    evaluation_paths = evaluate_run(
        str(ground_truth_dir),
        str(prediction_root),
        args.evaluation_filename,
        target_engine=engine_name,
        target_doc_id=args.doc_id,
    )

    if not evaluation_paths:
        raise RuntimeError("Evaluation did not produce any reports.")

    # Step 3: Table detection evaluation
    logging.info("Evaluating table detection...")
    reference_path = project_root / "ground-truth" / "reference.json"
    prediction_engine_dir = prediction_root / engine_name
    prediction_markdown_dir = prediction_engine_dir / "markdown"

    table_detection_metrics = evaluate_table_detection_batch(
        reference_path, prediction_markdown_dir
    )

    # Load evaluation results
    eval_path = evaluation_paths[0]
    with eval_path.open(encoding="utf-8") as f:
        eval_data = json.load(f)

    # Add table detection metrics to evaluation
    eval_data["table_detection"] = table_detection_metrics.to_dict()

    # Step 4: Load speed metrics from summary.json
    summary_path = prediction_engine_dir / "summary.json"
    if summary_path.exists():
        with summary_path.open(encoding="utf-8") as f:
            summary_data = json.load(f)
        eval_data["speed"] = {
            "total_elapsed": summary_data.get("total_elapsed"),
            "elapsed_per_doc": summary_data.get("elapsed_per_doc"),
            "document_count": summary_data.get("document_count"),
            "processor": summary_data.get("processor"),
        }

    # Step 5: Triage evaluation (for hybrid mode)
    triage_metrics = evaluate_triage_batch(reference_path, prediction_engine_dir)
    if triage_metrics.total_pages_evaluated > 0:
        eval_data["triage"] = triage_metrics.to_dict()
        logging.info("Triage evaluation: recall=%.4f, fn=%d",
                     triage_metrics.recall or 0, triage_metrics.fn_count)

    # Save updated evaluation
    with eval_path.open("w", encoding="utf-8") as f:
        json.dump(eval_data, f, indent=2, ensure_ascii=False)

    logging.info("Evaluation complete. Results saved to %s", eval_path)

    return eval_data


def check_regression(eval_data: dict, thresholds_path: Path) -> bool:
    """Check if evaluation results meet threshold requirements.

    Returns:
        True if all thresholds are met, False otherwise
    """
    if not thresholds_path.exists():
        logging.warning("Thresholds file not found: %s", thresholds_path)
        return True

    with thresholds_path.open(encoding="utf-8") as f:
        thresholds = json.load(f)

    scores = eval_data.get("metrics", {}).get("score", {})
    table_detection = eval_data.get("table_detection", {})
    speed = eval_data.get("speed", {})

    failures = []

    # Check NID
    nid = scores.get("nid_mean")
    if nid is not None and nid < thresholds.get("nid", 0):
        failures.append(f"NID {nid:.4f} < {thresholds['nid']}")

    # Check TEDS
    teds = scores.get("teds_mean")
    if teds is not None and teds < thresholds.get("teds", 0):
        failures.append(f"TEDS {teds:.4f} < {thresholds['teds']}")

    # Check MHS
    mhs = scores.get("mhs_mean")
    if mhs is not None and mhs < thresholds.get("mhs", 0):
        failures.append(f"MHS {mhs:.4f} < {thresholds['mhs']}")

    # Check Table Detection F1
    td_f1 = table_detection.get("f1")
    if td_f1 is not None and td_f1 < thresholds.get("table_detection_f1", 0):
        failures.append(f"Table Detection F1 {td_f1:.4f} < {thresholds['table_detection_f1']}")

    # Check Speed (elapsed_per_doc) - higher is worse
    elapsed_per_doc = speed.get("elapsed_per_doc")
    if elapsed_per_doc is not None and elapsed_per_doc > thresholds.get("elapsed_per_doc", float("inf")):
        failures.append(f"Speed {elapsed_per_doc:.2f}s/doc > {thresholds['elapsed_per_doc']}s/doc")

    # Check Triage metrics (only if triage data exists)
    triage = eval_data.get("triage", {})
    if triage:
        triage_recall = triage.get("recall")
        if triage_recall is not None and triage_recall < thresholds.get("triage_recall", 0):
            failures.append(f"Triage Recall {triage_recall:.4f} < {thresholds['triage_recall']}")

        triage_fn = triage.get("fn_count", 0)
        triage_fn_max = thresholds.get("triage_fn_max")
        if triage_fn_max is not None and triage_fn > triage_fn_max:
            failures.append(f"Triage FN {triage_fn} > {triage_fn_max}")

    if failures:
        logging.error("Regression detected:")
        for failure in failures:
            logging.error("  - %s", failure)
        return False

    logging.info("All thresholds met.")
    return True


def print_summary(eval_data: dict) -> None:
    """Print a summary of evaluation results."""
    scores = eval_data.get("metrics", {}).get("score", {})
    table_detection = eval_data.get("table_detection", {})
    speed = eval_data.get("speed", {})
    triage = eval_data.get("triage", {})

    print("\n" + "=" * 50)
    print("BENCHMARK RESULTS")
    print("=" * 50)

    nid = scores.get("nid_mean")
    teds = scores.get("teds_mean")
    mhs = scores.get("mhs_mean")
    td_f1 = table_detection.get("f1")
    td_precision = table_detection.get("precision")
    td_recall = table_detection.get("recall")
    elapsed_per_doc = speed.get("elapsed_per_doc")
    total_elapsed = speed.get("total_elapsed")
    document_count = speed.get("document_count")

    print(f"NID  (Reading Order):     {nid:.4f}" if nid else "NID:  N/A")
    print(f"TEDS (Table Structure):   {teds:.4f}" if teds else "TEDS: N/A")
    print(f"MHS  (Heading Structure): {mhs:.4f}" if mhs else "MHS:  N/A")
    print()
    print("Table Detection:")
    td_accuracy = table_detection.get("accuracy")
    print(f"  Precision: {td_precision:.4f}" if td_precision else "  Precision: N/A")
    print(f"  Recall:    {td_recall:.4f}" if td_recall else "  Recall: N/A")
    print(f"  F1:        {td_f1:.4f}" if td_f1 else "  F1: N/A")
    print(f"  Accuracy:  {td_accuracy:.4f}" if td_accuracy else "  Accuracy: N/A")
    print()
    print("  Confusion Matrix:")
    print(f"    TP: {table_detection.get('tp', 0):3d}  FP: {table_detection.get('fp', 0):3d}")
    print(f"    FN: {table_detection.get('fn', 0):3d}  TN: {table_detection.get('tn', 0):3d}")
    print()
    print("Speed:")
    print(f"  Per Document: {elapsed_per_doc:.2f}s" if elapsed_per_doc else "  Per Document: N/A")
    print(f"  Total:        {total_elapsed:.1f}s ({document_count} docs)" if total_elapsed else "  Total: N/A")

    # Print triage results if available
    if triage:
        print()
        print("Triage (Hybrid Mode):")
        tr_recall = triage.get("recall")
        tr_precision = triage.get("precision")
        tr_accuracy = triage.get("accuracy")
        tr_f1 = triage.get("f1")
        print(f"  Recall:    {tr_recall:.4f}" if tr_recall is not None else "  Recall: N/A")
        print(f"  Precision: {tr_precision:.4f}" if tr_precision is not None else "  Precision: N/A")
        print(f"  F1:        {tr_f1:.4f}" if tr_f1 is not None else "  F1: N/A")
        print(f"  Accuracy:  {tr_accuracy:.4f}" if tr_accuracy is not None else "  Accuracy: N/A")
        print()
        print("  Confusion Matrix:")
        print(f"    TP: {triage.get('tp_count', 0):3d}  FP: {triage.get('fp_count', 0):3d}")
        print(f"    FN: {triage.get('fn_count', 0):3d}  TN: {triage.get('tn_count', 0):3d}")
        print()
        print(f"  Pages: {triage.get('java_pages', 0)} JAVA, {triage.get('backend_pages', 0)} BACKEND")

    print("=" * 50 + "\n")


def _parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run opendataloader-pdf benchmark"
    )
    parser.add_argument(
        "--input-dir",
        default=DEFAULT_INPUT_DIR,
        help="Directory containing PDFs to parse (defaults to ./pdfs)",
    )
    parser.add_argument(
        "--doc-id",
        default=None,
        help="Process only the specified document ID",
    )
    parser.add_argument(
        "--ground-truth-dir",
        default=DEFAULT_GT_DIR,
        help="Directory containing ground-truth markdown files",
    )
    parser.add_argument(
        "--prediction-root",
        default=DEFAULT_PREDICTION_ROOT,
        help="Root directory for prediction outputs",
    )
    parser.add_argument(
        "--evaluation-filename",
        default=DEFAULT_OUTPUT_FILENAME,
        help="Filename for evaluation JSON output",
    )
    parser.add_argument(
        "--check-regression",
        action="store_true",
        help="Check results against thresholds and exit with error if failed",
    )
    parser.add_argument(
        "--thresholds",
        default="thresholds.json",
        help="Path to thresholds JSON file",
    )
    parser.add_argument(
        "--log-level",
        default="INFO",
        help="Logging verbosity (e.g. INFO, DEBUG)",
    )
    parser.add_argument(
        "--hybrid",
        default="off",
        choices=["off", "docling"],
        help="Hybrid backend: off (default), docling. Requires backend server running.",
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> None:
    args = _parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper(), logging.INFO),
        format="%(asctime)s - %(levelname)s - %(message)s",
    )

    try:
        eval_data = run_benchmark(args)
        print_summary(eval_data)

        if args.check_regression:
            project_root = Path(__file__).parent.resolve()
            thresholds_path = _resolve_path(args.thresholds, project_root)

            if not check_regression(eval_data, thresholds_path):
                raise SystemExit(1)

    except Exception as exc:
        logging.error("Benchmark failed: %s", exc)
        raise SystemExit(1) from exc


if __name__ == "__main__":
    main()
