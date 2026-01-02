"""End-to-end Markdown evaluator.

This module walks through prediction outputs, runs the individual
evaluation routines (heading level, reading order, and table similarity),
and emits a consolidated JSON report that combines the runtime summary
metadata with the computed scores.

The script can be executed directly. By default it evaluates every engine
version found under ``prediction`` and stores ``evaluation.json`` next to
the corresponding ``summary`` file.
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import time
from dataclasses import dataclass
from pathlib import Path
from statistics import fmean
from typing import Any, Dict, Iterable, List, Optional, Set

from evaluator_heading_level import evaluate_heading_level
from evaluator_reading_order import evaluate_reading_order
from evaluator_table import evaluate_table


DEFAULT_GT_DIR = "ground-truth/markdown"
DEFAULT_PREDICTION_ROOT = "prediction"
DEFAULT_OUTPUT_FILENAME = "evaluation.json"


@dataclass
class DocumentScores:
    """Container for per-document evaluation results."""

    document_id: str
    overall: Optional[float]
    nid: Optional[float]
    nid_s: Optional[float]
    teds: Optional[float]
    teds_s: Optional[float]
    mhs: Optional[float]
    mhs_s: Optional[float]
    prediction_available: bool

    def to_json(self) -> Dict[str, Any]:
        return {
            "document_id": self.document_id,
            "scores": {
                "overall": self.overall,
                "nid": self.nid,
                "nid_s": self.nid_s,
                "teds": self.teds,
                "teds_s": self.teds_s,
                "mhs": self.mhs,
                "mhs_s": self.mhs_s,
            },
            "prediction_available": self.prediction_available,
        }


def _read_text(path: Path) -> str:
    """Read UTF-8 text from ``path`` returning an empty string on failure."""

    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        logging.warning("Missing file: %s", path)
        return ""
    except UnicodeDecodeError:
        logging.warning("Failed to decode file as UTF-8: %s", path)
        return ""


def _safe_mean(values: Iterable[float]) -> Optional[float]:
    values = list(values)
    return fmean(values) if values else None


def _load_summary_metadata(summary_dir: Path) -> Dict[str, Any]:
    """Read the first ``summary.json`` file in ``summary_dir`` if it exists."""

    for summary_path in sorted(summary_dir.glob("summary.json")):
        try:
            with summary_path.open(encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError) as exc:
            logging.warning("Failed to read summary file %s: %s", summary_path, exc)


def _evaluate_single_document(
    doc_id: str,
    gt_path: Path,
    pred_path: Path,
) -> DocumentScores:
    gt_markdown = _read_text(gt_path)
    pred_markdown = _read_text(pred_path)
    prediction_available = pred_path.is_file()

    nid, nid_s = evaluate_reading_order(gt_markdown, pred_markdown)
    teds, teds_s = evaluate_table(gt_markdown, pred_markdown)
    mhs, mhs_s = evaluate_heading_level(gt_markdown, pred_markdown)

    overall_components = [
        nid,
        teds,
        mhs,
    ]
    overall_values = [value for value in overall_components if value is not None]
    overall_average = _safe_mean(overall_values)

    return DocumentScores(
        overall=overall_average,
        document_id=doc_id,
        nid=nid,
        nid_s=nid_s,
        teds=teds,
        teds_s=teds_s,
        mhs=mhs,
        mhs_s=mhs_s,
        prediction_available=prediction_available,
    )


def _aggregate_document_scores(documents: List[DocumentScores]) -> Dict[str, Any]:
    """Compute mean scores across documents and return a serialisable payload."""

    overall_values = [doc.overall for doc in documents if doc.overall is not None]
    nid_values = [doc.nid for doc in documents if doc.nid is not None]
    nid_s_values = [doc.nid_s for doc in documents if doc.nid_s is not None]
    teds_values = [doc.teds for doc in documents if doc.teds is not None]
    teds_s_values = [doc.teds_s for doc in documents if doc.teds_s is not None]
    mhs_values = [doc.mhs for doc in documents if doc.mhs is not None]
    mhs_s_values = [doc.mhs_s for doc in documents if doc.mhs_s is not None]

    overall_mean = _safe_mean(overall_values)
    nid_mean = _safe_mean(nid_values)
    nid_s_mean = _safe_mean(nid_s_values)
    teds_mean = _safe_mean(teds_values)
    teds_s_mean = _safe_mean(teds_s_values)
    mhs_mean = _safe_mean(mhs_values)
    mhs_s_mean = _safe_mean(mhs_s_values)

    missing_predictions = sum(1 for doc in documents if not doc.prediction_available)

    return {
        "score": {
            "overall_mean": overall_mean,
            "nid_mean": nid_mean,
            "nid_s_mean": nid_s_mean,
            "teds_mean": teds_mean,
            "teds_s_mean": teds_s_mean,
            "mhs_mean": mhs_mean,
            "mhs_s_mean": mhs_s_mean,
        },
        "nid_count": len(nid_values),
        "teds_count": len(teds_values),
        "mhs_count": len(mhs_values),
        "missing_predictions": missing_predictions,
    }


def _logging_scores(
    scores: DocumentScores,
    engine_name: str,
    doc_id: str,
) -> None:
    overall = scores.overall
    nid = scores.nid
    nid_s = scores.nid_s
    teds = scores.teds
    teds_s = scores.teds_s
    mhs = scores.mhs
    mhs_s = scores.mhs_s

    overall = f"{overall:.3f}" if overall is not None else "none "
    nid = f"{nid:.3f}" if nid is not None else "none "
    nid_s = f"{nid_s:.3f}" if nid_s is not None else "none "
    teds = f"{teds:.3f}" if teds is not None else "none "
    teds_s = f"{teds_s:.3f}" if teds_s is not None else "none "
    mhs = f"{mhs:.3f}" if mhs is not None else "none "
    mhs_s = f"{mhs_s:.3f}" if mhs_s is not None else "none "

    logging.info(
        "engine=%s document=%s overall=%s nid=%s nid_s=%s teds=%s teds_s=%s mhs=%s mhs_s=%s",
        engine_name,
        doc_id,
        overall,
        nid,
        nid_s,
        teds,
        teds_s,
        mhs,
        mhs_s,
    )


def _evaluate_engine_version(
    gt_dir: Path,
    prediction_dir: Path,
    output_filename: str,
    target_doc_id: Optional[str] = None,
) -> Optional[Path]:
    """Run evaluation for a single ``engine/version`` directory."""

    markdown_dir = prediction_dir / "markdown"
    if not markdown_dir.is_dir():
        logging.info("Skipping %s (no markdown directory)", prediction_dir)
        return None

    gt_paths = sorted(gt_dir.glob("*.md"))
    if not gt_paths:
        logging.error("No ground truth markdown files found in %s", gt_dir)
        return None

    documents: List[DocumentScores] = []

    engine_name = prediction_dir.name
    logging.info(
        "Evaluating engine=%s with %d documents",
        engine_name,
        len(gt_paths),
    )

    for gt_path in gt_paths:
        doc_id = gt_path.stem
        if target_doc_id and doc_id != target_doc_id:
            continue

        pred_path = markdown_dir / f"{doc_id}.md"
        try:
            scores = _evaluate_single_document(doc_id, gt_path, pred_path)
            _logging_scores(scores, engine_name, doc_id)
        except Exception as exc:  # pragma: no cover - defensive guard
            logging.exception("Failed to evaluate %s: %s", doc_id, exc)
            continue
        documents.append(scores)

    if not documents:
        logging.warning("No documents evaluated for %s", prediction_dir)
        return None

    summary_metadata = _load_summary_metadata(prediction_dir)

    aggregated = _aggregate_document_scores(documents)
    payload = {
        "summary": summary_metadata,
        "metrics": aggregated,
        "documents": [doc.to_json() for doc in documents],
    }

    output_path = prediction_dir / output_filename
    output_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False))
    logging.info("Wrote evaluation to %s", output_path)

    csv_filename = Path(output_filename).with_suffix(".csv").name
    csv_path = prediction_dir / csv_filename
    csv_fieldnames = [
        "index",
        "document_id",
        "overall",
        "nid",
        "nid_s",
        "teds",
        "teds_s",
        "mhs",
        "mhs_s",
    ]
    with csv_path.open("w", encoding="utf-8", newline="") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=csv_fieldnames)
        writer.writeheader()
        for index, doc in enumerate(documents):
            row = {
                "index": index + 1,
                "document_id": f"'{doc.document_id}",
                "overall": "" if doc.overall is None else doc.overall,
                "nid": "" if doc.nid is None else doc.nid,
                "nid_s": "" if doc.nid_s is None else doc.nid_s,
                "teds": "" if doc.teds is None else doc.teds,
                "teds_s": "" if doc.teds_s is None else doc.teds_s,
                "mhs": "" if doc.mhs is None else doc.mhs,
                "mhs_s": "" if doc.mhs_s is None else doc.mhs_s,
            }
            writer.writerow(row)
    logging.info("Wrote evaluation CSV to %s", csv_path)
    return output_path


def run(
    ground_truth_dir_name: str,
    prediction_root_name: str,
    output_filename: str,
    target_engine: Optional[str] = None,
    target_doc_id: Optional[str] = None,
) -> List[Path]:
    """Evaluate engine/version pairs under ``prediction_root`` optionally filtered to a single document."""
    project_root = Path(__file__).parent.parent.resolve()

    ground_truth_dir = project_root / ground_truth_dir_name
    prediction_root = project_root / prediction_root_name

    if not ground_truth_dir.is_dir():
        raise FileNotFoundError(f"Ground truth directory not found: {ground_truth_dir}")

    if not prediction_root.is_dir():
        raise FileNotFoundError(f"Prediction directory not found: {prediction_root}")

    start_time = time.time()

    generated_files: List[Path] = []

    if target_engine:
        engine_dirs = [prediction_root / target_engine]
        if not engine_dirs[0].is_dir():
            logging.warning("Engine directory not found: %s", engine_dirs[0])
            engine_dirs = []
    else:
        engine_dirs = [p for p in sorted(prediction_root.iterdir()) if p.is_dir()]

    for engine_dir in engine_dirs:
        result_path = _evaluate_engine_version(
            ground_truth_dir, engine_dir, output_filename, target_doc_id
        )
        if result_path:
            generated_files.append(result_path)

    end_time = time.time()
    total_elapsed = end_time - start_time
    logging.info(
        "Completed evaluation of %d engine versions in %.2f seconds",
        len(generated_files),
        total_elapsed,
    )

    return generated_files


def _parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate Markdown predictions")
    parser.add_argument(
        "--ground-truth-dir",
        type=str,
        default=DEFAULT_GT_DIR,
        help="Directory containing ground-truth markdown files",
    )
    parser.add_argument(
        "--prediction-root",
        type=str,
        default=DEFAULT_PREDICTION_ROOT,
        help="Directory containing engine prediction outputs",
    )
    parser.add_argument(
        "--engine",
        type=str,
        default=None,
        help="Name of the engine to evaluate. If not specified, all engines are evaluated.",
    )
    parser.add_argument(
        "--doc-id",
        type=str,
        default=None,
        help="Evaluate only the specified document ID",
    )
    parser.add_argument(
        "--output-filename",
        type=str,
        default=DEFAULT_OUTPUT_FILENAME,
        help="Filename for generated evaluation JSON (placed in each version dir)",
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
    args = _parse_args(argv)
    logging.basicConfig(level=getattr(logging, args.log_level.upper(), logging.INFO))
    generated = run(
        args.ground_truth_dir,
        args.prediction_root,
        args.output_filename,
        target_engine=args.engine,
        target_doc_id=args.doc_id,
    )
    for path in generated:
        print(path)


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    main()
