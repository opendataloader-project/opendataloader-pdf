"""Triage evaluator for hybrid mode.

Evaluates the accuracy of page triage decisions by comparing them against
ground truth table presence. The key metric is triage recall (tables correctly
sent to the backend) and triage_fn (tables missed by being sent to Java path).

Conservative strategy: We want high recall (minimize FN) even if precision is lower.
Missing a table (FN) is worse than sending a non-table page to backend (FP).
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


@dataclass
class TriageMetrics:
    """Container for triage evaluation metrics."""

    recall: Optional[float]  # Table pages correctly sent to BACKEND
    precision: Optional[float]  # BACKEND pages that actually had tables
    accuracy: Optional[float]  # Overall accuracy (TP + TN) / total
    f1: Optional[float]  # F1 score
    fn_count: int  # Tables missed (sent to JAVA) - critical metric
    fp_count: int  # Non-table pages sent to BACKEND (acceptable)
    tp_count: int  # Table pages sent to BACKEND (correct)
    tn_count: int  # Non-table pages sent to JAVA (correct)
    java_pages: int  # Total pages routed to JAVA
    backend_pages: int  # Total pages routed to BACKEND
    total_table_pages: int  # Total pages with tables in ground truth
    total_pages_evaluated: int  # Total pages evaluated

    def to_dict(self) -> dict:
        return {
            "recall": self.recall,
            "precision": self.precision,
            "accuracy": self.accuracy,
            "f1": self.f1,
            "fn_count": self.fn_count,
            "fp_count": self.fp_count,
            "tp_count": self.tp_count,
            "tn_count": self.tn_count,
            "java_pages": self.java_pages,
            "backend_pages": self.backend_pages,
            "total_table_pages": self.total_table_pages,
            "total_pages_evaluated": self.total_pages_evaluated,
        }


def get_pages_with_tables(reference_path: Path) -> dict[str, set[int]]:
    """Extract page numbers with tables from ground truth.

    Args:
        reference_path: Path to reference.json

    Returns:
        Dict mapping document_id (without .pdf) to set of 1-indexed page numbers with tables
    """
    with reference_path.open(encoding="utf-8") as f:
        reference = json.load(f)

    doc_table_pages: dict[str, set[int]] = {}

    for pdf_name, doc_data in reference.items():
        doc_id = pdf_name.replace(".pdf", "")
        elements = doc_data.get("elements", [])

        table_pages = set()
        for elem in elements:
            if elem.get("category") == "Table":
                page = elem.get("page")
                if page is not None:
                    table_pages.add(page)

        doc_table_pages[doc_id] = table_pages

    return doc_table_pages


def load_triage_results(triage_path: Path) -> dict[str, dict]:
    """Load triage results from triage.json file.

    Args:
        triage_path: Path to triage.json

    Returns:
        Dict with document name and page decisions
    """
    with triage_path.open(encoding="utf-8") as f:
        return json.load(f)


def evaluate_triage_single(
    gt_table_pages: set[int],
    triage_data: dict,
) -> tuple[int, int, int, int, int, int]:
    """Evaluate triage accuracy for a single document.

    Args:
        gt_table_pages: Set of page numbers (1-indexed) that have tables
        triage_data: Triage JSON data for the document

    Returns:
        Tuple of (tp, fp, fn, tn, java_count, backend_count)
    """
    triage_entries = triage_data.get("triage", [])

    tp = fp = fn = tn = 0
    java_count = backend_count = 0

    for entry in triage_entries:
        page = entry.get("page")
        decision = entry.get("decision")

        if page is None or decision is None:
            continue

        has_table = page in gt_table_pages
        sent_to_backend = decision == "BACKEND"

        if sent_to_backend:
            backend_count += 1
        else:
            java_count += 1

        if has_table and sent_to_backend:
            tp += 1
        elif has_table and not sent_to_backend:
            fn += 1  # Critical: missed table
        elif not has_table and sent_to_backend:
            fp += 1  # Acceptable: extra backend call
        else:
            tn += 1

    return tp, fp, fn, tn, java_count, backend_count


def evaluate_triage(
    reference_path: Path,
    triage_path: Path,
) -> TriageMetrics:
    """Evaluate triage accuracy against ground truth for a single document.

    Args:
        reference_path: Path to ground-truth reference.json
        triage_path: Path to triage.json from hybrid processing

    Returns:
        TriageMetrics with accuracy measurements
    """
    gt_table_pages = get_pages_with_tables(reference_path)
    triage_data = load_triage_results(triage_path)

    # Get document ID from triage data
    doc_name = triage_data.get("document", "")
    doc_id = doc_name.replace(".pdf", "")

    # Get table pages for this document
    table_pages = gt_table_pages.get(doc_id, set())

    tp, fp, fn, tn, java_count, backend_count = evaluate_triage_single(
        table_pages, triage_data
    )

    return _compute_metrics(tp, fp, fn, tn, java_count, backend_count, len(table_pages))


def evaluate_triage_batch(
    reference_path: Path,
    triage_dir: Path,
) -> TriageMetrics:
    """Evaluate triage accuracy across multiple documents.

    Looks for triage.json files in subdirectories of triage_dir.

    Args:
        reference_path: Path to ground-truth reference.json
        triage_dir: Directory containing per-document triage.json files

    Returns:
        TriageMetrics with aggregated accuracy measurements
    """
    gt_table_pages = get_pages_with_tables(reference_path)

    total_tp = total_fp = total_fn = total_tn = 0
    total_java = total_backend = 0
    total_table_pages = 0
    docs_evaluated = 0

    # Look for triage.json files
    triage_files = list(triage_dir.glob("**/triage.json"))

    if not triage_files:
        logging.warning("No triage.json files found in %s", triage_dir)
        return TriageMetrics(
            recall=None,
            precision=None,
            accuracy=None,
            f1=None,
            fn_count=0,
            fp_count=0,
            tp_count=0,
            tn_count=0,
            java_pages=0,
            backend_pages=0,
            total_table_pages=0,
            total_pages_evaluated=0,
        )

    for triage_path in triage_files:
        try:
            triage_data = load_triage_results(triage_path)
        except (json.JSONDecodeError, OSError) as e:
            logging.warning("Failed to load %s: %s", triage_path, e)
            continue

        doc_name = triage_data.get("document", "")
        doc_id = doc_name.replace(".pdf", "")

        table_pages = gt_table_pages.get(doc_id, set())

        tp, fp, fn, tn, java_count, backend_count = evaluate_triage_single(
            table_pages, triage_data
        )

        total_tp += tp
        total_fp += fp
        total_fn += fn
        total_tn += tn
        total_java += java_count
        total_backend += backend_count
        total_table_pages += len(table_pages)
        docs_evaluated += 1

    logging.info(
        "Evaluated triage for %d documents, %d triage files found",
        docs_evaluated,
        len(triage_files),
    )

    return _compute_metrics(
        total_tp, total_fp, total_fn, total_tn,
        total_java, total_backend, total_table_pages
    )


def _compute_metrics(
    tp: int, fp: int, fn: int, tn: int,
    java_count: int, backend_count: int,
    total_table_pages: int,
) -> TriageMetrics:
    """Compute triage metrics from confusion matrix values.

    Args:
        tp: True positives (table pages sent to BACKEND)
        fp: False positives (non-table pages sent to BACKEND)
        fn: False negatives (table pages sent to JAVA) - critical
        tn: True negatives (non-table pages sent to JAVA)
        java_count: Total pages routed to JAVA
        backend_count: Total pages routed to BACKEND
        total_table_pages: Total pages with tables

    Returns:
        TriageMetrics with computed values
    """
    # Recall = TP / (TP + FN) - how many tables did we catch?
    recall = tp / (tp + fn) if (tp + fn) > 0 else None

    # Precision = TP / (TP + FP) - of pages sent to backend, how many had tables?
    precision = tp / (tp + fp) if (tp + fp) > 0 else None

    total_pages = tp + fp + fn + tn

    # Accuracy = (TP + TN) / total
    accuracy = (tp + tn) / total_pages if total_pages > 0 else None

    # F1 = 2 * precision * recall / (precision + recall)
    f1 = None
    if precision is not None and recall is not None and (precision + recall) > 0:
        f1 = 2 * precision * recall / (precision + recall)

    return TriageMetrics(
        recall=recall,
        precision=precision,
        accuracy=accuracy,
        f1=f1,
        fn_count=fn,
        fp_count=fp,
        tp_count=tp,
        tn_count=tn,
        java_pages=java_count,
        backend_pages=backend_count,
        total_table_pages=total_table_pages,
        total_pages_evaluated=total_pages,
    )


def print_triage_summary(metrics: TriageMetrics) -> None:
    """Print a summary of triage evaluation results."""
    print("\n" + "-" * 40)
    print("TRIAGE EVALUATION")
    print("-" * 40)

    if metrics.recall is not None:
        print(f"Recall (Tables caught):    {metrics.recall:.4f}")
    else:
        print("Recall: N/A (no tables in ground truth)")

    if metrics.precision is not None:
        print(f"Precision:                 {metrics.precision:.4f}")
    else:
        print("Precision: N/A (no pages sent to backend)")

    print()
    print(f"False Negatives (missed):  {metrics.fn_count}")
    print(f"True Positives:            {metrics.tp_count}")
    print(f"False Positives:           {metrics.fp_count}")
    print(f"True Negatives:            {metrics.tn_count}")
    print()
    print(f"Pages to JAVA:             {metrics.java_pages}")
    print(f"Pages to BACKEND:          {metrics.backend_pages}")
    print(f"Total table pages (GT):    {metrics.total_table_pages}")
    print("-" * 40 + "\n")
