"""Evals of the odl-fix harness (scripts/fix_gates.py). Offline: no Maven, no jar, no network.

The judges get the files that the gates read (surefire XML, side_effects.sh output folders, records),
and check() gets a small git repository with a base tree. Each eval names the weakness it guards.
evals/run.py runs them with the issue-state evals.
"""

from __future__ import annotations

import importlib.util
import json
import os
import subprocess

SKILL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_spec = importlib.util.spec_from_file_location("fix_gates", os.path.join(SKILL, "scripts", "fix_gates.py"))
fg = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(fg)


def write(root, rel, text, mode="w"):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, mode, **({} if "b" in mode else {"encoding": "utf-8"})) as f:
        f.write(text)
    return path


def git(tree, *args):
    res = subprocess.run(["git", "-C", tree, *args], capture_output=True, text=True)
    assert res.returncode == 0, (args, res.stderr)
    return res.stdout.strip()


def case(cid, kind="problem", test="Issue1Test#reporterCase", symptom="lost the header"):
    k = {"id": cid, "kind": kind, "test": test}
    if kind == "problem":
        k["symptom"] = symptom
    return k


def contract(**over):
    c = {"issue": 1, "objective": "\"the header is missing\"", "scope": ["java/opendataloader-pdf-core/src/main/java/**"],
         "cases": [case("P1"), case("G1", "guard", "Issue1Test#defaultStays")], "invariants": [], "amendments": []}
    c.update(over)
    return c


def report_xml(cases):
    """surefire XML: [(class, method, status, message)]."""
    rows = []
    for cls, method, status, message in cases:
        inner = {"pass": "", "fail": f'<failure message="{message}"/>', "error": f'<error message="{message}"/>',
                 "skipped": "<skipped/>"}[status]
        rows.append(f'<testcase name="{method}" classname="org.example.{cls}">{inner}</testcase>')
    return f'<?xml version="1.0" encoding="UTF-8"?><testsuite>{"".join(rows)}</testsuite>'


# ---------------------------------------------------------------- the cases


def g1_problem_must_reproduce(tmp):
    """A problem case counts only if it fails on the base with the reporter's symptom (no pass, no other failure, no compile error)."""
    cases = [case("P1"), case("P2"), case("P3"), case("P4", symptom="NullPointerException")]
    res = {"P1": {"status": "fail", "message": "expected true: lost the header of the backend"},
           "P2": {"status": "pass", "message": ""},
           "P3": {"status": "fail", "message": "expected 3 but was 2"},
           "P4": {"status": "error", "message": "java.lang.NullPointerException: x"}}
    j = fg.judge_problem(cases, res)
    verdicts = {r["id"]: r["verdict"] for r in j["cases"]}
    assert verdicts == {"P1": "reproduced", "P2": "not reproduced", "P3": "other failure", "P4": "reproduced"}, verdicts
    assert not j["ok"]
    j = fg.judge_problem([case("P1")], {})
    assert not j["ok"] and j["cases"][0]["verdict"].startswith("no result"), j
    return "pass -> not reproduced; other message -> other failure; missing -> no result; exception with symptom counts"


def g2_guard_holds_on_base(tmp):
    """A guard is a behaviour that must stay: it must pass on the base, or it guards nothing."""
    j = fg.judge_problem([case("P1"), case("G1", "guard")], {"P1": {"status": "fail", "message": "lost the header"},
                                                             "G1": {"status": "fail", "message": "x"}})
    assert not j["ok"] and j["cases"][1]["verdict"].startswith("does not hold"), j
    return "guard fails on the base -> problem gate fails"


def g3_reports(tmp):
    """Surefire XML: a parameterized method is one entry and its worst run counts; a report with a DOCTYPE is ignored."""
    d = os.path.join(tmp, "reports")
    write(d, "TEST-org.example.Issue1Test.xml", report_xml([
        ("Issue1Test", "reporterCase(String)[1]", "pass", ""), ("Issue1Test", "reporterCase(String)[2]", "fail", "lost the header"),
        ("Issue1Test", "defaultStays()", "pass", ""), ("Issue1Test", "skippedOne", "skipped", "")]))
    write(d, "TEST-org.example.Evil.xml", '<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e "x">]><testsuite>'
          '<testcase name="defaultStays" classname="org.example.Issue1Test"><failure message="e"/></testcase></testsuite>')
    p = fg.parse_reports([d])
    assert p[("Issue1Test", "reporterCase")]["status"] == "fail", p
    assert p[("Issue1Test", "defaultStays")]["status"] == "pass", p
    assert p[("Issue1Test", "skippedOne")]["status"] == "skipped", p
    j = fg.judge_fix([case("S1", "guard", "Issue1Test#skippedOne")], {"S1": p[("Issue1Test", "skippedOne")]})
    assert not j["ok"], j
    return "worst run of a parameterized method; DOCTYPE report ignored; skipped is not a pass"


def g4_fix_needs_every_case(tmp):
    """On the fix, the problem cases pass and the guards still pass."""
    j = fg.judge_fix([case("P1"), case("G1", "guard")], {"P1": {"status": "pass", "message": ""}, "G1": {"status": "fail", "message": "x"}})
    assert not j["ok"]
    j = fg.judge_fix([case("P1"), case("G1", "guard")], {"P1": {"status": "pass", "message": ""}, "G1": {"status": "pass", "message": ""}})
    assert j["ok"]
    return "a failing guard fails the fix gate"


def g5_scope(tmp):
    """A change outside the contract scope is extra work: the scope gate fails. Tests are free."""
    changes = [{"status": "M", "path": "java/opendataloader-pdf-core/src/main/java/org/x/A.java"},
               {"status": "A", "path": "java/opendataloader-pdf-core/src/test/java/org/x/Issue1Test.java"},
               {"status": "M", "path": "python/opendataloader-pdf/tests/test_x.py"}]
    assert fg.judge_scope(changes, ["java/opendataloader-pdf-core/src/main/java/org/x/A.java"])["ok"]
    j = fg.judge_scope(changes + [{"status": "M", "path": "java/opendataloader-pdf-core/src/main/java/org/x/B.java"}],
                       ["java/opendataloader-pdf-core/src/main/java/org/x/A.java"])
    assert not j["ok"] and j["outside"] == ["java/opendataloader-pdf-core/src/main/java/org/x/B.java"], j
    assert fg.judge_scope(changes, ["java/*/src/main/java/org/x/*.java"])["ok"]
    return "B.java outside the scope -> fail; tests and globs ok"


def g6_pinning(tmp):
    """A changed main file that no case needs is not pinned: remove it, or add the case that needs it."""
    assert fg.judge_pinning([{"file": "A.java", "pinned": True, "pinned_by": ["P1"]}])
    assert not fg.judge_pinning([{"file": "A.java", "pinned": True, "pinned_by": ["P1"]}, {"file": "B.java", "pinned": False, "pinned_by": []}])
    return "an unpinned file fails the pinning gate"


def side_effects_out(tmp, files_main, files_merge, errors="(none)"):
    out = os.path.join(tmp, "side")
    for side, files in (("main", files_main), ("merge", files_merge)):
        for rel, data in files.items():
            write(out, os.path.join(side, rel), data, "wb" if isinstance(data, bytes) else "w")
    write(out, "summary.txt", f"corpus: x (1 PDFs)\n== error diff (main -> merge)\n{errors}\n== changed outputs\n== count: 0\n")
    return out


def g7_invariants(tmp):
    """Each side-effect difference must be equal after a declared normalizer, or allowed by an amendment; nothing to compare is not a pass."""
    j1 = '{\n  "id" : 5,\n  "kids" : [ { "id" : 2, "type" : "paragraph" } ]\n}'
    j2 = '{\n  "id" : 9,\n  "kids" : [ { "id" : 4, "type" : "paragraph" } ]\n}'
    out = side_effects_out(tmp, {"a/x.json": j1, "a/x.txt": "A\n\nB\n", "a/x.md": "A\n"},
                           {"a/x.json": j2, "a/x.txt": "A\n\n\nB\n", "a/x.md": "A\n"})
    inv = {"id": "I1", "formats": list(fg.KINDS), "ignore": [], "allow": []}
    j = fg.judge_side_effects(out, inv)
    assert not j["ok"] and {r["file"] for r in j["remaining"]} == {"a/x.json", "a/x.txt"}, j
    j = fg.judge_side_effects(out, {**inv, "ignore": ["json:id", "text:blank-lines"]})
    assert j["ok"] and j["normalized"] == 2 and j["equal"] == 1, j
    j = fg.judge_side_effects(out, {**inv, "ignore": ["json:id"], "allow": [{"file": "a/*.txt", "reason": "blank line", "amendment": 1}]})
    assert j["ok"] and j["allowed"][0]["file"] == "a/x.txt", j
    out2 = side_effects_out(os.path.join(tmp, "2"), {"a/x.md": "A\n"}, {"a/x.md": "A\n", "a/y.md": "new\n"})
    j = fg.judge_side_effects(out2, inv)
    assert not j["ok"] and j["remaining"] == [{"file": "a/y.md", "what": "only in merge"}], j
    out3 = side_effects_out(os.path.join(tmp, "3"), {"a/x.md": "A\n"}, {"a/x.md": "A\n"}, errors="> Error x")
    assert not fg.judge_side_effects(out3, inv)["ok"]
    out4 = side_effects_out(os.path.join(tmp, "4"), {}, {})
    j = fg.judge_side_effects(out4, inv)
    assert not j["ok"] and j["errors"] == "no outputs to compare", j
    return "id and blank-line diffs fail until declared; allow works; new file, error diff, and no output fail"


def g8_pdf_document_id(tmp):
    """A tagged PDF that differs only in its /ID is equal after tagged-pdf:document-id."""
    a = b"%PDF-1.7\n1 0 obj<<>>endobj\ntrailer<</ID [<AAAA><BBBB>] /Size 2>>\n%%EOF"
    b = b"%PDF-1.7\n1 0 obj<<>>endobj\ntrailer<</ID [<CCCC><DDDD>] /Size 2>>\n%%EOF"
    assert fg.normalize(a, "tagged-pdf", ["tagged-pdf:document-id"]) == fg.normalize(b, "tagged-pdf", ["tagged-pdf:document-id"])
    assert fg.normalize(a, "tagged-pdf", []) != fg.normalize(b, "tagged-pdf", [])
    return "/ID only -> equal after the normalizer"


def g9_contract_rules(tmp):
    """The contract refuses what the reproduction must not do: a remote hybrid URL, an output path, a document outside $WORK."""
    write(tmp, "attachments/a1.pdf", "%PDF")
    ok = contract(reporter={"document": "attachments/a1.pdf", "runs": [
        {"id": "R1", "options": ["--hybrid", "docling-fast", "--hybrid-url", "http://127.0.0.1:5012"], "format": "json",
         "fix": {"types": {"header": ">0"}}}]})
    assert fg.validate_contract(ok, tmp) == [], fg.validate_contract(ok, tmp)
    bad = [
        contract(reporter={**ok["reporter"], "runs": [{**ok["reporter"]["runs"][0], "options": ["--hybrid-url", "http://evil.example:5012"]}]}),
        contract(reporter={**ok["reporter"], "runs": [{**ok["reporter"]["runs"][0], "options": ["-o", "/tmp/x"]}]}),
        contract(reporter={**ok["reporter"], "runs": [{**ok["reporter"]["runs"][0], "fix_options": ["--hybrid-url", "http://10.0.0.1:5003"]}]}),
        contract(reporter={**ok["reporter"], "document": "../outside.pdf"}),
        contract(cases=[{"id": "P1", "kind": "problem", "test": "Issue1Test#x"}]),
        contract(cases=[case("G1", "guard")]),
        contract(cases=[case("P1", test="Issue1Test")]),
        contract(invariants=[{"id": "I1", "corpus": "samples/pdf", "allow": [{"file": "*", "reason": "x"}]}]),
        contract(invariants=[{"id": "I1", "corpus": "../../etc"}]),
        contract(scope=[]),
    ]
    for c in bad:
        assert fg.validate_contract(c, tmp), c
    return f"valid contract ok; {len(bad)} bad contracts refused"


def g10_contract_only_grows(tmp):
    """After Approval 1 the contract can only grow, and each change needs an amendment; a frozen case cannot change."""
    frozen = contract()
    grown = contract(cases=frozen["cases"] + [case("G2", "guard", "Issue1Test#twoColumns")])
    changes, errors = fg.contract_changes(frozen, grown)
    assert errors == ["the contract changed after the freeze without a new amendment"], errors
    grown["amendments"] = [{"n": 1, "what": "add G2", "why": "found a variant"}]
    changes, errors = fg.contract_changes(frozen, grown)
    assert not errors and any("G2" in x for x in changes), (changes, errors)
    weaker = contract(cases=[case("P1", symptom="other text"), frozen["cases"][1]], amendments=grown["amendments"])
    assert any("P1 changed" in e for e in fg.contract_changes(frozen, weaker)[1])
    removed = contract(cases=[frozen["cases"][0]], amendments=grown["amendments"])
    assert any("G1 was removed" in e for e in fg.contract_changes(frozen, removed)[1])
    return "change without amendment, changed symptom, removed guard -> errors; added guard with amendment -> listed"


def g11_required_gates(tmp):
    """The gates that a change needs come from its paths, not from the agent."""
    java = [{"status": "M", "path": "java/opendataloader-pdf-core/src/main/java/org/x/A.java"}]
    assert fg.required_gates(contract(), java) == ["base-build", "build", "fix", "scope", "pinning", "static", "wrappers",
                                                   "issue-corpus", "invariants", "bench"], fg.required_gates(contract(), java)
    docs = [{"status": "M", "path": "README.md"}]
    assert fg.required_gates(contract(), docs) == ["base-build", "build", "fix", "scope"]
    py = [{"status": "M", "path": "python/opendataloader-pdf/src/opendataloader_pdf/x.py"}]
    assert "wrappers" in fg.required_gates(contract(), py) and "pinning" not in fg.required_gates(contract(), py)
    return "Java main -> all gates; README -> 4; Python -> wrappers, no pinning"


def setup_repo(tmp, c_extra=None, work_files=None):
    """A repository with a base commit, a base tree, and a fix commit; a contract; jars; records of each gate."""
    work = os.path.join(tmp, "work")
    src = os.path.join(tmp, "src")
    os.makedirs(work)
    os.makedirs(src)
    git(src, "init", "-q", "-b", "main")
    git(src, "config", "user.email", "eval@example.invalid")
    git(src, "config", "user.name", "eval")
    write(src, "java/opendataloader-pdf-core/src/main/java/org/x/A.java", "class A {}\n")
    write(src, "samples/pdf/sample.pdf", "%PDF-1.7\n")
    git(src, "add", "-A")
    git(src, "commit", "-q", "-m", "base")
    git(src, "worktree", "add", "-q", "--detach", os.path.join(work, "base-src"), "HEAD")
    git(src, "switch", "-q", "-c", "fix")
    write(src, "java/opendataloader-pdf-core/src/main/java/org/x/A.java", "class A { int fixed; }\n")
    write(src, "java/opendataloader-pdf-core/src/test/java/org/x/Issue1Test.java", "class Issue1Test {}\n")
    git(src, "add", "-A")
    git(src, "commit", "-q", "-m", "fix")
    write(work, "env.sh", f"SRC='{src}'\n")
    write(work, "attachments/a1.pdf", "%PDF-1.7 the reporter's document\n")
    for rel, text in (work_files or {}).items():
        write(work, rel, text)
    write(work, "contract.json", json.dumps(contract(**(c_extra or {}))))
    write(work, "base.jar", "base", "w")
    write(work, "fix.jar", "fix", "w")
    ctx = fg.Ctx(work)
    c = fg.load_contract(ctx)
    with open(os.path.join(work, "contract.lock"), "w", encoding="utf-8") as f:
        json.dump({"sha256": fg.contract_hash(c), "contract": c, "document_sha256": fg.reporter_document_sha(ctx, c)}, f)
    jars = {"base": fg.sha256_file(ctx.jar("base")), "fix": fg.sha256_file(ctx.jar("fix"))}
    for g in ["problem"] + fg.required_gates(c, fg.changed_files(ctx)):
        summary = {}
        if g == "problem":
            summary = {"tests_digest": fg.files_digest(src, fg.case_files(ctx, c)), "patches_digest": fg.patches_digest(ctx, c),
                       "cases": [{"id": "P1", "status": "fail", "verdict": "reproduced"}, {"id": "G1", "status": "pass", "verdict": "holds"}]}
        if g == "fix":
            summary = {"cases": [{"id": "P1", "status": "pass"}, {"id": "G1", "status": "pass"}]}
        if g == "reporter":
            summary = {"runs": [], "document_sha256": fg.reporter_document_sha(ctx, c)}
        fg.append_record(ctx, {"gate": g, "state": "pass", "head": ctx.head(), "tree": ctx.tree(), "base": ctx.base(),
                               "contract": fg.contract_hash(c),
                               "jars": jars, "summary": summary})
    write(work, "selfcheck.json", json.dumps({"head": ctx.head(), "items": {str(i): "ok" for i in range(1, len(fg.SELFCHECK) + 1)}}))
    return ctx, src, work


def g12_check_freshness(tmp):
    """A record counts only for the current commit, base, contract, and jars; the problem proof also for the current test files."""
    ctx, src, work = setup_repo(tmp)
    chk = fg.compute_check(ctx)
    assert chk["ok"], chk
    git(src, "commit", "-q", "--amend", "-m", "fix(x): the squashed commit")
    chk = fg.compute_check(ctx)
    assert chk["ok"], ("a squash with the same content keeps the records", chk)
    write(work, "fix.jar", "rebuilt", "w")
    chk = fg.compute_check(ctx)
    assert not chk["ok"] and any(r["gate"] == "build" and "fix.jar changed" in r["why"] for r in chk["gates"]), chk
    write(work, "fix.jar", "fix", "w")
    write(src, "java/opendataloader-pdf-core/src/test/java/org/x/Issue1Test.java", "class Issue1Test { int weaker; }\n")
    assert any("uncommitted" in e for e in fg.compute_check(ctx)["errors"])
    git(src, "commit", "-q", "-am", "weaker test")
    chk = fg.compute_check(ctx)
    why = {r["gate"]: r["why"] for r in chk["gates"]}
    assert why["problem"] == "the case test files changed after the problem run" and why["fix"].startswith("for another commit"), why
    assert why["base-build"] == "" and why["issue-corpus"] == "", ("they read only the base tree", why)
    return "fresh -> DONE; same-content squash -> DONE; new jar, uncommitted change, new content, changed test -> NOT DONE"


def g13_check_needs_freeze_and_selfcheck(tmp):
    """check fails without the frozen contract and without a complete self-check for this commit."""
    ctx, src, work = setup_repo(tmp)
    with open(os.path.join(work, "selfcheck.json"), "w", encoding="utf-8") as f:
        json.dump({"head": ctx.head(), "items": {"1": "note: the parser loop has a limit of 32", **{str(i): "ok" for i in range(2, 12)},
                                                 "12": "the workflow uses pull_request_target"}}, f)
    chk = fg.compute_check(ctx)
    assert not chk["ok"] and any("item 12" in e for e in chk["errors"]) and chk["selfcheck_notes"], chk
    os.remove(os.path.join(work, "contract.lock"))
    assert any("not frozen" in e for e in fg.compute_check(ctx)["errors"])
    return "open self-check finding and no freeze -> NOT DONE; a note is reported"


def g14_report_has_no_local_paths(tmp):
    """The evidence for GitHub names base.jar, fix.jar, and $WORK, never a local path or the user name."""
    ctx, src, work = setup_repo(tmp)
    fg.append_record(ctx, {"gate": "invariants", "state": "pass", "head": ctx.head(), "tree": ctx.tree(), "base": ctx.base(),
                           "contract": fg.contract_hash(fg.load_contract(ctx)),
                           "jars": {"base": fg.sha256_file(ctx.jar("base")), "fix": fg.sha256_file(ctx.jar("fix"))},
                           "summary": {"invariants": [{"id": "I1", "corpus": "samples/pdf", "options": [], "ignore": ["json:id"],
                                                       "compared": 3, "equal": 2, "normalized": 1, "allowed": [], "remaining": [],
                                                       "errors": f"see {work}/gates/invariants/I1.log"}]}})
    text = fg.report_text(ctx)
    assert work not in text and os.path.expanduser("~") not in text and "$WORK/gates" in text, text
    assert "passes" in text.splitlines()[0], text
    assert "| P1 | problem | `Issue1Test#reporterCase` | reproduced | pass |" in text, text
    return "no local path; the check result and the case table come from the records"


def g15_pull_request(tmp):
    """After the push: CI must pass, and each critical or major CodeRabbit comment needs a reply from a person."""
    green = [{"name": "test", "bucket": "pass"}, {"name": "CodeRabbit", "bucket": "pass"}]
    major = {"id": 1, "user": {"login": "coderabbitai[bot]"}, "path": "A.java", "line": 3,
             "body": "_⚠️ Potential issue_ | _🟠 Major_\n\nThe loop has no limit."}
    nit = {"id": 2, "user": {"login": "coderabbitai[bot]"}, "body": "_🧹 Nitpick_ rename x"}
    assert fg.judge_pr(green, [nit])["state"] == "pass"
    assert fg.judge_pr(green, [major, nit])["state"] == "fail"
    assert fg.judge_pr(green, [major, {"id": 3, "in_reply_to_id": 1, "user": {"login": "coderabbitai[bot]"}, "body": "ok"}])["state"] == "fail"
    assert fg.judge_pr(green, [major, {"id": 4, "in_reply_to_id": 1, "user": {"login": "maintainer"}, "body": "Fixed in abc."}])["state"] == "pass"
    assert fg.judge_pr([{"name": "test", "bucket": "fail"}], [])["state"] == "fail"
    assert fg.judge_pr([{"name": "test", "bucket": "pending"}], [])["state"] == "waiting"
    return "major without a person's reply -> fail; nitpick ignored; failing CI -> fail; pending -> waiting"


def g16_reporter_expectations(tmp):
    """The reporter checks count element types and look for text, but a failed check never repeats the document text."""
    doc = json.dumps({"kids": [{"type": "header", "kids": [{"type": "paragraph", "content": "SECRET TEXT"}]}, {"type": "paragraph"}]})
    assert fg.judge_expect(doc, "json", {"types": {"header": ">0", "paragraph": "=2"}}) == []
    failed = fg.judge_expect(doc, "json", {"types": {"footer": ">=1"}, "contains": ["MISSING WORD"], "absent": ["SECRET TEXT"]})
    assert failed == ["types.footer >=1 (got 0)", "contains[0]", "absent[0]"], failed
    assert not any("SECRET" in f or "MISSING" in f for f in failed)
    return "counts and text checks; failure names carry no document text"


def g17_linters(tmp):
    """Static analysis fails only for a finding that the change adds: the same tool, file, and rule counted on both trees."""
    ruff = "src/opendataloader_pdf/x.py:3:1: F401 [*] `os` imported but unused\nFound 1 error.\n"
    assert fg.parse_ruff(ruff) == {"ruff src/opendataloader_pdf/x.py:F401": 1}, fg.parse_ruff(ruff)
    # ESLint 10 (#854): --format json; a parse error has no rule; an ignored file is not in the output
    eslint = json.dumps([
        {"filePath": "/w/node/opendataloader-pdf/src/a.ts", "messages": [
            {"ruleId": "@typescript-eslint/no-unused-vars", "severity": 1, "line": 4},
            {"ruleId": "@typescript-eslint/no-unused-vars", "severity": 1, "line": 9}]},
        {"filePath": "/w/node/opendataloader-pdf/src/b.ts", "messages": [{"ruleId": None, "fatal": True, "severity": 2}]},
        {"filePath": "/w/node/opendataloader-pdf/src/c.ts", "messages": []}])
    assert fg.parse_eslint(eslint, "/w/node/opendataloader-pdf") == {
        "eslint src/a.ts:@typescript-eslint/no-unused-vars": 2, "eslint src/b.ts:fatal": 1}, fg.parse_eslint(eslint, "/w/node/opendataloader-pdf")
    assert fg.parse_eslint("[]", "/w") == {}
    unix = "The unix formatter is no longer part of core ESLint. Install it manually with `npm install -D eslint-formatter-unix`\n"
    assert fg.parse_eslint(unix, "/w") is None and fg.parse_eslint('{"x": 1}', "/w") is None
    found ={"base": {"ruff": {"ruff a.py:F401": 1}, "checkstyle": {"A.java: NeedBraces": 2}},
             "fix": {"ruff": {"ruff a.py:F401": 1, "ruff a.py:E711": 1}, "checkstyle": {"A.java: NeedBraces": 2}}}
    assert fg.new_findings(found) == {"ruff a.py:E711": 1}, fg.new_findings(found)
    py = [{"status": "M", "path": "python/opendataloader-pdf/src/opendataloader_pdf/x.py"}]
    gen = [{"status": "M", "path": "python/opendataloader-pdf/src/opendataloader_pdf/cli_options_generated.py"}]
    assert "static" in fg.required_gates(contract(), py) and "static" not in fg.required_gates(contract(), gen)
    return "ruff lines and ESLint JSON parsed (no JSON -> None); only a grown count fails; generated files skipped"


def g18_wrapper_runners(tmp):
    """A fix in the Python or Node wrapper has pytest or vitest cases; their JUnit XML matches the case, and a bad id is refused."""
    py = {"id": "P1", "kind": "problem", "runner": "pytest", "test": "tests/test_hybrid_server.py::TestTimeout::test_limit",
          "symptom": "timeout", "extras": ["hybrid"]}
    vt = {"id": "G1", "kind": "guard", "runner": "vitest", "test": "test/convert.test.ts::keeps the page order"}
    assert fg.validate_contract(contract(cases=[py, vt]), tmp) == []
    for bad in ({**py, "test": "tests/test_x.py"}, {**py, "test": "/abs/test_x.py::test_a"}, {**vt, "test": "test/x.ts::name"},
                {**py, "runner": "jest"}, {**py, "extras": ["hybrid; rm -rf /"]}):
        assert fg.validate_contract(contract(cases=[bad]), tmp), bad
    tcs = [{"classname": "tests.test_hybrid_server.TestTimeout", "name": "test_limit[1]", "status": "pass", "message": ""},
           {"classname": "tests.test_hybrid_server.TestTimeout", "name": "test_limit[2]", "status": "fail", "message": "timeout after 0s"},
           {"classname": "test/convert.test.ts", "name": "convert > keeps the page order", "status": "pass", "message": ""}]
    assert fg.match_pytest(py, tcs)["status"] == "fail"
    assert fg.match_vitest(vt, tcs)["status"] == "pass"
    assert fg.match_pytest({**py, "test": "tests/test_hybrid_server.py::test_other"}, tcs)["status"] == "missing"
    return "pytest and vitest ids validated and matched (worst parameterized run counts)"


def g19_pinning_needs_junit(tmp):
    """Java main code that only wrapper tests cover is not pinned: the pinning gate reverts Java files and needs a JUnit case."""
    ctx, src, work = setup_repo(tmp)
    c = contract(cases=[{"id": "P1", "kind": "problem", "runner": "pytest", "test": "tests/test_x.py::test_a", "symptom": "x"}])
    res = fg.gate_pinning(ctx, c)
    assert res["state"] == "fail" and "no JUnit case" in res["summary"]["note"], res
    return "Java change with only a pytest case -> pinning fails without a Maven run"


def g20_formats_only_grow(tmp):
    """After the freeze, an invariant cannot compare fewer output kinds: that weakens it (CodeRabbit on #849)."""
    frozen = contract(invariants=[{"id": "I1", "corpus": "samples/pdf"}])
    weaker = contract(invariants=[{"id": "I1", "corpus": "samples/pdf", "formats": ["json"]}],
                      amendments=[{"n": 1, "what": "compare JSON only", "why": "faster"}])
    errors = fg.contract_changes(frozen, weaker)[1]
    assert any("formats" in e and "markdown" in e for e in errors), errors
    more = contract(invariants=[{"id": "I1", "corpus": "samples/pdf", "formats": list(fg.KINDS)}],
                    amendments=[{"n": 1, "what": "list the formats", "why": "explicit"}])
    assert not fg.contract_changes(frozen, more)[1], fg.contract_changes(frozen, more)
    return "a removed format -> error, also with an amendment"


def g21_review_body_findings(tmp):
    """A critical or major CodeRabbit finding outside the diff is in the review body, not in an inline thread (CodeRabbit on #849)."""
    green = [{"name": "test", "bucket": "pass"}]
    body = ("**Actionable comments posted: 1**\n<details><summary>Outside diff range comments (1)</summary>"
            "<details><summary>A.java (1)</summary> `40-44`: **Major** · The loop has no limit.</details></details>\n"
            "<details><summary>Review details</summary> Minor things</details>")
    rv = {"id": 9, "user": {"login": "coderabbitai[bot]", "type": "Bot"}, "submitted_at": "2026-10-29T10:00:00Z", "body": body}
    assert fg.judge_pr(green, [], [rv], [])["state"] == "fail"
    bot_later = {"user": {"login": "github-actions[bot]", "type": "Bot"}, "created_at": "2026-10-29T11:00:00Z", "body": "x"}
    assert fg.judge_pr(green, [], [rv], [bot_later])["state"] == "fail"
    person_before = {"user": {"login": "maintainer", "type": "User"}, "created_at": "2026-10-29T09:00:00Z", "body": "x"}
    assert fg.judge_pr(green, [], [rv], [person_before])["state"] == "fail"
    person_later = {"user": {"login": "maintainer", "type": "User"}, "created_at": "2026-10-29T11:00:00Z", "body": "Fixed in abc."}
    assert fg.judge_pr(green, [], [rv], [person_later])["state"] == "pass"
    summary_only = {**rv, "body": "**Actionable comments posted: 0**\n<details><summary>Review details</summary> Major refactor? no</details>"}
    assert fg.judge_pr(green, [], [summary_only], [])["state"] == "pass"
    for command in ("@coderabbitai review", "@CodeRabbitAI full review", "/review", "  "):
        later = {**person_later, "body": command}
        assert fg.judge_pr(green, [], [rv], [later])["state"] == "fail", command
    empty_review = {"user": {"login": "maintainer", "type": "User"}, "submitted_at": "2026-10-29T11:00:00Z", "body": ""}
    assert fg.judge_pr(green, [], [rv, empty_review], [])["state"] == "fail", "GitHub makes an empty review for each inline reply"
    addressed = {**person_later, "body": "@coderabbitai The loop has a limit of 32 levels: see line 41."}
    assert fg.judge_pr(green, [], [rv], [addressed])["state"] == "pass"
    major = {"id": 1, "user": {"login": "coderabbitai[bot]"}, "body": "**Major** · no limit"}
    assert fg.judge_pr(green, [major, {"id": 2, "in_reply_to_id": 1, "user": {"login": "maintainer"}, "body": "@coderabbitai review"}])["state"] == "fail"
    return "outside-diff major -> open until a later answer by a person; a bot, an earlier comment, a command, or an empty review does not count"


def g22_pinning_control(tmp):
    """Pinning copies the whole tree and needs a passing control run: a case that fails without a revert proves nothing (CodeRabbit on #849)."""
    ctx, src, work = setup_repo(tmp)
    c = fg.load_contract(ctx)
    seen = []

    def fake(control_fails, revert_fails):
        def run_cases(ctx_, tree, cases, out_dir):
            assert os.path.exists(os.path.join(tree, "samples", "pdf", "sample.pdf")), "the copy misses files outside java/"
            a_java = open(os.path.join(tree, "java/opendataloader-pdf-core/src/main/java/org/x/A.java")).read()
            seen.append((os.path.basename(out_dir), a_java))
            os.makedirs(os.path.join(out_dir, "reports-opendataloader-pdf-core"), exist_ok=True)
            fails = control_fails if out_dir.endswith("control") else revert_fails
            return ({k["id"]: {"status": "fail" if k["id"] in fails else "pass", "message": ""} for k in cases},
                    {"opendataloader-pdf-core": 0})
        return run_cases

    real = fg.run_cases
    try:
        fg.run_cases = fake({"P1"}, {"P1"})
        res = fg.gate_pinning(ctx, c)
        assert res["state"] == "fail" and res["summary"]["control_failed"] == ["P1"], res
        fg.run_cases = fake(set(), set())
        assert fg.gate_pinning(ctx, c)["state"] == "fail"
        seen.clear()
        fg.run_cases = fake(set(), {"P1"})
        res = fg.gate_pinning(ctx, c)
        assert res["state"] == "pass" and res["summary"]["files"][0]["pinned_by"] == ["P1"], res
        assert seen == [("control", "class A { int fixed; }\n"), ("00", "class A {}\n")], seen
    finally:
        fg.run_cases = real
    assert fg.pinned_by({}, False) == ["compile"]
    return "failing control -> refused; no failure -> unpinned; revert run sees the base file in a full copy"


def g23_module_keys(tmp):
    """A JUnit case reads only the reports of its own Maven module: two modules can have the same test names (CodeRabbit on #849)."""
    core, cli = os.path.join(tmp, "core"), os.path.join(tmp, "cli")
    write(core, "TEST-org.example.SharedTest.xml", report_xml([("SharedTest", "sameName", "fail", "lost the header")]))
    write(cli, "TEST-org.example.SharedTest.xml", report_xml([("SharedTest", "sameName", "pass", "")]))
    cases = [case("P1", test="SharedTest#sameName"), {**case("G1", "guard", "SharedTest#sameName"), "module": "opendataloader-pdf-cli"}]
    res = fg.junit_results(cases, {"opendataloader-pdf-core": core, "opendataloader-pdf-cli": cli})
    assert (res["P1"]["status"], res["G1"]["status"]) == ("fail", "pass"), res
    assert fg.junit_results(cases, {"opendataloader-pdf-core": None, "opendataloader-pdf-cli": cli})["P1"]["status"] == "missing"
    return "same class and method in core and cli -> each case keeps its own result"


def g24_reporter_only_grows(tmp):
    """After the freeze, a reporter run cannot be removed or changed; a new run can be added (CodeRabbit on #849)."""
    run1 = {"id": "R1", "options": ["--pages", "1"], "format": "json", "fix": {"types": {"header": ">0"}}}
    frozen = contract(reporter={"document": "attachments/a1.pdf", "runs": [run1]})
    amend = [{"n": 1, "what": "change the reporter runs", "why": "x"}]
    removed = contract(amendments=amend)
    assert any("reporter runs were removed" in e for e in fg.contract_changes(frozen, removed)[1])
    weaker = contract(reporter={"document": "attachments/a1.pdf", "runs": [{**run1, "fix": {}}]}, amendments=amend)
    assert any("R1 changed" in e for e in fg.contract_changes(frozen, weaker)[1])
    other_doc = contract(reporter={"document": "attachments/a2.pdf", "runs": [run1]}, amendments=amend)
    assert any("document changed" in e for e in fg.contract_changes(frozen, other_doc)[1])
    grown = contract(reporter={"document": "attachments/a1.pdf", "runs": [run1, {**run1, "id": "R2"}]}, amendments=amend)
    changes, errors = fg.contract_changes(frozen, grown)
    assert not errors and "new reporter run R2" in changes, (changes, errors)
    return "removed, weakened, or other document -> error; a new run -> listed"


def g25_reporter_document_content(tmp):
    """The contract names the content of the reporter's document, not only its path (CodeRabbit on #849)."""
    rep = {"document": "attachments/a1.pdf", "runs": [{"id": "R1", "options": [], "format": "json", "fix": {"types": {"paragraph": ">0"}}}]}
    ctx, src, work = setup_repo(tmp, {"reporter": rep})
    chk = fg.compute_check(ctx)
    assert chk["ok"] and "reporter" in [r["gate"] for r in chk["gates"]], chk
    write(work, "attachments/a1.pdf", "%PDF-1.7 another document under the same name\n")
    chk = fg.compute_check(ctx)
    why = {r["gate"]: r["why"] for r in chk["gates"]}
    assert not chk["ok"] and why["reporter"] == "for another reporter document", why
    assert any("document changed after the freeze" in e for e in chk["errors"]), chk["errors"]
    return "same path, other content -> the reporter record is stale and check names the change"


A_JAVA = "java/opendataloader-pdf-core/src/main/java/org/x/A.java"
MUTATION_PATCH = (f"diff --git a/{A_JAVA} b/{A_JAVA}\n--- a/{A_JAVA}\n+++ b/{A_JAVA}\n"
                  "@@ -1 +1 @@\n-class A {}\n+class A { int defect; }\n")


def mutation(cid="M1", **over):
    k = {"id": cid, "kind": "mutation", "test": "Issue1Test#findsTheDefect", "patch": "mutations/m1.patch",
         "misses": ["ATest"], "why": "A reads the default, not the configured value"}
    k.update(over)
    return k


def g26_mutation_contract(tmp):
    """An issue that asks only for tests has no problem case: a mutation case, a defect patch of main code, stands for it (#854)."""
    write(tmp, "mutations/m1.patch", MUTATION_PATCH)
    tests_only = contract(scope=[], cases=[mutation(), case("G1", "guard", "Issue1Test#defaultStays")])
    assert fg.validate_contract(tests_only, tmp) == [], fg.validate_contract(tests_only, tmp)
    mixed = contract(cases=[case("P1"), mutation(),
                            mutation("M2", runner="vitest", test="test/a.test.ts::finds it", misses=["test/a.test.ts"]),
                            mutation("M3", runner="pytest", test="tests/test_a.py::test_finds_it", misses=["tests/test_a.py::test_old"])])
    assert fg.validate_contract(mixed, tmp) == [], fg.validate_contract(mixed, tmp)
    assert fg.patch_paths(MUTATION_PATCH) == ([A_JAVA], [])
    write(tmp, "mutations/test.patch", MUTATION_PATCH.replace("src/main/java/org/x/A.java", "src/test/java/org/x/ATest.java"))
    write(tmp, "mutations/new.patch", "diff --git a/x/B.java b/x/B.java\nnew file mode 100644\n--- /dev/null\n+++ b/x/B.java\n"
          "@@ -0,0 +1 @@\n+class B {}\n")
    write(tmp, "mutations/empty.patch", "")
    with_p1 = lambda **over: contract(cases=[case("P1"), mutation(**over)])  # noqa: E731
    bad = {"only guards": contract(scope=[], cases=[case("G1", "guard")]),
           "a problem case without scope": contract(scope=[], cases=[case("P1"), mutation()]),
           "no patch file": with_p1(patch="mutations/none.patch"), "a patch outside $WORK": with_p1(patch="../m1.patch"),
           "a patch of a test": with_p1(patch="mutations/test.patch"), "a patch that makes a file": with_p1(patch="mutations/new.patch"),
           "an empty patch": with_p1(patch="mutations/empty.patch"), "no misses": with_p1(misses=[]),
           "misses is not a test": with_p1(misses=["ATest#x#y"]),
           "vitest misses with a test name": with_p1(runner="vitest", test="test/a.test.ts::x", misses=["test/a.test.ts::x"]),
           "no why": with_p1(why="")}
    for name, c in bad.items():
        assert fg.validate_contract(c, tmp), name
    return f"tests-only and mixed contracts ok; {len(bad)} bad contracts refused"


def g27_mutation_verdicts(tmp):
    """A mutation case counts only if its test passes on the base, the tests in misses pass with the patch, and its test fails."""
    base_pass = {"M1": {"status": "pass", "message": ""}}
    good = {"applied": True, "misses": {"passed": 12, "failed": [], "exit": 0}, "detect": {"status": "fail", "message": "expected 10"}}

    def judge(results, run):
        j = fg.judge_problem([mutation()], results, None if run is None else {"M1": run})
        return j["ok"], j["cases"][0]["verdict"]

    assert judge(base_pass, good) == (True, "detected; the base tests miss it (12 tests)"), judge(base_pass, good)
    bad = {"the test fails on the base": ({"M1": {"status": "fail", "message": "x"}}, good),
           "the patch does not apply": (base_pass, {"applied": False}), "no mutation run": (base_pass, None),
           "the base tests find it": (base_pass, {**good, "misses": {"passed": 11, "failed": ["ATest#x"], "exit": 1}}),
           "no base test ran": (base_pass, {**good, "misses": {"passed": 0, "failed": [], "exit": 1}}),
           "not detected": (base_pass, {**good, "detect": {"status": "pass", "message": ""}}),
           "no result with the patch": (base_pass, {**good, "detect": {"status": "missing", "message": ""}})}
    for name, (results, run) in bad.items():
        ok, verdict = judge(results, run)
        assert not ok, (name, verdict)
    return f"detected and missed -> ok; {len(bad)} other states -> the problem gate fails"


def g28_mutation_patch_frozen(tmp):
    """The problem record holds the content of each patch; patch or misses cannot change after the freeze (#854)."""
    m = mutation(test="Issue1Test#reporterCase")
    ctx, src, work = setup_repo(tmp, {"cases": [case("P1"), m, case("G1", "guard", "Issue1Test#defaultStays")]},
                                work_files={"mutations/m1.patch": MUTATION_PATCH})
    chk = fg.compute_check(ctx)
    assert chk["ok"], chk
    write(work, "mutations/m1.patch", MUTATION_PATCH.replace("int defect", "int other"))
    why = {r["gate"]: r["why"] for r in fg.compute_check(ctx)["gates"]}
    assert why["problem"] == "a mutation patch changed after the problem run", why
    frozen = contract(cases=[case("P1"), m])
    for change in ({"misses": ["ATest", "BTest"]}, {"patch": "mutations/m2.patch"}):
        grown = contract(cases=[case("P1"), {**m, **change}], amendments=[{"n": 1, "what": "x", "why": "y"}])
        assert any("M1 changed" in e for e in fg.contract_changes(frozen, grown)[1]), change
    return "other patch content -> the problem record is stale; changed misses or patch path -> error"


EVALS = [("G1-problem-reproduces", g1_problem_must_reproduce), ("G2-guard-on-base", g2_guard_holds_on_base),
         ("G3-reports", g3_reports), ("G4-fix-every-case", g4_fix_needs_every_case), ("G5-scope", g5_scope),
         ("G6-pinning", g6_pinning), ("G7-invariants", g7_invariants), ("G8-pdf-id", g8_pdf_document_id),
         ("G9-contract-rules", g9_contract_rules), ("G10-contract-grows", g10_contract_only_grows),
         ("G11-required-gates", g11_required_gates), ("G12-freshness", g12_check_freshness),
         ("G13-freeze-selfcheck", g13_check_needs_freeze_and_selfcheck), ("G14-report-paths", g14_report_has_no_local_paths),
         ("G15-pull-request", g15_pull_request), ("G16-reporter", g16_reporter_expectations), ("G17-linters", g17_linters),
         ("G18-wrapper-runners", g18_wrapper_runners), ("G19-pinning-junit", g19_pinning_needs_junit),
         ("G20-formats-grow", g20_formats_only_grow), ("G21-review-body", g21_review_body_findings),
         ("G22-pinning-control", g22_pinning_control), ("G23-module-keys", g23_module_keys),
         ("G24-reporter-grows", g24_reporter_only_grows), ("G25-reporter-document", g25_reporter_document_content),
         ("G26-mutation-contract", g26_mutation_contract), ("G27-mutation-verdicts", g27_mutation_verdicts),
         ("G28-mutation-frozen", g28_mutation_patch_frozen)]
