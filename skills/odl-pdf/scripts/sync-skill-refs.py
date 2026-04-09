#!/usr/bin/env python3
"""Drift detection script for the ODL-PDF agent skill.

Compares the option names declared in options.json (the authoritative source)
against the option names documented in skills/odl-pdf/references/options-matrix.md.

Any mismatch means the skill reference is out of sync with the actual CLI —
a condition referred to here as "drift".  Run this script in CI after any
change to options.json or options-matrix.md.

Usage:
    python sync-skill-refs.py
    python sync-skill-refs.py --options-json path/to/options.json \
                               --matrix path/to/options-matrix.md

Exit codes:
    0  No drift detected.
    1  Drift detected (new or removed options).
    2  Input error (file not found, invalid JSON, etc.).
"""

import argparse
import io
import json
import re
import sys
from pathlib import Path

# Reconfigure stdout to UTF-8 when running on Windows with a legacy code page
# so that Unicode symbols (checkmark, cross) print correctly in all terminals.
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# ---------------------------------------------------------------------------
# Defaults — resolved relative to this script's location so the script works
# when invoked from any directory.
# ---------------------------------------------------------------------------
_SCRIPT_DIR = Path(__file__).parent.resolve()
# skills/odl-pdf/scripts/ → project root is three levels up
_PROJECT_ROOT = _SCRIPT_DIR.parent.parent.parent

DEFAULT_OPTIONS_JSON = _PROJECT_ROOT / "options.json"
DEFAULT_MATRIX = _SCRIPT_DIR.parent / "references" / "options-matrix.md"


# ---------------------------------------------------------------------------
# Parsing helpers
# ---------------------------------------------------------------------------

def load_option_names_from_json(path: Path) -> set[str]:
    """Return the set of option names declared in options.json.

    Expects the file to contain a top-level object with an "options" array,
    where each element has a "name" field.  Example:

        { "options": [ { "name": "output-dir", ... }, ... ] }
    """
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"ERROR: Failed to parse {path}: {exc}", file=sys.stderr)
        sys.exit(2)

    options = data.get("options")
    if not isinstance(options, list):
        print(
            f"ERROR: {path} does not contain a top-level 'options' array.",
            file=sys.stderr,
        )
        sys.exit(2)

    names: set[str] = set()
    for i, item in enumerate(options):
        if not isinstance(item, dict) or "name" not in item:
            print(
                f"ERROR: options[{i}] in {path} is missing the 'name' field.",
                file=sys.stderr,
            )
            sys.exit(2)
        names.add(item["name"])

    return names


def load_option_names_from_matrix(path: Path) -> set[str]:
    """Return the set of option names found in options-matrix.md.

    Scans all Markdown table rows and extracts backtick-quoted option names
    from the first column.  Rows that contain only header separators (---) are
    skipped.

    Expected table format (any number of columns):
        | `option-name` | ... |
    """
    text = path.read_text(encoding="utf-8")

    names: set[str] = set()

    # Match table rows whose first cell contains a backtick-quoted token.
    # This pattern is intentionally permissive so it works even if the table
    # adds extra spaces or alignment padding.
    row_pattern = re.compile(
        r"^\s*\|\s*`([^`]+)`",  # | `option-name`  (first cell, backtick-quoted)
        re.MULTILINE,
    )
    for match in row_pattern.finditer(text):
        candidate = match.group(1).strip()
        # Skip tokens that look like option values rather than names.
        # Option names always contain at least one letter and may contain
        # hyphens but not spaces or equals signs.
        if re.fullmatch(r"[a-z][a-z0-9-]*", candidate):
            names.add(candidate)

    return names


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Detect drift between options.json and the skill reference matrix.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--options-json",
        type=Path,
        default=DEFAULT_OPTIONS_JSON,
        metavar="PATH",
        help=f"Path to options.json. Default: {DEFAULT_OPTIONS_JSON}",
    )
    parser.add_argument(
        "--matrix",
        type=Path,
        default=DEFAULT_MATRIX,
        metavar="PATH",
        help=f"Path to options-matrix.md. Default: {DEFAULT_MATRIX}",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    # Validate input paths
    if not args.options_json.is_file():
        print(f"ERROR: options.json not found: {args.options_json}", file=sys.stderr)
        return 2
    if not args.matrix.is_file():
        print(f"ERROR: options-matrix.md not found: {args.matrix}", file=sys.stderr)
        return 2

    print("Checking skill drift...")

    json_names = load_option_names_from_json(args.options_json)
    matrix_names = load_option_names_from_matrix(args.matrix)

    print(f"options.json:     {len(json_names)} options")
    print(f"options-matrix.md: {len(matrix_names)} options")

    # Compute drift sets
    new_options = sorted(json_names - matrix_names)      # in JSON but not in matrix
    removed_options = sorted(matrix_names - json_names)  # in matrix but not in JSON

    drift_detected = bool(new_options or removed_options)

    if not drift_detected:
        print("\u2713 No drift detected.")
        return 0

    # Report drift
    if new_options:
        print(f"\nNEW options (in options.json, not in skill):")
        for name in new_options:
            print(f"  - {name}")

    if removed_options:
        print(f"\nREMOVED options (in skill, not in options.json):")
        for name in removed_options:
            print(f"  - {name}")

    print(
        "\n\u2717 Drift detected. "
        "Update skills/odl-pdf/references/options-matrix.md to match options.json."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
