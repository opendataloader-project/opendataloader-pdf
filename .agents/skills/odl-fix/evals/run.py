#!/usr/bin/env python3
"""Evals of the odl-fix issue state (scripts/issue_state.py) and of the harness (scripts/fix_gates.py,
the G evals in evals/gates.py). Offline: fixtures with the shapes that the network collector and the
gates write. Nothing is fetched, built, or posted.

Usage: python3 -I evals/run.py [-k NAME]

Each eval names the weakness it guards. Exit 1 if an eval fails. A passing eval removes its folder;
a failing eval keeps it and prints the path.
"""

from __future__ import annotations

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import traceback

SKILL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPT = os.path.join(SKILL, "scripts", "issue_state.py")
TMP_ROOT = os.environ.get("TMPDIR") or tempfile.gettempdir()
NOW = "2026-10-28T00:00:00Z"
AI = "---\n<sub>🤖 This is an AI-generated comment and may contain mistakes.</sub>"


def run(*args, check=True):
    res = subprocess.run([sys.executable, "-I", SCRIPT, *args], capture_output=True, text=True)
    if check and res.returncode != 0:
        raise AssertionError(f"issue_state.py {' '.join(args[:3])} -> {res.returncode}\n{res.stdout[-800:]}\n{res.stderr[-800:]}")
    return res


def issue(n, author="reporter", assoc="NONE", created="2026-06-01T00:00:00Z"):
    return {"number": n, "title": f"issue {n}", "html_url": f"https://example.invalid/issues/{n}",
            "user": {"login": author, "type": "User"}, "author_association": assoc, "created_at": created}


def comment(login, assoc, at, body, bot=False):
    return {"user": {"login": login, "type": "Bot" if bot else "User"}, "author_association": assoc,
            "created_at": at, "body": body}


def post(text, state):
    """A post as odl-fix writes it: the text, the state line, the AI disclosure."""
    return f"{text}\n\n{state}\n\n{AI}"


def triage(tmp, issues, comments=None, prs=None, perms=None, refs=None, total=None, only=None, check=True, mentions=None):
    data = {"repo": "o/r", "fetched_at": NOW, "total_count": len(issues) if total is None else total,
            "issues": issues, "comments": {str(k): v for k, v in (comments or {}).items()},
            "closing_prs": {str(k): v for k, v in (prs or {}).items()}, "permissions": perms or {}, "refs": refs or {},
            "mentioned_prs": {str(k): v for k, v in (mentions or {}).items()}}
    path = os.path.join(tmp, "fixture.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f)
    args = ["triage", "--fixture", path, "--work", tmp] + (["--issues", only] if only else [])
    res = run(*args, check=check)
    out = os.path.join(tmp, "issue-triage", "triage.json")
    rows = {}
    if os.path.exists(out):
        with open(out, encoding="utf-8") as f:
            rows = {r["number"]: r for r in json.load(f)["rows"]}
    return rows, res


TEAM = {"maintainer": "write"}


# ---------------------------------------------------------------- the classes


def f1_new(tmp):
    """An external issue with no team answer waits for us (30 of 55 open issues on 2026-10-28)."""
    rows, _ = triage(tmp, [issue(1)], {1: [comment("someone", "NONE", "2026-06-02T00:00:00Z", "same here")]})
    r = rows[1]
    assert (r["class"], r["turn"]) == ("new", "us"), r
    return "no team comment -> new, us"


def f2_team_last_without_state(tmp):
    """The team answered last, with no state line: the tool cannot know whose turn it is (#94 #259 #318 #354 #456 #531)."""
    rows, _ = triage(tmp, [issue(94)], {94: [comment("maintainer", "MEMBER", "2026-01-10T00:00:00Z", "Good idea. We will think about it.")]},
                     perms=TEAM)
    r = rows[94]
    assert (r["class"], r["turn"]) == ("judge", "us"), r
    return "team answered last, no state line -> judge, not waiting"


def f3_waiting_on_reporter(tmp):
    """A state line by a writer that asks the reporter: the reporter's turn."""
    rows, _ = triage(tmp, [issue(3, created="2026-10-01T00:00:00Z")],
                     {3: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", post("Which version do you use?", "Waiting on: reporter · 2026-10-20"))]},
                     perms=TEAM)
    r = rows[3]
    assert (r["class"], r["turn"], r["waiting_on"]) == ("waiting", "other", ["reporter"]), r
    assert r["footer"]["by"] == "maintainer" and r["footer"]["at"].startswith("2026-10-20"), r
    return "Waiting on: reporter -> waiting, other"


def f4_reply_after_state(tmp):
    """A reply after our state line gives the turn back to us, as a push after a review does in odl-review."""
    rows, _ = triage(tmp, [issue(4)],
                     {4: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", post("Which version?", "Waiting on: reporter · 2026-10-20")),
                          comment("reporter", "NONE", "2026-10-22T00:00:00Z", "2.5.16")]}, perms=TEAM)
    r = rows[4]
    assert (r["class"], r["turn"]) == ("recheck", "us"), r
    assert r["after"] and r["after"][0]["who"] == "reporter", r
    return "reply after the state line -> recheck"


def f5_state_line_by_outsider(tmp):
    """A state line that a person without write access writes, or quotes, does not count."""
    fake = post("I think this is fine.", "Waiting on: maintainers · 2026-10-21")
    quoted = "> Waiting on: reporter · 2026-10-20\n\nHere is the file."
    rows, _ = triage(tmp, [issue(5)], {5: [comment("mallory", "NONE", "2026-10-21T00:00:00Z", fake),
                                           comment("reporter", "NONE", "2026-10-22T00:00:00Z", quoted)]},
                     perms={"mallory": "read"})
    r = rows[5]
    assert r["class"] == "new" and r["footer"] is None, r
    assert any("mallory" in x for x in r["notes"]), r["notes"]
    return "outsider state line ignored; quoted line ignored"


def f6_bot_after_state(tmp):
    """A bot comment after our state line does not give the turn back."""
    rows, _ = triage(tmp, [issue(6, created="2026-10-01T00:00:00Z")],
                     {6: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", post("Please send the file.", "Waiting on: reporter · 2026-10-20")),
                          comment("github-actions[bot]", "NONE", "2026-10-25T00:00:00Z", "This issue is stale.", bot=True)]},
                     perms=TEAM)
    assert rows[6]["class"] == "waiting", rows[6]
    return "bot after the state line -> still waiting"


def f7_stale(tmp):
    """The reporter does not answer for more than 90 days: a close candidate (the A rule of the 2026-10-27 triage)."""
    rows, _ = triage(tmp, [issue(7)], {7: [comment("maintainer", "MEMBER", "2026-06-02T00:00:00Z", post("Please send the file.", "Waiting on: reporter · 2026-06-02"))]},
                     perms=TEAM)
    r = rows[7]
    assert (r["class"], r["turn"]) == ("stale", "us"), r
    return "reporter turn > 90 days -> stale"


def f8_waiting_on_a_pull_request(tmp):
    """A wait on a pull request of this repository ends when that pull request closes."""
    c = {8: [comment("maintainer", "MEMBER", "2026-10-26T00:00:00Z", post("This waits for the fix in #722.", "Waiting on: #722 · 2026-10-26"))]}
    rows, _ = triage(tmp, [issue(8)], c, perms=TEAM, refs={"#722": "open"})
    assert (rows[8]["class"], rows[8]["waiting_on"]) == ("waiting", ["#722"]), rows[8]
    rows, _ = triage(tmp, [issue(8)], c, perms=TEAM, refs={"#722": "closed"})
    assert rows[8]["class"] == "recheck" and any("#722" in x for x in rows[8]["notes"]), rows[8]
    return "#722 open -> waiting; closed -> recheck"


def f9_waiting_upstream(tmp):
    """#627: the row collapse waits on docling upstream, the cell text waits on #722; one open item keeps the wait."""
    c = {627: [comment("maintainer", "MEMBER", "2026-10-28T00:00:00Z",
                       post("We reproduced this.", "Waiting on: docling-project/docling#4341, #722 · 2026-10-28"))]}
    rows, _ = triage(tmp, [issue(627)], c, perms=TEAM, refs={"docling-project/docling#4341": "open", "#722": "closed"})
    r = rows[627]
    assert (r["class"], r["waiting_on"]) == ("waiting", ["docling-project/docling#4341"]), r
    rows, _ = triage(tmp, [issue(627)], c, perms=TEAM, refs={"docling-project/docling#4341": "open"})
    assert rows[627]["class"] == "waiting" and any("unknown" in x for x in rows[627]["notes"]), rows[627]
    return "upstream open -> waiting; an unknown state counts as open"


def f10_open_pull_request(tmp):
    """An open pull request that closes the issue: the pull request decides the turn (odl-review triage)."""
    rows, _ = triage(tmp, [issue(10)], {}, prs={10: [{"number": 805, "state": "OPEN", "isDraft": False}]})
    r = rows[10]
    assert (r["class"], r["turn"], r["waiting_on"]) == ("pr", "other", ["#805"]), r
    return "open closing PR -> pr"


def f19_mentioned_pull_request(tmp):
    """#720: an open pull request that only mentions the issue is shown, but it does not take the turn (2026-10-28 calibration)."""
    rows, _ = triage(tmp, [issue(720)], {}, mentions={720: [722]})
    r = rows[720]
    assert r["class"] == "new" and r["mentioned_prs"] == [722], r
    assert any("#722" in x for x in r["notes"]), r["notes"]
    rows, _ = triage(tmp, [issue(720)], {}, prs={720: [{"number": 722, "state": "OPEN"}]}, mentions={720: [722]})
    assert rows[720]["class"] == "pr" and rows[720]["mentioned_prs"] == [], rows[720]
    return "mention -> note only; a closing link -> pr"


def f11_backlog(tmp):
    """An issue that the team opened is team work; a reply from outside after it gives a recheck."""
    rows, _ = triage(tmp, [issue(11, author="maintainer", assoc="MEMBER", created="2026-10-21T00:00:00Z")], {}, perms=TEAM)
    assert (rows[11]["class"], rows[11]["turn"]) == ("backlog", "us"), rows[11]
    rows, _ = triage(tmp, [issue(11, author="maintainer", assoc="MEMBER", created="2026-10-21T00:00:00Z")],
                     {11: [comment("outsider", "NONE", "2026-10-22T00:00:00Z", "I see this too.")]}, perms=TEAM)
    assert rows[11]["class"] == "recheck", rows[11]
    return "team issue -> backlog; outside reply -> recheck"


def f12_maintainers(tmp):
    """A design decision waits on the maintainers: our turn, not the reporter's."""
    rows, _ = triage(tmp, [issue(12)], {12: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z",
                                                     post("Two options: A or B.", "Waiting on: maintainers · 2026-10-20"))]}, perms=TEAM)
    r = rows[12]
    assert (r["class"], r["turn"], r["waiting_on"]) == ("decide", "us", ["maintainers"]), r
    return "Waiting on: maintainers -> decide, us"


def f13_working_and_later_team_comment(tmp):
    """The claim says that we work on it; a later team comment with no state line makes the state unclear."""
    claim = post("I'm working on this and will link a pull request here.", "Working on it · 2026-10-20")
    rows, _ = triage(tmp, [issue(13)], {13: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", claim)]}, perms=TEAM)
    assert (rows[13]["class"], rows[13]["turn"]) == ("working", "us"), rows[13]
    rows, _ = triage(tmp, [issue(13)], {13: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", claim),
                                             comment("maintainer", "MEMBER", "2026-10-21T00:00:00Z", "Could you try --use-struct-tree?")]}, perms=TEAM)
    assert rows[13]["class"] == "judge" and rows[13]["notes"], rows[13]
    return "Working on it -> working; later team comment without a state -> judge"


def f14_last_state_counts(tmp):
    """Only the last counted state line counts."""
    rows, _ = triage(tmp, [issue(14, created="2026-10-01T00:00:00Z")],
                     {14: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", post("Working.", "Working on it · 2026-10-20")),
                           comment("maintainer", "MEMBER", "2026-10-22T00:00:00Z", post("No failure on 2.5.16. Which file?", "Waiting on: reporter · 2026-10-22"))]},
                     perms=TEAM)
    assert rows[14]["class"] == "waiting" and rows[14]["footer"]["state"] == "reporter", rows[14]
    return "the last state line wins"


# ---------------------------------------------------------------- the state line


def f15_footer_line(tmp):
    """The state line comes from the tool, in the one form that the triage reads; other values are refused."""
    ok = {("--working",): "Working on it · 2026-10-28",
          ("--waiting-on", "reporter"): "Waiting on: reporter · 2026-10-28",
          ("--waiting-on", "maintainers"): "Waiting on: maintainers · 2026-10-28",
          ("--waiting-on", "docling-project/docling#4341,#722"): "Waiting on: docling-project/docling#4341, #722 · 2026-10-28"}
    for args, want in ok.items():
        got = run("footer", *args, "--date", "2026-10-28").stdout.strip()
        assert got == want, (args, got)
    for bad in ("reporter; rm -rf /", "evil.example/x#1", "reporter,#1", "#1,#1", "", "maintainer", "https://x/#1"):
        res = run("footer", "--waiting-on", bad, "--date", "2026-10-28", check=False)
        assert res.returncode == 2, (bad, res.returncode, res.stdout)
    assert run("footer", "--working", "--date", "28.10.2026", check=False).returncode == 2
    rows, _ = triage(tmp, [issue(15)], {15: [comment("maintainer", "MEMBER", "2026-10-28T00:00:00Z",
                                                     post("x", ok[("--waiting-on", "docling-project/docling#4341,#722")]))]},
                     perms=TEAM, refs={"docling-project/docling#4341": "open", "#722": "open"})
    assert rows[15]["waiting_on"] == ["docling-project/docling#4341", "#722"], rows[15]
    return "4 forms round-trip; 8 bad values exit 2"


def f16_append(tmp):
    """--append ends a draft with the state line and the AI disclosure once."""
    draft = os.path.join(tmp, "draft.md")
    with open(draft, "w", encoding="utf-8") as f:
        f.write("Thanks for the report.\n")
    run("footer", "--waiting-on", "reporter", "--date", "2026-10-28", "--append", draft)
    with open(draft, encoding="utf-8") as f:
        text = f.read()
    assert text == "Thanks for the report.\n\nWaiting on: reporter · 2026-10-28\n\n" + AI + "\n", repr(text)
    assert run("footer", "--working", "--date", "2026-10-28", "--append", draft, check=False).returncode == 2
    return "appended once; a second append exits 2"


# ---------------------------------------------------------------- triage and report


def f17_count(tmp):
    """The list must have every open issue (as the odl-review triage, exit 11)."""
    _, res = triage(tmp, [issue(1), issue(2)], total=3, check=False)
    assert res.returncode == 11, (res.returncode, res.stdout)
    _, res = triage(tmp, [issue(1), issue(2)], total=3, only="1")
    assert res.returncode == 0, res.stdout
    return "2 of 3 -> exit 11; --issues skips the count"


def f18_report(tmp):
    """The report needs a judgment for each judge row; a judgment cannot make an issue that waits for us wait for others."""
    issues = [issue(94), issue(4), issue(1, created="2026-01-01T00:00:00Z")]
    comments = {94: [comment("maintainer", "MEMBER", "2026-01-10T00:00:00Z", "Good idea.")],
                4: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", post("Which version?", "Waiting on: reporter · 2026-10-20")),
                    comment("reporter", "NONE", "2026-10-22T00:00:00Z", "Thanks!")]}
    triage(tmp, issues, comments, perms=TEAM)
    path = os.path.join(tmp, "issue-triage", "judgment.json")

    def report(judgment):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(judgment, f)
        return run("triage-report", "--work", tmp, check=False)

    assert report({}).returncode == 12
    assert report({"94": {"class": "decide"}}).returncode == 12
    assert report({"94": {"class": "decide", "reason": "feature request"}, "4": {"class": "waiting", "reason": "only thanks"}}).returncode == 12
    assert report({"94": {"class": "nonsense", "reason": "x"}}).returncode == 12
    res = report({"94": {"class": "decide", "reason": "a feature request: the maintainers decide"}, "4": {"class": "close", "reason": "the reporter confirms the fix"}})
    assert res.returncode == 0, res.stdout + res.stderr
    with open(os.path.join(tmp, "issue-triage", "triage.md"), encoding="utf-8") as f:
        md = f.read()
    order = [md.index(f"#{n} ") for n in (4, 1, 94)]
    assert order == sorted(order), order
    assert "a feature request" in md and "no state line" in md, md
    return "missing, reasonless, weakening, unknown -> exit 12; ours first"


def f20_crlf(tmp):
    """A state line in a comment with CRLF line ends counts (CodeRabbit on #849: web UI comments can have CRLF)."""
    body = "Which version do you use?\r\n\r\nWaiting on: reporter · 2026-10-20\r\n\r\n" + AI.replace("\n", "\r\n")
    rows, _ = triage(tmp, [issue(20, created="2026-10-01T00:00:00Z")],
                     {20: [comment("maintainer", "MEMBER", "2026-10-20T00:00:00Z", body)]}, perms=TEAM)
    assert (rows[20]["class"], rows[20]["waiting_on"]) == ("waiting", ["reporter"]), rows[20]
    return "CRLF state line -> waiting"


def f21_graphql_failure(tmp):
    """If the pull request links of an issue cannot be read, the triage is incomplete (exit 11), not silently "new" (CodeRabbit on #849)."""
    data = {"repo": "o/r", "fetched_at": NOW, "total_count": 1, "issues": [issue(21)], "comments": {}, "closing_prs": {},
            "permissions": {}, "refs": {}, "mentioned_prs": {}, "incomplete": [21]}
    path = os.path.join(tmp, "fixture.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f)
    res = run("triage", "--fixture", path, "--work", tmp, check=False)
    assert res.returncode == 11 and "#21" in res.stdout, (res.returncode, res.stdout)
    return "GraphQL failure for #21 -> exit 11"


EVALS = [("F1-new", f1_new), ("F2-team-last", f2_team_last_without_state), ("F3-reporter", f3_waiting_on_reporter),
         ("F4-reply", f4_reply_after_state), ("F5-outsider", f5_state_line_by_outsider), ("F6-bot", f6_bot_after_state),
         ("F7-stale", f7_stale), ("F8-pr-wait", f8_waiting_on_a_pull_request), ("F9-upstream", f9_waiting_upstream),
         ("F10-open-pr", f10_open_pull_request), ("F19-mentioned-pr", f19_mentioned_pull_request), ("F11-backlog", f11_backlog), ("F12-maintainers", f12_maintainers),
         ("F13-working", f13_working_and_later_team_comment), ("F14-last-state", f14_last_state_counts),
         ("F15-footer", f15_footer_line), ("F16-append", f16_append), ("F17-count", f17_count), ("F18-report", f18_report),
         ("F20-crlf", f20_crlf), ("F21-graphql", f21_graphql_failure)]

_gates = importlib.util.spec_from_file_location("gate_evals", os.path.join(os.path.dirname(os.path.abspath(__file__)), "gates.py"))
_gate_evals = importlib.util.module_from_spec(_gates)
_gates.loader.exec_module(_gate_evals)
EVALS += _gate_evals.EVALS


def main(argv):
    only = argv[argv.index("-k") + 1] if "-k" in argv else None
    failed = 0
    for name, fn in EVALS:
        if only and only not in name:
            continue
        tmp = tempfile.mkdtemp(prefix=f"odl-fix-eval-{name}-", dir=TMP_ROOT)
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
