#!/usr/bin/env python3
"""verify-json.py — schema-tolerant summary of an opendataloader-pdf JSON output.

Purpose: give an agent a safe way to VERIFY extraction results (SKILL.md Stage 4)
without hand-writing fragile jq / assuming exact key names that vary by release.

It parses the JSON, walks the element tree generically (any nested dict carrying a
"type" field, under any "kids"/children key), and reports element-type counts plus
whether text / tables / images are present. It is schema-tolerant, not fully
agnostic: it expects ODL-style `type` and `content`/`text` field names (it does
not assume tree location or child-key names). It does NOT decide pass/fail — the
agent judges the summary against the user's intent.

Usage:
    python verify-json.py output.json
Exit codes:
    0  parsed successfully (summary printed)
    1  file missing, empty, or not valid JSON
"""

import json
import sys
from pathlib import Path

# Make stdout tolerant of non-ASCII on Windows consoles (cp1252/cp949).
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):
        pass

TEXT_KEYS = ("content", "text")           # tried in order; first non-empty wins
IMAGE_TYPES = ("image", "picture", "figure")
TABLE_TYPES = ("table",)


def load(path: Path):
    if not path.exists():
        print(f"ERROR: file not found: {path}", file=sys.stderr)
        sys.exit(1)
    try:
        raw = path.read_text(encoding="utf-8").strip()
    except UnicodeDecodeError as e:
        print(f"ERROR: not valid UTF-8 ({e}): {path}", file=sys.stderr)
        sys.exit(1)
    if not raw:
        print(f"ERROR: file is empty: {path}", file=sys.stderr)
        sys.exit(1)
    try:
        return json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"ERROR: not valid JSON ({e}): {path}", file=sys.stderr)
        sys.exit(1)


def walk(node, types, stats):
    """Recursively find every dict that has a 'type' field; tally it."""
    if isinstance(node, dict):
        t = node.get("type")
        if isinstance(t, str):
            types[t] = types.get(t, 0) + 1
            tl = t.lower()
            if any(k in tl for k in IMAGE_TYPES):
                stats["images"] += 1
            if any(k == tl for k in TABLE_TYPES):
                stats["tables"] += 1
            for tk in TEXT_KEYS:
                v = node.get(tk)
                if isinstance(v, str) and v.strip():
                    stats["text_elements"] += 1
                    break
        for v in node.values():
            walk(v, types, stats)
    elif isinstance(node, list):
        for v in node:
            walk(v, types, stats)


def main(argv=None):
    argv = argv if argv is not None else sys.argv[1:]
    if len(argv) != 1:
        print("Usage: python verify-json.py <output.json>", file=sys.stderr)
        sys.exit(1)
    data = load(Path(argv[0]))

    types = {}
    stats = {"images": 0, "tables": 0, "text_elements": 0}
    walk(data, types, stats)

    total = sum(types.values())
    # "number of pages" key name varies; probe a few, else report unknown.
    pages = "unknown"
    if isinstance(data, dict):
        for k in ("number of pages", "number_of_pages", "pages", "page count"):
            if isinstance(data.get(k), int):
                pages = data[k]
                break

    print("=== opendataloader-pdf JSON summary ===")
    print(f"pages: {pages}")
    print(f"typed elements: {total}")
    print(f"has_text: {stats['text_elements'] > 0} (text-bearing elements: {stats['text_elements']})")
    print(f"has_tables: {stats['tables'] > 0} (tables: {stats['tables']})")
    print(f"has_images: {stats['images'] > 0} (images/pictures: {stats['images']})")
    if types:
        print("element types:")
        for t, n in sorted(types.items(), key=lambda kv: -kv[1]):
            print(f"  {t}: {n}")
    else:
        print("element types: (none found — output may be empty or an unexpected shape)")

    print()
    print("NOTE: this is a summary, not a pass/fail. Judge it against the user's "
          "intent (SKILL.md Stage 4): e.g. no text is a FAILURE only if text was expected.")
    sys.exit(0)


if __name__ == "__main__":
    main()
