#!/usr/bin/env python3
"""Evals of the review profile of this repository: the checks, path categories, bots, scan rules, and tools that only
this repository has. Offline: replays of real commits of this repository, synthetic commits in temporary git
repositories, and fake tools. Nothing is pushed or posted.

Usage: python3 -I evals/run.py --engine REVIEW_ENGINE_DIR [-k NAME]

REVIEW_ENGINE_DIR is the folder of the review procedure (its scripts/ and its evals/run.py, which hold the
helpers of these evals). It can also be given in the environment variable REVIEW_ENGINE.
An eval that needs a capability that is missing prints SKIP with the reason; a SKIP is not a pass.
"""

from __future__ import annotations

import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import traceback

sys.dont_write_bytecode = True  # the evals write nothing into the profile folder
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def load_engine(argv):
    path = argv[argv.index("--engine") + 1] if "--engine" in argv else os.environ.get("REVIEW_ENGINE")
    if not path or not os.path.isfile(os.path.join(path, "evals", "run.py")):
        print("usage: python3 -I evals/run.py --engine REVIEW_ENGINE_DIR [-k NAME]  (the folder with scripts/ and evals/run.py of the review procedure)")
        sys.exit(2)
    spec = importlib.util.spec_from_file_location("engine_evals", os.path.join(os.path.abspath(path), "evals", "run.py"))
    mod = importlib.util.module_from_spec(spec)
    sys.modules["engine_evals"] = mod
    spec.loader.exec_module(mod)
    return mod


E = load_engine(sys.argv[1:])
E.PROFILE_SRC = HERE  # the helpers copy this profile (without evals/) into the main commit of each fixture repository
globals().update({n: getattr(E, n) for n in dir(E) if not n.startswith("__") and n not in ("main", "EVALS")})
PROFILE_SRC = HERE


def repo_root():
    res = sh(["git", "-C", HERE, "rev-parse", "--show-toplevel"], check=False)
    return res.stdout.strip() if res.returncode == 0 else None


def wpy(script, *args, check=True):
    """Run a tool of this repository's profile folder (the folder that the review reads from main)."""
    return sh([sys.executable, "-I", os.path.join(HERE, script), *args], check=check)


# ---------------------------------------------------------------- evals

def e6_dependabot_replay(tmp):
    """B1 (R2): a Dependabot bump (4e11bbdc) is not closed as 'not reproduced', not Blocked for a missing CLA, and not approved by default."""
    root = repo_root()
    if not root or sh(["git", "-C", root, "cat-file", "-e", "4e11bbdc^{commit}"], check=False).returncode:
        raise Skip("commit 4e11bbdc is not in this checkout")
    h = sh(["git", "-C", root, "rev-parse", "4e11bbdc"]).stdout.strip()
    m = sh(["git", "-C", root, "rev-parse", "4e11bbdc^"]).stdout.strip()
    m = with_profile(root, m)
    h = with_profile(root, h, parent=m)
    rdir, _ = prepare(root, tmp, state(h, m, number=901, author="dependabot[bot]", bot=True, cla=None))
    k = load(rdir, "kind.json")
    assert k["auto"] == "dependency" and k["bot"], k
    sc = scan(root, rdir)
    assert not any(x["decide"] for x in sc["hits"]), sc["hits"]
    complete_round(rdir, "dependency")
    v = verdict(rdir)
    assert v["verdict"] == "Approve" and "--comment" in v["post_command"], (v["verdict"], v["post_command"], v["reasons"])
    complete_round(rdir, "bugfix")
    edit(rdir, "plan.json", lambda p: p.update(q1_reproduce={"release": "pass", "main": "pass", "same_environment": True,
                                                             "release_evidence": "E-release", "main_evidence": "E-main"}))
    vb = verdict(rdir)
    assert not vb["verdict"].startswith("Recommend close") and any("does not fit" in x for x in vb["reasons"]["hold"]), vb
    return f"as dependency: {v['verdict']} ({v['post_command']}); mislabeled as bugfix: {vb['verdict']}"


def e7_fixture_pdf_replay(tmp):
    """B3 (R2): an outside fix that commits a repro PDF (14f0306b) is not held only because a file is binary; the PDF needs a PII check."""
    root = repo_root()
    if not root or sh(["git", "-C", root, "cat-file", "-e", "14f0306b^{commit}"], check=False).returncode:
        raise Skip("commit 14f0306b is not in this checkout")
    h = sh(["git", "-C", root, "rev-parse", "14f0306b"]).stdout.strip()
    m = sh(["git", "-C", root, "rev-parse", "14f0306b^"]).stdout.strip()
    m = with_profile(root, m)
    h = with_profile(root, h, parent=m)
    rdir, _ = prepare(root, tmp, state(h, m, number=902))
    sc = scan(root, rdir)
    rules = {x["rule"] for x in sc["hits"]}
    assert "pdf-added" in rules and "executable-or-archive" not in rules and not any(x["decide"] for x in sc["hits"]), sc["hits"]
    complete_round(rdir, "bugfix")
    v = verdict(rdir)
    assert v["verdict"].startswith("Approve"), (v["verdict"], v["reasons"]["hold"])
    edit(rdir, "plan.json", lambda p: p["scan_explanations"].clear())
    vh = verdict(rdir)
    assert vh["verdict"] == "Hold" and any("pdf-added" in x for x in vh["reasons"]["hold"]), vh
    txt = os.path.join(tmp, "extracted.txt")
    with open(txt, "w") as f:
        f.write("Invoice for 홍길동, phone 010-2345-6789, RRN 900101-1234567\nplaceholder 010-0000-0000\n")
    py("diff_scan.py", "pii", "--out", os.path.join(tmp, "pii.json"), txt)
    pii = {x["rule"] for x in json.load(open(os.path.join(tmp, "pii.json")))["hits"]}
    assert pii == {"pii:phone-kr", "pii:rrn-shaped"}, pii
    return f"explained: {v['verdict']}; unexplained: {vh['verdict']}; PII scan of extracted text: {sorted(pii)}"


def e8_thead_against_earlier_decision(tmp):
    """C1, C5 (R3, R4): a THead/TBody change finds the Acrobat revert 2e5efa3e in the line history; Q2-tagged is needed; Decide."""
    root = repo_root()
    if not root:
        raise Skip("not in a git checkout")
    F = "java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/AutoTaggingProcessor.java"
    clone = os.path.join(tmp, "clone")
    sh(["git", "clone", "-q", "--shared", root, clone])
    full = os.path.join(clone, F)
    if not os.path.exists(full):
        raise Skip(f"{F} is not in this checkout")
    src = open(full, encoding="utf-8").read()
    old = "        // Flat structure: Table > TR > TH/TD (no THead/TBody wrappers)\n"
    if old not in src:
        raise Skip("the flat-table comment is not in this checkout")
    with open(full, "w", encoding="utf-8") as f:
        f.write(src.replace(old, "        // ISO 32000-2 / PDF/UA-2: group rows in THead and TBody.\n"))
    m = sh(["git", "-C", clone, "rev-parse", "HEAD"]).stdout.strip()
    sh(["git", *GITC, "-C", clone, "commit", "-q", "-am", "feat: THead/TBody per PDF/UA-2"])
    h = sh(["git", "-C", clone, "rev-parse", "HEAD"]).stdout.strip()
    m = with_profile(clone, m)
    h = with_profile(clone, h, parent=m)
    rdir, _ = prepare(clone, tmp, state(h, m, number=903))
    py("precedents.py", "--repo", "o/r", "--round-dir", rdir, "--git", clone, "--no-github")
    prec = load(rdir, "precedents.json")
    refs = {x["ref"]: x for x in prec["hits"]}
    assert "2e5efa3e" in refs and refs["2e5efa3e"].get("markers"), sorted(refs)
    assert any(x["source"] == "code-comment" and "Acrobat" in x["title"] for x in prec["hits"]), prec["hits"]
    scan(clone, rdir)
    complete_round(rdir, "feature", precedents=None)
    v = verdict(rdir)
    tagged = next(c for c in v["checks"] if c["id"] == "Q2-tagged")
    assert tagged["needed"], tagged
    assert any("precedent" in x and "not assessed" in x for x in v["reasons"]["hold"]), v["reasons"]["hold"]
    pr = load(rdir, "pr.json")
    with open(os.path.join(rdir, "evidence.jsonl"), "a") as f:
        f.write(json.dumps({"id": "E-prec", "condition": "precedent", "state": "pass", "head": pr["head"], "main": pr["main"]}) + "\n")
    edit(rdir, "plan.json", lambda p: p.update(precedents=[{"id": x["id"], "covers": x["ref"] == "2e5efa3e", "valid": True,
                                                             "effect": "conflict", "evidence": "E-prec"} for x in prec["hits"]]))
    dump(rdir, "decisions.json", {"accept_gaps": ["Q0-precedents"]})
    v2 = verdict(rdir)
    assert v2["verdict"].startswith("Decide") and any("2e5efa3e" in d for d in v2["reasons"]["decide"]), v2
    # #811 r2: the user answers the conflict by the precedent id; it leaves Decide and stays in the notes.
    pid = refs["2e5efa3e"]["id"]
    dump(rdir, "decisions.json", {"tradeoffs": {pid: "keep the change; the earlier decision is revisited separately"}})
    v3 = verdict(rdir)
    assert not any("2e5efa3e" in d for d in v3["reasons"]["decide"]), v3["reasons"]["decide"]
    assert any("2e5efa3e" in n and "answered" in n for n in v3["reasons"]["notes"]), v3["reasons"]["notes"]
    return v2["verdict"] + "; answered by its id -> note"


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




def old_category(path):
    """The path categories as the reviews decided them before they moved into profile.json: the oracle of W2."""
    import fnmatch
    base = path.rsplit("/", 1)[-1]
    instr = P.is_instruction(path)
    if instr:
        return "instruction"
    if path.startswith(".github/"):
        return "workflow"
    hooks = {"hatch_build.py", "mvnw", "mvnw.cmd", "gradlew", "gradlew.bat", "checkstyle.xml", "spotbugs-exclude.xml"}
    if path.startswith(("scripts/", "verification/", "build-scripts/", ".mvn/")) or base.startswith("Dockerfile") \
            or base in {"Makefile", ".gitattributes", ".gitmodules"} or base in hooks or re.match(r"(node|python)/[^/]+/scripts/", path):
        return "tooling"
    if base in P.BUILD_FILES or fnmatch.fnmatch(base, "requirements*.txt"):
        return "build"
    if "/src/test/" in path or re.match(r"python/[^/]+/tests/", path) or re.match(r"node/[^/]+/test/", path) or re.search(r"\.test\.[jt]sx?$", path):
        return "test"
    if path.startswith("samples/"):
        return "samples"
    if path.startswith("skills/"):
        return "skill"
    if re.match(r"(java/[^/]+/src/main/|python/[^/]+/src/|node/[^/]+/src/)", path):
        return "code"
    if base.endswith((".md", ".mdx")) or path.startswith(("docs/", "content/")) or base.startswith(("LICENSE", "NOTICE")):
        return "docs"
    return "other"


def w1_profile_valid(tmp):
    """W1: profile.json is valid, its catalog merges into the generic catalog without a problem, and each file that it names exists."""
    profile = json.load(open(os.path.join(HERE, "profile.json")))
    assert profile["schema"] == 1 and profile["catalog"] == "catalog.json", profile.keys()
    with open(E.CATALOG) as f:
        generic = json.load(f)
    cat, problems = V.merge_catalog(generic, json.load(open(os.path.join(HERE, "catalog.json"))))
    assert not problems, problems
    mine = {c["id"] for c in cat["checks"] if c.get("repository")}
    assert mine == {"Q2-corpus", "Q2-tagged", "Q2-benchmark", "Q2-format"}, mine
    for name, tool in profile["tools"].items():
        assert os.path.isfile(os.path.join(HERE, tool["file"])), tool
    for key, name in profile["docs"].items():
        assert os.path.isfile(os.path.join(HERE, name)), name
    for rule in profile["categories"]["before_build"] + profile["categories"]["after_build"]:
        for rx in rule.get("regex", []):
            re.compile(rx)
    return f"schema 1; repository checks {sorted(mine)}; {len(profile['tools'])} tools and 3 documents exist"


def w2_categories_same(tmp):
    """W2: the path categories of profile.json give the same result as the rules that the reviews used before: each tracked file, and odd paths."""
    profile = json.load(open(os.path.join(HERE, "profile.json")))
    root = repo_root()
    paths = sh(["git", "-C", root, "ls-files"]).stdout.splitlines() if root else []
    paths += ["java/opendataloader-pdf-core/src/main/java/A.java", "java/opendataloader-pdf-cli/src/test/java/ATest.java", "java/pom.xml",
              "python/opendataloader-pdf/tests/test_a.py", "python/opendataloader-pdf/src/x/a.py", "python/x/scripts/gen.py", "python/x/hatch_build.py",
              "node/x/test/a.js", "node/x/src/a.generated.ts", "node/x/scripts/a.mjs", "node/x/src/a.test.ts", "samples/pdf/a.pdf", "skills/odl-pdf/SKILL.md",
              "content/docs/a.mdx", "docs/a.md", "verification/ci-verify.py", "build-scripts/a.sh", ".mvn/wrapper/a", "java/checkstyle.xml",
              "java/x/src/main/README.md", "AGENTS.md", ".github/workflows/a.yml", ".agents/x/y.md", "Makefile", "requirements-dev.txt", "NOTICE", "x/y/z.bin"]
    bad = [(p, P.category(p, profile), old_category(p)) for p in paths if P.category(p, profile) != old_category(p)]
    assert not bad, bad[:10]
    return f"{len(paths)} paths: the same category as the old rules"


def w3_needed_checks(tmp):
    """W3: the checks of this repository are needed for their paths, and the widened generic checks keep their old triggers."""
    cases = [
        ("java/opendataloader-pdf-core/src/main/java/org/x/A.java", {"Q2-corpus", "Q2-benchmark", "Q2-suite", "Q3-lint", "Q2-counterexamples"}, {"Q2-tagged", "Q2-format", "Q3-related", "Q3-text"}),
        ("java/opendataloader-pdf-core/src/main/java/org/x/processors/AutoTaggingProcessor.java", {"Q2-tagged", "Q2-corpus"}, {"Q2-format"}),
        ("java/opendataloader-pdf-core/src/main/java/org/x/markdown/MarkdownGenerator.java", {"Q2-format", "Q2-corpus"}, {"Q2-tagged"}),
        ("java/opendataloader-pdf-core/src/main/java/org/x/cli/CLIOptions.java", {"Q3-text", "Q3-related"}, {"Q2-tagged"}),
        ("options.json", {"Q3-related"}, {"Q2-corpus", "Q2-benchmark"}),
        ("schema.json", {"Q2-format", "Q3-related"}, {"Q2-corpus"}),
        ("python/opendataloader-pdf/src/opendataloader_pdf/a.py", {"Q3-lint"}, {"Q2-corpus"}),
        ("node/opendataloader-pdf/src/a.ts", {"Q3-lint"}, {"Q2-corpus"}),
        ("content/docs/a.mdx", {"Q3-text"}, {"Q2-corpus", "Q3-lint"}),
        ("scripts/generate-options.mjs", {"Q3-related"}, {"Q2-corpus"}),
    ]
    kinds = {"java": "feature", "python": "feature", "node": "feature", "options.json": "feature", "schema.json": "feature", "scripts": "tooling", "content": "docs"}
    out = []
    for i, (path, want, absent) in enumerate(cases):
        kind = kinds[path.split("/")[0]]
        rdir, pr = fixture_round(tmp, f"n{i}", "code" if kind == "feature" else ("docs" if kind == "docs" else "tooling"), [path], kind)
        need = {c["id"] for c in verdict(rdir)["checks"] if c["needed"]}
        assert want <= need and not (absent & need), (path, sorted(need), want, absent)
        out.append(path.rsplit("/", 1)[-1])
    return "needed checks as expected for " + ", ".join(out)


def w4_triage_bots(tmp):
    """W4: the review bot, the CLA bot, and the reminder markers of this repository's profile drive the triage."""
    repo = new_repo(tmp)
    commit(repo, {"README.md": "# x\n"})
    ok = [{"workflowName": "Test", "status": "completed", "conclusion": "success"}]
    cla = [{"name": "license/cla", "state": "SUCCESS"}]
    code = [{"path": "java/opendataloader-pdf-core/src/main/java/A.java"}]

    def pr(n, **kw):
        d = {"number": n, "title": f"fix: change {n}", "author": {"login": f"c{n}"}, "state": "OPEN", "isDraft": False, "isCrossRepository": True,
             "createdAt": f"2026-09-{n:02d}T00:00:00Z", "additions": 5, "deletions": 1, "changedFiles": 1, "files": code, "statusCheckRollup": cla,
             "closingIssuesReferences": [{"number": 100 + n}], "mergeable": "MERGEABLE", "baseRefName": "main", "headRefOid": f"{n:040x}", "url": "u"}
        d.update(kw)
        return d
    prs = [pr(1), pr(2), pr(3, statusCheckRollup=[]), pr(4, files=[{"path": "skills/odl-pdf/SKILL.md"}]),
           pr(5, labels=[{"name": "needs-cla"}, {"name": "stale"}])]
    marker = "<!-- odl-pr-reminder -->"
    data = {"repo": "o/r", "viewer": "maint", "total_count": len(prs), "prs": prs, "reviews": {}, "commits": {},
            "comments": {"3": [{"user": {"login": "CLAassistant"}, "created_at": "2026-10-01T00:00:00Z", "body": "[![CLA](https://x/badge/signed)](u)"}],
                         "5": [{"user": {"login": "github-actions[bot]"}, "created_at": "2026-10-01T00:00:00Z", "body": marker + " sign"}]},
            "threads": {"1": [{"resolved": False, "outdated": False, "authors": ["coderabbitai"]}],
                        "2": [{"resolved": False, "outdated": False, "authors": ["reviewbot"]}]},
            "runs": {str(n): ok for n in range(1, 6)}, "permissions": {}}
    data["workflows_hint"] = "the fixture repository has no workflow, so no run is expected"
    fx = os.path.join(tmp, "triage.json")
    json.dump(data, open(fx, "w"))
    work = os.path.join(tmp, "work")
    py("prepare.py", "triage", "--fixture", fx, "--work", work, "--git", repo)
    rows = {r["number"]: r for r in load(os.path.join(work, "triage"), "triage.json")["rows"]}
    assert "coderabbitai" not in str(rows[1]) and any("review-bot thread" in b for b in rows[1]["blocked"]), rows[1]
    assert not rows[2]["blocked"], rows[2]  # a bot that the profile does not name
    assert any("CLA bot's comment says signed" in n for n in rows[3]["notes"]), rows[3]["notes"]
    assert any("second maintainer" in n and "skills/odl-pdf" in n for n in rows[4]["notes"]), rows[4]["notes"]  # skills/ is sensitive here
    assert rows[5]["reminders"] == 1 and "close candidate" in rows[5]["notes"][-1], rows[5]
    return "CodeRabbit thread blocks; a bot outside the profile does not; CLA bot comment; skills/ is sensitive; reminders and the stale label"


def w5_corpus_refusal(tmp):
    """W5: side_effects.sh refuses a corpus inside the run copy: a PR can change its own samples."""
    run_copy = os.path.join(tmp, "runcopy")
    os.makedirs(os.path.join(run_copy, "samples", "pdf"))
    res = sh(["bash", os.path.join(HERE, "side_effects.sh"), "m.jar", "g.jar", "samples", os.path.join(tmp, "se")], cwd=run_copy, check=False)
    assert res.returncode == 2 and "refused" in res.stdout, res.stdout
    return "a corpus folder inside the run copy -> refused (exit 2)"


def w6_needs_checks(tmp):
    """W6: a change to the tagging code needs Q2-tagged, and a generated file needs Q3-related."""
    rdir, pr = fixture_round(tmp, "i", "code", ["java/opendataloader-pdf-core/src/main/java/org/x/autotagging/ChunksWriter.java",
                                                "node/x/src/cli.generated.ts"], "feature")
    need = {c["id"] for c in verdict(rdir)["checks"] if c["needed"]}
    assert {"Q2-tagged", "Q3-related"} <= need, need
    return "autotagging and generated files need their checks"


def w7_evidence_ids(tmp):
    """W7: a stale record of a check of this repository holds the verdict, and a skip needs its reason like a generic check (the old U1 and X29 steps on Q2-corpus and Q2-benchmark)."""
    rdir, pr = fixture_round(tmp, "u1", "code", ["java/x/src/main/A.java"], "feature")
    assert verdict(rdir)["verdict"] == "Approve", verdict(rdir)["reasons"]
    recs = [json.loads(x) for x in open(os.path.join(rdir, "evidence.jsonl")) if x.strip()]
    stale = [dict(r, head="d" * 40) if r["condition"] == "Q2-corpus" else r for r in recs]
    open(os.path.join(rdir, "evidence.jsonl"), "w").write("\n".join(json.dumps(r) for r in stale) + "\n")
    v = verdict(rdir)
    assert v["verdict"] == "Hold" and any("Q2-corpus: no evidence" in x for x in v["reasons"]["hold"]), v
    rdir, pr = fixture_round(tmp, "u2", "code", ["java/x/src/main/A.java"], "feature")
    edit(rdir, "plan.json", lambda p: [c.update(status="skipped", reason="only labels change") for c in p["checks"] if c["id"] == "Q2-benchmark"])
    v = verdict(rdir)
    assert v["verdict"] == "Hold" and any("Q2-benchmark: skipped" in x for x in v["reasons"]["hold"]), v
    add_ev(rdir, pr, {"id": "E-sk", "condition": "skip:Q2-benchmark", "state": "pass", "claim": "no call path"})
    assert verdict(rdir)["verdict"] == "Approve", verdict(rdir)["reasons"]
    return "stale Q2-corpus record -> Hold; Q2-benchmark skip without its record -> Hold; with it -> Approve"


EVALS = [("E6", e6_dependabot_replay), ("E7", e7_fixture_pdf_replay), ("E8", e8_thead_against_earlier_decision),
         ("X6-side-effects-top", x6_side_effects_top), ("X21-inspect-pdf", x21_inspect_pdf), ("X22-bench-commit", x22_bench_commit),
         ("X24-md-lists-quote", x24_md_lists_quote), ("X25-side-effects-names", x25_side_effects_names),
         ("W1-profile-valid", w1_profile_valid), ("W2-categories-same", w2_categories_same), ("W3-needed-checks", w3_needed_checks),
         ("W4-triage-bots", w4_triage_bots), ("W5-corpus-refusal", w5_corpus_refusal), ("W6-needs-checks", w6_needs_checks),
         ("W7-evidence-ids", w7_evidence_ids)]


def main(argv):
    only = argv[argv.index("-k") + 1] if "-k" in argv else None
    failed = 0
    for name, fn in EVALS:
        if only and only not in name:
            continue
        tmp = tempfile.mkdtemp(prefix=f"review-profile-eval-{name}-", dir=E.TMP_ROOT)
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
