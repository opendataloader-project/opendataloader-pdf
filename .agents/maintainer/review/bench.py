#!/usr/bin/env python3
"""Score Markdown that the isolated environment made, and compare two scores.

The jar of a pull request must not run on the host. So:
  1. In the isolated environment, convert the benchmark PDFs with the same arguments as the
     benchmark (opendataloader-bench, src/pdf_parser_opendataloader.py):
       java -jar <jar> <bench>/pdfs --output-dir /work/out/md --format markdown \
            --table-method cluster --image-output off --quiet
  2. On the host, score each output folder with the benchmark's own evaluator:
       <bench>/.venv/bin/python bench.py score <bench> <md-folder> <evaluation.json>
  3. Compare:
       python3 bench.py compare base.json head.json

"score" DELETES prediction/opendataloader/markdown and evaluation.json in <bench> first. Use a
copy or a git worktree of the benchmark under the work folder, not a checkout that you keep.
"score" replaces only the parsing step of run.py with a copy of the given folder. The
evaluator, the ground truth, and the thresholds come from the benchmark repository.
"score" also writes <evaluation.json>.commit with the benchmark commit. "compare" exits 1 if the
two commits differ, a mean drops, a document drops more than 0.05, or predictions are missing.
In the conversion log, count the lines "Error converting" and "Exception" for base and head.
"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path


def score(bench: Path, md: Path, out: Path) -> int:
    dst = bench / "prediction" / "opendataloader" / "markdown"
    shutil.rmtree(dst, ignore_errors=True)
    (bench / "prediction" / "opendataloader" / "evaluation.json").unlink(missing_ok=True)
    shutil.copytree(md, dst)
    sys.path.insert(0, str(bench / "src"))
    import run  # the benchmark's run.py

    run.process_markdown = lambda *a, **k: None
    try:
        run.main(["--engine", "opendataloader", "--force", "--check-regression", "--history-no-overwrite"])
    finally:
        shutil.copy2(bench / "prediction" / "opendataloader" / "evaluation.json", out)
        commit = subprocess.run(["git", "-C", str(bench), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
        Path(str(out) + ".commit").write_text(commit + "\n")
    return 0


def compare(base_path: str, head_path: str) -> int:
    b, h = (json.load(open(p)) for p in (base_path, head_path))
    bad = []
    commits = [Path(p + ".commit").read_text().strip() if Path(p + ".commit").exists() else None for p in (base_path, head_path)]
    if not all(commits) or commits[0] != commits[1]:  # a missing or empty commit is not the same revision (#836)
        bad.append(f"benchmark commits missing or different: {commits[0]!r} vs {commits[1]!r}; score both again")
    for m in ("overall", "nid", "teds", "mhs"):
        x, y = b["metrics"]["score"][f"{m}_mean"], h["metrics"]["score"][f"{m}_mean"]
        print(f"{m}: {x:.4f} -> {y:.4f} ({y - x:+.4f})")
        if y < x - 1e-9:
            bad.append(f"mean {m} dropped")
    head = {d["document_id"]: d["scores"].get("overall") for d in h["documents"]}
    for d in b["documents"]:
        x, y = d["scores"].get("overall"), head.get(d["document_id"])
        if x is not None and (y is None or x - y > 0.05):
            bad.append(f"{d['document_id']}: {x} -> {y}")
        elif x is not None and y is not None and abs(x - y) > 1e-9:
            print(f"  changed {d['document_id']}: {x:.4f} -> {y:.4f}")
    if h["metrics"].get("missing_predictions", 0) > b["metrics"].get("missing_predictions", 0):
        bad.append("more missing predictions than base")
    print("\n".join(bad) or "no drop")
    return 1 if bad else 0


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 4 and args[0] == "score":
        sys.exit(score(Path(args[1]), Path(args[2]), Path(args[3])))
    if len(args) == 3 and args[0] == "compare":
        sys.exit(compare(args[1], args[2]))
    print(__doc__)
    sys.exit(2)
