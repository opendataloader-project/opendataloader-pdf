#!/usr/bin/env python3
"""Quick quality evaluation script for opendataloader-pdf output.

Compares extracted text against a ground truth file and reports a similarity
score. Uses difflib.SequenceMatcher from the Python standard library by default.
If rapidfuzz is installed, it computes a more accurate Normalized Indel
Distance (NID) score instead.

Usage:
    python quick-eval.py extracted.md ground-truth.md
    python quick-eval.py extracted.md ground-truth.md --verbose
    python quick-eval.py extracted.md ground-truth.md --threshold 0.90
"""

import argparse
import difflib
import re
import sys
from pathlib import Path

# Ensure stdout can print non-ASCII report content on Windows consoles
# (cp1252 / cp949 default). Without this, a single non-ASCII character
# crashes the script with UnicodeEncodeError -- including under
# `windows-latest` in GitHub Actions.
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):
        pass

# ---------------------------------------------------------------------------
# Optional rapidfuzz import -- used for NID scoring when available
# ---------------------------------------------------------------------------
try:
    from rapidfuzz.distance import Indel

    _RAPIDFUZZ_AVAILABLE = True
except ImportError:
    _RAPIDFUZZ_AVAILABLE = False


# ---------------------------------------------------------------------------
# Score thresholds and their human-readable interpretations
# ---------------------------------------------------------------------------
SCORE_LEVELS = [
    (0.95, "Excellent", "Output closely matches the ground truth."),
    (0.85, "Good", "Minor differences; output is usable as-is."),
    (0.70, "Fair", "Noticeable differences - consider hybrid mode or different options."),
    (0.00, "Poor", "Significant quality issues - review extraction settings."),
]


def normalize(text: str) -> str:
    """Collapse runs of whitespace to a single space and strip leading/trailing
    whitespace.  This makes the comparison insensitive to cosmetic formatting
    differences such as extra blank lines or trailing spaces."""
    return re.sub(r"\s+", " ", text).strip()


def read_file(path: Path) -> str:
    """Read a text file and return its content, normalized."""
    try:
        raw = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        # Fall back to Latin-1 for PDFs extracted without explicit encoding
        raw = path.read_text(encoding="latin-1")
    return normalize(raw)


def compute_similarity_stdlib(extracted: str, ground_truth: str) -> float:
    """Return a similarity ratio in [0, 1] using difflib.SequenceMatcher.

    The ratio is defined as 2 * M / T, where M is the number of matching
    characters and T is the total number of characters in both sequences.
    This is equivalent to 1 - NID when strings share large common blocks.
    """
    return difflib.SequenceMatcher(None, extracted, ground_truth, autojunk=False).ratio()


def compute_similarity_rapidfuzz(extracted: str, ground_truth: str) -> float:
    """Return a similarity score in [0, 1] using rapidfuzz Indel distance.

    Computes Normalized Indel Distance:
        NID = indel_distance / (len(a) + len(b))
    The similarity score returned is 1 - NID, so higher is better.
    """
    if not extracted and not ground_truth:
        return 1.0
    return max(0.0, 1.0 - float(Indel.normalized_distance(extracted, ground_truth)))


def compute_similarity(extracted: str, ground_truth: str) -> tuple[float, str]:
    """Compute similarity score using the best available method.

    Returns:
        (score, method_name) where score is in [0, 1].
    """
    if _RAPIDFUZZ_AVAILABLE:
        return compute_similarity_rapidfuzz(extracted, ground_truth), "NID (rapidfuzz)"
    return compute_similarity_stdlib(extracted, ground_truth), "SequenceMatcher ratio (difflib)"


def interpret_score(score: float) -> tuple[str, str]:
    """Return (label, description) for a given score."""
    for threshold, label, description in SCORE_LEVELS:
        if score >= threshold:
            return label, description
    # Should never reach here, but guard anyway
    return "Poor", SCORE_LEVELS[-1][2]


def diff_snippets(extracted: str, ground_truth: str, max_snippets: int = 5) -> list[str]:
    """Return up to max_snippets diff hunks for low-scoring sections.

    Uses difflib.unified_diff on word-tokenised lines so the output is readable
    even for long single-line documents.
    """
    # Re-wrap into ~80-char logical lines for readability
    def wrap_words(text: str, width: int = 80) -> list[str]:
        words = text.split()
        lines: list[str] = []
        line: list[str] = []
        length = 0
        for word in words:
            if length + len(word) + 1 > width and line:
                lines.append(" ".join(line))
                line = [word]
                length = len(word)
            else:
                line.append(word)
                length += len(word) + 1
        if line:
            lines.append(" ".join(line))
        return lines

    ext_lines = wrap_words(extracted)
    gt_lines = wrap_words(ground_truth)

    diff = list(
        difflib.unified_diff(
            gt_lines,
            ext_lines,
            fromfile="ground-truth",
            tofile="extracted",
            lineterm="",
            n=2,
        )
    )

    # Collect individual hunks (separated by @@ markers)
    snippets: list[str] = []
    current_hunk: list[str] = []
    for line in diff:
        if line.startswith("@@") and current_hunk:
            snippets.append("\n".join(current_hunk))
            current_hunk = [line]
            if len(snippets) >= max_snippets:
                break
        else:
            current_hunk.append(line)
    if current_hunk and len(snippets) < max_snippets:
        snippets.append("\n".join(current_hunk))

    return snippets


def build_report(
    extracted_path: Path,
    ground_truth_path: Path,
    score: float,
    method: str,
    threshold: float,
    verbose: bool,
    extracted: str,
    ground_truth: str,
) -> str:
    """Assemble the formatted report string."""
    label, description = interpret_score(score)
    passed = score >= threshold
    status = "PASS" if passed else "FAIL"

    lines = [
        "=" * 60,
        "ODL-PDF Quick Quality Evaluation",
        "=" * 60,
        f"Extracted:    {extracted_path}",
        f"Ground truth: {ground_truth_path}",
        f"Method:       {method}",
        "-" * 60,
        f"Score:        {score:.4f}  [{label}]",
        f"Threshold:    {threshold:.4f}",
        f"Result:       {status}",
        "-" * 60,
        f"Interpretation: {description}",
    ]

    if not passed:
        suggestions: list[str] = []
        if score < 0.70:
            suggestions.extend([
                "  - Try --hybrid docling-fast for better OCR coverage.",
                "  - Check --format is appropriate for this document type.",
                "  - Inspect whether the PDF is scanned (image-only) vs. native.",
            ])
        elif score < 0.85:
            suggestions.extend([
                "  - Consider --hybrid docling-fast or --table-method cluster.",
                "  - Try --use-struct-tree if the PDF is tagged (accessible).",
            ])
        else:
            # Score is above the general-quality bar but below the caller's
            # custom threshold. Generic guidance only.
            suggestions.append(
                "  - Score is above the usable-quality bar but below your custom threshold; "
                "tighten input quality or relax --threshold if appropriate."
            )

        if suggestions:
            lines.append("")
            lines.append("Suggestions:")
            lines.extend(suggestions)

    if verbose:
        lines.append("")
        lines.append("Diff snippets (ground-truth → extracted):")
        snippets = diff_snippets(extracted, ground_truth)
        if snippets:
            for i, snippet in enumerate(snippets, 1):
                lines.append(f"\n--- Hunk {i} ---")
                lines.append(snippet)
        else:
            lines.append("  (no differences found)")

    lines.append("=" * 60)
    return "\n".join(lines)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare ODL-PDF extracted output against a ground truth file.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "extracted",
        type=Path,
        help="Path to the extracted text file produced by opendataloader-pdf.",
    )
    parser.add_argument(
        "ground_truth",
        type=Path,
        help="Path to the ground truth reference file.",
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.85,
        metavar="T",
        help="Pass/fail threshold in [0, 1]. Default: 0.85.",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Show diff snippets for sections where the files diverge.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    # Validate input paths
    if not args.extracted.is_file():
        print(f"ERROR: Extracted file not found: {args.extracted}", file=sys.stderr)
        return 2
    if not args.ground_truth.is_file():
        print(f"ERROR: Ground truth file not found: {args.ground_truth}", file=sys.stderr)
        return 2
    if not (0.0 <= args.threshold <= 1.0):
        print(f"ERROR: --threshold must be between 0 and 1, got {args.threshold}", file=sys.stderr)
        return 2

    extracted = read_file(args.extracted)
    ground_truth = read_file(args.ground_truth)

    score, method = compute_similarity(extracted, ground_truth)

    report = build_report(
        extracted_path=args.extracted,
        ground_truth_path=args.ground_truth,
        score=score,
        method=method,
        threshold=args.threshold,
        verbose=args.verbose,
        extracted=extracted,
        ground_truth=ground_truth,
    )

    print(report)

    # Exit 0 = pass, 1 = fail (score below threshold)
    return 0 if score >= args.threshold else 1


if __name__ == "__main__":
    sys.exit(main())
