#!/usr/bin/env python3
"""Assemble the demo folder that the maintainer opens: one case folder for each expectation.

Usage:
  demo.py add DEMO_DIR --case 01-issue --title "Chart grid gives no table" \
      --input in.pdf --command "java -jar <jar> in.pdf --format markdown" \
      --before OUT_BASE --after OUT_HEAD --expect "No table" --look "chart.md: before has a table" \
      [--files chart.md,chart.json] [--result pass|fail|info]
  demo.py show DEMO_DIR

"add" copies the input and the chosen output files into DEMO_DIR/<case>/{before,after}/,
writes diff.txt for text files and README.txt (what to look at), and updates DEMO_DIR/INDEX.txt.
The tool does not run anything. Make the outputs first (isolated run).
Outputs are untrusted: the tool never follows a symlink. It skips each symlink and says so.
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import os
import shutil
import sys

TEXT = (".md", ".json", ".txt", ".html", ".csv", ".xml")


def sha(path: str) -> str:
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()[:16]


def add(a: argparse.Namespace) -> None:
    case = os.path.join(a.demo, a.case)
    for side in ("before", "after"):
        os.makedirs(os.path.join(case, side), exist_ok=True)
    if os.path.islink(a.input):
        raise SystemExit(f"refused: the input is a symlink: {a.input}")
    shutil.copy2(a.input, os.path.join(case, os.path.basename(a.input)))
    files = a.files.split(",") if a.files else sorted(set(os.listdir(a.before)) | set(os.listdir(a.after)))
    diffs, rows = [], []
    for name in files:
        b, h = os.path.join(a.before, name), os.path.join(a.after, name)
        if os.path.islink(b) or os.path.islink(h):
            rows.append(f"  {name}: SKIPPED (a symlink in the output; outputs are untrusted)")
            continue
        for src, side in ((b, "before"), (h, "after")):
            if os.path.isfile(src):
                shutil.copy2(src, os.path.join(case, side, name))
        if name.endswith(TEXT) and os.path.isfile(b) and os.path.isfile(h):
            with open(b, encoding="utf-8", errors="replace") as f1, open(h, encoding="utf-8", errors="replace") as f2:
                d = list(difflib.unified_diff(f1.read().splitlines(), f2.read().splitlines(),
                                              f"before/{name}", f"after/{name}", lineterm=""))
            diffs += d
            changed = sum(1 for x in d[2:] if x.startswith(("+", "-")))  # d[:2] are the file headers; "---" can be content (#836)
            rows.append(f"  {name}: {'same' if not d else f'{changed} changed lines'}")
        else:
            state = "both" if os.path.isfile(b) and os.path.isfile(h) else ("before only" if os.path.isfile(b) else "after only")
            rows.append(f"  {name}: {state}" + ("" if state != "both" else (" (same bytes)" if sha(b) == sha(h) else " (different bytes)")))
    with open(os.path.join(case, "diff.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(diffs) + ("\n" if diffs else "no text differences\n"))
    readme = [
        f"{a.case}: {a.title}",
        f"Result: {a.result}",
        "",
        f"Input:    {os.path.basename(a.input)} (sha256 {sha(a.input)})",
        f"Command:  {a.command}",
        f"Expected: {a.expect}",
        "",
        "Look at, in this order:",
        *[f"  {i}. {x.strip()}" for i, x in enumerate(a.look.split(";"), 1)],
        "",
        "Files (before = main, after = this PR):",
        *rows,
        "",
        "diff.txt has the text differences.",
    ]
    with open(os.path.join(case, "README.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(readme) + "\n")
    index = os.path.join(a.demo, "INDEX.txt")
    lines = [l for l in (open(index, encoding="utf-8").read().splitlines() if os.path.exists(index) else [])
             if not l.startswith(a.case + " ")]
    lines.append(f"{a.case} [{a.result}] {a.title} -> {a.expect}")
    with open(index, "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(lines)) + "\n")
    print(os.path.join(case, "README.txt"))


def show(a: argparse.Namespace) -> None:
    index = os.path.join(a.demo, "INDEX.txt")
    print(f"Demo: {os.path.abspath(a.demo)}  (open the cases in this order)")
    print(open(index, encoding="utf-8").read() if os.path.exists(index) else "(no cases)")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("add")
    s.add_argument("demo")
    for opt in ("--case", "--title", "--input", "--command", "--before", "--after", "--expect", "--look"):
        s.add_argument(opt, required=True)
    s.add_argument("--files")
    s.add_argument("--result", default="info", choices=["pass", "fail", "info"])
    s = sub.add_parser("show")
    s.add_argument("demo")
    a = p.parse_args(argv)
    add(a) if a.cmd == "add" else show(a)
    return 0


if __name__ == "__main__":
    sys.exit(main())
