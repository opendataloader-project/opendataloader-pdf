#!/usr/bin/env python3
"""Evals of the review tools of this folder: side_effects.sh, inspect_pdf.py, bench.py, and md_lists.py.
Offline: synthetic files in temporary folders and a fake java. Nothing is pushed or posted.

Usage: python3 -I -B evals/run.py [-k NAME]

An eval that needs a tool that is missing prints SKIP with the reason; a SKIP is not a pass.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import traceback

sys.dont_write_bytecode = True  # the evals write nothing into this folder
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


class Skip(Exception):
    pass


def sh(cmd, cwd=None, env=None, check=True):
    res = subprocess.run(cmd, cwd=cwd, env=env, capture_output=True, text=True)
    if check and res.returncode != 0:
        raise RuntimeError(f"{cmd}: exit {res.returncode}\n{res.stdout[-2000:]}\n{res.stderr[-2000:]}")
    return res


def wpy(script, *args, check=True):
    """Run a Python tool of this folder."""
    return sh([sys.executable, "-I", os.path.join(HERE, script), *args], check=check)


# ---------------------------------------------------------------- evals

def x6_side_effects_top(tmp):
    """#805 (critic P3): side_effects.sh also converts the PDFs at the top of the corpus, not only its subfolders."""
    corpus = os.path.join(tmp, "corpus")
    os.makedirs(os.path.join(corpus, "sub"))
    for path in ("top.pdf", "sub/inner.pdf"):
        with open(os.path.join(corpus, path), "wb") as f:
            f.write(b"%PDF-1.4\n")
    fake = os.path.join(tmp, "bin")
    os.makedirs(fake)
    with open(os.path.join(fake, "java"), "w") as f:  # writes one output per input; reads nothing
        f.write("#!/bin/sh\nout=''\nwhile [ $# -gt 0 ]; do case $1 in -o) out=$2; shift;; *.pdf|*/) ;; esac; "
                "case $1 in *.pdf) mkdir -p \"$out\"; : > \"$out/$(basename $1 .pdf).md\";; esac; "
                "[ -d \"$1\" ] && for p in \"$1\"/*.pdf; do mkdir -p \"$out\"; : > \"$out/$(basename $p .pdf).md\"; done; shift; done\n")
    os.chmod(os.path.join(fake, "java"), 0o755)
    env = dict(os.environ, PATH=fake + os.pathsep + os.environ.get("PATH", ""))
    run_dir = os.path.join(tmp, "run")
    os.makedirs(run_dir)
    res = sh(["bash", os.path.join(HERE, "side_effects.sh"), "m.jar", "n.jar", corpus, os.path.join(run_dir, "out")], cwd=run_dir, env=env, check=False)
    made = sorted(os.path.relpath(os.path.join(r, n), os.path.join(run_dir, "out", "main")) for r, _, ns in os.walk(os.path.join(run_dir, "out", "main")) for n in ns)
    assert "top.md" in " ".join(made) and "inner.md" in " ".join(made), (made, res.stdout, res.stderr)
    assert "(2 PDFs)" in res.stdout, res.stdout
    return "outputs: " + ", ".join(made)


def pdf_bytes(objects):
    """A small PDF from object bodies (object 1 is the catalog), with a correct xref table."""
    out, offsets = bytearray(b"%PDF-1.7\n"), []
    for i, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode() + b"".join(f"{o:010d} 00000 n \n".encode() for o in offsets)
    out += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


def x21_inspect_pdf(tmp):
    """#836: inspect_pdf.py fails a PDF without a structure tree and a table that breaks Table > TR > TH/TD or has a cell without a child."""
    if not shutil.which("qpdf"):
        raise Skip("qpdf is not on PATH")
    content = b"/TD <</MCID 0>> BDC EMC /TD <</MCID 1>> BDC EMC"

    def tagged(table_kids, row_kids):
        return pdf_bytes([
            b"<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 5 0 R /MarkInfo << /Marked true >> >>",
            b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R /StructParents 0 >>",
            b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream",
            b"<< /Type /StructTreeRoot /K [6 0 R] >>",
            b"<< /Type /StructElem /S /Table /P 5 0 R /K [" + table_kids + b"] >>",
            b"<< /Type /StructElem /S /TR /P 6 0 R /K [" + row_kids + b"] >>",
            b"<< /Type /StructElem /S /TD /P 6 0 R /Pg 3 0 R /K 1 >>",
            b"<< /Type /StructElem /S /TD /P 7 0 R /Pg 3 0 R /K 0 >>",
            b"<< /Type /StructElem /S /TD /P 7 0 R >>"])
    files = {"good": tagged(b"7 0 R", b"9 0 R"), "bad": tagged(b"7 0 R 8 0 R", b"9 0 R 10 0 R"),
             "untagged": pdf_bytes([b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                                    b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>"])}
    got = {}
    for name, data in files.items():
        path = os.path.join(tmp, f"{name}.pdf")
        with open(path, "wb") as f:
            f.write(data)
        res = wpy("inspect_pdf.py", "--json", path, check=False)
        got[name] = (res.returncode, json.loads(res.stdout)[0] if res.stdout.startswith("[") else res.stdout[-300:])
    assert got["good"][0] == 0 and got["good"][1]["table"] == [], got["good"]
    assert got["bad"][0] == 1 and sorted(got["bad"][1]["table"]) == ["TD in Table, not in TR", "TD without a child"], got["bad"]
    assert got["untagged"][0] == 1 and got["untagged"][1]["tagged"] is False, got["untagged"]
    return "Table > TR > TD -> 0; TD in Table and a TD without a child -> 1; no structure tree -> 1"


def x22_bench_commit(tmp):
    """#836: bench.py compare fails when a .commit file is missing or empty: the scores may come from different benchmark revisions."""
    score = {"metrics": {"score": {f"{m}_mean": 0.9 for m in ("overall", "nid", "teds", "mhs")}, "missing_predictions": 0}, "documents": []}
    paths = [os.path.join(tmp, f"{s}.json") for s in ("base", "head")]
    for p in paths:
        with open(p, "w") as f:
            json.dump(score, f)
    out = []
    for label, commits, want in (("no .commit files", None, 1), ("empty commits", ("", ""), 1), ("same commit", ("abc", "abc"), 0)):
        for p, c in zip(paths, commits or ("", "")):
            if commits is None:
                if os.path.exists(p + ".commit"):
                    os.remove(p + ".commit")
            else:
                with open(p + ".commit", "w") as f:
                    f.write(c + "\n")
        res = wpy("bench.py", "compare", *paths, check=False)
        assert res.returncode == want, (label, res.returncode, res.stdout)
        out.append(f"{label} -> {res.returncode}")
    return "; ".join(out)


def x24_md_lists_quote(tmp):
    """#836: md_lists.py reads the written number of an ordered list item inside a blockquote."""
    if sh([sys.executable, "-I", "-c", "import markdown_it"], check=False).returncode:
        raise Skip("markdown-it-py is not installed for this Python")
    path = os.path.join(tmp, "q.md")
    with open(path, "w") as f:
        f.write("> 1. first\n>\n> 3. second\n")
    res = wpy("md_lists.py", path, check=False)
    assert res.returncode == 1 and "written 3, shown 2" in res.stdout, (res.returncode, res.stdout)
    return "'> 3.' after '> 1.' -> written 3, shown 2"


def x25_side_effects_names(tmp):
    """#836: side_effects.sh converts a corpus without subfolders, and a PDF whose name has a space."""
    corpus = os.path.join(tmp, "corpus")
    os.makedirs(corpus)
    for name in ("a b.pdf", "c.pdf"):
        with open(os.path.join(corpus, name), "wb") as f:
            f.write(b"%PDF-1.4\n")
    fake = os.path.join(tmp, "bin")
    os.makedirs(fake)
    with open(os.path.join(fake, "java"), "w") as f:  # writes one output per input PDF; reads nothing
        f.write('#!/bin/bash\nout=; prev=\nfor a in "$@"; do [ "$prev" = -o ] && out=$a; prev=$a; done\n'
                'for a in "$@"; do case $a in *.pdf) mkdir -p "$out"; : > "$out/$(basename "$a" .pdf).md";; esac; done\n')
    os.chmod(os.path.join(fake, "java"), 0o755)
    env = dict(os.environ, PATH=fake + os.pathsep + os.environ.get("PATH", ""))
    run_dir = os.path.join(tmp, "run")
    os.makedirs(run_dir)
    res = sh(["bash", os.path.join(HERE, "side_effects.sh"), "m.jar", "n.jar", corpus, os.path.join(run_dir, "out")], cwd=run_dir, env=env, check=False)
    made = sorted(os.listdir(os.path.join(run_dir, "out", "main", "_top")))
    log = open(os.path.join(run_dir, "out", "main.log")).read()
    assert made == ["a b.md", "c.md"] and "main *:" not in log, (made, log, res.stderr[-300:])
    return "outputs: " + ", ".join(made) + "; no run on an unexpanded folder pattern"


def x26_corpus_refusal(tmp):
    """side_effects.sh refuses a corpus inside the run copy: a PR can change its own samples."""
    run_copy = os.path.join(tmp, "runcopy")
    os.makedirs(os.path.join(run_copy, "samples", "pdf"))
    res = sh(["bash", os.path.join(HERE, "side_effects.sh"), "m.jar", "g.jar", "samples", os.path.join(tmp, "se")], cwd=run_copy, check=False)
    assert res.returncode == 2 and "refused" in res.stdout, res.stdout
    return "a corpus folder inside the run copy -> refused (exit 2)"


EVALS = [("X6-side-effects-top", x6_side_effects_top), ("X21-inspect-pdf", x21_inspect_pdf), ("X22-bench-commit", x22_bench_commit),
         ("X24-md-lists-quote", x24_md_lists_quote), ("X25-side-effects-names", x25_side_effects_names),
         ("X26-corpus-refusal", x26_corpus_refusal)]


def main(argv):
    only = argv[argv.index("-k") + 1] if "-k" in argv else None
    failed = 0
    if only and not any(only in name for name, _ in EVALS):
        print(f"no eval matches -k {only}")
        return 2
    for name, fn in EVALS:
        if only and only not in name:
            continue
        tmp = tempfile.mkdtemp(prefix=f"review-tools-eval-{name}-")
        doc = (fn.__doc__ or "").strip().split("\n")[0]
        try:
            detail = fn(tmp)
            print(f"PASS {name}  {doc}\n       {detail}")
        except Skip as e:
            print(f"SKIP {name}  {doc}\n       {e}")
        except Exception:
            failed += 1
            print(f"FAIL {name}  {doc}\n       kept: {tmp}")
            print("       " + traceback.format_exc().strip().replace("\n", "\n       ")[-3000:])
    print(f"{'FAILED' if failed else 'OK'}: {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
