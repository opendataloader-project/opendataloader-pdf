#!/usr/bin/env python3
"""Check ordered lists in Markdown as a CommonMark renderer shows them. Reads files only.

Usage: uvx --from markdown-it-py==4.0.0 python md_lists.py FILE.md [FILE.md ...]

CommonMark numbers an ordered list from the number of its first item, and items with the
same delimiter stay in one list across blank lines. So the shown number can differ from the
written number (for example two lists "1. 2." and "1. 2." become one list "1. 2. 3. 4.").
For each item whose shown number differs from its written number, the tool prints
"<file>:<line>: written N, shown M". Exit code 1 if any item differs.
"""

from __future__ import annotations

import re
import sys

from markdown_it import MarkdownIt

NUMBER = re.compile(r"^(?:\s*>)*\s*(\d{1,9})[.)]")  # also inside a blockquote (#836)


def check(path: str) -> list[str]:
    with open(path, encoding="utf-8") as f:
        text = f.read()
    lines = text.splitlines()
    tokens = MarkdownIt("commonmark").parse(text)
    problems, stack = [], []
    for tok in tokens:
        if tok.type == "ordered_list_open":
            stack.append([int(tok.attrGet("start") or 1), 0])
        elif tok.type == "bullet_list_open":
            stack.append(None)
        elif tok.type in ("ordered_list_close", "bullet_list_close"):
            stack.pop()
        elif tok.type == "list_item_open" and stack and stack[-1] is not None:
            start, index = stack[-1]
            shown = start + index
            stack[-1][1] += 1
            line = tok.map[0] if tok.map else None
            m = NUMBER.match(lines[line]) if line is not None else None
            if m and int(m.group(1)) != shown:
                problems.append(f"{path}:{line + 1}: written {m.group(1)}, shown {shown}")
    return problems


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 2
    problems = [p for f in argv for p in check(f)]
    print("\n".join(problems) if problems else f"{len(argv)} files: each ordered item shows its written number")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
