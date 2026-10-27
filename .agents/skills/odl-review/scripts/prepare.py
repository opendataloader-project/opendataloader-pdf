#!/usr/bin/env python3
"""Prepare one review round. The trust boundary for the trees is here (SKILL.md, C1, C3, C6).

  prepare.py fetch --repo OWNER/REPO --pr N --work WORK [--git DIR]
      Network (gh, git fetch). Writes WORK/<n>/state.json: the PR state, the main SHA, the reviews
      that have a review-state footer, and the permission of each of their authors.
  prepare.py fetch --fixture STATE.json --pr N --work WORK [--git DIR]
      Offline: copies a state file (for the evals). Its head and main SHAs must exist in DIR.
  prepare.py trees --pr N --work WORK [--git DIR]
      Local. Makes WORK/<n>/r<k>/ for the current round k:
        trees/{main,base,head,merge}/   the files, written from git objects; no .git
        trees/<name>.rev                the SHA of each tree
        quoted/<tree>/<path>.txt        agent instruction files and symbolic links, as text only
        pr.json  kind.json  files.json  diff.patch
      Exit 3: the PR is not open, or it is a draft. Exit 4: the base branch is not main.
      Exit 5: an agent instruction file or a symbolic link is left in a tree, or two paths collide on a
      case-insensitive file system. Exit 6: a file cannot be written (for example a name that is too long),
      or the old trees of the round cannot be removed (for example a folder that a sandbox protects).
  prepare.py fresh --pr N --work WORK --round-dir WORK/<n>/r<k> [--git DIR]
      Local, after a new fetch. Compares state.json with pr.json of the round before a post and writes
      fresh.json. Exit 0: the same head and main, open, not a draft, base main: post.
      Exit 7: do not post (no fetch after the round was made, not open, a draft, another base, or a
      new head: make a new round). Exit 8: main moved; fresh.json lists the files that changed on
      main and the ones that this PR also changes.
      With --approve (the post approves), exit 7 also when a CI check or a pull_request workflow
      of main is not finished or not green for the head.
  prepare.py ci-approval --pr N --work WORK --round-dir WORK/<n>/r<k>
      Local, after a fetch. Prints the approve command of each fork CI run of the head that waits for
      a maintainer, only if: the head is the round's head; the PR changes no workflow, build, tooling,
      or agent file; each scan hit that is not info is explained with passing evidence; a passing
      Q2-security record exists; and decisions.json has "ci_approve": true. Else exit 9.
  prepare.py cleanup --pr N --work WORK --round-dir WORK/<n>/r<k> [--git DIR]
      Local, after the post. Exit 10 if the round folder has no retro.md with a table
      "User message | What it showed | Skill log | Eval" (SKILL.md, "Retrospective"). Else removes
      refs/odl-review/<n>. It does not remove the demo folder.
  prepare.py triage --repo OWNER/REPO --work WORK [--prs N,N] [--git DIR]
  prepare.py triage --fixture TRIAGE.json --work WORK [--prs N,N] [--git DIR] [--main-ref REF]
      Network (gh, git fetch), read-only. Before a batch: all open pull requests, or the listed ones.
      Writes WORK/triage/triage.json: for each pull request the facts (CLA, CI checks, the runs of the
      head against the pull_request workflows of main, conflicts, draft, base, sensitive files, the same
      closing issue as another pull request, the round from footers by writers, a push or an author
      comment after the last counted review) and a class from them: blocked, limit, decide, reviewed,
      re-review, or review. Exit 11: the list length is not GitHub's count of open pull requests.
  prepare.py triage-report --work WORK
      Local. Merges WORK/triage/judgment.json (the agent's class and answers for each pull request) and
      writes WORK/triage/triage.md. Exit 12: the triage is incomplete, a judgment is missing or unknown,
      a class is weaker than the facts, a changed class has no reason, or a concern (no problem, more
      than one problem, a direction to decide) has the class review.
  prepare.py blind --round-dir WORK/<n>/r<k>
      Local. Makes WORK/<n>/r<k>-blind/ for the blind correctness reviewer: diff.patch, the demo
      input files (no README.txt, no outputs, no diff.txt), and a README.txt with the tree paths.
      No plan, cases, precedents, or evidence. Prints the folder.
  prepare.py strip-outputs DIR
      Remove links, special files, and agent instruction files from the outputs of an isolated run
      (scripts/isolate.reference.sh calls it). Prints the two counts.
  prepare.py check-tree --tree WORK/<n>/r<k>/trees/<name>
      Local. Compares the tree with the manifest that `trees` wrote (<name>.manifest: each folder and
      each file with its SHA-256). Exit 13: a path that is not in the git objects (for example a
      .claude folder that an agent wrote when its shell was in the tree), a missing path, or a
      changed file. scripts/isolate.reference.sh runs it before each run.

The trees are written blob by blob from `git cat-file`, not with checkout or archive. Thus no
attribute of the PR (export-ignore, filters, eol) and no symbolic link has an effect. Agent
instruction files (AGENTS.md, CLAUDE.md, .claude/, .agents/, and the others in INSTRUCTION_*)
are left out of the trees at any depth, so that an agent that reads a tree does not load them.
"""

from __future__ import annotations

import argparse
import datetime
import fnmatch
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import unicodedata

INSTRUCTION_FILES = {"agents.md", "agents.override.md", "agent.md", "claude.md", "claude.local.md", "gemini.md", "codex.md",
                     "qwen.md", "warp.md", "crush.md", "conventions.md", "copilot-instructions.md", ".cursorrules",
                     ".cursorignore", ".windsurfrules", ".clinerules", ".rules", ".mcp.json", "opencode.json",
                     ".augment-guidelines", ".aider.conf.yml", ".aiderrules", ".goosehints"}
INSTRUCTION_DIRS = {".claude", ".agents", ".cursor", ".windsurf", ".gemini", ".codex", ".clinerules", ".roo", ".kiro",
                    ".junie", ".continue", ".aider", ".goose", ".amazonq", ".openhands", ".augment", ".trae", ".kilocode",
                    ".opencode"}
INSTRUCTION_PREFIXES = (".github/instructions/", ".github/prompts/", ".github/chatmodes/", ".vscode/mcp.json/")
SOURCE_DIR = re.compile(r"(^|/)(java/[^/]+/src|python/[^/]+/src|python/[^/]+/tests|node/[^/]+/src|node/[^/]+/test)/")
BUILD_HOOKS = {"hatch_build.py", "mvnw", "mvnw.cmd", "gradlew", "gradlew.bat", "checkstyle.xml", "spotbugs-exclude.xml"}
INSTRUCTION_SUFFIXES = (".instructions.md", ".prompt.md", ".chatmode.md")
FOOTER = re.compile(r"Reviewed at [0-9a-f]{7,40} · round [0-9]+")
WRITERS = {"admin", "maintain", "write"}
PASSED_STATES = {"SUCCESS", "SKIPPED", "NEUTRAL"}
FAILED_STATES = {"FAILURE", "ERROR", "TIMED_OUT", "CANCELLED", "ACTION_REQUIRED", "STARTUP_FAILURE", "STALE"}

BUILD_FILES = {"pom.xml", "package.json", "pnpm-lock.yaml", "package-lock.json", "yarn.lock",
               "pnpm-workspace.yaml", ".npmrc", "pyproject.toml", "uv.lock", "setup.py", "setup.cfg",
               "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}


def run(args: list[str], cwd: str | None = None, check: bool = True, input: bytes | None = None) -> str:
    res = subprocess.run(args, cwd=cwd, capture_output=True, input=input)
    if check and res.returncode != 0:
        raise SystemExit(f"{' '.join(args[:4])}: {res.stderr.decode(errors='replace').strip()[:300]}")
    return res.stdout.decode("utf-8", errors="surrogateescape")


GIT_SAFE = ["-c", "core.hooksPath=/dev/null", "-c", "core.quotePath=false", "-c", "color.ui=never", "-c", "diff.noprefix=false",
            "-c", "diff.mnemonicPrefix=false", "-c", "diff.relative=false", "-c", "diff.external="]
DIFF_SAFE = ["--no-color", "--no-ext-diff", "--no-textconv", "--src-prefix=a/", "--dst-prefix=b/"]


def git(repo: str, *args: str, check: bool = True) -> str:
    return run(["git", "-C", repo, *GIT_SAFE, *args], check=check)


class Refuse(Exception):
    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code


def fold(path: str) -> str:
    """The name that a case- and normalization-insensitive file system (macOS APFS) sees, and more."""
    return unicodedata.normalize("NFKC", path).casefold()


def is_instruction(path: str) -> bool:
    p = fold(path)
    parts = p.split("/")
    if parts[-1] in INSTRUCTION_FILES or any(q in INSTRUCTION_DIRS for q in parts) or p.endswith(INSTRUCTION_SUFFIXES):
        return True
    ps = p + "/"
    return any(ps.startswith(x) or f"/{x}" in ps for x in INSTRUCTION_PREFIXES)


def strip_outputs(folder: str) -> tuple[int, int]:
    """Remove links, special files, and agent instruction files from the outputs of a run (C8)."""
    links = instr = 0

    def fail(err: OSError) -> None:
        raise err

    os.chmod(folder, stat.S_IMODE(os.lstat(folder).st_mode) | stat.S_IRWXU)
    for root, dirs, files in os.walk(folder, topdown=True, followlinks=False, onerror=fail):
        for name in list(dirs):
            full = os.path.join(root, name)
            if not os.path.islink(full):  # a run can make its output folders read-only or unreadable
                os.chmod(full, stat.S_IMODE(os.lstat(full).st_mode) | stat.S_IRWXU)
            if os.path.islink(full):
                os.unlink(full)
                links += 1
                dirs.remove(name)
            elif is_instruction(os.path.relpath(full, folder)):
                for r2, d2, _ in os.walk(full, onerror=fail):
                    for n2 in d2:
                        if not os.path.islink(os.path.join(r2, n2)):
                            os.chmod(os.path.join(r2, n2), stat.S_IMODE(os.lstat(os.path.join(r2, n2)).st_mode) | stat.S_IRWXU)
                shutil.rmtree(full)
                instr += 1
                dirs.remove(name)
        for name in files:
            full = os.path.join(root, name)
            if not stat.S_ISREG(os.lstat(full).st_mode):
                os.unlink(full)
                links += 1
            elif is_instruction(os.path.relpath(full, folder)):
                os.unlink(full)
                instr += 1
    return links, instr


def category(path: str) -> str:
    p, base = path, path.rsplit("/", 1)[-1]
    if is_instruction(p):
        return "instruction"
    if p.startswith(".github/"):
        return "workflow"
    if p.startswith(("scripts/", "verification/", "build-scripts/", ".mvn/")) or base.startswith("Dockerfile") \
            or base in {"Makefile", ".gitattributes", ".gitmodules"} or base in BUILD_HOOKS \
            or re.match(r"(node|python)/[^/]+/scripts/", p):
        return "tooling"
    if base in BUILD_FILES or fnmatch.fnmatch(base, "requirements*.txt"):
        return "build"
    if "/src/test/" in p or re.match(r"python/[^/]+/tests/", p) or re.match(r"node/[^/]+/test/", p) \
            or re.search(r"\.test\.[jt]sx?$", p):
        return "test"
    if p.startswith("samples/"):
        return "samples"
    if p.startswith("skills/"):
        return "skill"
    if re.match(r"(java/[^/]+/src/main/|python/[^/]+/src/|node/[^/]+/src/)", p):
        return "code"
    if base.endswith((".md", ".mdx")) or p.startswith(("docs/", "content/")) or base.startswith(("LICENSE", "NOTICE")):
        return "docs"
    return "other"


def auto_kind(cats: set[str]) -> str:
    if not cats:
        return "mixed"
    if cats <= {"build"}:
        return "dependency"
    if cats <= {"docs"}:
        return "docs"
    if cats <= {"test", "samples"}:
        return "test"
    if cats <= {"workflow", "tooling", "instruction", "skill"}:
        return "tooling"
    if "code" in cats and cats <= {"code", "test", "samples", "docs"}:
        return "code"
    return "mixed"


RUN_FAILED = {"failure", "cancelled", "timed_out", "action_required", "startup_failure", "stale"}


def pull_request_workflows(tree: str) -> list[tuple[str, bool]]:
    """The workflows of MAIN that run on pull_request: (name, has a path filter)."""
    folder = os.path.join(tree, ".github", "workflows")
    items = []
    for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
        if name.endswith((".yml", ".yaml")):
            with open(os.path.join(folder, name), encoding="utf-8", errors="replace") as f:
                items.append((name, f.read()))
    return pull_request_workflows_of(items)


def pull_request_workflows_at(repo: str, ref: str) -> list[tuple[str, bool]]:
    """The same, read from git objects of REF (for the triage, which has no trees)."""
    raw = git(repo, "ls-tree", "-z", "--name-only", f"{ref}:.github/workflows", check=False)
    items = [(n, git(repo, "cat-file", "blob", f"{ref}:.github/workflows/{n}"))
             for n in sorted(raw.split("\0")) if n.endswith((".yml", ".yaml"))]
    return pull_request_workflows_of(items)


def pull_request_workflows_of(items: list[tuple[str, str]]) -> list[tuple[str, bool]]:
    found = []
    for name, text in items:
        if not re.search(r"(?m)^on:.*\bpull_request\b|^\s+pull_request:|^\s+-\s*pull_request\s*$", text):
            continue
        title = re.search(r"(?m)^name:\s*['\"]?(.+?)['\"]?\s*$", text)
        found.append((title.group(1) if title else name, bool(re.search(r"(?m)^\s+(paths|paths-ignore):", text))))
    return found


RERUN_DAYS = 30  # GitHub refuses to rerun a workflow run that was created longer ago (#721)


def parse_time(value):
    try:
        return datetime.datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None


def fetched_at(st: dict):
    """The time of the fetch that wrote the state, or None for a state of an older prepare.py."""
    return parse_time(st["fetched_at"]) if st.get("fetched_at") else None


def too_old_to_rerun(r: dict, now) -> bool:
    """A run that GitHub cannot rerun: created RERUN_DAYS or more before the fetch."""
    created = parse_time(r.get("createdAt")) if r.get("createdAt") else None
    return bool(now and created) and (now - created).total_seconds() >= RERUN_DAYS * 86400


def workflow_states(tree: str, runs, now=None) -> tuple[list[str], list[str]]:
    """Failing and pending entries for the pull_request workflows of main, from the runs of the head."""
    return workflow_states_of(pull_request_workflows(tree), runs, now=now)


def workflow_states_of(expected: list[tuple[str, bool]], runs, skip: frozenset = frozenset(), now=None) -> tuple[list[str], list[str]]:
    failing, pending = [], []
    expected = [(n, f) for n, f in expected if n not in skip]
    if expected and not isinstance(runs, list):
        return failing, ["workflow runs: could not list the runs of the head; run prepare.py fetch again"]
    for name, filtered in expected:
        mine = [r for r in runs if isinstance(r, dict) and r.get("workflowName") == name]
        if not mine:
            if not filtered:  # a path filter can skip a workflow on purpose
                pending.append(f"workflow '{name}': no run for the head")
            continue
        latest = mine[0]  # gh lists the newest run first
        if latest.get("status") != "completed":
            pending.append(f"workflow '{name}': {latest.get('status') or 'unknown'}")
        elif did_not_start(latest) and too_old_to_rerun(latest, now):
            # No rerun is possible, so it cannot stay pending: it fails, and the verdict needs its explanation.
            failing.append(f"workflow '{name}': failed before any job started; GitHub cannot rerun it (older than {RERUN_DAYS} days)")
        elif did_not_start(latest):
            pending.append(f"workflow '{name}': failed before any job started; a maintainer reruns it (prepare.py ci-approval)")
        elif (latest.get("conclusion") or "").lower() in RUN_FAILED:
            failing.append(f"workflow '{name}'")
    return failing, pending


def did_not_start(r: dict) -> bool:
    """A run that failed with no job: the workflow could not start (a GitHub or workflow problem, not the code)."""
    return (r.get("conclusion") or "").lower() == "failure" and r.get("jobs") == 0


def add_job_counts(repo: str, runs) -> None:
    for r in runs if isinstance(runs, list) else []:
        if (r.get("conclusion") or "").lower() == "failure" and r.get("databaseId"):
            got = run(["gh", "api", f"repos/{repo}/actions/runs/{r['databaseId']}/jobs", "--jq", ".total_count"], check=False).strip()
            r["jobs"] = int(got) if got.isdigit() else None


def safe_path(path: str) -> bool:
    parts = path.split("/")
    return not (path.startswith("/") or any(p in ("", ".", "..") for p in parts) or ".git" in (q.lower() for q in parts))


def materialize(repo: str, sha: str, dest: str, quoted: str, report: dict) -> None:
    """Write the files of commit SHA into DEST from git objects."""
    listing = git(repo, "ls-tree", "-r", "-z", "--full-tree", sha)
    entries = []
    for rec in listing.split("\0"):
        if not rec:
            continue
        meta, path = rec.split("\t", 1)
        mode, typ, obj = meta.split()
        entries.append((mode, typ, obj, path))
    # Two paths that the file system can see as one would make the tree differ from git: refuse.
    keys: dict[str, tuple[str, str]] = {}
    for mode, typ, obj, path in entries:
        if is_instruction(path) and SOURCE_DIR.search(path):
            raise Refuse(5, f"refused: an agent instruction name inside a source folder: {path!r}")
        if not safe_path(path) or typ == "commit" or mode == "120000" or is_instruction(path):
            continue
        parts = unicodedata.normalize("NFD", path).casefold().split("/")
        for i in range(1, len(parts) + 1):
            key, kind = "/".join(parts[:i]), "file" if i == len(parts) else "dir"
            prev = keys.get(key)
            if prev and (prev[1] != "/".join(path.split("/")[:i]) or prev[0] != kind):
                raise Refuse(5, f"refused: paths collide on a case-insensitive file system: {prev[1]!r} and {path!r}")
            keys.setdefault(key, (kind, "/".join(path.split("/")[:i])))
    os.makedirs(dest)
    proc = subprocess.Popen(["git", "-C", repo, "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    try:
        for mode, typ, obj, path in entries:
            if not safe_path(path):
                report["refused_paths"].append(path)
                continue
            if typ == "commit":
                write_quoted(quoted, path, f"SUBMODULE {obj} (not checked out by prepare.py)\n".encode())
                report["submodules"].append(path)
                continue
            proc.stdin.write(obj.encode() + b"\n")
            proc.stdin.flush()
            header = proc.stdout.readline().split()
            size = int(header[2])
            data = proc.stdout.read(size)
            proc.stdout.read(1)
            if mode == "120000":
                write_quoted(quoted, path, b"SYMLINK -> " + data + b"\n")
                report["symlinks"].append(path)
                continue
            if is_instruction(path):
                write_quoted(quoted, path, data)
                report["instruction_files"].append(path)
                continue
            target = os.path.join(dest, path)
            try:
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, "wb") as f:
                    f.write(data)
                if mode == "100755":
                    os.chmod(target, 0o755)
            except OSError as e:
                raise Refuse(6, f"refused: cannot write {path[:120]!r}: {e.strerror}") from None
    finally:
        proc.stdin.close()
        proc.wait()


def write_quoted(quoted: str, path: str, data: bytes) -> None:
    target = os.path.join(quoted, path + ".txt")
    try:
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "wb") as f:
            f.write(data)
    except OSError as e:
        raise Refuse(6, f"refused: cannot write the quoted copy of {path[:120]!r}: {e.strerror}") from None


def leftovers(dest: str) -> list[str]:
    bad = []
    for root, dirs, files in os.walk(dest, followlinks=False):
        for name in dirs + files:
            full = os.path.join(root, name)
            rel = os.path.relpath(full, dest)
            if os.path.islink(full) or is_instruction(rel):
                bad.append(rel)
    return bad


def tree_listing(dest: str) -> dict:
    """Each folder, and each file with its SHA-256, of a tree (relative paths)."""
    dirs, files = [], {}
    for root, dnames, fnames in os.walk(dest, followlinks=False):
        for name in dnames:
            dirs.append(os.path.relpath(os.path.join(root, name), dest))
        for name in fnames:
            full = os.path.join(root, name)
            h = hashlib.sha256()
            with open(full, "rb") as f:
                for block in iter(lambda: f.read(1 << 20), b""):
                    h.update(block)
            files[os.path.relpath(full, dest)] = h.hexdigest()
    return {"dirs": sorted(dirs), "files": files}


def check_tree(a: argparse.Namespace) -> int:
    """Refuse a tree that is not what `trees` wrote: a path outside the git objects, or a changed file (#721)."""
    tree = os.path.abspath(a.tree).rstrip(os.sep)
    path = tree + ".manifest"
    if not os.path.isfile(path):  # a wrong path, or a removed manifest, is not a checked tree (#836)
        print(f"refused: no manifest for {tree}; run prepare.py trees again")
        return 13
    with open(path, encoding="utf-8") as f:
        want = json.load(f)
    have = tree_listing(tree)
    extra = sorted((set(have["dirs"]) - set(want["dirs"])) | (set(have["files"]) - set(want["files"])))
    missing = sorted((set(want["dirs"]) - set(have["dirs"])) | (set(want["files"]) - set(have["files"])))
    changed = sorted(p for p in set(have["files"]) & set(want["files"]) if have["files"][p] != want["files"][p])
    if extra or missing or changed:
        parts = [f"{k}: {', '.join(v[:5])}" for k, v in (("not in the git objects", extra), ("missing", missing), ("changed", changed)) if v]
        print(f"refused: tree {tree} is not what prepare.py trees wrote ({'; '.join(parts)}). Remove what an agent or a "
              "tool wrote there (for example a .claude folder made when a shell was in the tree), or run prepare.py trees again")
        return 13
    print(f"tree {tree}: {len(have['files'])} files as written")
    return 0


def fetch(a: argparse.Namespace) -> int:
    base = os.path.join(a.work, str(a.pr))
    os.makedirs(base, exist_ok=True)
    if a.fixture:
        with open(a.fixture, encoding="utf-8") as f:
            state = json.load(f)
        for key in ("head", "main"):
            git(a.git, "cat-file", "-e", state[key] + "^{commit}")
        if state.get("runs") == "fixture:all-success":  # only a fixture can say that every workflow passed
            state["runs"], state["runs_fixture_all_success"] = [], True
    else:
        if not a.repo:
            raise SystemExit("fetch needs --repo or --fixture")
        fields = ("number,title,author,state,isDraft,headRefOid,baseRefName,statusCheckRollup,"
                  "closingIssuesReferences,url,isCrossRepository")
        pr = json.loads(run(["gh", "pr", "view", str(a.pr), "--repo", a.repo, "--json", fields]))
        git(a.git, "fetch", "--no-tags", "origin", "main", f"+pull/{a.pr}/head:refs/odl-review/{a.pr}")
        head = git(a.git, "rev-parse", f"refs/odl-review/{a.pr}").strip()
        if head != pr["headRefOid"]:
            raise SystemExit(f"the head moved during the fetch: {pr['headRefOid'][:7]} -> {head[:7]}; run fetch again")
        main = git(a.git, "rev-parse", "origin/main").strip()
        viewer = run(["gh", "api", "user", "--jq", ".login"]).strip()
        reviews, perms = [], {}
        raw = json.loads(run(["gh", "api", f"repos/{a.repo}/pulls/{a.pr}/reviews", "--paginate", "--slurp"]))
        for page in raw:
            for r in page:
                if FOOTER.search(r.get("body") or ""):
                    login = r["user"]["login"]
                    if login not in perms:
                        perms[login] = run(["gh", "api", f"repos/{a.repo}/collaborators/{login}/permission",
                                            "--jq", ".permission"], check=False).strip() or "unknown"
                    reviews.append({"user": login, "commit": r.get("commit_id"), "at": r.get("submitted_at"),
                                    "footer": FOOTER.search(r["body"]).group(0), "state": r.get("state"),
                                    "body": r.get("body") or ""})
        checks = []
        for c in pr.get("statusCheckRollup") or []:
            checks.append({"name": c.get("name") or c.get("context"),
                           "state": (c.get("conclusion") or c.get("state") or "").upper()})
        # Workflow runs of the head: a run that waits for approval or a runner is not in statusCheckRollup.
        raw_runs = run(["gh", "run", "list", "--repo", a.repo, "--commit", head, "--limit", "100",
                        "--json", "databaseId,workflowName,status,conclusion,event,createdAt"], check=False)
        try:
            runs = json.loads(raw_runs) if raw_runs.strip() else []
        except json.JSONDecodeError:
            runs = None
        add_job_counts(a.repo, runs)
        cla_comment = None
        if not any((c.get("name") or "").lower() == "license/cla" for c in checks):
            comments = [c for page in json.loads(run(["gh", "api", f"repos/{a.repo}/issues/{a.pr}/comments", "--paginate", "--slurp"]))
                        for c in page if (c.get("user") or {}).get("login") == "CLAassistant"]
            commits = [c for page in json.loads(run(["gh", "api", f"repos/{a.repo}/pulls/{a.pr}/commits", "--paginate", "--slurp"]))
                       for c in page]
            people = {(c.get(k) or {}).get("login") for c in commits for k in ("author", "committer")} - {"web-flow"}
            body = (comments[-1].get("body") or "") if comments else ""
            cla_comment = {"signed": "badge/signed" in body and "not_signed" not in body,
                           "commit_authors_ok": bool(commits) and people == {pr["author"]["login"]},
                           "at": comments[-1].get("updated_at") if comments else None}
        author_permission = run(["gh", "api", f"repos/{a.repo}/collaborators/{pr['author']['login']}/permission",
                                 "--jq", ".permission"], check=False).strip() or "unknown"
        state = {"repo": a.repo, "number": pr["number"], "title": pr["title"], "url": pr["url"],
                 "runs": runs, "author_permission": author_permission,
                 "fetched_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                 "author": pr["author"]["login"], "author_is_bot": bool(pr["author"].get("is_bot"))
                 or pr["author"]["login"].endswith("[bot]"), "viewer": viewer, "state": pr["state"],
                 "isDraft": pr["isDraft"], "baseRefName": pr["baseRefName"], "head": head, "main": main,
                 "cross_repository": pr.get("isCrossRepository"), "checks": checks,
                 "closing_issues": [i.get("number") for i in pr.get("closingIssuesReferences") or []],
                 "reviews": reviews, "permissions": perms, "cla_comment": cla_comment}
    if not state.get("head_committed_at"):  # a status bot can post minutes after a push (#832: license/cla)
        state["head_committed_at"] = git(a.git, "show", "-s", "--format=%cI", state["head"]).strip()
    with open(os.path.join(base, "state.json"), "w", encoding="utf-8") as f:
        json.dump(state, f, indent=1)
    print(os.path.join(base, "state.json"))
    return 0


TREES_OUTPUTS = {"pr.json", "kind.json", "files.json", "diff.patch", "trees", "quoted", "earlier-review.md", "new.diff", "head.txt"}
FRESH_HEAD_MINUTES = 15


def keep_old_head_files(rdir: str, head: str):
    """If the round folder holds the work of another head (the author pushed before a post), move every file that
    trees does not make into old-<sha8>/, so that no plan, record, report, or draft of the old head mixes with the new
    head (#832). The head of the files is in head.txt: pr.json is gone if an earlier trees run stopped (#836).
    Returns (folder, names) or None."""
    prev = None
    try:
        with open(os.path.join(rdir, "head.txt"), encoding="utf-8") as f:
            prev = f.read().strip()
    except OSError:
        try:
            with open(os.path.join(rdir, "pr.json"), encoding="utf-8") as f:
                prev = json.load(f).get("head")
        except (OSError, ValueError):
            return None
    if not prev or prev == head:
        return None
    keep = f"old-{prev[:8]}"
    names = [n for n in sorted(os.listdir(rdir)) if n not in TREES_OUTPUTS and not n.startswith("old-")]
    if not names:
        return None
    os.makedirs(os.path.join(rdir, keep), exist_ok=True)
    for n in names:
        shutil.move(os.path.join(rdir, n), os.path.join(rdir, keep, n))
    return keep, names


def head_age_minutes(st: dict):
    made, now = parse_time(st.get("head_committed_at")), fetched_at(st)
    if not made or not now:
        return None
    return (now - made).total_seconds() / 60


def trees(a: argparse.Namespace) -> int:
    base = os.path.join(a.work, str(a.pr))
    with open(os.path.join(base, "state.json"), encoding="utf-8") as f:
        st = json.load(f)
    if st.get("state") != "OPEN" or st.get("isDraft"):
        print(f"refused: the pull request is {'a draft' if st.get('isDraft') else st.get('state')}; no review")
        return 3
    if st.get("baseRefName") != "main":
        print(f"refused: the base branch is {st.get('baseRefName')!r}, not main (stacked pull request); Hold")
        return 4
    perms = st.get("permissions", {})
    counted = [r for r in st.get("reviews", []) if perms.get(r["user"]) in WRITERS]
    rnd = len(counted) + 1
    rdir = os.path.join(base, f"r{rnd}")
    os.makedirs(rdir, exist_ok=True)
    moved = keep_old_head_files(rdir, st["head"])
    if moved:
        print(f"the head changed before a post: moved the files of the old head to {moved[0]}/: {moved[1]}")
    with open(os.path.join(rdir, "head.txt"), "w", encoding="utf-8") as f:  # the files that stay are of this head
        f.write(st["head"] + "\n")
    # No file of an older head stays if this run stops, or if this head has no new.diff (a force push, #836).
    for name in ("pr.json", "kind.json", "files.json", "diff.patch", "earlier-review.md", "new.diff"):
        if os.path.exists(os.path.join(rdir, name)):
            os.remove(os.path.join(rdir, name))
    M, H = st["main"], st["head"]
    B = git(a.git, "merge-base", M, H).strip()
    mt = subprocess.run(["git", "-C", a.git, "merge-tree", "--write-tree", "--name-only", "--no-messages", M, H], capture_output=True)
    if mt.returncode not in (0, 1):  # 1 is a conflict; any other code is an error of git (#836)
        raise SystemExit(f"git merge-tree failed ({mt.returncode}): {mt.stderr.decode(errors='replace').strip()[:300]}")
    out = mt.stdout.decode().split("\n")
    conflict = mt.returncode == 1
    C = None
    if not conflict:
        tree = out[0].strip()
        C = git(a.git, "commit-tree", tree, "-p", M, "-p", H, "-m", f"verdict: merge of #{a.pr}").strip()
    tdir, qdir = os.path.join(rdir, "trees"), os.path.join(rdir, "quoted")
    for d in (tdir, qdir):
        if os.path.isdir(d):
            try:
                shutil.rmtree(d)
            except OSError as e:
                print(f"refused: cannot remove the old {os.path.basename(d)}/ of this round ({e.filename}: {e.strerror}). "
                      "Move it out of the round folder, or make the round under a new --work folder and copy your round files, "
                      "then run trees again")
                return 6
    report = {"instruction_files": [], "symlinks": [], "submodules": [], "refused_paths": []}
    shas = {"main": M, "base": B, "head": H, "merge": C}
    per_tree = {}
    for name, sha in shas.items():
        if not sha:
            continue
        rep = {k: [] for k in report}
        try:
            materialize(a.git, sha, os.path.join(tdir, name), os.path.join(qdir, name), rep)
        except Refuse as e:
            print(f"{e} (tree {name})")
            return e.code
        with open(os.path.join(tdir, f"{name}.rev"), "w") as f:
            f.write(sha + "\n")
        bad = leftovers(os.path.join(tdir, name))
        if bad:
            print(f"refused: tree {name} still has instruction files or links: {bad[:5]}")
            return 5
        with open(os.path.join(tdir, f"{name}.manifest"), "w", encoding="utf-8") as f:
            json.dump(tree_listing(os.path.join(tdir, name)), f)
        per_tree[name] = rep
    old, new = (M, C) if C else (B, H)
    raw = git(a.git, "diff", *DIFF_SAFE, "--name-status", "-z", "-M", old, new)
    toks, files = raw.split("\0"), []
    i = 0
    while i < len(toks) and toks[i]:
        status = toks[i]
        if status[0] in "RC":
            files.append({"status": status[0], "old": toks[i + 1], "path": toks[i + 2]})
            i += 3
        else:
            files.append({"status": status[0], "path": toks[i + 1]})
            i += 2
    for f in files:
        f["category"] = category(f["path"])
        if "old" in f and category(f["old"]) != f["category"]:
            f["old_category"] = category(f["old"])
    with open(os.path.join(rdir, "diff.patch"), "w", encoding="utf-8") as f:
        f.write(git(a.git, "diff", *DIFF_SAFE, "-M", old, new).encode("utf-8", "surrogateescape").decode("utf-8", "replace"))
    cats = {f["category"] for f in files} | {f.get("old_category") for f in files if f.get("old_category")}
    viewer = st.get("viewer")
    kind = {"auto": auto_kind(cats), "categories": sorted(cats), "bot": bool(st.get("author_is_bot")),
            "own": bool(viewer) and viewer == st.get("author"), "confirmed": None, "reason": None}
    cla = next((c["state"] for c in st.get("checks", []) if (c.get("name") or "").lower() == "license/cla"), "MISSING")
    cc = st.get("cla_comment") or {}
    if cla == "MISSING" and cc.get("signed") is True and cc.get("commit_authors_ok") is True:
        cla = "SIGNED_COMMENT"  # the CLA assistant posts no status on a new head; its comment says the author signed
    age = head_age_minutes(st)
    if cla == "MISSING" and age is not None and 0 <= age < FRESH_HEAD_MINUTES:
        cla = "PENDING"  # the status can still come (#832: it came minutes after the push); fetch again
    ci = [c for c in st.get("checks", []) if (c.get("name") or "").lower() != "license/cla"]
    failing = [c["name"] for c in ci if c["state"] in FAILED_STATES]
    pending = [c["name"] for c in ci if c["state"] not in FAILED_STATES and c["state"] not in PASSED_STATES]
    if not st.get("runs_fixture_all_success"):
        wf_failing, wf_pending = workflow_states(os.path.join(tdir, "main"), st.get("runs"), fetched_at(st))
        failing += wf_failing
        pending += wf_pending
    pr = {"number": st["number"], "title": st.get("title"), "author": st.get("author"), "repo": st.get("repo"),
          "state": st["state"], "isDraft": st["isDraft"], "baseRefName": st["baseRefName"], "round": rnd,
          "reviews_counted": len(counted), "main": M, "head": H, "base": B, "merge": C, "conflict": conflict,
          "conflict_files": [x for x in out[1:] if x.strip()] if conflict else [], "cla": cla,
          "head_age_minutes": round(age, 1) if age is not None else None,
          "failing_checks": failing, "pending_checks": pending, "closing_issues": st.get("closing_issues", []),
          "author_permission": st.get("author_permission", "unknown"),
          "trees": per_tree, "diff_range": [old, new]}
    if counted:  # a later round: the earlier requests are its checks (SKILL.md, "Later rounds")
        last = sorted(counted, key=lambda r: r.get("at") or "")[-1]
        known = bool(last.get("commit")) and subprocess.run(
            ["git", "-C", a.git, "cat-file", "-e", f"{last['commit']}^{{commit}}"], capture_output=True).returncode == 0
        in_history = known and subprocess.run(["git", "-C", a.git, *GIT_SAFE, "merge-base", "--is-ancestor", last["commit"], H],
                                              capture_output=True).returncode == 0
        pr["last_review"] = {"commit": last.get("commit"), "at": last.get("at"), "user": last.get("user"),
                             "state": last.get("state"), "in_history": in_history}
        with open(os.path.join(rdir, "earlier-review.md"), "w", encoding="utf-8") as f:
            f.write(last.get("body") or "")
        if in_history:
            with open(os.path.join(rdir, "new.diff"), "w", encoding="utf-8", errors="surrogateescape") as f:
                f.write(git(a.git, "diff", *DIFF_SAFE, last["commit"], H))
    for name, obj in (("pr.json", pr), ("kind.json", kind), ("files.json", files)):
        with open(os.path.join(rdir, name), "w", encoding="utf-8") as f:
            json.dump(obj, f, indent=1)
    print(f"round {rnd}: {rdir}")
    print(f"main {M[:7]} head {H[:7]} base {B[:7]} merge {(C or 'CONFLICT')[:7]}; {len(files)} files; kind {kind['auto']}"
          f"{' (bot)' if kind['bot'] else ''}{' (own)' if kind['own'] else ''}; cla {cla}")
    removed = sorted({p for t in per_tree.values() for p in t['instruction_files'] + t['symlinks']})
    if removed:
        print(f"left out of the trees (text in quoted/): {removed[:10]}{' ...' if len(removed) > 10 else ''}")
    return 0


# The agent cannot keep a promise after the session, and a post speaks for the maintainer (#722): a draft that
# promises maintainer work, a schedule, or credit waits for the user. Code blocks and inline code are not scanned.
PROMISE_RE = re.compile(
    r"\b(?:we|i)(?: will|'ll| are going to| am going to| plan to| intend to| promise)\b"
    r"|\b(?:a|the|our) maintainers? (?:will|can take|is going to|are going to|plans? to)\b"
    r"|\bwithin (?:\d+|one|two|three|four|a few|a) (?:hours?|days?|weeks?|months?)\b"
    r"|\bwill (?:credit|follow up|take (?:it|this) over)\b", re.I)


def draft_promises(rdir: str, dec: dict) -> list[str]:
    """Lines of the post drafts that promise what only the maintainer can decide, as 'file:line: text'."""
    ok = {str(x).strip() for x in (dec.get("commitments_ok") or []) if isinstance(x, str)}
    decided = isinstance(dec.get("decided_checks"), dict) and "Q0-direction" in dec["decided_checks"]
    hits = []
    for name in sorted(os.listdir(rdir)):
        if not (name in ("review.md", "close.md", "thanks.md") or re.fullmatch(r"(new-issue|comment)[\w.-]*\.md", name)):
            continue
        with open(os.path.join(rdir, name), encoding="utf-8", errors="replace") as f:
            lines = f.read().split("\n")
        in_code = False
        for i, line in enumerate(lines, 1):
            if line.lstrip().startswith("```"):
                in_code = not in_code
                continue
            if in_code:
                continue
            prose = re.sub(r"`[^`]*`", "", line)  # quoted document text and code are not the reviewer's words
            if not (PROMISE_RE.search(prose) or ("(from the maintainers)" in prose and not decided)):
                continue
            if f"{name}:{i}" in ok or line.strip() in ok:
                continue
            hits.append(f"{name}:{i}: {line.strip()[:120]}")
    return hits


def gain_rows_missing(rdir: str) -> list[str]:
    """Gain cases (main fail, PR pass) when review.md has a Checks table with no ✅ in its 'This PR' column (#722)."""
    cp, rp = os.path.join(rdir, "cases.json"), os.path.join(rdir, "review.md")
    if not (os.path.exists(cp) and os.path.exists(rp)):
        return []
    with open(cp, encoding="utf-8") as f:
        cases = json.load(f)
    gains = [str(c.get("id")) for c in cases if isinstance(c, dict) and c.get("main") == "fail" and c.get("pr") == "pass"]
    if not gains:
        return []
    with open(rp, encoding="utf-8", errors="replace") as f:
        lines = f.read().split("\n")
    col, found = None, False
    for line in lines:
        cells = [c.strip() for c in line.strip().strip("|").split("|")] if line.strip().startswith("|") else None
        if cells is None:
            col = None
        elif col is None:
            col = next((i for i, c in enumerate(cells) if c.startswith("This PR")), None)
        elif col < len(cells) and "✅" in cells[col]:
            found = True
    return [] if found else gains


def fresh(a: argparse.Namespace) -> int:
    sp, pp = os.path.join(a.work, str(a.pr), "state.json"), os.path.join(a.round_dir, "pr.json")
    if os.stat(sp).st_mtime_ns <= os.stat(pp).st_mtime_ns:
        print("refused: state.json is not newer than pr.json; run prepare.py fetch first, then fresh")
        return 7
    with open(sp, encoding="utf-8") as f:
        st = json.load(f)
    with open(pp, encoding="utf-8") as f:
        pr = json.load(f)
    with open(os.path.join(a.round_dir, "files.json"), encoding="utf-8") as f:
        pr_files = {x["path"] for x in json.load(f)}
    stop = []
    card, vj = os.path.join(a.round_dir, "card.md"), os.path.join(a.round_dir, "verdict.json")
    if os.path.exists(vj) and (not os.path.exists(card) or os.stat(card).st_mtime_ns < os.stat(vj).st_mtime_ns):
        stop.append("card.md is older than verdict.json: write the card again from the current verdict and records")
    dec_path = os.path.join(a.round_dir, "decisions.json")
    if os.path.exists(vj) and os.path.exists(dec_path) and os.stat(dec_path).st_mtime_ns > os.stat(vj).st_mtime_ns:
        stop.append("decisions.json is newer than verdict.json: run verdict.py again with the user's answers")
    dec = {}
    if os.path.exists(dec_path):
        with open(dec_path, encoding="utf-8") as f:
            dec = json.load(f)
    promises = draft_promises(a.round_dir, dec if isinstance(dec, dict) else {})
    missing_gain = gain_rows_missing(a.round_dir)
    if missing_gain:
        stop.append(f"the PR fixes case(s) {', '.join(missing_gain)}, but the Checks table of review.md has no row with ✅ "
                    "in its 'This PR' column: add the row, so that the author keeps it")
    if promises:
        stop.append("a draft promises maintainer work, a schedule, or credit (rewrite it as a fact or a request, or the "
                    "user accepts the line in decisions.json commitments_ok): " + "; ".join(promises[:6]))
    if st.get("state") != "OPEN" or st.get("isDraft"):
        stop.append(f"the pull request is {'a draft' if st.get('isDraft') else st.get('state')}")
    if st.get("baseRefName") != "main":
        stop.append(f"the base branch is {st.get('baseRefName')!r}, not main")
    if st["head"] != pr["head"]:
        stop.append(f"the head moved {pr['head'][:7]} -> {st['head'][:7]}: make a new round (prepare.py trees)")
    changed = []
    if st["main"] != pr["main"]:
        raw = git(a.git, "diff", *DIFF_SAFE, "--name-only", "-z", pr["main"], st["main"])
        changed = [{"path": p, "category": category(p)} for p in raw.split("\0") if p]
    result = {"round_head": pr["head"], "round_main": pr["main"], "head": st["head"], "main": st["main"], "stop": stop,
              "main_changed_files": changed, "also_changed_by_pr": sorted({c["path"] for c in changed} & pr_files),
              "promises": promises}
    with open(os.path.join(a.round_dir, "fresh.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, indent=1)
    if a.approve:  # an approving review waits for the CI of the head
        checks = [c for c in st.get("checks", []) if (c.get("name") or "").lower() != "license/cla"]
        wf_failing, wf_pending = ([], []) if st.get("runs_fixture_all_success") else \
            workflow_states(os.path.join(a.round_dir, "trees", "main"), st.get("runs"), fetched_at(st))
        ci_open = [c["name"] for c in checks if c["state"] not in PASSED_STATES] + wf_failing + wf_pending
        if ci_open:
            stop.append(f"CI is not finished or not green for an approving review: {', '.join(ci_open[:5])}")
        result["ci_open"] = ci_open
        with open(os.path.join(a.round_dir, "fresh.json"), "w", encoding="utf-8") as f:
            json.dump(result, f, indent=1)
    if stop:
        print("do not post: " + "; ".join(stop))
        return 7
    if changed:
        cats = sorted({c["category"] for c in changed})
        print(f"main moved {pr['main'][:7]} -> {st['main'][:7]}: {len(changed)} files ({', '.join(cats)}); "
              f"also changed by this PR: {result['also_changed_by_pr'] or 'none'}. Merge again (prepare.py trees) and run the "
              "evidence that the files can reach again, or hold. The list is in fresh.json")
        return 8
    print(f"fresh: head {pr['head'][:7]} and main {pr['main'][:7]} are the same; the pull request is open")
    return 0


CI_RISKY = {"workflow", "tooling", "build", "instruction"}


def ci_approval(a: argparse.Namespace) -> int:
    """Approve a waiting fork CI run only after the scan, the security review, and the user's yes."""
    rdir = a.round_dir

    def load(name, default):
        path = os.path.join(rdir, name)
        if not os.path.exists(path):
            return default
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    with open(os.path.join(a.work, str(a.pr), "state.json"), encoding="utf-8") as f:
        st = json.load(f)
    pr, files, plan, dec = load("pr.json", {}), load("files.json", []), load("plan.json", {}), load("decisions.json", {})
    scan = load("scan.json", None)
    refuse = []
    if st.get("head") != pr.get("head"):
        refuse.append("the head changed since the round was made")
    risky = [x["path"] for x in files if x.get("category") in CI_RISKY or x.get("old_category") in CI_RISKY]
    if risky:
        refuse.append(f"the PR changes workflow, build, tooling, or agent files ({', '.join(risky[:5])}): CI would run them; "
                      "the user reads them and approves in the browser, not this tool")
    if scan is None:
        refuse.append("no scan.json: run diff_scan.py first")
    else:
        recs = []
        if os.path.exists(os.path.join(rdir, "evidence.jsonl")):
            with open(os.path.join(rdir, "evidence.jsonl"), encoding="utf-8") as f:
                recs = [json.loads(x) for x in f if x.strip()]
        current = {r.get("id"): r for r in recs if isinstance(r, dict) and r.get("head") == pr.get("head") and r.get("main") == pr.get("main")}
        expl = plan.get("scan_explanations") or {}
        for h in scan.get("hits", []):
            e = expl.get(h["id"])
            if h.get("severity") != "info" and not (isinstance(e, dict) and current.get(e.get("evidence"), {}).get("state") == "pass"):
                refuse.append(f"scan hit {h['id']} {h['rule']} is not explained with evidence")
        if not any(r.get("condition") == "Q2-security" and r.get("state") == "pass" for r in current.values()):
            refuse.append("no passing security review (a Q2-security record) for the current head and main")
    if dec.get("ci_approve") is not True:
        refuse.append("the user has not said yes to the CI run (decisions.json ci_approve)")
    elif os.stat(os.path.join(rdir, "decisions.json")).st_mtime_ns < os.stat(os.path.join(rdir, "pr.json")).st_mtime_ns:
        refuse.append("ci_approve was recorded before this round: ask the user again at the start gate of this round")
    runs = st.get("runs") if isinstance(st.get("runs"), list) else []
    waiting = [r for r in runs if (r.get("conclusion") or "").lower() == "action_required"
               or (r.get("status") or "").lower() in ("waiting", "action_required")]
    latest = {}
    for r in runs:  # gh lists the newest run first
        latest.setdefault(r.get("workflowName"), r)
    now = fetched_at(st)
    rerun = [r for r in latest.values() if did_not_start(r) and not too_old_to_rerun(r, now)]
    old = [r for r in latest.values() if did_not_start(r) and too_old_to_rerun(r, now)]
    if refuse:
        print("refused: " + "; ".join(refuse))
        return 9
    if not waiting and not rerun and not old:
        print("no run of the head waits for approval or needs a rerun")
        return 0
    for r in waiting:
        print(f"gh api -X POST repos/{st.get('repo')}/actions/runs/{r.get('databaseId')}/approve   # {r.get('workflowName')} ({r.get('event')})")
    for r in rerun:
        print(f"gh run rerun {r.get('databaseId')} --repo {st.get('repo')}   # {r.get('workflowName')}: failed before any job started")
    for r in old:
        print(f"cannot rerun {r.get('databaseId')} ({r.get('workflowName')}, created {(r.get('createdAt') or '?')[:10]}): "
              f"GitHub refuses a rerun after {RERUN_DAYS} days. A new run needs a push by the author, or a maintainer "
              "closes and reopens the pull request (a public write: ask the user). Explain the check in plan.json ci_explanations")
    return 0


RETRO_TABLE = re.compile(r"(?m)^\|[^\n]*User message[^\n]*\|\s*\n\|[\s:|-]+\|\s*\n(\|[^\n]*\|\s*\n?)+")


def cleanup(a: argparse.Namespace) -> int:
    """After the post: refuse without a retrospective, then remove the temporary ref of the PR."""
    path = os.path.join(a.round_dir, "retro.md")
    if not os.path.isfile(path):
        print(f"refused: no retro.md in {a.round_dir}. Write the retrospective first (SKILL.md, 'Retrospective')")
        return 10
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if not RETRO_TABLE.search(text):
        print("refused: retro.md has no table with the columns 'User message | What it showed | Skill log | Eval' "
              "and at least one row (write 'none' in a row if the user only answered the gates)")
        return 10
    ref = f"refs/odl-review/{a.pr}"
    if git(a.git, "rev-parse", "--verify", "--quiet", ref, check=False).strip():
        git(a.git, "update-ref", "-d", ref)
        print(f"removed {ref}")
    print("clean-up done; keep the demo folder until the user says that it can go")
    return 0


BLIND_LEFT_OUT = {"readme.txt", "diff.txt", "index.txt"}


def blind(a: argparse.Namespace) -> int:
    rdir = os.path.abspath(a.round_dir).rstrip("/")
    out = rdir + "-blind"
    if os.path.lexists(out):
        shutil.rmtree(out)
    os.makedirs(os.path.join(out, "inputs"))
    shutil.copyfile(os.path.join(rdir, "diff.patch"), os.path.join(out, "diff.patch"))
    demo = os.path.join(rdir, "demo")
    cases = sorted(os.listdir(demo)) if os.path.isdir(demo) and not os.path.islink(demo) else []
    for case in cases:
        cdir = os.path.join(demo, case)
        if os.path.islink(cdir) or not os.path.isdir(cdir):
            continue
        for name in sorted(os.listdir(cdir)):  # only the files at the top of a case: before/ and after/ hold outputs
            src = os.path.join(cdir, name)
            if name.lower() in BLIND_LEFT_OUT or os.path.islink(src) or not os.path.isfile(src):
                continue
            os.makedirs(os.path.join(out, "inputs", case), exist_ok=True)
            shutil.copyfile(src, os.path.join(out, "inputs", case, name))
    trees_dir = os.path.join(rdir, "trees")
    names = [t for t in ("main", "head", "merge") if os.path.isdir(os.path.join(trees_dir, t))]
    with open(os.path.join(out, "README.txt"), "w", encoding="utf-8") as f:
        f.write("Inputs of the blind correctness reviewer (round folder " + os.path.basename(rdir) + ").\n"
                "diff.patch: the change. inputs/<case>/: input files of the demo, without expected or actual outputs.\n"
                "Trees (read only; read no other file of the round folder):\n"
                + "".join(f"  {t}: {os.path.join(trees_dir, t)}\n" for t in names))
    print(out)
    return 0


TRIAGE_FIELDS = ("number,title,author,state,isDraft,isCrossRepository,createdAt,additions,deletions,changedFiles,files,labels,"
                 "statusCheckRollup,closingIssuesReferences,mergeable,baseRefName,headRefOid,url")
SENSITIVE = {"workflow", "tooling", "build", "instruction", "skill"}
CLASS_RANK = {"review": 0, "re-review": 0, "reviewed": 0, "decide": 1, "limit": 2, "blocked": 2, "close": 3}
CLASS_ACTION = {"review": "Verdict round 1", "re-review": "Verdict, next round", "reviewed": "None: reviewed at this head",
                "decide": "Maintainers decide first; then a verdict", "limit": "Round limit report (SKILL.md, Outputs 4)",
                "blocked": "No action: wait", "close": "Draft a close comment"}
WAITING_RUN = {"waiting", "action_required"}
REVIEW_BOTS = {"coderabbitai", "coderabbitai[bot]"}
THREADS_QUERY = ("query($o:String!,$n:String!,$p:Int!){repository(owner:$o,name:$n){pullRequest(number:$p){"
                 "reviewThreads(first:100){nodes{isResolved isOutdated comments(first:30){nodes{author{login}}}}}}}}")
JUDGED_WAITING = {"decide": "maintainers", "close": "maintainers (close)", "blocked": "author", "limit": "maintainers"}
# The repository's pr-reminders workflow sends the CLA and CodeRabbit reminders. The triage only reads their state.
REMINDER_MARKER = "<!-- odl-pr-reminder -->"
# More commits than this: the branch carries a history that main does not have (for example from before a rewrite of main).
MAX_PR_COMMITS = 100
REMINDER_MANUAL = "This comment was generated by AI and may contain mistakes."


def gh_json(args: list[str]):
    return json.loads(run(["gh", *args]) or "null")


def triage_collect(a: argparse.Namespace) -> dict:
    """The data of the triage: from GitHub, or from a fixture with the same shapes (for the evals)."""
    if a.fixture:
        with open(a.fixture, encoding="utf-8") as f:
            data = json.load(f)
        data.setdefault("missing", [])
        if a.prs:
            want = set(a.prs)
            data["missing"] += [{"number": n, "state": "not found"} for n in sorted(want - {p["number"] for p in data["prs"]})]
            data["prs"] = [p for p in data["prs"] if p["number"] in want]
        data["listed"] = not a.prs
        data["workflows"] = pull_request_workflows_at(a.git, a.main_ref)
        return data
    if not a.repo:
        raise SystemExit("triage needs --repo or --fixture")
    git(a.git, "fetch", "--no-tags", "origin", "main")
    data = {"repo": a.repo, "viewer": run(["gh", "api", "user", "--jq", ".login"]).strip(), "listed": not a.prs,
            "prs": [], "missing": [], "reviews": {}, "comments": {}, "runs": {}, "permissions": {}, "commits": {},
            "workflows": pull_request_workflows_at(a.git, "origin/main"),
            "fetched_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
    if a.prs:
        for n in a.prs:
            res = subprocess.run(["gh", "pr", "view", str(n), "--repo", a.repo, "--json", TRIAGE_FIELDS], capture_output=True)
            pr = json.loads(res.stdout or b"null") if res.returncode == 0 else None
            if not pr or pr.get("state") != "OPEN":
                data["missing"].append({"number": n, "state": (pr or {}).get("state") or "not found"})
            else:
                data["prs"].append(pr)
    else:
        data["prs"] = gh_json(["pr", "list", "--repo", a.repo, "--state", "open", "--limit", "1000", "--json", TRIAGE_FIELDS])
        data["total_count"] = gh_json(["api", f"search/issues?q=repo:{a.repo}+is:pr+is:open&per_page=1", "--jq", ".total_count"])
    for pr in data["prs"]:
        n = str(pr["number"])
        data["commits"][n] = gh_json(["api", f"repos/{a.repo}/pulls/{n}", "--jq", ".commits"])
        data["reviews"][n] = [r for page in gh_json(["api", f"repos/{a.repo}/pulls/{n}/reviews", "--paginate", "--slurp"]) for r in page]
        data["comments"][n] = [c for page in gh_json(["api", f"repos/{a.repo}/issues/{n}/comments", "--paginate", "--slurp"]) for c in page]
        owner, name = a.repo.split("/", 1)
        nodes = (gh_json(["api", "graphql", "-f", f"query={THREADS_QUERY}", "-F", f"o={owner}", "-F", f"n={name}", "-F", f"p={n}"])
                 or {}).get("data", {}).get("repository", {}).get("pullRequest", {}).get("reviewThreads", {}).get("nodes", [])
        data.setdefault("threads", {})[n] = [{"resolved": t.get("isResolved"), "outdated": t.get("isOutdated"),
                                              "authors": [(c.get("author") or {}).get("login") for c in t["comments"]["nodes"]]}
                                             for t in nodes]
        raw = run(["gh", "run", "list", "--repo", a.repo, "--commit", pr["headRefOid"], "--limit", "100",
                   "--json", "databaseId,workflowName,status,conclusion,event,createdAt"], check=False)
        try:
            data["runs"][n] = json.loads(raw) if raw.strip() else []
        except json.JSONDecodeError:
            data["runs"][n] = None
        add_job_counts(a.repo, data["runs"][n])
        for r in data["reviews"][n]:
            login = (r.get("user") or {}).get("login")
            if login and FOOTER.search(r.get("body") or "") and login not in data["permissions"]:
                data["permissions"][login] = run(["gh", "api", f"repos/{a.repo}/collaborators/{login}/permission",
                                                  "--jq", ".permission"], check=False).strip() or "unknown"
    return data


def triage_one(pr: dict, data: dict, issue_prs: dict) -> dict:
    """The class of one pull request from facts only. The agent can make it stricter, never weaker (triage-report)."""
    n = str(pr["number"])
    author = (pr.get("author") or {}).get("login") or ""
    bot = bool((pr.get("author") or {}).get("is_bot")) or author.endswith("[bot]")
    perms = data.get("permissions", {})
    footers = [r for r in data.get("reviews", {}).get(n, []) if FOOTER.search(r.get("body") or "")]
    counted = sorted((r for r in footers if perms.get((r.get("user") or {}).get("login")) in WRITERS),
                     key=lambda r: r.get("submitted_at") or "")
    ignored = sorted({(r.get("user") or {}).get("login") or "?" for r in footers} - {(r.get("user") or {}).get("login") for r in counted})
    last = counted[-1] if counted else None
    pushed = bool(last and last.get("commit_id") != pr.get("headRefOid"))
    commented = bool(last and any((c.get("user") or {}).get("login") == author and (c.get("created_at") or "") > (last.get("submitted_at") or "")
                                  for c in data.get("comments", {}).get(n, [])))
    blocked, decide, notes = [], [], []
    if pr.get("isDraft"):
        blocked.append("draft")
    if pr.get("baseRefName") != "main":
        blocked.append(f"base is {pr.get('baseRefName')!r}, not main")
    checks = [((c.get("name") or c.get("context") or ""), (c.get("conclusion") or c.get("state") or "").upper(), bool(c.get("workflowName")))
              for c in pr.get("statusCheckRollup") or []]
    cla = [s for name, s, _ in checks if name.lower() == "license/cla"]
    signed_comment = any((c.get("user") or {}).get("login") == "CLAassistant" and "badge/signed" in (c.get("body") or "")
                         and "not_signed" not in (c.get("body") or "") for c in data.get("comments", {}).get(n, []))
    if not bot and not cla and signed_comment:
        notes.append("no license/cla status on the head, but the CLA assistant's comment says signed (prepare.py fetch checks the commit authors)")
    elif not bot and not cla:
        notes.append("no license/cla status on the head: the verdict needs it, or the user's cla_not_required; "
                     f"the user opens https://cla-assistant.io/check/{data.get('repo') or '<owner>/<repo>'}?pullRequest={n} "
                     "in a signed-in browser")
    elif not bot and not all(s in PASSED_STATES for s in cla):
        blocked.append("CLA: " + cla[0].lower())
    commits = (data.get("commits") or {}).get(n)
    if isinstance(commits, int) and commits > MAX_PR_COMMITS:
        blocked.append(f"{commits} commits: the branch carries history that main does not have; the author must rebase onto main")
    if pr.get("mergeable") == "CONFLICTING":
        blocked.append("merge conflict")
    elif pr.get("mergeable") != "MERGEABLE":
        notes.append("mergeable unknown: GitHub computes it when it is first asked; run the triage again")
    runs = data.get("runs", {}).get(n)
    waiting = {r.get("workflowName") for r in runs or [] if (r.get("status") or "").lower() in WAITING_RUN
               or (r.get("conclusion") or "").lower() == "action_required"}
    first = {}
    for r in runs or []:
        first.setdefault(r.get("workflowName"), r)
    unstarted = {w for w, r in first.items() if did_not_start(r)}
    wf_failing, wf_pending = workflow_states_of(data.get("workflows") or [], runs, frozenset(waiting | unstarted))
    # A check of a workflow of this repository blocks; another service's check (codecov, a bot) is for the verdict to explain.
    others = [(name, s, wf) for name, s, wf in checks if name.lower() != "license/cla"]
    failing = [name for name, s, wf in others if wf and s in FAILED_STATES and s != "ACTION_REQUIRED"] + wf_failing
    pending = [name for name, s, wf in others if wf and s not in PASSED_STATES and s not in FAILED_STATES] + wf_pending
    outside = [f"{name} {s.lower() or 'pending'}" for name, s, wf in others if not wf and s not in PASSED_STATES]
    if outside:
        notes.append("checks outside the workflows: " + ", ".join(outside[:3]) + " (the verdict needs an explanation)")
    if failing:
        blocked.append("CI failing: " + ", ".join(failing[:3]))
    if pending:
        blocked.append("CI not finished: " + ", ".join(pending[:3]))
    now = parse_time(data["fetched_at"]) if data.get("fetched_at") else None
    old = {w for w in unstarted if too_old_to_rerun(first[w], now)}
    if unstarted - old:
        notes.append("CI failed before any job started: " + ", ".join(sorted(w or "?" for w in unstarted - old))
                     + " (not the code; rerun only through prepare.py ci-approval, after the scan and the security review)")
    if old:
        notes.append(f"CI failed before any job started and GitHub cannot rerun it (older than {RERUN_DAYS} days): "
                     + ", ".join(sorted(w or "?" for w in old))
                     + " (a new run needs a push by the author, or a maintainer closes and reopens the pull request)")
    if waiting:
        notes.append("CI waits for a maintainer: " + ", ".join(sorted(w or "?" for w in waiting))
                     + " (approve only through prepare.py ci-approval, after the scan and the security review)")
    if last and last.get("state") == "CHANGES_REQUESTED" and not (pushed or commented):
        blocked.append(f"waiting on the author (round {len(counted)} requested changes)")
    # The review bot of this project (CodeRabbit): an open thread on the head that the author has not answered comes first.
    open_bot = [t for t in data.get("threads", {}).get(n) or [] if isinstance(t, dict) and not t.get("resolved")
                and not t.get("outdated") and (t.get("authors") or [None])[0] in REVIEW_BOTS
                and author not in (t.get("authors") or [])[1:]]
    if open_bot:
        blocked.append(f"waiting on the author: {len(open_bot)} open CodeRabbit thread(s) without an answer")
    files = pr.get("files") or []
    if len(files) < (pr.get("changedFiles") or 0):
        decide.append(f"file list incomplete ({len(files)} of {pr.get('changedFiles')}): treat as sensitive")
    cats = {category(f.get("path") or "") for f in files}
    sens = [f["path"] for f in files if category(f.get("path") or "") in SENSITIVE]
    if sens:
        decide.append("sensitive files (two maintainers): " + ", ".join(sens[:3]) + (" …" if len(sens) > 3 else ""))
    issues = sorted({i.get("number") for i in pr.get("closingIssuesReferences") or [] if i.get("number")})
    dups = sorted({m for i in issues for m in issue_prs.get(i, []) if m != pr["number"]})
    if dups:
        decide.append("same issue as " + ", ".join(f"#{m}" for m in dups) + ": choose one")
    limit = len(counted) >= 3 and (pushed or commented or (last or {}).get("state") == "CHANGES_REQUESTED")
    reminders = sum(1 for c in data.get("comments", {}).get(n, []) if (c.get("user") or {}).get("login") != author
                    and (REMINDER_MARKER in (c.get("body") or "") or REMINDER_MANUAL in (c.get("body") or "")))
    labels = sorted({(x.get("name") or "") for x in pr.get("labels") or []} & {"needs-cla", "stale"})
    if reminders or labels:
        notes.append(f"pr-reminders: {reminders} sent" + (f", label {', '.join(labels)}" if labels else "")
                     + ("; no answer after the reminders: a close candidate" if "stale" in labels else ""))
    if ignored:
        notes.append("review-state footers ignored (no write permission): " + ", ".join(ignored))
    if blocked:
        cls = "blocked"
    elif limit:
        cls = "limit"
    elif decide:
        cls = "decide"
    elif last and not (pushed or commented):
        cls = "reviewed"
    else:
        cls = "re-review" if last else "review"
    waiting_on = {"blocked": "CI" if all(b.startswith("CI not finished") for b in blocked) else "author", "limit": "maintainers",
                  "decide": "maintainers", "reviewed": "maintainers (merge)"}.get(cls, "us")
    return {"number": pr["number"], "title": pr.get("title") or "", "url": pr.get("url"), "author": author,
            "author_kind": "you" if author == data.get("viewer") else "bot" if bot else "fork" if pr.get("isCrossRepository") else "branch",
            "created": (pr.get("createdAt") or "")[:10], "size": f"+{pr.get('additions', 0)}/-{pr.get('deletions', 0)} in {pr.get('changedFiles', 0)}",
            "kind_auto": auto_kind(cats), "issues": issues, "round": len(counted), "pushed_after_review": pushed,
            "commented_after_review": commented, "class": cls, "blocked": blocked, "decide": decide, "notes": notes,
            "waiting_on": waiting_on, "reminders": reminders, "labels": labels,
            "reminded": any(b.startswith("CLA") for b in blocked), "us_first": cls != "blocked" and (pushed or commented)}


def triage(a: argparse.Namespace) -> int:
    data = triage_collect(a)
    issue_prs: dict = {}
    for pr in data["prs"]:
        for i in pr.get("closingIssuesReferences") or []:
            if i.get("number"):
                issue_prs.setdefault(i["number"], []).append(pr["number"])
    rows = [triage_one(pr, data, issue_prs) for pr in data["prs"]]
    rows.sort(key=lambda r: (not r["us_first"], not r["issues"], r["created"], r["number"]))
    complete, why = True, []
    if data.get("listed") and data.get("total_count") != len(data["prs"]):
        complete = False
        why.append(f"the list has {len(data['prs'])} pull requests, GitHub counts {data.get('total_count')}: "
                   f"{'missing' if (data.get('total_count') or 0) > len(data['prs']) else 'extra'} "
                   f"{abs((data.get('total_count') or 0) - len(data['prs']))}")
    out_dir = os.path.join(a.work, "triage")
    os.makedirs(out_dir, exist_ok=True)
    result = {"repo": data.get("repo"), "complete": complete, "incomplete_reasons": why, "listed": data.get("listed"),
              "total_count": data.get("total_count"), "missing": data.get("missing", []), "rows": rows}
    with open(os.path.join(out_dir, "triage.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, indent=1)
    counts = {}
    for r in rows:
        counts[r["class"]] = counts.get(r["class"], 0) + 1
    print(os.path.join(out_dir, "triage.json"))
    print(f"{len(rows)} pull requests: " + ", ".join(f"{v} {k}" for k, v in sorted(counts.items()))
          + (f"; not open or not found: {', '.join(str(m['number']) for m in result['missing'])}" if result["missing"] else ""))
    if not complete:
        print("incomplete: " + "; ".join(why))
        return 11
    return 0


def cell(text, width: int = 70) -> str:
    """Outside text in a table cell: one line, no pipes, short (C3)."""
    t = re.sub(r"\s+", " ", str(text)).replace("|", "\\|").replace("<", "&lt;").strip()
    return t if len(t) <= width else t[:width - 1] + "…"


def triage_report(a: argparse.Namespace) -> int:
    """Merge the agent's judgment into the triage. Refuse a judgment that is missing, unknown, or weaker than the facts."""
    out_dir = os.path.join(a.work, "triage")
    with open(os.path.join(out_dir, "triage.json"), encoding="utf-8") as f:
        tri = json.load(f)
    jpath = os.path.join(out_dir, "judgment.json")
    judg = {}
    if os.path.exists(jpath):
        with open(jpath, encoding="utf-8") as f:
            judg = json.load(f)
    refuse = []
    if not tri.get("complete"):
        refuse.append("the triage is incomplete (" + "; ".join(tri.get("incomplete_reasons") or []) + "): run prepare.py triage again")
    lines = []
    for r in tri["rows"]:
        j = judg.get(str(r["number"]))
        if not isinstance(j, dict):
            refuse.append(f"#{r['number']}: no judgment")
            continue
        cls, reason = j.get("class"), (j.get("reason") or "").strip()
        if cls not in CLASS_RANK:
            refuse.append(f"#{r['number']}: unknown class {cls!r}")
            continue
        if CLASS_RANK[cls] < CLASS_RANK[r["class"]]:
            refuse.append(f"#{r['number']}: {cls!r} is weaker than the facts ({r['class']}: "
                          + "; ".join(r["blocked"] + r["decide"])[:200] + ")")
        if cls != r["class"] and not reason:
            refuse.append(f"#{r['number']}: the class changes {r['class']} -> {cls} without a reason")
        concerns = [k for k, bad in (("problem", j.get("problem") == "none"), ("one_problem", j.get("one_problem") is False),
                                     ("direction", j.get("direction") == "decide")) if bad]
        for k in ("problem", "one_problem", "direction"):
            if k not in j:
                refuse.append(f"#{r['number']}: no answer for {k!r}")
        if concerns and CLASS_RANK[cls] < CLASS_RANK["decide"]:
            refuse.append(f"#{r['number']}: {', '.join(concerns)} needs decide, blocked, or close, not {cls}")
        why = "; ".join(r["blocked"] + (["round limit"] if r["class"] == "limit" else []) + r["decide"]
                        + ([reason] if reason else []) + r["notes"]) or "—"
        issue = ", ".join(f"#{i}" for i in r["issues"]) or "—"
        lines.append(f"| #{r['number']} | {cell(r['title'], 50)} | {cell(r['author'], 20)} ({r['author_kind']}) | {issue} | "
                     f"{cls} | {r['round']} | {r['kind_auto']} {r['size']} | {cell(why, 160)} | {r['waiting_on'] if cls == r['class'] else JUDGED_WAITING.get(cls, 'us')} | "
                     f"{'No action: pr-reminders follows up' if cls == 'blocked' and r.get('reminded') else CLASS_ACTION[cls]} |")
    extra = sorted(set(judg) - {str(r["number"]) for r in tri["rows"]})
    if extra:
        refuse.append("judgment for pull requests that are not in the triage: " + ", ".join(extra))
    if refuse:
        print("refused: " + "\n  ".join(refuse))
        return 12
    with open(os.path.join(out_dir, "triage.md"), "w", encoding="utf-8") as f:
        f.write(f"# Triage {tri.get('repo') or ''}\n\n"
                "| PR | Title | Author | Issue | Class | Round | Kind, size | Why | Waiting on | Recommended action |\n"
                "|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|\n" + "\n".join(lines) + "\n")
        if tri.get("missing"):
            f.write("\nNot open or not found: " + ", ".join(f"#{m['number']} ({m['state']})" for m in tri["missing"]) + "\n")
    print(os.path.join(out_dir, "triage.md"))
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("fetch", "trees", "fresh", "ci-approval", "cleanup"):
        s = sub.add_parser(name)
        s.add_argument("--pr", type=int, required=True)
        s.add_argument("--work", required=True)
        s.add_argument("--git", default=".")
        if name == "fetch":
            s.add_argument("--repo")
            s.add_argument("--fixture")
        if name in ("fresh", "ci-approval", "cleanup"):
            s.add_argument("--round-dir", required=True)
        if name == "fresh":
            s.add_argument("--approve", action="store_true", help="the post approves: the CI of the head must be green")
    s = sub.add_parser("triage")
    s.add_argument("--work", required=True)
    s.add_argument("--git", default=".")
    s.add_argument("--repo")
    s.add_argument("--fixture")
    s.add_argument("--main-ref", default="main", help="with --fixture: the ref whose workflows are expected")
    s.add_argument("--prs", type=lambda v: [int(x) for x in v.split(",") if x.strip()], help="only these numbers, comma-separated")
    s = sub.add_parser("triage-report")
    s.add_argument("--work", required=True)
    s = sub.add_parser("blind")
    s.add_argument("--round-dir", required=True)
    s = sub.add_parser("strip-outputs")
    s.add_argument("dir")
    s = sub.add_parser("check-tree")
    s.add_argument("--tree", required=True)
    a = p.parse_args(argv)
    if a.cmd == "check-tree":
        return check_tree(a)
    if a.cmd == "strip-outputs":
        if not os.path.isdir(a.dir) or os.path.islink(a.dir):
            print("0 0")
            return 0
        print(*strip_outputs(a.dir))
        return 0
    if a.cmd == "blind":
        return blind(a)
    a.work = os.path.abspath(a.work)
    if a.cmd in ("triage", "triage-report"):
        return (triage if a.cmd == "triage" else triage_report)(a)
    return {"fetch": fetch, "trees": trees, "fresh": fresh, "ci-approval": ci_approval, "cleanup": cleanup}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
