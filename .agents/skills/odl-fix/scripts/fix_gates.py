#!/usr/bin/env python3
"""The harness of odl-fix: a contract defines the problem and the fix as tests; code computes "done".

  fix_gates.py template --issue N
      Prints a contract skeleton for $WORK/contract.json.
  fix_gates.py validate --work WORK
      Checks the contract. Exit 2: the contract is not valid.
  fix_gates.py gate NAME --work WORK [--src DIR] [--offline]
      Runs one gate and appends its record to WORK/records.jsonl. Exit 13: the gate failed.
      problem     on the base tree: each problem case fails with its symptom, each guard passes
      base-build  builds WORK/base.jar from the base tree
      build       ./scripts/build-java.sh on the fix tree; copies WORK/fix.jar
      fix         on the fix tree: each case passes
      pinning     each changed Java main file, reverted alone, makes a case fail
      scope       each changed file that is not a test is in the contract scope
      static      checkstyle and spotbugs: no new finding in the changed Java files
      wrappers    build-python.sh, build-node.sh, and verification/ci-verify.py
      issue-corpus  the PDFs that the Issue*Test classes of the base tree make: WORK/corpus/issue-samples
      invariants  side_effects.sh for each invariant; each difference must be normalized or allowed
      reporter    the reporter's runs on the original document, with base.jar and fix.jar
      bench       scripts/bench.sh for both jars, then bench.py compare
  fix_gates.py run --work WORK [--gates A,B] [--offline]
      Runs the required gates of the change (not problem, not pr-watch), in order.
  fix_gates.py freeze --work WORK
      After Approval 1: keeps the approved contract. Needs a passing problem record of this contract.
  fix_gates.py check --work WORK [--final]
      Computes the result from the records: each required gate passed for the current commits,
      jars, and contract; the contract only grew after the freeze, with an amendment for each change;
      the self-check is complete. --final also needs a passing pr-watch record. Exit 13: not done.
  fix_gates.py report --work WORK
      Writes WORK/evidence.md (for the pull request) from the records. No local paths.
  fix_gates.py pr-watch --work WORK --pr N [--repo OWNER/REPO]
      Network (gh), read-only: the CI checks and the CodeRabbit comments of the pull request.
      Fails while a check fails or a critical or major CodeRabbit comment has no reply.

The agent judges what the contract says. This tool judges whether the code does what it says.
"""

from __future__ import annotations

import argparse
import datetime
import fnmatch
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REVIEW_SCRIPTS = os.path.normpath(os.path.join(HERE, "..", "..", "odl-review", "scripts"))
MODULES = ("opendataloader-pdf-core", "opendataloader-pdf-cli")
PY_PKG = "python/opendataloader-pdf"
NODE_PKG = "node/opendataloader-pdf"
RUNNERS = ("junit", "pytest", "vitest")
NORMALIZERS = ("json:id", "json:round-2", "text:blank-lines", "tagged-pdf:document-id")
KINDS = ("json", "markdown", "html", "text", "tagged-pdf", "images")
FORMAT_EXT = {"json": ".json", "markdown": ".md", "html": ".html", "text": ".txt"}
TEST_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*#[A-Za-z_][A-Za-z0-9_]*$")
PYTEST_RE = re.compile(r"^tests/[A-Za-z0-9_./-]+\.py(::[A-Za-z_][A-Za-z0-9_]*)+$")
VITEST_RE = re.compile(r"^test/[A-Za-z0-9_./-]+\.test\.(ts|mts|js)::.+$")
COUNT_RE = re.compile(r"^(=|>|>=|<|<=)?([0-9]+)$")
HYBRID_URL_RE = re.compile(r"^http://127\.0\.0\.1:[0-9]{1,5}$")
SELFCHECK = (
    "PDF input: loops and recursion have a limit; skip a bad object; no allocation sized from a document value",
    "Paths: no document or backend text in a file path",
    "Secrets: no --password value or argument list in logs, messages, exceptions",
    "Processes: wrappers start java with an argument list, not a shell",
    "Network (hybrid): reads have a limit and a timeout; do not log backend bodies with document text",
    "Output: HTML via escapeHtmlText/escapeHtmlAttribute; Markdown via getCorrectMarkdownString; links via formatMarkdownLinkDestination",
    "Content safety: hidden, off-page, tiny, hidden-layer text stays out of default output",
    "Temp files: Files.createTemp*, deleted in finally; tests use @TempDir or TestSamples",
    "Logging: no extracted text, alt text, password, backend body at INFO or higher",
    "Static state: reset between documents and copied to parallel workers (StaticLayoutContainers, SerializerUtil)",
    "Dependencies: exact versions; license compatible with Apache-2.0",
    "CI: no pull_request_target, no secrets: inherit, minimum permissions",
)
DEFAULT_INVARIANTS = (
    {"id": "native-samples", "corpus": "samples/pdf", "options": [], "formats": list(KINDS), "ignore": [], "allow": []},
    {"id": "native-issue-samples", "corpus": "@issue-samples", "options": [], "formats": list(KINDS), "ignore": [], "allow": []},
)
ORDER = ("base-build", "build", "fix", "scope", "pinning", "static", "wrappers", "issue-corpus", "invariants", "reporter", "bench")


class Refused(Exception):
    """A usage, contract, or environment problem (exit 2 or 3), not a failed gate."""

    def __init__(self, message: str, code: int = 2):
        super().__init__(message)
        self.code = code


# ---------------------------------------------------------------- common


def now() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sha256_file(path: str) -> str | None:
    if not os.path.isfile(path):
        return None
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def canonical(obj) -> str:
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def run_cmd(args: list[str], cwd: str | None = None, log: str | None = None, env: dict | None = None, stdin=None) -> int:
    """Runs a command with an argument list (no shell). With log, stdout and stderr go to the file."""
    full_env = {**os.environ, **(env or {})}
    if log:
        os.makedirs(os.path.dirname(log), exist_ok=True)
        with open(log, "ab") as f:
            f.write(f"$ {' '.join(args)}\n".encode())
            f.flush()
            return subprocess.run(args, cwd=cwd, stdout=f, stderr=subprocess.STDOUT, env=full_env, stdin=stdin).returncode
    return subprocess.run(args, cwd=cwd, env=full_env, stdin=stdin).returncode


def git(tree: str, *args: str) -> str:
    res = subprocess.run(["git", "-C", tree, *args], capture_output=True, text=True)
    if res.returncode != 0:
        raise Refused(f"git {' '.join(args[:2])} in {tree}: {res.stderr.strip()[:300]}", 3)
    return res.stdout


def java_env() -> dict:
    """JAVA_TOOL_OPTIONS keeps its value (for example -Djava.net.preferIPv4Stack=true) and gets headless and tmpdir."""
    extra = "-Djava.awt.headless=true"
    if os.environ.get("TMPDIR"):
        extra += f" -Djava.io.tmpdir={os.environ['TMPDIR']}"
    old = os.environ.get("JAVA_TOOL_OPTIONS", "")
    return {"JAVA_TOOL_OPTIONS": (old + " " + extra).strip()}


def read_text(path: str) -> str:
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


# ---------------------------------------------------------------- context


class Ctx:
    def __init__(self, work: str, src: str | None = None, base_src: str | None = None, offline: bool = False):
        self.work = os.path.realpath(work)
        if not os.path.isdir(self.work):
            raise Refused(f"no work folder: {work}", 3)
        env = load_env(self.work)
        self.src = os.path.realpath(src or env.get("SRC") or git(os.getcwd(), "rev-parse", "--show-toplevel").strip())
        self.base_src = os.path.realpath(base_src or os.path.join(self.work, "base-src"))
        self.offline = offline
        self.records_path = os.path.join(self.work, "records.jsonl")

    def head(self) -> str:
        return git(self.src, "rev-parse", "HEAD").strip()

    def tree(self, commit: str = "HEAD") -> str:
        """The content of a commit. A squash keeps it; a rebase or a code change does not."""
        return git(self.src, "rev-parse", f"{commit}^{{tree}}").strip()

    def base(self) -> str:
        if not os.path.isdir(self.base_src):
            raise Refused(f"no base tree: {self.base_src} (git worktree add --detach $WORK/base-src $UPSTREAM/main)", 3)
        return git(self.base_src, "rev-parse", "HEAD").strip()

    def jar(self, name: str) -> str:
        return os.path.join(self.work, f"{name}.jar")

    def gate_dir(self, name: str) -> str:
        d = os.path.join(self.work, "gates", name)
        os.makedirs(d, exist_ok=True)
        return d

    def mvn(self) -> list[str]:
        return ["mvn", "-B"] + (["-o"] if self.offline else [])


def load_env(work: str) -> dict:
    env, path = {}, os.path.join(work, "env.sh")
    if os.path.exists(path):
        for line in read_text(path).splitlines():
            m = re.fullmatch(r"([A-Z_]+)='([^']*)'", line.strip())
            if m:
                env[m.group(1)] = m.group(2)
    return env


def require_clean(tree: str, what: str):
    if git(tree, "status", "--porcelain").strip():
        raise Refused(f"the {what} has uncommitted changes: commit them first (the gates compare commits)", 3)


def changed_files(ctx: Ctx) -> list[dict]:
    """The files that the branch changes against the base: [{"status", "path", "old"}]."""
    base = ctx.base()
    if subprocess.run(["git", "-C", ctx.src, "merge-base", "--is-ancestor", base, "HEAD"]).returncode != 0:
        raise Refused(f"the branch does not contain the base {base[:8]}: rebase, or move the base tree", 3)
    out = []
    for line in git(ctx.src, "diff", "--name-status", "-M", f"{base}...HEAD").splitlines():
        parts = line.split("\t")
        if parts[0].startswith("R"):
            out.append({"status": "R", "path": parts[2], "old": parts[1]})
        else:
            out.append({"status": parts[0][0], "path": parts[1], "old": None})
    return out


def is_test_path(p: str) -> bool:
    return "/src/test/" in p or re.search(r"(^|/)(tests?|__tests__)/", p) is not None


def is_java_main(p: str) -> bool:
    return re.match(r"java/[^/]+/src/main/java/.+\.java$", p) is not None


# ---------------------------------------------------------------- contract


def contract_path(ctx: Ctx) -> str:
    return os.path.join(ctx.work, "contract.json")


def load_contract(ctx: Ctx) -> dict:
    path = contract_path(ctx)
    if not os.path.exists(path):
        raise Refused(f"no contract: {path} (fix_gates.py template --issue N)")
    try:
        with open(path, encoding="utf-8") as f:
            c = json.load(f)
    except json.JSONDecodeError as e:
        raise Refused(f"contract.json: {e}")
    errors = validate_contract(c, ctx.work)
    if errors:
        raise Refused("contract.json is not valid:\n  " + "\n  ".join(errors))
    return c


def contract_hash(c: dict) -> str:
    return hashlib.sha256(canonical(c).encode()).hexdigest()


def check_options(options, where: str, errors: list):
    if not isinstance(options, list) or not all(isinstance(o, str) for o in options):
        errors.append(f"{where}: options must be a list of strings")
        return
    for i, o in enumerate(options):
        if o in ("-o", "--output-dir", "-f", "--format"):
            errors.append(f"{where}: {o} is set by the harness")
        if o == "--hybrid-url" and (i + 1 >= len(options) or not HYBRID_URL_RE.fullmatch(options[i + 1])):
            errors.append(f"{where}: --hybrid-url must be http://127.0.0.1:<port>")
        if o.startswith("--hybrid-url="):
            errors.append(f"{where}: write --hybrid-url and its value as two items")


def check_expect(e, where: str, errors: list):
    if not isinstance(e, dict) or set(e) - {"types", "contains", "absent"}:
        errors.append(f"{where}: use types, contains, absent")
        return
    for k, v in (e.get("types") or {}).items():
        if not isinstance(v, str) or not COUNT_RE.fullmatch(v):
            errors.append(f"{where}: types.{k} {v!r}: use =N, >N, >=N, <N, <=N")
    for k in ("contains", "absent"):
        if not isinstance(e.get(k, []), list):
            errors.append(f"{where}: {k} must be a list")


def validate_contract(c, work: str) -> list[str]:
    errors = []
    if not isinstance(c, dict):
        return ["the contract must be an object"]
    if not isinstance(c.get("issue"), int):
        errors.append("issue: the issue number")
    if not (c.get("objective") or "").strip():
        errors.append("objective: the reporter's pain point, quoted")
    scope = c.get("scope")
    if not isinstance(scope, list) or not scope or not all(isinstance(s, str) and s for s in scope):
        errors.append("scope: the files (or globs) that the fix can change, other than tests")
    cases = c.get("cases")
    if not isinstance(cases, list) or not cases:
        errors.append("cases: at least one problem case")
        cases = []
    ids = set()
    for k in cases:
        cid = k.get("id") if isinstance(k, dict) else None
        if not cid or cid in ids:
            errors.append(f"case {cid!r}: a unique id")
            continue
        ids.add(cid)
        if k.get("kind") not in ("problem", "guard"):
            errors.append(f"case {cid}: kind is problem or guard")
        runner = k.get("runner", "junit")
        pattern = {"junit": TEST_RE, "pytest": PYTEST_RE, "vitest": VITEST_RE}.get(runner)
        if pattern is None:
            errors.append(f"case {cid}: runner is one of {', '.join(RUNNERS)}")
        elif not pattern.fullmatch(k.get("test") or ""):
            errors.append(f"case {cid}: test is " + {"junit": "Class#method", "pytest": "tests/<file>.py::[Class::]test",
                                                    "vitest": "test/<file>.test.ts::<test name>"}[runner])
        if k.get("module", MODULES[0]) not in MODULES:
            errors.append(f"case {cid}: module is one of {', '.join(MODULES)}")
        if not all(isinstance(e, str) and re.fullmatch(r"[a-z0-9-]+", e) for e in k.get("extras", [])):
            errors.append(f"case {cid}: extras are names of Python extras")
        if k.get("kind") == "problem" and not (k.get("symptom") or "").strip():
            errors.append(f"case {cid}: a problem case needs the symptom text of its failure message")
    if cases and not any(k.get("kind") == "problem" for k in cases if isinstance(k, dict)):
        errors.append("cases: at least one problem case")
    for s in c.get("support", []):
        if not isinstance(s, str) or s.startswith("/") or ".." in s.split("/"):
            errors.append(f"support {s!r}: a path in the repository")
    rep = c.get("reporter")
    if rep is not None:
        doc = os.path.realpath(os.path.join(work, rep.get("document") or ""))
        if not rep.get("document") or not doc.startswith(os.path.realpath(work) + os.sep):
            errors.append("reporter.document: a file in $WORK")
        elif not os.path.isfile(doc):
            errors.append(f"reporter.document: no file {rep.get('document')}")
        rids = set()
        for r in rep.get("runs") or []:
            rid = r.get("id")
            if not rid or rid in rids:
                errors.append(f"reporter run {rid!r}: a unique id")
                continue
            rids.add(rid)
            check_options(r.get("options"), f"reporter run {rid}", errors)
            if "fix_options" in r:
                check_options(r["fix_options"], f"reporter run {rid}.fix_options", errors)
            if r.get("format") not in FORMAT_EXT:
                errors.append(f"reporter run {rid}: format is {', '.join(FORMAT_EXT)}")
            for side in ("base", "fix"):
                if side in r:
                    check_expect(r[side], f"reporter run {rid}.{side}", errors)
            for n in r.get("fix_equals_base", []):
                if n not in NORMALIZERS:
                    errors.append(f"reporter run {rid}: unknown normalizer {n}")
        if not rids:
            errors.append("reporter.runs: at least one run")
    amendments = c.get("amendments", [])
    numbers = [a.get("n") for a in amendments if isinstance(a, dict)]
    if numbers != list(range(1, len(amendments) + 1)) or not all((a.get("what") or "").strip() and (a.get("why") or "").strip() for a in amendments):
        errors.append("amendments: n = 1, 2, ... in order, each with what and why")
    iids = set()
    for inv in c.get("invariants", []):
        iid = inv.get("id")
        if not iid or iid in iids:
            errors.append(f"invariant {iid!r}: a unique id")
            continue
        iids.add(iid)
        corpus = inv.get("corpus") or ""
        if not corpus or corpus.startswith("/") or ".." in corpus.split("/"):
            errors.append(f"invariant {iid}: corpus is a folder of the base tree or @issue-samples")
        check_options(inv.get("options", []), f"invariant {iid}", errors)
        for f in inv.get("formats", KINDS):
            if f not in KINDS:
                errors.append(f"invariant {iid}: unknown format {f}")
        for n in inv.get("ignore", []):
            if n not in NORMALIZERS:
                errors.append(f"invariant {iid}: unknown normalizer {n}")
        for a in inv.get("allow", []):
            if not (a.get("file") and (a.get("reason") or "").strip() and a.get("amendment") in numbers):
                errors.append(f"invariant {iid}: each allow has file, reason, and the n of its amendment")
    return errors


def effective_invariants(c: dict, changes: list[dict]) -> list[dict]:
    """The contract's invariants, and the native corpus invariants when Java main code changes."""
    out = [{"options": [], "formats": list(KINDS), "ignore": [], "allow": [], **inv} for inv in c.get("invariants", [])]
    if any(is_java_main(ch["path"]) for ch in changes):
        have = {inv["id"] for inv in out}
        out += [dict(d) for d in DEFAULT_INVARIANTS if d["id"] not in have]
    return out


def template(issue: int) -> dict:
    return {
        "issue": issue,
        "objective": "\"<the reporter's pain point, quoted>\"",
        "scope": ["java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/<package>/<Class>.java"],
        "cases": [
            {"id": "P1", "kind": "problem", "test": f"Issue{issue}DescriptionTest#reporterExpectation",
             "symptom": "<a part of the failure message that names the reporter's symptom>"},
            {"id": "G1", "kind": "guard", "test": f"Issue{issue}DescriptionTest#behaviourThatMustStay",
             "why": "<the variant: same cause, same code path; or an output that must not change>"},
        ],
        "invariants": [],
        "amendments": [],
    }


# ---------------------------------------------------------------- records


def append_record(ctx: Ctx, rec: dict) -> dict:
    rec = {"at": now(), **rec}
    with open(ctx.records_path, "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    return rec


def read_records(ctx: Ctx) -> list[dict]:
    if not os.path.exists(ctx.records_path):
        return []
    with open(ctx.records_path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def latest(records: list[dict], gate: str) -> dict | None:
    found = [r for r in records if r.get("gate") == gate]
    return found[-1] if found else None


# ---------------------------------------------------------------- tests


def runner_of(case: dict) -> str:
    return case.get("runner", "junit")


def case_key(case: dict) -> tuple[str, str]:
    cls, method = case["test"].split("#", 1)
    return cls, method


def find_test_file(tree: str, case: dict) -> str:
    runner = runner_of(case)
    if runner in ("pytest", "vitest"):
        rel = f"{PY_PKG if runner == 'pytest' else NODE_PKG}/{case['test'].split('::', 1)[0]}"
        if not os.path.isfile(os.path.join(tree, rel)):
            raise Refused(f"case {case['id']}: no file {rel}", 2)
        return rel
    cls, _ = case_key(case)
    module = case.get("module", MODULES[0])
    hits = glob.glob(os.path.join(tree, "java", module, "src", "test", "java", "**", f"{cls}.java"), recursive=True)
    if len(hits) != 1:
        raise Refused(f"case {case['id']}: {len(hits)} files named {cls}.java in {module}", 2)
    return os.path.relpath(hits[0], tree)


RANK = {"pass": 0, "skipped": 1, "fail": 2, "error": 3}


def read_testcases(paths: list[str]) -> list[dict]:
    """The test cases of JUnit XML reports (surefire, pytest --junitxml, vitest --reporter=junit)."""
    out = []
    for path in paths:
        data = open(path, "rb").read()
        if b"<!DOCTYPE" in data[:2000]:
            continue
        try:
            root = ET.fromstring(data)
        except ET.ParseError:
            continue
        for tc in root.iter("testcase"):
            status, message = "pass", ""
            for tag, st in (("failure", "fail"), ("error", "error"), ("skipped", "skipped")):
                el = tc.find(tag)
                if el is not None:
                    status = st
                    message = ((el.get("message") or "") + "\n" + (el.text or ""))[:4000]
                    break
            out.append({"classname": tc.get("classname") or "", "name": tc.get("name") or "", "status": status, "message": message})
    return out


def worst(hits: list[dict]) -> dict:
    """A parameterized test is one case: its worst run counts. No run is "missing"."""
    if not hits:
        return {"status": "missing", "message": ""}
    best = max(hits, key=lambda h: RANK[h["status"]])
    return {"status": best["status"], "message": best["message"]}


def parse_reports(report_dirs: list[str]) -> dict:
    """{(simple class, method): {"status", "message"}} from surefire XML. A parameterized method is one entry."""
    groups: dict[tuple[str, str], list[dict]] = {}
    for tc in read_testcases([p for d in report_dirs for p in sorted(glob.glob(os.path.join(d, "TEST-*.xml")))]):
        m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", tc["name"])
        if m:
            groups.setdefault((tc["classname"].rsplit(".", 1)[-1], m.group(0)), []).append(tc)
    return {key: worst(hits) for key, hits in groups.items()}


def match_pytest(case: dict, testcases: list[dict]) -> dict:
    """tests/test_x.py::TestA::test_b is classname tests.test_x.TestA and name test_b (or test_b[param])."""
    file, *parts = case["test"].split("::")
    classname = ".".join([file[:-3].replace("/", ".")] + parts[:-1])
    return worst([t for t in testcases if t["classname"] == classname and re.sub(r"\[.*$", "", t["name"]) == parts[-1]])


def match_vitest(case: dict, testcases: list[dict]) -> dict:
    """test/x.test.ts::name is a test of that file whose full name is name or ends with " > name"."""
    file, name = case["test"].split("::", 1)
    return worst([t for t in testcases if (t["classname"] == file or t["classname"].endswith("/" + file))
                  and (t["name"] == name or t["name"].endswith(" > " + name))])


def run_junit(ctx: Ctx, tree: str, cases: list[dict], out_dir: str, results: dict, exits: dict):
    by_module: dict[str, list[dict]] = {}
    for k in cases:
        by_module.setdefault(k.get("module", MODULES[0]), []).append(k)
    reports = []
    for module, ks in by_module.items():
        classes: dict[str, list[str]] = {}
        for k in ks:
            cls, method = case_key(k)
            classes.setdefault(cls, []).append(method)
        selector = ",".join(f"{cls}#{'+'.join(ms)}" for cls, ms in classes.items())
        report_dir = os.path.join(tree, "java", module, "target", "surefire-reports")
        shutil.rmtree(report_dir, ignore_errors=True)
        if os.path.exists(os.path.join(out_dir, f"{module}.log")):
            os.remove(os.path.join(out_dir, f"{module}.log"))
        args = ctx.mvn() + ["test", "-pl", module] + (["-am"] if module != MODULES[0] else []) + [
            f"-Dtest={selector}", "-Dsurefire.failIfNoSpecifiedTests=false", "-Djava.awt.headless=true"]
        exits[module] = run_cmd(args, cwd=os.path.join(tree, "java"), log=os.path.join(out_dir, f"{module}.log"), env=java_env())
        if os.path.isdir(report_dir):
            keep = os.path.join(out_dir, f"reports-{module}")
            shutil.rmtree(keep, ignore_errors=True)
            shutil.copytree(report_dir, keep)
            reports.append(keep)
    parsed = parse_reports(reports)
    for k in cases:
        results[k["id"]] = parsed.get(case_key(k), {"status": "missing", "message": ""})


def run_pytest(tree: str, cases: list[dict], out_dir: str, results: dict, exits: dict):
    """The Python wrapper tests need the CLI jar of the tree (base-build or build) and README.md in the package."""
    pkg = os.path.join(tree, PY_PKG)
    log = os.path.join(out_dir, "pytest.log")
    if os.path.exists(log):
        os.remove(log)
    readme = os.path.join(pkg, "README.md")
    made = not os.path.exists(readme)
    if made:
        shutil.copyfile(os.path.join(tree, "README.md"), readme)
    shutil.rmtree(os.path.join(pkg, "src", "opendataloader_pdf", "jar"), ignore_errors=True)
    extras = sorted({e for k in cases for e in k.get("extras", [])})
    try:
        # One run for each case: pytest runs nothing if one node id of the list does not exist
        for i, k in enumerate(cases):
            xml = os.path.join(out_dir, f"pytest-{k['id']}.xml")
            if os.path.exists(xml):
                os.remove(xml)
            args = ["uv", "run"] + [x for e in extras for x in ("--extra", e)] + (
                ["--reinstall-package", "opendataloader-pdf"] if i == 0 else []) + [
                "pytest", "-p", "no:cacheprovider", f"--junitxml={xml}", k["test"]]
            exits[f"pytest-{k['id']}"] = run_cmd(args, cwd=pkg, log=log, env=java_env())
            results[k["id"]] = match_pytest(k, read_testcases([xml]) if os.path.exists(xml) else [])
    finally:
        if made and os.path.exists(readme):
            os.remove(readme)


def run_vitest(tree: str, cases: list[dict], out_dir: str, results: dict, exits: dict):
    """The Node wrapper tests need the CLI jar of the tree: pnpm run build copies it."""
    pkg = os.path.join(tree, NODE_PKG)
    log = os.path.join(out_dir, "vitest.log")
    if os.path.exists(log):
        os.remove(log)
    code = run_cmd(["pnpm", "install", "--frozen-lockfile"], cwd=pkg, log=log)
    if code == 0:
        code = run_cmd(["pnpm", "run", "build"], cwd=pkg, log=log, env=java_env())
    exits["vitest-build"] = code
    for k in cases:
        file, name = k["test"].split("::", 1)
        xml = os.path.join(out_dir, f"vitest-{k['id']}.xml")
        if os.path.exists(xml):
            os.remove(xml)
        if code == 0:
            exits[f"vitest-{k['id']}"] = run_cmd(["pnpm", "exec", "vitest", "--run", file, "-t", re.escape(name),
                                                  "--reporter=junit", f"--outputFile={xml}"], cwd=pkg, log=log, env=java_env())
        results[k["id"]] = match_vitest(k, read_testcases([xml]) if os.path.exists(xml) else [])


def run_cases(ctx: Ctx, tree: str, cases: list[dict], out_dir: str) -> tuple[dict, dict]:
    """Runs the cases in a tree. Returns ({case id: result}, {runner or module: exit code})."""
    os.makedirs(out_dir, exist_ok=True)
    results, exits = {}, {}
    for runner, fn in (("junit", lambda ks: run_junit(ctx, tree, ks, out_dir, results, exits)),
                       ("pytest", lambda ks: run_pytest(tree, ks, out_dir, results, exits)),
                       ("vitest", lambda ks: run_vitest(tree, ks, out_dir, results, exits))):
        ks = [k for k in cases if runner_of(k) == runner]
        if ks:
            fn(ks)
    return results, exits


def judge_problem(cases: list[dict], results: dict) -> dict:
    """On the base: a problem case must fail with its symptom; a guard must pass."""
    rows, ok = [], True
    for k in cases:
        r = results.get(k["id"], {"status": "missing", "message": ""})
        if k["kind"] == "problem":
            if r["status"] in ("fail", "error"):
                verdict = "reproduced" if k["symptom"] in r["message"] else "other failure"
            elif r["status"] == "pass":
                verdict = "not reproduced"
            else:
                verdict = "no result (compile error, skip, or no test)"
            good = verdict == "reproduced"
        else:
            verdict = "holds" if r["status"] == "pass" else f"does not hold on the base ({r['status']})"
            good = r["status"] == "pass"
        ok &= good
        rows.append({"id": k["id"], "kind": k["kind"], "test": k["test"], "status": r["status"], "verdict": verdict, "ok": good,
                     "message": r["message"][:300]})
    return {"ok": ok, "cases": rows}


def judge_fix(cases: list[dict], results: dict) -> dict:
    rows, ok = [], True
    for k in cases:
        r = results.get(k["id"], {"status": "missing", "message": ""})
        good = r["status"] == "pass"
        ok &= good
        rows.append({"id": k["id"], "kind": k["kind"], "test": k["test"], "status": r["status"], "ok": good, "message": r["message"][:300]})
    return {"ok": ok, "cases": rows}


# ---------------------------------------------------------------- gates


def case_files(ctx: Ctx, c: dict) -> list[str]:
    """The test files of the cases and the support files, relative to the fix tree."""
    return sorted({find_test_file(ctx.src, k) for k in c["cases"]} | set(c.get("support", [])))


def files_digest(tree: str, files: list[str]) -> str:
    h = hashlib.sha256()
    for f in files:
        h.update(f.encode() + b"\0" + (sha256_file(os.path.join(tree, f)) or "missing").encode() + b"\0")
    return h.hexdigest()


def gate_problem(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    require_clean(ctx.base_src, "base tree")
    files = case_files(ctx, c)
    tracked = set(git(ctx.base_src, "ls-files", "--", *files).split())
    try:
        for f in files:
            dst = os.path.join(ctx.base_src, f)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(os.path.join(ctx.src, f), dst)
        results, exits = run_cases(ctx, ctx.base_src, c["cases"], ctx.gate_dir("problem"))
    finally:
        restore = [f for f in files if f in tracked]
        if restore:
            git(ctx.base_src, "checkout", "--", *restore)
        for f in files:
            if f not in tracked and os.path.exists(os.path.join(ctx.base_src, f)):
                os.remove(os.path.join(ctx.base_src, f))
    require_clean(ctx.base_src, "base tree after the problem run")
    j = judge_problem(c["cases"], results)
    return {"state": "pass" if j["ok"] else "fail",
            "summary": {**j, "maven_exit": exits, "copied": files, "tests_digest": files_digest(ctx.src, files)}}


def gate_fix(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    results, exits = run_cases(ctx, ctx.src, c["cases"], ctx.gate_dir("fix"))
    j = judge_fix(c["cases"], results)
    return {"state": "pass" if j["ok"] else "fail", "summary": {**j, "maven_exit": exits}}


def judge_pinning(rows: list[dict]) -> bool:
    return all(r["pinned"] for r in rows)


def gate_pinning(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    base = ctx.base()
    changes = [ch for ch in changed_files(ctx) if is_java_main(ch["path"])]
    junit = [k for k in c["cases"] if runner_of(k) == "junit"]
    if changes and not junit:
        rows = [{"file": ch["path"], "status": ch["status"], "pinned": False, "pinned_by": []} for ch in changes]
        return {"state": "fail", "summary": {"files": rows, "note": "Java main code changed, but no JUnit case can pin it"}}
    rows = []
    for i, ch in enumerate(changes):
        d = os.path.join(ctx.gate_dir("pinning"), f"{i:02d}")
        tree = os.path.join(d, "tree")
        shutil.rmtree(tree, ignore_errors=True)
        os.makedirs(tree)
        archive = os.path.join(d, "java.tar")
        with open(archive, "wb") as f:
            if subprocess.run(["git", "-C", ctx.src, "archive", "HEAD", "java"], stdout=f).returncode != 0:
                raise Refused("git archive failed", 3)
        if subprocess.run(["tar", "-x", "-f", archive, "-C", tree]).returncode != 0:
            raise Refused("tar failed", 3)
        os.remove(archive)
        if ch["status"] in ("A", "R"):
            os.remove(os.path.join(tree, ch["path"]))
        if ch["status"] in ("M", "R", "D"):
            old = ch["old"] or ch["path"]
            os.makedirs(os.path.dirname(os.path.join(tree, old)), exist_ok=True)
            with open(os.path.join(tree, old), "wb") as f:
                f.write(subprocess.run(["git", "-C", ctx.src, "show", f"{base}:{old}"], capture_output=True).stdout)
        # The Java files are reverted, so the JUnit cases decide; the wrapper tests would fail without a jar
        results, exits = run_cases(ctx, tree, junit, os.path.join(d, "run"))
        failing = [cid for cid, r in results.items() if r["status"] != "pass"]
        compiled = any(os.path.isdir(os.path.join(d, "run", f"reports-{m}")) for m in exits)
        pinned_by = failing if compiled else ["compile"]
        rows.append({"file": ch["path"], "status": ch["status"], "pinned": bool(pinned_by), "pinned_by": pinned_by})
        shutil.rmtree(tree, ignore_errors=True)
    return {"state": "pass" if judge_pinning(rows) else "fail", "summary": {"files": rows}}


def judge_scope(changes: list[dict], scope: list[str]) -> dict:
    outside = [ch["path"] for ch in changes if not is_test_path(ch["path"])
               and not any(fnmatch.fnmatch(ch["path"], s) for s in scope)]
    return {"ok": not outside, "changed": [ch["path"] for ch in changes], "outside": outside}


def gate_scope(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    j = judge_scope(changed_files(ctx), c["scope"])
    return {"state": "pass" if j["ok"] else "fail", "summary": j}


def find_cli_jar(tree: str) -> str:
    jars = [j for j in glob.glob(os.path.join(tree, "java", "opendataloader-pdf-cli", "target", "opendataloader-pdf-cli-*.jar"))
            if not os.path.basename(j).startswith("original-")]
    if len(jars) != 1:
        raise Refused(f"{len(jars)} CLI jars in {tree}: build again with clean", 3)
    return jars[0]


def suite_totals(tree: str, log: str) -> dict:
    """Maven's module totals from the log, and the names of the skipped tests from the reports."""
    totals = {"tests": 0, "fail": 0, "error": 0, "skipped": 0, "modules": 0}
    for m in re.finditer(r"^\[[A-Z]+\] Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+), Skipped: ([0-9]+)$",
                         read_text(log) if os.path.exists(log) else "", re.M):
        for key, value in zip(("tests", "fail", "error", "skipped"), m.groups()):
            totals[key] += int(value)
        totals["modules"] += 1
    reports = [d for d in glob.glob(os.path.join(tree, "java", "*", "target", "surefire-reports")) if os.path.isdir(d)]
    skipped = sorted(f"{cls}#{m}" for (cls, m), r in parse_reports(reports).items() if r["status"] == "skipped")
    return {**totals, "skipped_tests": skipped}


def gate_base_build(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.base_src, "base tree")
    log = os.path.join(ctx.gate_dir("base-build"), "build.log")
    code = run_cmd(ctx.mvn() + ["-q", "clean", "package", "-DskipTests"], cwd=os.path.join(ctx.base_src, "java"), log=log, env=java_env())
    if code != 0:
        return {"state": "fail", "summary": {"exit": code, "log": log}}
    shutil.copyfile(find_cli_jar(ctx.base_src), ctx.jar("base"))
    return {"state": "pass", "summary": {"exit": 0}}


def gate_build(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    log = os.path.join(ctx.gate_dir("build"), "build-java.log")
    if os.path.exists(log):
        os.remove(log)
    code = run_cmd(["./scripts/build-java.sh"], cwd=ctx.src, log=log, env=java_env())
    success = code == 0 and "BUILD SUCCESS" in read_text(log)
    summary = {"exit": code, "suite": suite_totals(ctx.src, log)}
    if success:
        shutil.copyfile(find_cli_jar(ctx.src), ctx.jar("fix"))
    require_clean(ctx.src, "fix tree after the build")
    return {"state": "pass" if success and not summary["suite"]["fail"] and not summary["suite"]["error"] else "fail", "summary": summary}


def lint_findings(log: str, kind: str) -> dict:
    counts: dict[str, int] = {}
    for line in read_text(log).splitlines():
        if kind == "checkstyle" and re.search(r"\.java:[0-9]+:", line):
            key = re.sub(r"^.*?/(opendataloader-pdf-[a-z]+/src/)", r"\1", line)
            key = re.sub(r":[0-9]+(:[0-9]+)?:", ":", key)
        elif kind == "spotbugs" and re.match(r"^\[ERROR\] (High|Medium|Low):", line):
            key = re.sub(r"\[line [0-9]+\]", "", line)
        else:
            continue
        counts[key] = counts.get(key, 0) + 1
    return counts


GENERATED_RE = re.compile(r"(_generated\.py|\.generated\.ts)$")


def wrapper_files(changes: list[dict], pkg: str, exts: tuple[str, ...]) -> list[str]:
    return [ch["path"][len(pkg) + 1:] for ch in changes if ch["path"].startswith(pkg + "/") and ch["path"].endswith(exts)
            and ch["status"] != "D" and not GENERATED_RE.search(ch["path"])]


def run_capture(args: list[str], cwd: str, log: str, env: dict | None = None) -> tuple[int, str]:
    res = subprocess.run(args, cwd=cwd, capture_output=True, text=True, env={**os.environ, **(env or {})})
    with open(log, "a", encoding="utf-8") as f:
        f.write(f"$ {' '.join(args)}\n{res.stdout}{res.stderr}\nexit {res.returncode}\n")
    return res.returncode, res.stdout + res.stderr


def parse_ruff(text: str) -> dict:
    counts: dict[str, int] = {}
    for m in re.finditer(r"^(\S[^:\n]*):[0-9]+:[0-9]+: ([A-Z]+[0-9]+)", text, re.M):
        key = f"ruff {m.group(1)}:{m.group(2)}"
        counts[key] = counts.get(key, 0) + 1
    return counts


def parse_eslint(text: str, tree: str) -> dict:
    counts: dict[str, int] = {}
    for m in re.finditer(r"^(.+?):[0-9]+:[0-9]+: .*\[(?:Error|Warning)/([^\]]+)\]$", text, re.M):
        key = f"eslint {os.path.relpath(m.group(1), tree) if m.group(1).startswith('/') else m.group(1)}:{m.group(2)}"
        counts[key] = counts.get(key, 0) + 1
    return counts


def java_lint(ctx: Ctx, tree: str, side: str, java: list[str], problems: list) -> dict:
    includes = ",".join(sorted({f"**/{os.path.basename(p)}" for p in java}))
    classes = ",".join(sorted({re.sub(r"^.*/src/main/java/", "", p)[:-5].replace("/", ".") for p in java if is_java_main(p)}))
    d = ctx.gate_dir("static")
    cs, sb = os.path.join(d, f"checkstyle-{side}.log"), os.path.join(d, f"spotbugs-{side}.log")
    for p in (cs, sb):
        if os.path.exists(p):
            os.remove(p)
    run_cmd(ctx.mvn() + ["-fn", "-pl", ",".join(MODULES), "checkstyle:check", f"-Dcheckstyle.includes={includes}"],
            cwd=os.path.join(tree, "java"), log=cs, env=java_env())
    if classes:
        run_cmd(ctx.mvn() + ["-fn", "-DskipTests", "compile", "spotbugs:check", f"-Dspotbugs.onlyAnalyze={classes}"],
                cwd=os.path.join(tree, "java"), log=sb, env=java_env())
    for p, goal in ((cs, "--- checkstyle:"), (sb, "--- spotbugs:")):
        if p == sb and not classes:
            continue
        text = read_text(p)
        if text.count(goal) < len(MODULES) or "COMPILATION ERROR" in text or "Could not resolve" in text:
            problems.append(f"{side} {os.path.basename(p)}: the tool did not run on each module")
    return {"checkstyle": lint_findings(cs, "checkstyle"), "spotbugs": lint_findings(sb, "spotbugs") if classes else {}}


def python_lint(ctx: Ctx, tree: str, side: str, files: list[str], problems: list) -> dict:
    pkg = os.path.join(tree, PY_PKG)
    files = [f for f in files if os.path.isfile(os.path.join(pkg, f))]
    out = {"ruff": {}, "black": {}}
    if not files:
        return out
    log = os.path.join(ctx.gate_dir("static"), f"python-{side}.log")
    code, text = run_capture(["uvx", "ruff", "check", "--no-cache", "--output-format", "concise", *files], pkg, log)
    if code not in (0, 1):
        problems.append(f"{side} ruff: exit {code}")
    out["ruff"] = parse_ruff(text)
    for f in files:
        code, text = run_capture(["uvx", "black", "--diff", "--quiet", f], pkg, log)
        if code != 0:
            problems.append(f"{side} black {f}: exit {code}")
        hunks = len(re.findall(r"^@@ ", text, re.M))
        if hunks:
            out["black"][f"black {f}:hunks"] = hunks
    return out


def node_lint(ctx: Ctx, tree: str, side: str, files: list[str], problems: list) -> dict:
    pkg = os.path.join(tree, NODE_PKG)
    files = [f for f in files if os.path.isfile(os.path.join(pkg, f))]
    out = {"eslint": {}, "prettier": {}}
    if not files:
        return out
    log = os.path.join(ctx.gate_dir("static"), f"node-{side}.log")
    if not os.path.isdir(os.path.join(pkg, "node_modules")):
        code, _ = run_capture(["pnpm", "install", "--frozen-lockfile"], pkg, log)
        if code != 0:
            problems.append(f"{side} pnpm install: exit {code}")
            return out
    code, text = run_capture(["pnpm", "exec", "eslint", "--format", "unix", *files], pkg, log)
    if code not in (0, 1):
        problems.append(f"{side} eslint: exit {code}")
    out["eslint"] = parse_eslint(text, pkg)
    for f in files:
        code, _ = run_capture(["pnpm", "exec", "prettier", "--check", f], pkg, log)
        if code not in (0, 1):
            problems.append(f"{side} prettier {f}: exit {code}")
        elif code == 1:
            out["prettier"][f"prettier {f}:not formatted"] = 1
    return out


def new_findings(found: dict) -> dict:
    """The findings whose count grows from the base to the fix, per tool, file, and rule."""
    new = {}
    for tool, fix_counts in found["fix"].items():
        for key, n in fix_counts.items():
            base_n = found["base"].get(tool, {}).get(key, 0)
            if n > base_n:
                new[key] = n - base_n
    return new


def gate_static(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    changes = changed_files(ctx)
    java = [ch["path"] for ch in changes if ch["path"].endswith(".java") and ch["status"] != "D"]
    py = wrapper_files(changes, PY_PKG, (".py",))
    node = wrapper_files(changes, NODE_PKG, (".ts", ".tsx", ".js", ".mjs", ".cjs"))
    if not (java or py or node):
        return {"state": "pass", "summary": {"files": [], "note": "no Java, Python, or Node source file changed"}}
    found, problems = {"fix": {}, "base": {}}, []
    for side, tree in (("fix", ctx.src), ("base", ctx.base_src)):
        for d in ("python", "node"):
            log = os.path.join(ctx.gate_dir("static"), f"{d}-{side}.log")
            if os.path.exists(log):
                os.remove(log)
        if java:
            found[side].update(java_lint(ctx, tree, side, java, problems))
        if py:
            found[side].update(python_lint(ctx, tree, side, py, problems))
        if node:
            found[side].update(node_lint(ctx, tree, side, node, problems))
    new = new_findings(found)
    require_clean(ctx.src, "fix tree after the static analysis")
    return {"state": "pass" if not new and not problems else "fail",
            "summary": {"files": java + [f"{PY_PKG}/{f}" for f in py] + [f"{NODE_PKG}/{f}" for f in node], "new": new,
                        "problems": problems, "counts": {s: {t: sum(v.values()) for t, v in found[s].items()} for s in found}}}


def last_match(pattern: str, text: str) -> str:
    found = re.findall(pattern, text, re.M)
    return found[-1] if found else ""


def gate_wrappers(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    d = ctx.gate_dir("wrappers")
    for name in ("python.log", "node.log", "ci-verify.log"):
        if os.path.exists(os.path.join(d, name)):
            os.remove(os.path.join(d, name))
    shutil.rmtree(os.path.join(ctx.src, "python", "opendataloader-pdf", "src", "opendataloader_pdf", "jar"), ignore_errors=True)
    steps = {"python": run_cmd(["./scripts/build-python.sh"], cwd=ctx.src, log=os.path.join(d, "python.log"), env=java_env()),
             "node": run_cmd(["./scripts/build-node.sh"], cwd=ctx.src, log=os.path.join(d, "node.log"), env=java_env())}
    wheels = glob.glob(os.path.join(ctx.src, "python", "opendataloader-pdf", "dist", "*.whl"))
    venv = os.path.join(ctx.work, "venv")
    ci = os.path.join(d, "ci-verify.log")
    if len(wheels) == 1:
        steps["venv"] = run_cmd(["uv", "venv", "-q", "--clear", venv], log=ci)
        steps["install"] = run_cmd(["uv", "pip", "install", "-q", "--python", os.path.join(venv, "bin", "python"), wheels[0]], log=ci)
        steps["ci-verify"] = run_cmd([os.path.join(venv, "bin", "python"), "verification/ci-verify.py"], cwd=ctx.src, log=ci, env=java_env())
    else:
        steps["wheel"] = f"{len(wheels)} wheels"
    summary = {"exit": steps,
               "python": last_match(r"^=+ (.*(?:passed|failed).*) =+$", read_text(os.path.join(d, "python.log"))),
               "node": last_match(r"^\s*Tests\s+(.*)$", read_text(os.path.join(d, "node.log"))),
               "ci-verify": last_match(r"^Results: (.*)$", read_text(ci)) if os.path.exists(ci) else ""}
    require_clean(ctx.src, "fix tree after the wrapper builds")
    return {"state": "pass" if all(v == 0 for v in steps.values()) else "fail", "summary": summary}


def gate_issue_corpus(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.base_src, "base tree")
    log = os.path.join(ctx.gate_dir("issue-corpus"), "tests.log")
    if os.path.exists(log):
        os.remove(log)
    for module in MODULES:
        shutil.rmtree(os.path.join(ctx.base_src, "java", module, "target", "test-samples"), ignore_errors=True)
    code = run_cmd(ctx.mvn() + ["test", "-Dtest=Issue*Test", "-Dsurefire.failIfNoSpecifiedTests=false", "-Djava.awt.headless=true"],
                   cwd=os.path.join(ctx.base_src, "java"), log=log, env=java_env())
    out = os.path.join(ctx.work, "corpus", "issue-samples")
    shutil.rmtree(out, ignore_errors=True)
    count = 0
    for module in MODULES:
        root = os.path.join(ctx.base_src, "java", module, "target", "test-samples")
        for path in sorted(glob.glob(os.path.join(root, "**", "*.pdf"), recursive=True)):
            rel = os.path.relpath(path, root).split(os.sep)
            folder = rel[0] if len(rel) > 1 else "_top"
            name = "__".join(rel[1:]) if len(rel) > 1 else rel[0]
            if name.endswith("_tagged.pdf"):
                continue
            os.makedirs(os.path.join(out, folder), exist_ok=True)
            shutil.copyfile(path, os.path.join(out, folder, name))
            count += 1
    require_clean(ctx.base_src, "base tree after the issue tests")
    return {"state": "pass" if count else "fail", "summary": {"pdfs": count, "maven_exit": code}}


def kind_of(rel: str) -> str:
    if rel.endswith("_tagged.pdf"):
        return "tagged-pdf"
    for kind, ext in FORMAT_EXT.items():
        if rel.endswith(ext):
            return kind
    return "images"


def normalize(data: bytes, kind: str, names: list[str]) -> bytes:
    if kind == "json" and ({"json:id", "json:round-2"} & set(names)):
        try:
            obj = json.loads(data)
        except ValueError:
            return data

        def walk(x):
            if isinstance(x, dict):
                return {k: walk(v) for k, v in x.items() if not (k == "id" and "json:id" in names)}
            if isinstance(x, list):
                return [walk(v) for v in x]
            if isinstance(x, float) and "json:round-2" in names:
                return round(x, 2)
            return x
        return canonical(walk(obj)).encode()
    if kind == "text" and "text:blank-lines" in names:
        return b"\n".join(line for line in data.split(b"\n") if line.strip())
    if kind == "tagged-pdf" and "tagged-pdf:document-id" in names:
        return re.sub(rb"/ID\s*\[\s*<[0-9A-Fa-f]*>\s*<[0-9A-Fa-f]*>\s*\]", b"/ID[]", data)
    return data


def judge_side_effects(out: str, inv: dict) -> dict:
    """Compares out/main and out/merge of side_effects.sh. Each difference must be normalized or allowed."""
    summary_path = os.path.join(out, "summary.txt")
    errors = "missing summary"
    if os.path.exists(summary_path):
        m = re.search(r"== error diff \(main -> merge\)\n(.*?)\n== changed outputs", read_text(summary_path), re.S)
        errors = (m.group(1).strip() if m else "unreadable summary")
    files = set()
    for side in ("main", "merge"):
        root = os.path.join(out, side)
        for dirpath, _, names in os.walk(root):
            for n in names:
                files.add(os.path.relpath(os.path.join(dirpath, n), root))
    formats = set(inv.get("formats", KINDS))
    stats = {"compared": 0, "equal": 0, "normalized": 0, "allowed": [], "remaining": []}
    for rel in sorted(files):
        kind = kind_of(rel)
        if kind not in formats:
            continue
        stats["compared"] += 1
        a, b = os.path.join(out, "main", rel), os.path.join(out, "merge", rel)
        if os.path.exists(a) and os.path.exists(b):
            da, db = open(a, "rb").read(), open(b, "rb").read()
            if da == db:
                stats["equal"] += 1
                continue
            names = inv.get("ignore", [])
            if names and normalize(da, kind, names) == normalize(db, kind, names):
                stats["normalized"] += 1
                continue
            what = "differs"
        else:
            what = "only in " + ("main" if os.path.exists(a) else "merge")
        allow = next((x for x in inv.get("allow", []) if fnmatch.fnmatch(rel, x["file"])), None)
        if allow:
            stats["allowed"].append({"file": rel, "what": what, "reason": allow["reason"], "amendment": allow["amendment"]})
        else:
            stats["remaining"].append({"file": rel, "what": what})
    # No output at all (for example, no hybrid server) compares nothing: that is not a pass.
    ok = errors == "(none)" and not stats["remaining"] and stats["compared"] > 0
    if not stats["compared"]:
        errors = errors if errors != "(none)" else "no outputs to compare"
    return {"ok": ok, "errors": errors, **stats}


def corpus_dir(ctx: Ctx, corpus: str) -> str:
    if corpus == "@issue-samples":
        return os.path.join(ctx.work, "corpus", "issue-samples")
    path = os.path.realpath(os.path.join(ctx.base_src, corpus))
    if not path.startswith(ctx.base_src + os.sep):
        raise Refused(f"corpus {corpus}: not a folder of the base tree")
    return path


def gate_invariants(ctx: Ctx, c: dict) -> dict:
    require_clean(ctx.src, "fix tree")
    invs = effective_invariants(c, changed_files(ctx))
    script = os.path.join(REVIEW_SCRIPTS, "side_effects.sh")
    for side in ("base", "fix"):
        if not os.path.isfile(ctx.jar(side)):
            raise Refused(f"no {side}.jar: run the {'base-build' if side == 'base' else 'build'} gate first", 3)
    rows, ok = [], True
    for inv in invs:
        corpus = corpus_dir(ctx, inv["corpus"])
        if not os.path.isdir(corpus):
            rows.append({"id": inv["id"], "ok": False, "errors": f"no corpus folder: {inv['corpus']} (run the issue-corpus gate)"})
            ok = False
            continue
        run_dir = ctx.gate_dir("invariants")
        out = os.path.join(run_dir, inv["id"])
        run_cmd(["bash", script, ctx.jar("base"), ctx.jar("fix"), corpus, out, *inv.get("options", [])], cwd=run_dir,
                log=os.path.join(run_dir, f"{inv['id']}.log"), env=java_env())
        j = judge_side_effects(out, inv)
        ok &= j["ok"]
        rows.append({"id": inv["id"], "corpus": inv["corpus"], "options": inv.get("options", []),
                     "ignore": inv.get("ignore", []), **j, "remaining": j["remaining"][:100]})
    return {"state": "pass" if ok else "fail", "summary": {"invariants": rows}}


def count_types(obj, counts=None) -> dict:
    counts = {} if counts is None else counts
    if isinstance(obj, dict):
        if isinstance(obj.get("type"), str):
            counts[obj["type"]] = counts.get(obj["type"], 0) + 1
        for v in obj.values():
            count_types(v, counts)
    elif isinstance(obj, list):
        for v in obj:
            count_types(v, counts)
    return counts


def compare_count(n: int, rule: str) -> bool:
    op, value = COUNT_RE.fullmatch(rule).groups()
    value = int(value)
    return {None: n == value, "=": n == value, ">": n > value, ">=": n >= value, "<": n < value, "<=": n <= value}[op]


def judge_expect(text: str, fmt: str, expect: dict) -> list[str]:
    """The names of the expectations that do not hold. The names never contain document text."""
    failed = []
    if expect.get("types"):
        try:
            counts = count_types(json.loads(text)) if fmt == "json" else {}
        except ValueError:
            counts = {}
        for t, rule in expect["types"].items():
            if not compare_count(counts.get(t, 0), rule):
                failed.append(f"types.{t} {rule} (got {counts.get(t, 0)})")
    for i, s in enumerate(expect.get("contains", [])):
        if s not in text:
            failed.append(f"contains[{i}]")
    for i, s in enumerate(expect.get("absent", [])):
        if s in text:
            failed.append(f"absent[{i}]")
    return failed


def gate_reporter(ctx: Ctx, c: dict) -> dict:
    rep = c.get("reporter")
    if not rep:
        return {"state": "pass", "summary": {"runs": [], "note": "no reporter runs in the contract"}}
    doc = os.path.join(ctx.work, rep["document"])
    rows, ok = [], True
    for r in rep["runs"]:
        texts = {}
        for side in ("base", "fix"):
            out = os.path.join(ctx.gate_dir("reporter"), r["id"], side)
            shutil.rmtree(out, ignore_errors=True)
            os.makedirs(out)
            options = r.get("fix_options", r["options"]) if side == "fix" else r["options"]
            code = run_cmd(["java", "-jar", ctx.jar(side), *options, "-f", r["format"], "-o", out, doc],
                           log=os.path.join(out, "run.log"), env=java_env())
            outputs = glob.glob(os.path.join(out, "*" + FORMAT_EXT[r["format"]]))
            texts[side] = (code, read_text(outputs[0]) if len(outputs) == 1 else None)
        row = {"id": r["id"]}
        for side in ("base", "fix"):
            code, text = texts[side]
            if text is None:
                row[side] = {"ok": False, "failed": [f"no output (exit {code})"]}
            else:
                failed = judge_expect(text, r["format"], r.get(side, {}))
                row[side] = {"ok": not failed, "failed": failed}
        if "fix_equals_base" in r:
            a, b = texts["base"][1], texts["fix"][1]
            kind = r["format"]
            row["equal"] = a is not None and b is not None and \
                normalize(a.encode(), kind, r["fix_equals_base"]) == normalize(b.encode(), kind, r["fix_equals_base"])
        good = row["base"]["ok"] and row["fix"]["ok"] and row.get("equal", True)
        ok &= good
        rows.append({**row, "ok": good})
    return {"state": "pass" if ok else "fail", "summary": {"runs": rows}}


def gate_bench(ctx: Ctx, c: dict) -> dict:
    bench = os.path.join(ctx.work, "bench")
    d = ctx.gate_dir("bench")
    if not os.path.isdir(os.path.join(bench, ".git")):
        if run_cmd(["git", "clone", "-q", "--depth", "1", "https://github.com/opendataloader-project/opendataloader-bench.git", bench],
                   log=os.path.join(d, "clone.log")) != 0:
            raise Refused("git clone of the bench failed (network)", 3)
    logs, errors = {}, {}
    for side in ("base", "fix"):
        shutil.rmtree(os.path.join(bench, "prediction", "opendataloader", "markdown"), ignore_errors=True)
        ev = os.path.join(bench, "prediction", "opendataloader", "evaluation.json")
        if os.path.exists(ev):
            os.remove(ev)
        log = os.path.join(d, f"bench-{side}.log")
        if os.path.exists(log):
            os.remove(log)
        run_cmd(["./scripts/bench.sh", "--skip-build", "--check-regression", "--force", "--jar-path", ctx.jar(side)],
                cwd=ctx.src, log=log, env={"BENCH_DIR": bench, **java_env()})
        if not os.path.exists(ev):
            return {"state": "fail", "summary": {"note": f"no evaluation.json for {side}", "log": log}}
        shutil.copyfile(ev, os.path.join(d, f"bench-{side}.json"))
        with open(os.path.join(d, f"bench-{side}.json.commit"), "w") as f:
            f.write(git(bench, "rev-parse", "HEAD"))
        text = read_text(log)
        logs[side] = "All thresholds met." in text
        errors[side] = text.count("Error converting")
    res = subprocess.run([sys.executable, "-I", os.path.join(REVIEW_SCRIPTS, "bench.py"), "compare",
                          os.path.join(d, "bench-base.json"), os.path.join(d, "bench-fix.json")], capture_output=True, text=True)
    ok = res.returncode == 0 and (logs["fix"] or not logs["base"]) and errors["fix"] <= errors["base"]
    return {"state": "pass" if ok else "fail",
            "summary": {"compare_exit": res.returncode, "compare": res.stdout.strip().splitlines()[:40],
                        "thresholds_met": logs, "error_converting": errors}}


GATES = {"problem": gate_problem, "base-build": gate_base_build, "build": gate_build, "fix": gate_fix, "pinning": gate_pinning,
         "scope": gate_scope, "static": gate_static, "wrappers": gate_wrappers, "issue-corpus": gate_issue_corpus,
         "invariants": gate_invariants, "reporter": gate_reporter, "bench": gate_bench}
USES_CONTRACT = {"problem", "fix", "pinning", "scope", "invariants", "reporter"}
USES_JARS = {"invariants", "reporter", "bench"}
BASE_ONLY = {"base-build", "issue-corpus"}  # they read only the base tree


def run_gate(ctx: Ctx, name: str) -> dict:
    # base-build runs in Phase 2 step 1, before the contract exists; the gates of USES_CONTRACT need it
    c = load_contract(ctx) if name in USES_CONTRACT or os.path.exists(contract_path(ctx)) else {}
    result = GATES[name](ctx, c)
    rec = {"gate": name, "state": result["state"], "head": ctx.head(), "tree": ctx.tree(), "base": ctx.base(),
           "contract": contract_hash(c) if c else None}
    if name in USES_JARS or name in ("build", "base-build"):
        rec["jars"] = {"base": sha256_file(ctx.jar("base")), "fix": sha256_file(ctx.jar("fix"))}
    rec["summary"] = result["summary"]
    return append_record(ctx, rec)


# ---------------------------------------------------------------- required gates and check


def required_gates(c: dict, changes: list[dict]) -> list[str]:
    paths = [ch["path"] for ch in changes]
    req = ["base-build", "build", "fix", "scope"]
    if any(is_java_main(p) for p in paths):
        req += ["pinning"]
    if any(p.endswith(".java") for p in paths) or wrapper_files(changes, PY_PKG, (".py",)) or \
            wrapper_files(changes, NODE_PKG, (".ts", ".tsx", ".js", ".mjs", ".cjs")):
        req += ["static"]
    if any(p.startswith(("java/", "python/", "node/")) or p == "options.json" for p in paths):
        req += ["wrappers"]
    invs = effective_invariants(c, changes)
    if any(inv["corpus"] == "@issue-samples" for inv in invs):
        req += ["issue-corpus"]
    if invs:
        req += ["invariants"]
    if c.get("reporter"):
        req += ["reporter"]
    if any(p.startswith("java/") and not is_test_path(p) for p in paths):
        req += ["bench"]
    return [g for g in ORDER if g in req]


def contract_changes(frozen: dict, current: dict) -> tuple[list[str], list[str]]:
    """(changes, errors). The contract can only grow after the freeze, and each change needs an amendment."""
    changes, errors = [], []
    fcases = {k["id"]: k for k in frozen.get("cases", [])}
    ccases = {k["id"]: k for k in current.get("cases", [])}
    for cid, k in fcases.items():
        now_k = ccases.get(cid)
        if now_k is None:
            errors.append(f"case {cid} was removed after the freeze")
        elif any(now_k.get(f) != k.get(f) for f in ("kind", "test", "symptom", "module", "runner")):
            errors.append(f"case {cid} changed after the freeze (kind, test, symptom, or module)")
    for cid in sorted(set(ccases) - set(fcases)):
        changes.append(f"new {ccases[cid]['kind']} case {cid}: {ccases[cid]['test']}")
    for s in current.get("scope", []):
        if s not in frozen.get("scope", []):
            changes.append(f"scope + {s}")
    finv = {i["id"]: i for i in frozen.get("invariants", [])}
    cinv = {i["id"]: i for i in current.get("invariants", [])}
    for iid, inv in finv.items():
        now_i = cinv.get(iid)
        if now_i is None:
            errors.append(f"invariant {iid} was removed after the freeze")
            continue
        for n in now_i.get("ignore", []):
            if n not in inv.get("ignore", []):
                changes.append(f"invariant {iid}: ignore + {n}")
        for a in now_i.get("allow", []):
            if a not in inv.get("allow", []):
                changes.append(f"invariant {iid}: allow {a['file']} ({a['reason']})")
        if now_i.get("corpus") != inv.get("corpus") or now_i.get("options", []) != inv.get("options", []):
            errors.append(f"invariant {iid}: corpus or options changed after the freeze")
    for iid in sorted(set(cinv) - set(finv)):
        changes.append(f"new invariant {iid}")
    if canonical(frozen.get("reporter")) != canonical(current.get("reporter")):
        changes.append("reporter runs changed")
    if (frozen.get("objective"), frozen.get("issue")) != (current.get("objective"), current.get("issue")):
        errors.append("issue or objective changed after the freeze")
    stripped = {k: v for k, v in current.items() if k != "amendments"}
    fstripped = {k: v for k, v in frozen.items() if k != "amendments"}
    if canonical(stripped) != canonical(fstripped) and len(current.get("amendments", [])) <= len(frozen.get("amendments", [])):
        errors.append("the contract changed after the freeze without a new amendment")
    for a in current.get("amendments", [])[len(frozen.get("amendments", [])):]:
        changes.append(f"amendment {a['n']}: {a['what']} (why: {a['why']})")
    return changes, errors


def load_lock(ctx: Ctx) -> dict | None:
    path = os.path.join(ctx.work, "contract.lock")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def selfcheck_state(ctx: Ctx, tree: str) -> tuple[bool, list[str], list[str]]:
    path = os.path.join(ctx.work, "selfcheck.json")
    if not os.path.exists(path):
        return False, ["no selfcheck.json (fix_gates.py template --selfcheck prints the items)"], []
    with open(path, encoding="utf-8") as f:
        s = json.load(f)
    errors, notes = [], []
    try:
        same = ctx.tree(s.get("head") or "-") == tree
    except Refused:
        same = False
    if not same:
        errors.append("selfcheck.json is for another commit (head: a commit with the current content)")
    items = s.get("items") or {}
    for i in range(1, len(SELFCHECK) + 1):
        v = (items.get(str(i)) or "").strip()
        if v == "ok":
            continue
        if v.startswith("note: ") and len(v) > 6:
            notes.append(f"{i}. {v[6:]}")
        else:
            errors.append(f"self-check item {i}: write ok, or note: <text>; an open finding must be fixed first")
    return not errors, errors, notes


def record_tree(ctx: Ctx, rec: dict) -> str | None:
    """The content that a record is for. A record without a tree (an older harness) has the tree of its commit."""
    if rec.get("tree"):
        return rec["tree"]
    try:
        return ctx.tree(rec["head"]) if rec.get("head") else None
    except Refused:
        return None


def compute_check(ctx: Ctx, final: bool = False) -> dict:
    c = load_contract(ctx)
    head, tree, base, chash = ctx.head(), ctx.tree(), ctx.base(), contract_hash(c)
    errors, rows = [], []
    if git(ctx.src, "status", "--porcelain").strip():
        errors.append("the fix tree has uncommitted changes")
    changes = changed_files(ctx)
    records = read_records(ctx)
    lock = load_lock(ctx)
    amend_changes = []
    if lock is None:
        errors.append("the contract is not frozen (Approval 1, then fix_gates.py freeze)")
    else:
        amend_changes, amend_errors = contract_changes(lock["contract"], c)
        errors += amend_errors
    jars = {"base": sha256_file(ctx.jar("base")), "fix": sha256_file(ctx.jar("fix"))}
    gates = ["problem"] + required_gates(c, changes) + (["pr-watch"] if final else [])
    for g in gates:
        rec = latest(records, g)
        why = ""
        if rec is None:
            why = "no record"
        elif rec["state"] != "pass":
            why = "failed"
        elif g == "problem":
            if rec["base"] != base:
                why = "for another base (run it again after a rebase)"
            elif rec["contract"] != chash:
                why = "for another contract (a new case must fail on the base too)"
            elif rec["summary"].get("tests_digest") != files_digest(ctx.src, case_files(ctx, c)):
                why = "the case test files changed after the problem run"
        elif g not in BASE_ONLY and record_tree(ctx, rec) != tree:
            why = "for another commit (other content)"
        elif rec["base"] != base:
            why = "for another base"
        elif g in USES_CONTRACT and rec["contract"] != chash:
            why = "for another contract"
        if not why and g == "build" and rec["jars"]["fix"] != jars["fix"]:
            why = "fix.jar changed after the build"
        if not why and g == "base-build" and rec["jars"]["base"] != jars["base"]:
            why = "base.jar changed after the build"
        if not why and g in USES_JARS and rec.get("jars") != jars:
            why = "for other jars"
        rows.append({"gate": g, "state": rec["state"] if rec else "none", "ok": not why, "why": why})
    ok_self, self_errors, self_notes = selfcheck_state(ctx, tree)
    errors += self_errors
    ok = not errors and all(r["ok"] for r in rows)
    return {"ok": ok, "head": head, "base": base, "contract": chash, "gates": rows, "errors": errors,
            "contract_changes": amend_changes, "selfcheck_notes": self_notes, "changed": [ch["path"] for ch in changes]}


# ---------------------------------------------------------------- report


def path_forms(path: str) -> list[str]:
    """A path as written and as resolved: on macOS, /tmp and /var are links to /private/tmp and /private/var."""
    forms = {path, os.path.realpath(path)}
    for p in list(forms):
        if p.startswith("/private/"):
            forms.add(p[len("/private"):])
    return sorted(forms, key=len, reverse=True)


def sanitize(text: str, ctx: Ctx) -> str:
    pairs = [(p, "base.jar") for p in path_forms(ctx.jar("base"))] + [(p, "fix.jar") for p in path_forms(ctx.jar("fix"))]
    pairs += [(p, "$WORK") for p in path_forms(ctx.work)] + [(p, "<repo>") for p in path_forms(ctx.src)]
    pairs += [(p, "~") for p in path_forms(os.path.expanduser("~"))]
    for path, name in pairs:
        text = text.replace(path, name)
    return text


def report_text(ctx: Ctx) -> str:
    chk = compute_check(ctx)
    c = load_contract(ctx)
    records = read_records(ctx)
    rec = {g: latest(records, g) for g in list(GATES) + ["pr-watch"]}
    lines = [f"Harness: `fix_gates.py check` {'passes' if chk['ok'] else 'FAILS'} on {chk['head'][:8]} (base {chk['base'][:8]}).", ""]
    prob, fix = rec.get("problem"), rec.get("fix")
    if prob and fix:
        before = {r["id"]: r for r in prob["summary"].get("cases", [])}
        after = {r["id"]: r for r in fix["summary"].get("cases", [])}
        lines += ["| Case | Kind | Test | Before (`main`) | After |", "|:--|:--|:--|:--|:--|"]
        for k in c["cases"]:
            b, a = before.get(k["id"], {}), after.get(k["id"], {})
            lines.append(f"| {k['id']} | {k['kind']} | `{k['test']}` | {b.get('verdict', b.get('status', 'not run'))} | {a.get('status', 'not run')} |")
        lines.append("")
    if rec.get("reporter") and rec["reporter"]["summary"].get("runs"):
        lines += ["The reporter's document (not shared): " + "; ".join(
            f"{r['id']} {'pass' if r['ok'] else 'fail'}" for r in rec["reporter"]["summary"]["runs"]) + ".", ""]
    if rec.get("pinning"):
        lines.append("- Pinning: " + "; ".join(f"`{os.path.basename(f['file'])}` reverted alone fails {', '.join(f['pinned_by']) or 'nothing'}"
                                               for f in rec["pinning"]["summary"].get("files", [])))
    if rec.get("build") and rec["build"]["summary"].get("suite"):
        s = rec["build"]["summary"]["suite"]
        lines.append(f"- Java tests: {s['tests']} run, {s['fail']} failed, {s['error']} errors, {s['skipped']} skipped"
                     + (f" ({', '.join(s['skipped_tests'][:5])})" if s["skipped_tests"] else ""))
    if rec.get("static"):
        s = rec["static"]["summary"]
        lines.append(f"- Static analysis of the changed files (checkstyle, spotbugs, ruff, black, eslint, prettier as they apply): "
                     f"{len(s.get('new', {}))} new findings")
    if rec.get("wrappers"):
        s = rec["wrappers"]["summary"]
        lines.append(f"- Python: {s.get('python') or '-'}; Node: {s.get('node') or '-'}; `verification/ci-verify.py`: {s.get('ci-verify') or '-'}")
    if rec.get("invariants"):
        lines.append("- Side effects (`side_effects.sh`: json, markdown, html, text, tagged-pdf):")
        for inv in rec["invariants"]["summary"].get("invariants", []):
            opts = " ".join(inv.get("options", [])) or "default options"
            parts = [f"{inv.get('compared', 0)} files", f"{inv.get('equal', 0)} equal"]
            if inv.get("ignore"):
                parts.append(f"{inv.get('normalized', 0)} equal after {', '.join(inv['ignore'])}")
            parts += [f"{len(inv.get('allowed', []))} allowed", f"{len(inv.get('remaining', []))} remaining"]
            lines.append(f"  - {inv['id']} ({inv.get('corpus')}, {opts}): {', '.join(parts)}; errors: {inv.get('errors')}")
            for a in inv.get("allowed", [])[:10]:
                lines.append(f"    - allowed (amendment {a['amendment']}): `{a['file']}`: {a['reason']}")
    if rec.get("bench"):
        lines.append("- Benchmark: " + "; ".join(rec["bench"]["summary"].get("compare", [])[:6]))
    if chk["contract_changes"]:
        lines += ["", "Contract changes after Approval 1:"] + [f"- {x}" for x in chk["contract_changes"]]
    if chk["selfcheck_notes"]:
        lines += ["", "Self-check notes:"] + [f"- {x}" for x in chk["selfcheck_notes"]]
    if not chk["ok"]:
        lines += ["", "Not done:"] + [f"- {r['gate']}: {r['why']}" for r in chk["gates"] if not r["ok"]] + [f"- {e}" for e in chk["errors"]]
    return sanitize("\n".join(lines) + "\n", ctx)


# ---------------------------------------------------------------- pull request


def judge_pr(checks: list[dict], comments: list[dict]) -> dict:
    """CI buckets and CodeRabbit comments. Critical and major need a reply from a person."""
    buckets = {}
    for ch in checks:
        buckets[ch.get("bucket") or "unknown"] = buckets.get(ch.get("bucket") or "unknown", 0) + 1
    top = [x for x in comments if (x.get("user") or {}).get("login") == "coderabbitai[bot]" and not x.get("in_reply_to_id")]
    rows = []
    for x in top:
        m = re.search(r"(Critical|Major|Minor|Trivial|Nitpick)", x.get("body") or "")
        sev = m.group(1).lower() if m else "unknown"
        replied = any(y.get("in_reply_to_id") == x.get("id") and not (y.get("user") or {}).get("login", "").endswith("[bot]") for y in comments)
        rows.append({"id": x.get("id"), "path": x.get("path"), "line": x.get("line"), "severity": sev, "replied": replied})
    open_items = [r for r in rows if r["severity"] in ("critical", "major", "unknown") and not r["replied"]]
    failing = buckets.get("fail", 0) + buckets.get("cancel", 0)
    pending = buckets.get("pending", 0)
    state = "fail" if failing or open_items else "waiting" if pending else "pass"
    return {"state": state, "buckets": buckets, "coderabbit": rows, "open": open_items}


def cmd_pr_watch(a, ctx: Ctx) -> int:
    def gh_json(args, ok_codes=(0,)):
        res = subprocess.run(["gh", *args], capture_output=True, text=True)
        if res.returncode not in ok_codes:
            raise Refused(f"gh {' '.join(args[:3])}: {res.stderr.strip()[:300]}", 3)
        return json.loads(res.stdout or "null")
    # gh pr checks exits 1 while a check fails and 8 while a check is pending; the JSON is the same.
    pr = gh_json(["pr", "view", str(a.pr), "--repo", a.repo, "--json", "headRefOid,state"])
    checks = gh_json(["pr", "checks", str(a.pr), "--repo", a.repo, "--json", "name,bucket,state"], (0, 1, 8)) or []
    pages = gh_json(["api", f"repos/{a.repo}/pulls/{a.pr}/comments?per_page=100", "--paginate", "--slurp"]) or []
    comments = [x for page in pages for x in page]
    j = judge_pr(checks, comments)
    c = load_contract(ctx)
    try:
        pr_tree = ctx.tree(pr["headRefOid"])
    except Refused:
        pr_tree = None
    rec = append_record(ctx, {"gate": "pr-watch", "state": j["state"], "head": pr["headRefOid"], "tree": pr_tree, "base": ctx.base(),
                              "contract": contract_hash(c), "summary": {"pr": a.pr, **j}})
    print(json.dumps(rec["summary"], indent=1))
    return 0 if j["state"] == "pass" else 13


# ---------------------------------------------------------------- CLI


def print_check(chk: dict):
    print(f"head {chk['head'][:8]}  base {chk['base'][:8]}  contract {chk['contract'][:8]}")
    for r in chk["gates"]:
        print(f"  {'ok  ' if r['ok'] else 'NOT '} {r['gate']:<13} {r['state']:<8} {r['why']}")
    for e in chk["errors"]:
        print(f"  error: {e}")
    for x in chk["contract_changes"]:
        print(f"  contract change: {x}")
    print("DONE" if chk["ok"] else "NOT DONE")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    t = sub.add_parser("template")
    t.add_argument("--issue", type=int)
    t.add_argument("--selfcheck", action="store_true", help="print the self-check items instead")
    for name in ("validate", "gate", "run", "freeze", "check", "report", "pr-watch"):
        p = sub.add_parser(name)
        p.add_argument("--work", required=True)
        p.add_argument("--src")
        p.add_argument("--base-src")
        p.add_argument("--offline", action="store_true", help="mvn -o")
        if name == "gate":
            p.add_argument("name", choices=sorted(GATES))
        if name == "run":
            p.add_argument("--gates", help="A,B: only these gates")
        if name == "check":
            p.add_argument("--final", action="store_true")
        if name == "pr-watch":
            p.add_argument("--pr", type=int, required=True)
            p.add_argument("--repo", default="opendataloader-project/opendataloader-pdf")
    a = ap.parse_args(argv)
    try:
        if a.cmd == "template":
            if a.selfcheck:
                print("\n".join(f"{i + 1}. {s}" for i, s in enumerate(SELFCHECK)))
                print(json.dumps({"head": "<HEAD sha>", "items": {str(i + 1): "ok" for i in range(len(SELFCHECK))}}, indent=1))
            elif a.issue is None:
                raise Refused("template needs --issue N (or --selfcheck)")
            else:
                print(json.dumps(template(a.issue), indent=1))
            return 0
        ctx = Ctx(a.work, a.src, a.base_src, a.offline)
        if a.cmd == "validate":
            c = load_contract(ctx)
            print(f"contract {contract_hash(c)[:8]}: {sum(k['kind'] == 'problem' for k in c['cases'])} problem, "
                  f"{sum(k['kind'] == 'guard' for k in c['cases'])} guard cases")
            return 0
        if a.cmd == "gate":
            rec = run_gate(ctx, a.name)
            print(json.dumps({"gate": rec["gate"], "state": rec["state"], "summary": rec["summary"]}, indent=1)[:6000])
            return 0 if rec["state"] == "pass" else 13
        if a.cmd == "run":
            c = load_contract(ctx)
            names = a.gates.split(",") if a.gates else required_gates(c, changed_files(ctx))
            bad = [n for n in names if n not in GATES]
            if bad:
                raise Refused(f"unknown gates: {', '.join(bad)}")
            failed = []
            for n in names:
                rec = run_gate(ctx, n)
                print(f"{n}: {rec['state']}")
                if rec["state"] != "pass":
                    failed.append(n)
            return 13 if failed else 0
        if a.cmd == "freeze":
            c = load_contract(ctx)
            if load_lock(ctx):
                raise Refused("the contract is frozen already; change it with an amendment")
            rec = latest(read_records(ctx), "problem")
            if not rec or rec["state"] != "pass" or rec["contract"] != contract_hash(c) or rec["base"] != ctx.base():
                raise Refused("freeze needs a passing problem record of this contract on this base")
            with open(os.path.join(ctx.work, "contract.lock"), "w", encoding="utf-8") as f:
                json.dump({"sha256": contract_hash(c), "frozen_at": now(), "base": ctx.base(), "contract": c}, f, indent=1)
            print(f"frozen {contract_hash(c)[:8]}")
            return 0
        if a.cmd == "check":
            chk = compute_check(ctx, a.final)
            print_check(chk)
            return 0 if chk["ok"] else 13
        if a.cmd == "report":
            text = report_text(ctx)
            path = os.path.join(ctx.work, "evidence.md")
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            print(path)
            return 0
        if a.cmd == "pr-watch":
            return cmd_pr_watch(a, ctx)
    except Refused as e:
        print(f"refused: {e}", file=sys.stderr)
        return e.code
    return 2


if __name__ == "__main__":
    sys.exit(main())
