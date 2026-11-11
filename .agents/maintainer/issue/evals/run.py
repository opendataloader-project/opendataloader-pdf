#!/usr/bin/env python3
"""Evals of the issue harness (fix_gates.py). Offline: fixtures with the files that the gates read.
Nothing is built, fetched, or posted.

Usage: python3 -I -B evals/run.py [-k NAME]

Each eval names the weakness it guards. Exit 1 if an eval fails. A passing eval removes its folder;
a failing eval keeps it and prints the path.
"""

from __future__ import annotations

import importlib.util
import os
import shutil
import sys
import tempfile
import traceback

TMP_ROOT = os.environ.get("TMPDIR") or tempfile.gettempdir()

_gates = importlib.util.spec_from_file_location("gate_evals", os.path.join(os.path.dirname(os.path.abspath(__file__)), "gates.py"))
_gate_evals = importlib.util.module_from_spec(_gates)
_gates.loader.exec_module(_gate_evals)
EVALS = list(_gate_evals.EVALS)


def main(argv):
    only = argv[argv.index("-k") + 1] if "-k" in argv else None
    failed = 0
    for name, fn in EVALS:
        if only and only not in name:
            continue
        tmp = tempfile.mkdtemp(prefix=f"issue-eval-{name}-", dir=TMP_ROOT)
        doc = (fn.__doc__ or "").strip().split("\n")[0]
        try:
            detail = fn(tmp)
            shutil.rmtree(tmp, ignore_errors=True)
            print(f"PASS {name}  {doc}\n       {detail}")
        except Exception:
            failed += 1
            print(f"FAIL {name}  {doc}\n       kept: {tmp}")
            print("       " + traceback.format_exc().strip().replace("\n", "\n       ")[-3000:])
    print(f"{'FAILED' if failed else 'OK'}: {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
