#!/usr/bin/env python3
"""Compare the test suite of main with the test suite of the merge (catalog: Q2-suite).

Usage: suite_compare.py --main DIR --merge DIR --out suite.json [--git DIR --old SHA --new SHA]
                        (--round-dir WORK/<n>/r<k> [--id E7] | --isolated)
                        [--overlay EXTRA_DIR ... --tree MAIN_TREE]

With --round-dir, it appends its result to evidence.jsonl of the round as condition Q2-suite,
with the head and main SHAs of pr.json: the tool, not the agent, sets pass or fail.

DIR holds the JUnit XML reports of one isolated run (for example surefire-reports copied to
/work/out). The comparison is by test id (class and method). With --git, it also lists the test
source files that the pull request deleted or changed, and the assertion lines that it removed.

Where it runs (SKILL.md, C1): the reports are bytes of a run of the PR. On the host, it reads them
only if the PR changes no build or tooling file (files.json of --round-dir): then main's build
(surefire) wrote them. Else it refuses (exit 3); run it with --isolated inside the isolated
environment, or hold. A report with a DOCTYPE or an entity, a link, or a file over 50 MB does not
count (surefire writes none of them); its tests are then missing.

--overlay: the folders that the review copied over the run copies (extra/<label>/). Their test
classes (src/test/java/**) are the review's own tests, not existing tests: they are left out of the
comparison. An overlay file that replaces a file of MAIN_TREE is not left out.

Each finding is a case that needs a source (SKILL.md, "Cases"):
  missing-on-merge   ran on main, absent on the merge (deleted, renamed, or excluded by configuration)
  newly-skipped      passed on main, skipped on the merge
  newly-failing      passed on main, failed or errored on the merge
  failing-on-both    failed on main and on the merge (environment or main defect; not a PR effect)
  failing-new        absent on main, failed or errored on the merge (a test of the PR that fails)
Exit 1 if there is a finding of the first three kinds or failing-new, else 0.
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

MAX_REPORT = 50 * 1024 * 1024
HOST_REFUSED = {"build", "tooling"}


def results(folder: str, refused: list[str], excluded: set[str]) -> dict[str, str]:
    out: dict[str, str] = {}
    for path in sorted(glob.glob(os.path.join(folder, "**", "*.xml"), recursive=True)):
        if os.path.islink(path) or not os.path.isfile(path) or os.path.getsize(path) > MAX_REPORT:
            refused.append(f"{path}: a link, not a file, or over 50 MB")
            continue
        with open(path, "rb") as f:
            data = f.read()
        if b"<!DOCTYPE" in data or b"<!ENTITY" in data:
            refused.append(f"{path}: has a DOCTYPE or an entity")
            continue
        try:
            root = ET.fromstring(data)
        except ET.ParseError:
            refused.append(f"{path}: not XML")
            continue
        for tc in root.iter("testcase"):
            cls = tc.get("classname", "?")
            if cls in excluded or cls.split("$", 1)[0] in excluded:
                continue
            tid = f"{cls}#{tc.get('name', '?')}"
            if tc.find("failure") is not None or tc.find("error") is not None:
                state = "fail"
            elif tc.find("skipped") is not None:
                state = "skip"
            else:
                state = "pass"
            prev = out.get(tid)
            out[tid] = state if prev in (None, "pass") else prev
    return out


def test_source_changes(repo: str, old: str, new: str) -> list[dict]:
    res = subprocess.run(["git", "-C", repo, "-c", "core.quotePath=false", "-c", "color.ui=never", "diff", "--no-color",
                          "--no-ext-diff", "--no-textconv", "--src-prefix=a/", "--dst-prefix=b/", "-U0", "-M", old, new, "--",
                          ":(glob)**/src/test/**", ":(glob)python/**/tests/**", ":(glob)node/**/test/**"],
                         capture_output=True)
    if res.returncode != 0:  # an empty list would read as "no test file changed" (#836)
        raise SystemExit(f"git diff {old}..{new}: {res.stderr.decode(errors='replace').strip()[:300]}")
    changes, cur = [], None
    for line in res.stdout.decode("utf-8", "replace").split("\n"):
        if line.startswith("diff --git"):
            cur = {"file": line.split(" b/", 1)[-1], "status": "modified", "removed_assertions": []}
            changes.append(cur)
        elif cur and line.startswith("deleted file"):
            cur["status"] = "deleted"
        elif cur and line.startswith("new file"):
            cur["status"] = "added"
        elif cur and line.startswith("-") and not line.startswith("---") and re.search(r"\bassert|\bexpect\(|\bverify\(|@Test", line):
            cur["removed_assertions"].append(line[1:].strip()[:160])
    return [c for c in changes if c["status"] != "added"]


def overlay_classes(dirs: list[str], tree: str) -> tuple[set[str], list[str]]:
    classes, replaces = set(), set()
    for d in dirs:
        for path in glob.glob(os.path.join(d, "**", "*.java"), recursive=True):
            rel = os.path.relpath(path, d).replace(os.sep, "/")
            m = re.search(r"(?:^|/)src/test/java/(.+)\.java$", rel)
            if not m:
                continue
            if os.path.lexists(os.path.join(tree, rel)):
                replaces.add(rel)
            else:
                classes.add(m.group(1).replace("/", "."))
    return classes, sorted(replaces)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--main", required=True)
    p.add_argument("--merge", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--git")
    p.add_argument("--old")
    p.add_argument("--new")
    p.add_argument("--round-dir")
    p.add_argument("--id", default="E-suite")
    p.add_argument("--isolated", action="store_true", help="this run is inside the isolated environment")
    p.add_argument("--overlay", action="append", default=[])
    p.add_argument("--tree", help="the main tree; needed with --overlay")
    a = p.parse_args(argv)
    if a.overlay and not a.tree:
        p.error("--overlay needs --tree (the main tree), to find overlay files that replace a file of main")
    if not a.isolated:
        if not a.round_dir:
            print("refused: on the host, give --round-dir (the check reads files.json); inside the isolated environment, give --isolated")
            return 3
        with open(os.path.join(a.round_dir, "files.json"), encoding="utf-8") as f:
            risky = [x["path"] for x in json.load(f) if x.get("category") in HOST_REFUSED or x.get("old_category") in HOST_REFUSED]
        if risky:
            print(f"refused: this PR changes build or tooling files ({', '.join(risky[:5])}), so its build can write the reports. "
                  "Run suite_compare.py in the isolated environment (--isolated), or hold")
            return 3
    excluded, replaces = overlay_classes(a.overlay, a.tree) if a.overlay else (set(), [])
    refused: list[str] = []
    m, g = results(a.main, refused, excluded), results(a.merge, refused, excluded)
    if not m:
        raise SystemExit(f"no test reports in {a.main}: the comparison has no baseline")
    found = {"missing-on-merge": [], "newly-skipped": [], "newly-failing": [], "failing-on-both": [],
             "failing-new": sorted(tid for tid in set(g) - set(m) if g[tid] == "fail")}
    for tid, state in sorted(m.items()):
        new = g.get(tid)
        if new is None:
            found["missing-on-merge"].append(tid)
        elif state == "pass" and new == "skip":
            found["newly-skipped"].append(tid)
        elif state == "pass" and new == "fail":
            found["newly-failing"].append(tid)
        elif state == "fail" and new == "fail":
            found["failing-on-both"].append(tid)
    result = {"main_tests": len(m), "merge_tests": len(g), "new_on_merge": sorted(set(g) - set(m)), **found,
              "excluded_overlay": sorted(excluded), "overlay_replaces": replaces, "refused_reports": refused}
    if a.git and a.old and a.new:
        result["test_source_changes"] = test_source_changes(a.git, a.old, a.new)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=1)
    summary = (f"main {len(m)} tests, merge {len(g)}; " + ", ".join(f"{k} {len(v)}" for k, v in found.items())
               + (f"; changed or deleted test files {len(result.get('test_source_changes', []))}" if a.git else "")
               + (f"; review overlay classes left out {len(excluded)}" if excluded else "")
               + (f"; refused reports {len(refused)}" if refused else ""))
    print(summary)
    bad = any(found[k] for k in ("missing-on-merge", "newly-skipped", "newly-failing", "failing-new"))
    if a.round_dir:
        with open(os.path.join(a.round_dir, "pr.json"), encoding="utf-8") as f:
            pr = json.load(f)
        rec = {"id": a.id, "condition": "Q2-suite", "state": "fail" if bad else "pass", "head": pr["head"],
               "main": pr["main"], "claim": summary, "output": os.path.abspath(a.out), "source": "suite_compare.py"}
        with open(os.path.join(a.round_dir, "evidence.jsonl"), "a", encoding="utf-8") as f:
            f.write(json.dumps(rec) + "\n")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
