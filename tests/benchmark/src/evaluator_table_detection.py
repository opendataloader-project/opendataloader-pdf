"""Table detection evaluator.

Evaluates the binary classification performance of table presence detection.
Returns precision, recall, and F1 score based on whether the prediction
correctly identifies documents that contain tables.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple


@dataclass
class TableDetectionMetrics:
    """Container for table detection evaluation metrics."""

    precision: Optional[float]
    recall: Optional[float]
    f1: Optional[float]
    tp: int  # True positives
    fp: int  # False positives
    fn: int  # False negatives
    tn: int  # True negatives

    def to_dict(self):
        return {
            "precision": self.precision,
            "recall": self.recall,
            "f1": self.f1,
            "tp": self.tp,
            "fp": self.fp,
            "fn": self.fn,
            "tn": self.tn,
        }


def _has_markdown_table(markdown: str) -> bool:
    """Check if markdown contains a table.

    Detects tables by looking for the separator line pattern:
    |---|---| or | --- | --- | (with optional colons for alignment)
    """
    # Pattern matches table separator lines like |---|---| or | :---: | ---: |
    table_separator_pattern = r"^\s*\|[\s:]*-+[\s:]*\|"
    return bool(re.search(table_separator_pattern, markdown, re.MULTILINE))


def _load_ground_truth_tables(reference_path: Path) -> dict[str, bool]:
    """Load ground truth and determine which documents have tables.

    Args:
        reference_path: Path to reference.json

    Returns:
        Dict mapping document_id to boolean (True if has table)
    """
    with reference_path.open(encoding="utf-8") as f:
        reference = json.load(f)

    doc_has_table = {}
    for pdf_name, doc_data in reference.items():
        doc_id = pdf_name.replace(".pdf", "")
        elements = doc_data.get("elements", [])
        has_table = any(elem.get("category") == "Table" for elem in elements)
        doc_has_table[doc_id] = has_table

    return doc_has_table


def evaluate_table_detection_single(
    gt_has_table: bool,
    pred_markdown: str,
) -> Tuple[bool, bool]:
    """Evaluate table detection for a single document.

    Args:
        gt_has_table: Whether ground truth indicates table presence
        pred_markdown: Predicted markdown content

    Returns:
        Tuple of (ground_truth_has_table, prediction_has_table)
    """
    pred_has_table = _has_markdown_table(pred_markdown)
    return gt_has_table, pred_has_table


def compute_metrics(
    tp: int, fp: int, fn: int, tn: int
) -> TableDetectionMetrics:
    """Compute precision, recall, and F1 from confusion matrix values."""
    precision = tp / (tp + fp) if (tp + fp) > 0 else None
    recall = tp / (tp + fn) if (tp + fn) > 0 else None

    if precision is not None and recall is not None and (precision + recall) > 0:
        f1 = 2 * precision * recall / (precision + recall)
    else:
        f1 = None

    return TableDetectionMetrics(
        precision=precision,
        recall=recall,
        f1=f1,
        tp=tp,
        fp=fp,
        fn=fn,
        tn=tn,
    )


def evaluate_table_detection_batch(
    reference_path: Path,
    prediction_markdown_dir: Path,
) -> TableDetectionMetrics:
    """Evaluate table detection across all documents.

    Args:
        reference_path: Path to ground-truth reference.json
        prediction_markdown_dir: Directory containing predicted markdown files

    Returns:
        TableDetectionMetrics with aggregated scores
    """
    gt_tables = _load_ground_truth_tables(reference_path)

    tp = fp = fn = tn = 0

    for doc_id, gt_has_table in gt_tables.items():
        pred_path = prediction_markdown_dir / f"{doc_id}.md"

        if not pred_path.exists():
            # Missing prediction - count as false negative if GT has table
            if gt_has_table:
                fn += 1
            else:
                tn += 1
            continue

        pred_markdown = pred_path.read_text(encoding="utf-8")
        pred_has_table = _has_markdown_table(pred_markdown)

        if gt_has_table and pred_has_table:
            tp += 1
        elif gt_has_table and not pred_has_table:
            fn += 1
        elif not gt_has_table and pred_has_table:
            fp += 1
        else:
            tn += 1

    return compute_metrics(tp, fp, fn, tn)
