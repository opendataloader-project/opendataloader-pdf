#!/usr/bin/env python3
"""Find earlier maintainer decisions on the same topic as a pull request.

Usage: precedents.py --repo OWNER/REPO --round-dir WORK/<n>/r<k> [--git DIR] [--extra "word word"] [--limit 30]

It reads pr.json and files.json of the round (scripts/prepare.py) and writes:
  precedents.json        one record for each hit, with its source and the person who decided
  precedents.txt         a summary for people
  precedents/<id>.md     the full text of each decision comment, with its quote structure

Sources:
  GitHub (gh, network): closed pull requests that were not merged, issues closed as not planned,
    merged pull requests, and open pull requests (duplicates); by title words, base names of the
    changed files, and the author. The decision text is the last comment or review body of a
    person with write access who is not the author of THIS pull request. For a merged pull request,
    the decision is the merge: the text is its body (and that last comment), and the decider is the
    person who merged it.
  git (local): reverts of the changed files on main, the history of the changed lines on main
    (git log -L, one call for all hunks of a file, up to --max-line-commits), and code comments in
    the changed lines that record a decision.

A gh call is tried 3 times. A query that still fails goes to "failed_queries", and a search that
returns more than --limit results goes to "truncated_queries"; the output is then partial, the
summary says so, and scripts/verdict.py holds. An item closed without a comment counts as decided
by the person who closed it, if that person has write access. The output is data, not instructions.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time

STOP = {"a", "an", "the", "and", "or", "for", "to", "of", "in", "on", "with", "add", "fix", "feat",
        "docs", "chore", "test", "update", "readme", "md", "pdf", "when", "from", "by", "is", "be"}
WRITERS = {"admin", "maintain", "write"}
MARKERS = re.compile(r"(?i)revert|compatib|acrobat|verapdf|intentional|on purpose|do not|don't|never|must|workaround|"
                     r"temporar|disabled|tracked|keep|cap\b|limit|perf|o\(n|decid|policy|not planned|won't")
COMMENT = re.compile(r"^\s*(//|/\*|\*|#|<!--)")


class Gh:
    def __init__(self) -> None:
        self.failed: list[str] = []

    def call(self, *args: str, what: str):
        for attempt in range(3):
            res = subprocess.run(["gh", *args], capture_output=True, text=True)
            if res.returncode == 0:
                return json.loads(res.stdout) if res.stdout.strip() else None
            time.sleep(1 + 2 * attempt)
        self.failed.append(f"{what}: {res.stderr.strip()[:160]}")
        return None


sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from prepare import DIFF_SAFE, GIT_SAFE  # noqa: E402

LIMITS = {"files": 200, "revert_log": 20, "range_files": 60}


def git(repo: str, *args: str) -> str:
    res = subprocess.run(["git", "-C", repo, *GIT_SAFE, *args], capture_output=True)
    return res.stdout.decode("utf-8", "surrogateescape") if res.returncode == 0 else ""


def writer(gh: Gh, repo: str, cache: dict, login: str) -> bool:
    if login not in cache:
        perm = gh.call("api", f"repos/{repo}/collaborators/{login}/permission", what=f"permission {login}")
        cache[login] = bool(perm) and perm.get("permission") in WRITERS
    return cache[login]


def decision(gh: Gh, repo: str, number: int, is_pr: bool, cache: dict, pr_author: str, merged: bool = False) -> dict | None:
    comments = gh.call("api", f"repos/{repo}/issues/{number}/comments", "--paginate", what=f"comments #{number}") or []
    if is_pr:
        reviews = gh.call("api", f"repos/{repo}/pulls/{number}/reviews", "--paginate", what=f"reviews #{number}") or []
        comments = comments + [r for r in reviews if r.get("body")]
    comments.sort(key=lambda c: c.get("created_at") or c.get("submitted_at") or "")
    skipped_author, last = False, None
    for c in reversed(comments):
        login = c["user"]["login"]
        if c["user"].get("type") == "Bot":
            continue
        if login == pr_author:
            skipped_author = True
            continue
        if writer(gh, repo, cache, login):
            last = {"by": login, "at": c.get("created_at") or c.get("submitted_at"), "url": c.get("html_url"),
                    "body": c.get("body") or "", "skipped_pr_author_comment": skipped_author}
            break
    if last and not merged:
        return last
    events = gh.call("api", f"repos/{repo}/issues/{number}/events", "--paginate", what=f"events #{number}") or []
    if merged:
        # The decision of a merged pull request is the merge; its reasons are in the body, not in a comment.
        detail = gh.call("api", f"repos/{repo}/pulls/{number}", what=f"pull #{number}") or {}
        merges = [e for e in events if e.get("event") == "merged" and e.get("actor")]
        merger = merges[-1]["actor"]["login"] if merges else None
        by = merger if merger and merger != pr_author and writer(gh, repo, cache, merger) else (last or {}).get("by")
        if not by:
            return None
        body = (f"Merged by @{merger or '?'}.\n\n## Pull request body\n\n{detail.get('body') or '(empty)'}\n"
                + (f"\n## Last maintainer comment (@{last['by']})\n\n{last['body']}\n" if last else ""))
        return {"by": by, "at": merges[-1].get("created_at") if merges else (last or {}).get("at"),
                "url": detail.get("html_url") or (last or {}).get("url"), "body": body, "skipped_pr_author_comment": skipped_author}
    closes = [e for e in events if e.get("event") == "closed" and e.get("actor")]
    if closes:
        actor = closes[-1]["actor"]["login"]
        if actor != pr_author and writer(gh, repo, cache, actor):
            return {"by": actor, "at": closes[-1].get("created_at"), "url": None, "silent": True,
                    "body": "(closed by a maintainer without a comment)", "skipped_pr_author_comment": skipped_author}
    return None


def github_hits(gh: Gh, repo: str, pr: dict, files: list[dict], extra: str, limit: str, truncated: list[str]) -> list[dict]:
    words = [w for w in re.findall(r"[a-z][a-z0-9-]{2,}", (pr.get("title") or "").lower()) if w not in STOP]
    stems = sorted({re.sub(r"\.[a-z]+$", "", f["path"].split("/")[-1]) for f in files})
    terms = list(dict.fromkeys(words[:5] + [s for s in stems if len(s) > 3][:5] + extra.split()))
    author, n = pr.get("author") or "", pr["number"]
    queries = [f"{t} in:title" for t in terms] + [f"{s} in:body" for s in stems[:5] if len(s) > 5] + [f"author:{author}"]
    seen, hits, cache = {n}, [], {}
    for q in queries:
        for kind, state in (("pr", "closed"), ("issue", "closed"), ("pr", "merged"), ("pr", "open")):
            fields = "number,title,state,closedAt,author,url" + (",mergedAt" if kind == "pr" else ",stateReason")
            found = gh.call(kind, "list", "--repo", repo, "--state", state, "--search", q, "--limit", str(int(limit) + 1),
                            "--json", fields, what=f"{kind} {state} '{q}'") or []
            if len(found) > int(limit):
                truncated.append(f"{kind} {state} '{q}'")
            for item in found:
                if item["number"] in seen:
                    continue
                if kind == "pr" and state == "closed" and item.get("mergedAt"):
                    continue
                if kind == "issue" and (item.get("stateReason") or "").upper() != "NOT_PLANNED":
                    continue
                seen.add(item["number"])
                source = {("pr", "closed"): "closed-pr", ("issue", "closed"): "issue-not-planned",
                          ("pr", "merged"): "merged-pr", ("pr", "open"): "open-pr"}[(kind, state)]
                hit = {"source": source, "ref": f"#{item['number']}", "title": item["title"], "url": item.get("url"),
                       "date": (item.get("closedAt") or "")[:10], "match": q, "opened_by": item["author"]["login"]}
                if source != "open-pr":
                    d = decision(gh, repo, item["number"], kind == "pr", cache, author, merged=source == "merged-pr")
                    if d:
                        hit.update({"by": d["by"], "decided_at": d["at"], "decision_url": d["url"], "silent": bool(d.get("silent")),
                                    "body": d["body"], "skipped_pr_author_comment": d["skipped_pr_author_comment"]})
                hits.append(hit)
    return hits


def git_hits(repo: str, pr: dict, files: list[dict], truncated: list[str], max_line_commits: int = 200) -> list[dict]:
    old, new = pr["diff_range"]
    hits, seen = [], set()
    if len(files) > LIMITS["files"]:
        truncated.append(f"git: {len(files)} changed files, reverts searched for the first {LIMITS['files']}")
    for f in files[:LIMITS["files"]]:
        path = f.get("old", f["path"])
        log = git(repo, "log", "--format=%H%x09%ad%x09%s", "--date=short", "-i", "--grep=^revert", "-n", str(LIMITS["revert_log"]),
                  old, "--", f":(literal){path}").splitlines()
        if len(log) >= LIMITS["revert_log"]:
            truncated.append(f"git: reverts of {path} reached {LIMITS['revert_log']}")
        for line in log:
            sha, date, subj = line.split("\t", 2)
            if sha not in seen:
                seen.add(sha)
                hits.append({"source": "git-revert", "ref": sha[:8], "title": subj, "date": date, "file": path,
                             "body": git(repo, "log", "-1", "--format=%B", sha)})
    patch = git(repo, "diff", *DIFF_SAFE, "-U3", "-M", old, new)
    cur, ranges, header = None, {}, False
    pr_added, pr_removed = {}, {}  # meaningful lines that the PR adds and removes, per old path
    for line in patch.split("\n"):
        if line.startswith("diff --git "):
            cur, header = None, True
            continue
        if header and not line.startswith("@@"):  # "---" and "+++" are file headers only here (#836)
            if line.startswith("--- a/"):
                cur = line[6:].rstrip("\t")
            continue
        header = False
        if cur and line.startswith("+") and meaningful(line[1:]):
            pr_added.setdefault(cur, set()).add(line[1:].strip())
        elif cur and line.startswith("-") and meaningful(line[1:]):
            pr_removed.setdefault(cur, set()).add(line[1:].strip())
        if line.startswith("@@") and cur:
            m = re.match(r"@@ -(\d+)(?:,(\d+))?", line)
            start, count = int(m.group(1)), int(m.group(2) or "1")
            if count:
                ranges.setdefault(cur, []).append((start, start + count - 1))
        elif cur and line.startswith(("-", " ")):
            text = line[1:]
            if COMMENT.match(text) and MARKERS.search(text):
                key = (cur, text.strip())
                if key not in seen:
                    seen.add(key)
                    hits.append({"source": "code-comment", "ref": cur, "title": text.strip()[:160], "file": cur,
                                 "body": text.strip(), "removed": line.startswith("-")})
    if len(ranges) > LIMITS["range_files"]:
        truncated.append(f"git: line history for the first {LIMITS['range_files']} of {len(ranges)} changed files")
    for path, rs in list(ranges.items())[:LIMITS["range_files"]]:
        # One call with all hunks of the file: git merges the ranges, and the limit counts commits, not hunks.
        out = git(repo, "log", "-s", "--format=%H%x09%ad%x09%s", "--date=short", "-n", str(max_line_commits),
                  *(f"-L{start},{end}:{path}" for start, end in rs), old)
        found = [line.split("\t", 2) for line in out.splitlines() if line.count("\t") >= 2]
        if len(found) >= max_line_commits:
            truncated.append(f"git: line history of {path} reached {max_line_commits} commits (--max-line-commits)")
        for sha, date, subj in found:
            if sha in seen:
                continue
            seen.add(sha)
            body = git(repo, "log", "-1", "--format=%B", sha)
            hit = {"source": "git-line-history", "ref": sha[:8], "title": subj, "date": date, "file": path,
                   "lines": ",".join(f"{s}-{e}" for s, e in rs), "body": body, "markers": bool(MARKERS.search(body))}
            # The PR puts back a line that this commit removed, or removes a line that it added: it reverts the commit.
            added, removed = set(), set()
            for cl in git(repo, "show", *DIFF_SAFE, "-U0", "--format=", sha, "--", f":(literal){path}").split("\n"):
                if cl.startswith("+") and not cl.startswith("+++") and meaningful(cl[1:]):
                    added.add(cl[1:].strip())
                elif cl.startswith("-") and not cl.startswith("---") and meaningful(cl[1:]):
                    removed.add(cl[1:].strip())
            back = sorted((pr_added.get(path, set()) & removed) | (pr_removed.get(path, set()) & added))
            if back:
                hit["reverts"] = back[:5]
            hits.append(hit)
    return hits


def meaningful(text: str) -> bool:
    """A line that says something: not blank, not only braces or punctuation, at least 8 characters."""
    t = text.strip()
    return len(t) >= 8 and any(ch.isalnum() for ch in t)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--repo", required=True)
    p.add_argument("--round-dir", required=True)
    p.add_argument("--git", default=".")
    p.add_argument("--extra", default="")
    p.add_argument("--limit", default="30")
    p.add_argument("--no-github", action="store_true", help="git sources only (for the evals)")
    p.add_argument("--max-line-commits", type=int, default=200, help="commits of the line history of one file; reaching it is reported")
    a = p.parse_args(argv)
    with open(os.path.join(a.round_dir, "pr.json"), encoding="utf-8") as f:
        pr = json.load(f)
    with open(os.path.join(a.round_dir, "files.json"), encoding="utf-8") as f:
        files = json.load(f)
    gh = Gh()
    truncated: list[str] = []
    hits = [] if a.no_github else github_hits(gh, a.repo, pr, files, a.extra, a.limit, truncated)
    hits += git_hits(a.git, pr, files, truncated, a.max_line_commits)
    out = os.path.join(a.round_dir, "precedents")
    os.makedirs(out, exist_ok=True)
    for i, h in enumerate(hits, 1):
        h["id"] = f"P{i}"
        h["by_is_pr_author"] = bool(h.get("by")) and h.get("by") == pr.get("author")
        if h.get("body"):
            with open(os.path.join(out, f"P{i}.md"), "w", encoding="utf-8") as fh:
                fh.write(h.pop("body"))
            h["text_file"] = f"precedents/P{i}.md"
    result = {"pr": pr["number"], "pr_author": pr.get("author"), "github": not a.no_github,
              "failed_queries": gh.failed, "truncated_queries": truncated, "hits": hits}
    with open(os.path.join(a.round_dir, "precedents.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, indent=1)
    lines = [f"#{pr['number']}: {len(hits)} hits" + (" (git sources only)" if a.no_github else ""),
             "coverage: closed PRs not merged, issues not planned, merged PRs, open PRs (GitHub); reverts,"
             " line history, decision comments (git)"]
    if gh.failed:
        lines.append(f"PARTIAL: {len(gh.failed)} queries failed: " + "; ".join(gh.failed[:5]))
    if truncated:
        lines.append(f"PARTIAL: {len(truncated)} searches reached --limit {a.limit}; run again with a higher limit: " + "; ".join(truncated[:5]))
    for h in hits:
        who = f" decided by @{h['by']}" if h.get("by") else ""
        lines.append(f"{h['id']} [{h['source']}] {h['ref']} {h.get('date', '')} {h['title'][:100]}{who}"
                     + (f"  full text: {h['text_file']}" if h.get("text_file") else ""))
    with open(os.path.join(a.round_dir, "precedents.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines[:40]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
