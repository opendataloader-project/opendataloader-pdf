#!/usr/bin/env python3
"""The state of an issue: the state line that each odl-fix issue post ends with, and a read-only triage.

  issue_state.py footer (--working | --waiting-on WHO[,WHO]) [--date YYYY-MM-DD] [--append FILE]
      Prints the state line. WHO is reporter, maintainers, #N (a pull request or an issue of this
      repository), or OWNER/REPO#N (an issue upstream). Only #N and OWNER/REPO#N can be combined.
      With --append, ends the draft FILE with the state line and the AI disclosure.
      Exit 2: an unknown WHO or date, or a FILE that already has a state line or the AI disclosure.
  issue_state.py triage --repo OWNER/REPO --work WORK [--issues N,N]
  issue_state.py triage --fixture DATA.json --work WORK [--issues N,N]
      Network (gh), read-only; or offline from a fixture with the same shapes (for the evals).
      Writes WORK/issue-triage/triage.json: for each open issue the facts and a class from them.
      A state line counts only if a person with write access wrote it, as the review-state footer
      of odl-review. Exit 11: the list length is not GitHub's count of open issues.
  issue_state.py triage-report --work WORK
      Local. Merges WORK/issue-triage/judgment.json (the agent's class for each issue that needs one)
      and writes WORK/issue-triage/triage.md. Exit 12: the triage is incomplete, a judge row has no
      judgment, a class is unknown, a changed class has no reason, or a judgment makes an issue that
      waits for us wait for another person (post a comment with a state line for that).
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import re
import subprocess
import sys

TEAM = {"MEMBER", "OWNER", "COLLABORATOR"}
WRITERS = {"admin", "maintain", "write"}
STALE_DAYS = 90
AI_LINE = "<sub>🤖 This is an AI-generated comment and may contain mistakes.</sub>"
AI = "---\n" + AI_LINE
REF = r"(?:[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9._-]+)?#[0-9]+"
FOOTER = re.compile(rf"^(Working on it|Waiting on: (?:reporter|maintainers|{REF}(?:, {REF})*)) · ([0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}})[ \t]*$", re.M)

# class: (whose turn, next action). The order is the order of the report.
CLASSES = {
    "recheck": ("us", "Read the new comment or the closed item. Answer, fix, or post a new state"),
    "close": ("us", "Close with a comment (from a judgment only)"),
    "new": ("us", "First answer: an analysis, a question, or a fix"),
    "judge": ("us", "Read the thread and decide the state (judgment.json)"),
    "decide": ("us", "The maintainers decide"),
    "working": ("us", "Continue the fix"),
    "backlog": ("us", "Team work"),
    "stale": ("us", f"Close candidate: no answer for more than {STALE_DAYS} days"),
    "pr": ("other", "An open pull request closes it: see the odl-review triage"),
    "waiting": ("other", "None: wait"),
}
FACT_TURN_US = {"new", "recheck", "decide", "working", "backlog", "stale"}


# ---------------------------------------------------------------- common


def parse_time(value: str) -> datetime.datetime:
    return datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))


def days_between(start: str, end: str) -> int:
    return (parse_time(end) - parse_time(start)).days


def is_bot(user) -> bool:
    user = user or {}
    return user.get("type") == "Bot" or (user.get("login") or "").endswith("[bot]")


def footer_of(body):
    """The last state line of a post: {"state": working|reporter|maintainers|refs, "waiting_on": [...]}, or None."""
    found = list(FOOTER.finditer(body or ""))
    if not found:
        return None
    line = found[-1].group(1)
    if line == "Working on it":
        return {"state": "working", "waiting_on": []}
    who = line.split(": ", 1)[1]
    if who in ("reporter", "maintainers"):
        return {"state": who, "waiting_on": [who]}
    return {"state": "refs", "waiting_on": who.split(", ")}


def split_ref(ref: str, repo: str):
    where, num = ref.split("#", 1)
    owner, name = (where or repo).split("/", 1)
    return owner, name, num


# ---------------------------------------------------------------- footer


def footer_line(working: bool, waiting_on: str | None, date: str) -> str:
    if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", date or ""):
        raise ValueError(f"date {date!r}: use YYYY-MM-DD")
    datetime.date.fromisoformat(date)
    if working:
        state = "Working on it"
    else:
        who = [w.strip() for w in (waiting_on or "").split(",")]
        if not who or any(not w for w in who):
            raise ValueError("no WHO")
        for w in who:
            if not (w in ("reporter", "maintainers") or re.fullmatch(REF, w)):
                raise ValueError(f"{w!r}: use reporter, maintainers, #N, or OWNER/REPO#N")
        if len(who) > 1 and any(w in ("reporter", "maintainers") for w in who):
            raise ValueError("reporter and maintainers stand alone; only #N and OWNER/REPO#N can be combined")
        if len(set(who)) != len(who):
            raise ValueError("a WHO is repeated")
        state = "Waiting on: " + ", ".join(who)
    line = f"{state} · {date}"
    m = FOOTER.search(line)
    if not m or m.group(0) != line:
        raise ValueError(f"not a state line: {line!r}")
    return line


def cmd_footer(a: argparse.Namespace) -> int:
    try:
        line = footer_line(a.working, a.waiting_on, a.date or datetime.date.today().isoformat())
    except ValueError as e:
        print(f"refused: {e}", file=sys.stderr)
        return 2
    if a.append:
        with open(a.append, encoding="utf-8") as f:
            text = f.read()
        if FOOTER.search(text) or AI_LINE in text:
            print(f"refused: {a.append} already has a state line or the AI disclosure", file=sys.stderr)
            return 2
        with open(a.append, "w", encoding="utf-8") as f:
            f.write(text.rstrip("\n") + "\n\n" + line + "\n\n" + AI + "\n")
    print(line)
    return 0


# ---------------------------------------------------------------- triage


def gh(args: list[str], check: bool = True):
    res = subprocess.run(["gh", *args], capture_output=True, text=True)
    if res.returncode != 0:
        if check:
            raise SystemExit(f"gh {' '.join(args[:2])} failed: {res.stderr.strip()[:300]}")
        return None
    return res.stdout


def gh_json(args: list[str], check: bool = True):
    out = gh(args, check)
    return json.loads(out) if out and out.strip() else None


ISSUE_QUERY = """query($o:String!, $n:String!, $i:Int!) { repository(owner:$o, name:$n) { issue(number:$i) {
  closedByPullRequestsReferences(first:20, includeClosedPrs:false) { nodes { number state isDraft } }
  timelineItems(last:50, itemTypes:[CROSS_REFERENCED_EVENT]) { nodes { ... on CrossReferencedEvent {
    source { ... on PullRequest { number state repository { nameWithOwner } } } } } } } } }"""


def counted_footers(comments, perms):
    """The state lines by writers, oldest first, and the logins of the other authors of state lines."""
    counted, ignored = [], set()
    for c in comments:
        f = footer_of(c.get("body"))
        if not f:
            continue
        login = (c.get("user") or {}).get("login") or "?"
        if perms.get(login) in WRITERS:
            counted.append({**f, "by": login, "at": c["created_at"]})
        else:
            ignored.add(login)
    return counted, ignored


def collect(a: argparse.Namespace) -> dict:
    """The data of the triage: from GitHub, or from a fixture with the same shapes (for the evals)."""
    if a.fixture:
        with open(a.fixture, encoding="utf-8") as f:
            data = json.load(f)
        data.setdefault("missing", [])
        if a.issues:
            want = set(a.issues)
            data["missing"] += [{"number": n, "state": "not found"} for n in sorted(want - {i["number"] for i in data["issues"]})]
            data["issues"] = [i for i in data["issues"] if i["number"] in want]
        data["listed"] = not a.issues
        return data
    if not a.repo or not re.fullmatch(r"[A-Za-z0-9-]+/[A-Za-z0-9._-]+", a.repo):
        raise SystemExit("triage needs --repo OWNER/REPO or --fixture")
    repo = a.repo
    owner, name = repo.split("/", 1)
    data = {"repo": repo, "listed": not a.issues, "issues": [], "missing": [], "comments": {}, "closing_prs": {},
            "mentioned_prs": {}, "permissions": {}, "refs": {},
            "fetched_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
    if a.issues:
        for n in a.issues:
            i = gh_json(["api", f"repos/{repo}/issues/{n}"], check=False)
            if not i or i.get("state") != "open" or "pull_request" in i:
                data["missing"].append({"number": n, "state": (i or {}).get("state") or "not found"})
            else:
                data["issues"].append(i)
    else:
        pages = gh_json(["api", f"repos/{repo}/issues?state=open&per_page=100", "--paginate", "--slurp"]) or []
        data["issues"] = [i for page in pages for i in page if "pull_request" not in i]
        data["total_count"] = gh_json(["api", f"search/issues?q=repo:{repo}+is:issue+is:open&per_page=1", "--jq", ".total_count"])
    for i in data["issues"]:
        n = str(i["number"])
        pages = gh_json(["api", f"repos/{repo}/issues/{n}/comments?per_page=100", "--paginate", "--slurp"]) or []
        data["comments"][n] = [c for page in pages for c in page]
        q = gh_json(["api", "graphql", "-f", f"query={ISSUE_QUERY}", "-F", f"o={owner}", "-F", f"n={name}", "-F", f"i={n}"],
                    check=False) or {}
        issue_node = ((q.get("data") or {}).get("repository") or {}).get("issue") or {}
        data["closing_prs"][n] = (issue_node.get("closedByPullRequestsReferences") or {}).get("nodes") or []
        sources = [(e or {}).get("source") or {} for e in (issue_node.get("timelineItems") or {}).get("nodes") or []]
        data["mentioned_prs"][n] = sorted({s["number"] for s in sources if s.get("number") and s.get("state") == "OPEN"
                                           and (s.get("repository") or {}).get("nameWithOwner") == repo})
        for c in data["comments"][n]:
            login = (c.get("user") or {}).get("login")
            if login and footer_of(c.get("body")) and login not in data["permissions"]:
                data["permissions"][login] = (gh(["api", f"repos/{repo}/collaborators/{login}/permission", "--jq", ".permission"],
                                                 check=False) or "").strip() or "unknown"
    for cs in data["comments"].values():
        for f in counted_footers(cs, data["permissions"])[0]:
            for ref in f["waiting_on"] if f["state"] == "refs" else []:
                if ref not in data["refs"]:
                    o, r, num = split_ref(ref, repo)
                    data["refs"][ref] = (gh(["api", f"repos/{o}/{r}/issues/{num}", "--jq", ".state"], check=False) or "").strip() or "unknown"
    return data


def triage_one(issue: dict, data: dict) -> dict:
    """The class of one issue from facts only. A judgment can change it only as triage-report allows."""
    n = str(issue["number"])
    user = issue.get("user") or {}
    team_issue = issue.get("author_association") in TEAM
    comments = sorted(data.get("comments", {}).get(n, []), key=lambda c: c.get("created_at") or "")
    people = [c for c in comments if not is_bot(c.get("user"))]
    team_c = [c for c in people if c.get("author_association") in TEAM]
    outside = [c for c in people if c.get("author_association") not in TEAM]
    counted, ignored = counted_footers(comments, data.get("permissions", {}))
    last = counted[-1] if counted else None
    anchors = [t for t in ((last or {}).get("at"), team_c[-1]["created_at"] if team_c else None,
                           issue.get("created_at") if team_issue else None) if t]
    anchor = max(anchors) if anchors else None
    after = [{"who": (c.get("user") or {}).get("login"), "at": c["created_at"]} for c in outside if anchor and c["created_at"] > anchor]
    prs = [p for p in data.get("closing_prs", {}).get(n, []) if (p.get("state") or "").upper() == "OPEN"]
    mentioned = [m for m in data.get("mentioned_prs", {}).get(n, []) if m not in {p["number"] for p in prs}]
    notes, waiting_on = [], []
    if ignored:
        notes.append("state lines ignored (no write access): " + ", ".join(sorted(ignored)))
    if mentioned:
        notes.append("open pull requests mention it, but do not close it: " + ", ".join(f"#{m}" for m in mentioned))
    if after:
        cls = "recheck"
    elif prs:
        cls, waiting_on = "pr", [f"#{p['number']}" for p in prs]
    elif not team_issue and not team_c:
        cls = "new"
    elif last:
        later = [c for c in team_c if c["created_at"] > last["at"] and not footer_of(c.get("body"))]
        if later:
            cls = "judge"
            notes.append("a team comment after the last state line has no state line")
        elif last["state"] == "working":
            cls = "working"
        elif last["state"] == "maintainers":
            cls, waiting_on = "decide", ["maintainers"]
        elif last["state"] == "reporter":
            age = days_between(last["at"], data["fetched_at"])
            cls, waiting_on = ("stale" if age > STALE_DAYS else "waiting"), ["reporter"]
            if cls == "stale":
                notes.append(f"waits on the reporter for {age} days")
        else:
            states = {r: data.get("refs", {}).get(r, "unknown") for r in last["waiting_on"]}
            waiting_on = [r for r, s in states.items() if s != "closed"]
            closed = [r for r, s in states.items() if s == "closed"]
            unknown = [r for r, s in states.items() if s not in ("open", "closed")]
            cls = "waiting" if waiting_on else "recheck"
            if closed:
                notes.append("closed now: " + ", ".join(closed))
            if unknown:
                notes.append("state unknown, counted as open: " + ", ".join(unknown))
    elif team_issue:
        cls = "backlog"
    else:
        cls = "judge"
        notes.append("the team answered, but no state line counts")
    times = [c.get("created_at") for c in comments if c.get("created_at")] + [issue.get("created_at") or ""]
    return {"number": issue["number"], "title": issue.get("title") or "", "url": issue.get("html_url"),
            "author": user.get("login"), "author_kind": "bot" if is_bot(user) else "team" if team_issue else "external",
            "created": (issue.get("created_at") or "")[:10], "last_activity": max(times)[:10], "class": cls,
            "turn": CLASSES[cls][0], "waiting_on": waiting_on, "footer": last, "after": after,
            "closing_prs": [p["number"] for p in prs], "mentioned_prs": mentioned, "notes": notes}


def cmd_triage(a: argparse.Namespace) -> int:
    data = collect(a)
    rows = [triage_one(i, data) for i in data["issues"]]
    rows.sort(key=lambda r: (list(CLASSES).index(r["class"]), r["created"], r["number"]))
    complete, why = True, []
    if data.get("listed") and data.get("total_count") != len(data["issues"]):
        complete = False
        why.append(f"the list has {len(data['issues'])} issues, GitHub counts {data.get('total_count')}")
    out = os.path.join(a.work, "issue-triage")
    os.makedirs(out, exist_ok=True)
    result = {"repo": data.get("repo"), "fetched_at": data.get("fetched_at"), "complete": complete, "incomplete_reasons": why,
              "listed": data.get("listed"), "total_count": data.get("total_count"), "missing": data.get("missing", []), "rows": rows}
    with open(os.path.join(out, "triage.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, indent=1)
    counts = {}
    for r in rows:
        counts[r["class"]] = counts.get(r["class"], 0) + 1
    print(os.path.join(out, "triage.json"))
    print(f"{len(rows)} issues: " + ", ".join(f"{counts[k]} {k}" for k in CLASSES if k in counts)
          + (f"; not open or not found: {', '.join(str(m['number']) for m in result['missing'])}" if result["missing"] else ""))
    if not complete:
        print("incomplete: " + "; ".join(why))
        return 11
    return 0


# ---------------------------------------------------------------- report


def cell(text, width: int = 70) -> str:
    text = re.sub(r"\s+", " ", str(text or "")).replace("|", "\\|").strip()
    return text if len(text) <= width else text[: width - 1] + "…"


def cmd_report(a: argparse.Namespace) -> int:
    out = os.path.join(a.work, "issue-triage")
    with open(os.path.join(out, "triage.json"), encoding="utf-8") as f:
        tri = json.load(f)
    path = os.path.join(out, "judgment.json")
    judgment = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            judgment = json.load(f)
    errors = []
    if not tri.get("complete"):
        errors.append("the triage is incomplete: " + "; ".join(tri.get("incomplete_reasons") or []))
    rows = {str(r["number"]): r for r in tri["rows"]}
    for key in judgment:
        if key not in rows:
            errors.append(f"#{key}: not in the triage")
    final = []
    for key, r in rows.items():
        j = judgment.get(key) or {}
        cls, reason = j.get("class") or r["class"], (j.get("reason") or "").strip()
        if r["class"] == "judge" and not j:
            errors.append(f"#{key}: judge needs a judgment (class and reason)")
        elif cls not in CLASSES or cls == "judge" and j:
            errors.append(f"#{key}: unknown class {cls!r}")
        elif cls != r["class"] and not reason:
            errors.append(f"#{key}: a changed class needs a reason")
        elif r["class"] in FACT_TURN_US and CLASSES[cls][0] == "other":
            errors.append(f"#{key}: {r['class']} waits for us; to make it wait, post a comment with a state line")
        final.append({**r, "final": cls, "reason": reason})
    if errors:
        for e in errors:
            print(f"error: {e}")
        return 12
    final.sort(key=lambda r: (list(CLASSES).index(r["final"]), r["created"], r["number"]))
    us = [r for r in final if CLASSES[r["final"]][0] == "us"]
    lines = [f"# Issue triage: {tri.get('repo')}", "",
             f"Fetched {tri.get('fetched_at')}. {len(final)} open issues: {len(us)} wait for us, {len(final) - len(us)} wait for others.", "",
             "| Issue | Title | Class | Turn | Waiting on | Last state | Next | Notes |",
             "|:--|:--|:--|:--|:--|:--|:--|:--|"]
    for r in final:
        f = r.get("footer")
        state = f"{f['state']} ({f['at'][:10]}, {f['by']})" if f else "none"
        cls = r["final"] if r["final"] == r["class"] else f"{r['final']} (fact: {r['class']})"
        notes = ([r["reason"]] if r["reason"] else []) + r["notes"]
        if r["class"] == "judge":
            notes.append("no state line: add one with the next post")
        lines.append(f"| #{r['number']} | {cell(r['title'])} | {cls} | {CLASSES[r['final']][0]} | {cell(', '.join(r['waiting_on']), 40)} "
                     f"| {cell(state, 40)} | {cell(CLASSES[r['final']][1], 60)} | {cell('; '.join(notes), 120)} |")
    with open(os.path.join(out, "triage.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(os.path.join(out, "triage.md"))
    return 0


# ---------------------------------------------------------------- CLI


def numbers(text: str) -> list[int]:
    if not re.fullmatch(r"[0-9]+(,[0-9]+)*", text or ""):
        raise argparse.ArgumentTypeError("use N,N")
    return [int(x) for x in text.split(",")]


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("footer")
    g = f.add_mutually_exclusive_group(required=True)
    g.add_argument("--working", action="store_true")
    g.add_argument("--waiting-on")
    f.add_argument("--date")
    f.add_argument("--append")
    t = sub.add_parser("triage")
    t.add_argument("--repo")
    t.add_argument("--fixture")
    t.add_argument("--work", required=True)
    t.add_argument("--issues", type=numbers)
    r = sub.add_parser("triage-report")
    r.add_argument("--work", required=True)
    a = ap.parse_args(argv)
    return {"footer": cmd_footer, "triage": cmd_triage, "triage-report": cmd_report}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
