#!/usr/bin/env python3
"""Compute the verdict of one review round from its files. The agent makes evidence; this tool
decides. Its result is final for the agent; only the user can change an input at a gate
(decisions.json, which verdict.md lists so that the user sees each decision that was used).

Usage: verdict.py --round-dir WORK/<n>/r<k> [--catalog PATH]

Inputs in the round folder (references/tools.md, "The round folder"):
  pr.json kind.json files.json      scripts/prepare.py          (required)
  scan.json                         scripts/diff_scan.py        (required)
  precedents.json                   scripts/precedents.py       (required)
  plan.json                         the agent                   (required)
  evidence.jsonl                    the agent and the tools     (required)
  cases.json                        the agent                   (required)
  objections.json                   the agent, from the panel   (required; [] if the panel had none)
  decisions.json                    the user's answers at gates (optional)
Outputs: verdict.json and verdict.md. A missing or malformed input gives Hold. If this tool fails,
it writes a Hold verdict with the error, so that an old verdict does not stay on disk.

Rules that the agent cannot change:
  - Evidence counts only for the current head and main SHAs.
  - The catalog sets which checks are needed (by kind, by changed path, and by file category) and
    the impact of each check, not the plan. A kind must fit the changed files.
  - Only a skip that the panel verified makes a needed check not applicable; an "n/a" record does not.
  - A record that an explanation, an assessment, or a case points to must have a real state.
  - Approve is impossible with: a needed check without evidence, an unexplained scan hit, an open or
    disputed objection of major or higher, a failing or pending CI check without an explanation,
    an unassessed precedent, a partial precedent search, or an unknown value in a round file.
  - A security check cannot be accepted as a gap. An earlier decision leads only to Recommend close
    or Decide.
  - A user answer closes a trade-off (by its id, also if several cases carry it), a decide reason
    with an id, or a failed check with the impact "decide"; never a check with another impact.
  - maintainer_followup (the user's decision) moves failed block checks and failing request cases into
    a follow-up list; Approve then names them. Security, hold, and decide items cannot be followed up.
  - A record that cites a reviewer counts only with the reviewer's saved output in the round folder.
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SEV = {"blocker": 3, "major": 2, "minor": 1, "nit": 0}
CASE_TYPES = {"request", "main-defect", "info"}
CASE_STATES = {"pass", "fail", "n/a"}
OBJECTION_STATES = {"confirmed-pr", "fixed", "refuted", "open"}
RUN_STATES = ("pass", "fail")


class Inputs:
    def __init__(self, rdir: str) -> None:
        self.rdir, self.problems = rdir, []
        self.pr = self.load("pr.json", {}, dict)
        self.kind = self.load("kind.json", {}, dict)
        self.files = self.load("files.json", [], list)
        self.scan = self.load("scan.json", {"hits": []}, dict)
        self.prec = self.load("precedents.json", {"hits": [], "failed_queries": []}, dict)
        self.plan = self.load("plan.json", {}, dict)
        self.cases = self.load("cases.json", [], list)
        self.objections = self.load("objections.json", [], list)
        self.decisions = self.load("decisions.json", {}, dict, required=False)
        self.evidence, self.stale = [], 0
        path = os.path.join(rdir, "evidence.jsonl")
        if not os.path.exists(path):
            self.problems.append("missing evidence.jsonl")
            return
        with open(path, encoding="utf-8") as f:
            for i, line in enumerate(f, 1):
                if not line.strip():
                    continue
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError:
                    self.problems.append(f"evidence.jsonl line {i} is not JSON")
                    continue
                if not isinstance(rec, dict):
                    self.problems.append(f"evidence.jsonl line {i} is not an object")
                    continue
                if same(rec.get("head"), self.pr.get("head")) and same(rec.get("main"), self.pr.get("main")):
                    self.evidence.append(rec)
                else:
                    self.stale += 1
        ids = [r.get("id") for r in self.evidence]
        for dup in sorted({i for i in ids if ids.count(i) > 1 and isinstance(i, str)}):
            self.problems.append(f"evidence id {dup} is used more than once")
        for key in ("failing_checks", "pending_checks", "cla", "head", "main"):
            if self.pr and key not in self.pr:
                self.problems.append(f"pr.json has no '{key}' (made by an older prepare.py; run it again)")

    def load(self, name: str, default, typ, required: bool = True):
        path = os.path.join(self.rdir, name)
        if not os.path.exists(path):
            if required:
                self.problems.append(f"missing {name}")
            return default
        try:
            with open(path, encoding="utf-8") as f:
                obj = json.load(f)
        except json.JSONDecodeError as e:
            self.problems.append(f"{name} is not JSON ({e.msg})")
            return default
        if not isinstance(obj, typ):
            self.problems.append(f"{name} is not a JSON {typ.__name__}")
            return default
        return obj


def same(a, b) -> bool:
    return isinstance(a, str) and isinstance(b, str) and (a.startswith(b) or b.startswith(a)) and min(len(a), len(b)) >= 7


def glob_match(path: str, glob: str) -> bool:
    return fnmatch.fnmatch(path, glob) or (glob.startswith("**/") and fnmatch.fnmatch(path, glob[3:]))


def needed(check: dict, kind: str, paths: list[str], categories: set[str] | None = None) -> bool:
    if check.get("always") or kind in check.get("kinds", []):
        return True
    if categories and categories & set(check.get("categories", [])):
        return True
    return any(glob_match(p, g) for p in paths for g in check.get("paths", []))


def must_assess(h: dict) -> bool:
    """A precedent hit that the plan must assess: a decision, a revert, a marked comment, or reverted lines."""
    return h["source"] in ("closed-pr", "issue-not-planned", "git-revert", "code-comment") \
        or (h["source"] == "git-line-history" and bool(h.get("markers") or h.get("reverts")))


def as_list(v) -> list:
    if isinstance(v, list):
        return [x for x in v if isinstance(x, str)]
    return [v] if isinstance(v, str) else []


def compute(rdir: str, catalog_path: str) -> dict:
    x = Inputs(rdir)
    with open(catalog_path, encoding="utf-8") as f:
        catalog = json.load(f)
    pr, plan = x.pr, x.plan
    raw_dec = x.decisions
    answers = lambda key: {k: v for k, v in raw_dec[key].items() if isinstance(v, str)} if isinstance(raw_dec.get(key), dict) else {}  # noqa: E731
    dec = {"tradeoffs": answers("tradeoffs"), "decided_checks": answers("decided_checks"),
           "accept_gaps": as_list(raw_dec.get("accept_gaps")), "sensitive_ok": as_list(raw_dec.get("sensitive_ok")),
           "followup": as_list(raw_dec.get("maintainer_followup")),
           **{k: raw_dec.get(k) is True for k in ("approve_ok", "cla_not_required", "post_while_deciding")}}
    ev = {r.get("id"): r for r in x.evidence if isinstance(r.get("id"), str)}
    r = {k: [] for k in ("blocked", "close", "security", "decide", "block", "followup", "hold", "gaps", "notes")}
    r["hold"] += x.problems
    for rec in x.evidence:  # a reviewer's claim counts only with the reviewer's saved output
        if "reviewer" in str(rec.get("source", "")).lower():
            outs = [t for t in re.split(r"[,\s]+", str(rec.get("output") or "")) if t]
            if not any(os.path.isfile(os.path.join(rdir, t)) for t in outs):
                r["hold"].append(f"record {rec.get('id')} cites a reviewer, but no saved output of the reviewer is in the round folder ({rec.get('output')})")
    if x.stale:
        r["notes"].append(f"{x.stale} evidence records are for another head or main and do not count")

    def has_ev(eid, ok=("pass",)) -> bool:
        ids = eid if isinstance(eid, list) else [eid]
        return bool(ids) and all(isinstance(i, str) and i in ev and ev[i].get("state") in ok for i in ids)

    # Entry state
    if pr and pr.get("state") != "OPEN":
        r["blocked"].append(f"the pull request is {pr.get('state')}")
    if pr.get("isDraft"):
        r["blocked"].append("draft")
    if pr and pr.get("baseRefName") != "main":
        r["hold"].append(f"base branch is {pr.get('baseRefName')}, not main (stacked)")
    # Later rounds: each request of the last review has a status with evidence (SKILL.md, "Later rounds").
    if pr and isinstance(pr.get("round"), int) and pr["round"] > 1:
        earlier = plan.get("earlier_requests")
        if not isinstance(earlier, list) or not earlier:
            r["hold"].append(f"round {pr['round']}: plan.json has no earlier_requests (a status for each request of the last review)")
        else:
            for q in earlier:
                qid, st = (q or {}).get("id"), (q or {}).get("status")
                if st not in ("resolved", "not-resolved", "answered"):
                    r["hold"].append(f"earlier request {qid}: unknown status {st!r}")
                elif not has_ev(q.get("evidence"), ("pass", "fail")):
                    r["hold"].append(f"earlier request {qid}: no record for the current head and main")
                elif st == "not-resolved":
                    r["block"].append(f"earlier request {qid} is not resolved: {str(q.get('text', ''))[:80]}")
    if pr and not x.kind.get("bot") and pr.get("cla") == "PENDING" and not dec["cla_not_required"]:
        r["hold"].append(f"CLA status not posted yet: the head was {pr.get('head_age_minutes')} minutes old at the fetch; "
                         "run prepare.py fetch and trees again")
    elif pr and not x.kind.get("bot") and pr.get("cla") not in ("SUCCESS", "SIGNED_COMMENT") and not dec["cla_not_required"]:
        r["blocked"].append(f"CLA is {pr.get('cla')}")
    if pr.get("conflict"):
        r["block"].append(f"merge conflict with main: {', '.join(pr.get('conflict_files', [])[:5])}")

    # Kind: it must fit the auto kind and must not hide a category of the changed files
    kind = plan.get("kind")
    kinds = catalog["kinds"]
    categories = {f.get("category") for f in x.files} | {f.get("old_category") for f in x.files if f.get("old_category")}
    categories.discard(None)
    kind_ok = False
    if kind not in kinds:
        r["hold"].append(f"plan.json has no valid kind (got {kind!r})")
    elif x.kind.get("auto") not in kinds[kind]["auto"]:
        r["hold"].append(f"kind '{kind}' does not fit the changed files (prepare.py: '{x.kind.get('auto')}', categories {sorted(categories)})")
    elif categories & set(kinds[kind].get("forbid", [])):
        r["hold"].append(f"kind '{kind}' cannot cover files of the categories {sorted(categories & set(kinds[kind]['forbid']))}")
    else:
        kind_ok = True

    # Checks
    paths = [f["path"] for f in x.files if isinstance(f.get("path"), str)] + [f["old"] for f in x.files if isinstance(f.get("old"), str)]
    planned = {c.get("id"): c for c in plan.get("checks", []) if isinstance(c, dict) and isinstance(c.get("id"), str)}
    for cid in [c for c in planned if c.startswith("skip:") or ":" in c]:
        r["hold"].append(f"plan.json: {cid!r} is not a valid check id")
        planned.pop(cid)
    rows = []
    cat_by_id = {c["id"]: c for c in catalog["checks"]}
    all_checks = list(catalog["checks"]) + [
        {"id": cid, "question": c.get("question", "?"), "title": c.get("title", cid), "impact": "block", "invented": True}
        for cid, c in planned.items() if cid not in cat_by_id]
    for c in all_checks:
        cid, p = c["id"], planned.get(c["id"])
        need = True if c.get("invented") else needed(c, kind or "", paths, categories)
        impact = c["impact"]
        if c.get("invented") and p and p.get("impact") in ("decide", "hold", "info"):
            if p.get("impact_shown_at_gate") is True:
                impact = p["impact"]
            else:
                r["hold"].append(f"{cid}: the plan lowers the impact of an invented check to {p['impact']}; show it at the post gate")
        recs = [e for e in x.evidence if e.get("condition") == cid]
        states = {e.get("state") for e in recs}
        state = "fail" if "fail" in states else "pass" if "pass" in states else "n/a" if states == {"n/a"} else "none"
        status = (p or {}).get("status", "not planned")
        if status not in ("planned", "done", "skipped", "cannot", "not planned"):
            r["hold"].append(f"{cid}: unknown status {status!r} in plan.json")
            status = "not planned"
        verified_skip = need and status == "skipped" and any(
            e.get("condition") == f"skip:{cid}" and e.get("state") == "pass" and e.get("verified_by") == "panel"
            for e in x.evidence)
        if need and status in ("skipped", "cannot") and state == "fail":
            r["notes"].append(f"{cid}: the plan says {status}, but a record fails; the fail counts")
        elif need and status == "skipped":
            if impact == "security":
                r["hold"].append(f"{cid}: a security check cannot be skipped")
            elif verified_skip:
                state = "n/a"
            else:
                r["hold"].append(f"{cid}: skipped without a reason that the panel verified ({(p or {}).get('reason', 'no reason')})")
        elif need and status == "cannot":
            if impact == "security":
                r["hold"].append(f"{cid}: a security check cannot be waived; get the capability or hold")
            elif cid in dec["accept_gaps"]:
                r["gaps"].append(cid)
                state = "gap"
            else:
                r["hold"].append(f"{cid}: cannot run here ({(p or {}).get('reason', '')}); decide: get the capability, accept the gap, or hold")
        elif need and status == "not planned":
            r["hold"].append(f"{cid}: needed ({c.get('title')}) but not in the plan")
        elif need and state == "none":
            r["hold"].append(f"{cid}: no evidence for the current head and main")
        elif need and state == "n/a":
            r["hold"].append(f"{cid}: 'n/a' records do not make a needed check pass; skip it with a reason that the panel verifies")
        if state == "fail":
            failed = [str(e.get("id")) for e in recs if e.get("state") == "fail"]
            target = {"security": "security", "decide": "decide", "hold": "hold", "info": "notes"}.get(impact, "block")
            line = f"{cid} failed (expected: {c.get('title')}; {', '.join(failed)})"
            if target == "decide" and cid in dec["decided_checks"]:
                r["notes"].append(f"{line}; decided by the user: {dec['decided_checks'][cid]}")
            elif target == "block" and cid in dec["followup"]:
                r["followup"].append(line)
            else:
                r[target].append(line)
        rows.append({"id": cid, "question": c.get("question"), "needed": need, "status": status, "state": state, "impact": impact})

    # Q1 for bug fixes: same environment, and release/main states. Only for a kind that fits the files.
    if kind == "bugfix" and kind_ok:
        q = plan.get("q1_reproduce") or {}
        if not isinstance(q, dict) or not q:
            r["hold"].append("bugfix: plan.json has no q1_reproduce")
        elif q.get("same_environment") is not True:
            r["hold"].append("Q1-reproduce: release and main did not run in the same environment")
        elif not (has_ev(q.get("release_evidence"), RUN_STATES) and has_ev(q.get("main_evidence"), RUN_STATES)):
            r["hold"].append("Q1-reproduce: the release run or the main run is missing for the current head and main")
        elif not ev[q["release_evidence"]].get("env") or ev[q["release_evidence"]].get("env") != ev[q["main_evidence"]].get("env"):
            r["hold"].append("Q1-reproduce: the release run and the main run need the same 'env' (references/evidence.md)")  # #836
        elif q.get("release") != ev[q["release_evidence"]]["state"] or q.get("main") != ev[q["main_evidence"]]["state"]:
            r["hold"].append("Q1-reproduce: plan.json states differ from the states of their runs")
        elif q.get("issue_environment_available") is False:
            r["hold"].append("Q1-reproduce: the issue names an environment that this computer cannot run")
        elif q.get("main") == "pass":
            r["close"].append("already fixed on main" if q.get("release") == "fail" else "not reproduced on the release or main")

    # Scan hits
    expl = plan.get("scan_explanations") if isinstance(plan.get("scan_explanations"), dict) else {}
    for h in x.scan.get("hits", []):
        e = expl.get(h["id"]) or (expl.get(f"rule:{h['rule']}") if h["severity"] == "info" else None)
        if not isinstance(e, dict) or (h["severity"] != "info" and not has_ev(e.get("evidence"))):
            r["hold"].append(f"scan {h['id']} {h['rule']} {h['file']}{':' + str(h['line']) if h.get('line') else ''}: not explained with evidence")
        if h.get("decide") and h["id"] not in dec["sensitive_ok"] and h["rule"] not in dec["sensitive_ok"]:
            r["decide"].append(f"sensitive change {h['rule']} in {h['file']} (two maintainers)")

    # Precedents
    partial = bool(x.prec.get("failed_queries") or x.prec.get("truncated_queries")) or x.prec.get("github") is not True
    if partial and "Q0-precedents" not in dec["accept_gaps"]:
        r["hold"].append(f"the precedent search is partial ({len(x.prec.get('failed_queries') or [])} failed, "
                         f"{len(x.prec.get('truncated_queries') or [])} truncated queries, GitHub {'yes' if x.prec.get('github') is True else 'no'})")
    elif partial:
        r["gaps"].append("Q0-precedents")
    hits = {h["id"]: h for h in x.prec.get("hits", []) if isinstance(h, dict) and h.get("id")}
    assessed = {a.get("id"): a for a in plan.get("precedents", []) if isinstance(a, dict)}
    # In a later round, each hit that the earlier round did not have must be assessed (by its ref; ids change).
    earlier_refs = None
    if isinstance(pr.get("round"), int) and pr["round"] > 1:
        prev = os.path.join(os.path.dirname(os.path.abspath(x.rdir)), f"r{pr['round'] - 1}", "precedents.json")
        if not os.path.exists(prev):  # the earlier review was made elsewhere, or its folder is gone: each hit is new (#836)
            earlier_refs = set()
            r["notes"].append(f"no precedents.json of round {pr['round'] - 1}: each precedent hit counts as new")
        else:
            try:
                with open(prev, encoding="utf-8") as f:
                    earlier_refs = {(h.get("source"), h.get("ref")) for h in json.load(f).get("hits", []) if isinstance(h, dict)}
            except (OSError, ValueError):
                r["hold"].append(f"the precedents of round {pr['round'] - 1} cannot be read")
    def is_new(h: dict) -> bool:
        return earlier_refs is not None and (h.get("source"), h.get("ref")) not in earlier_refs

    for hid, h in hits.items():
        must = must_assess(h)
        new = is_new(h)
        if (must or new) and hid not in assessed:
            why = "reverts lines of it" if h.get("reverts") else f"new since round {pr['round'] - 1}" if new else ""
            r["hold"].append(f"precedent {hid} ({h['source']} {h['ref']}) is not assessed" + (f" ({why})" if why else ""))
    for hid, a in assessed.items():
        h = hits.get(hid)
        if not h:
            r["hold"].append(f"plan.json assesses {hid}, which precedents.json does not have")
            continue
        github = h["source"] in ("closed-pr", "issue-not-planned", "merged-pr")
        if github and (h.get("by_is_pr_author") or not h.get("by")):
            r["notes"].append(f"{hid} {h['ref']} is not a maintainer decision (no decider, or the author of this PR); ignored")
            continue
        if a.get("covers") is not True:
            if (must_assess(h) or is_new(h)) and not has_ev(a.get("evidence")):  # "does not cover" is a claim too (#836)
                r["hold"].append(f"precedent {hid}: 'does not cover' needs passing evidence")
            continue
        if not has_ev(a.get("evidence")):
            r["hold"].append(f"precedent {hid}: its assessment has no passing evidence")
            continue
        effect = a.get("effect")
        if effect == "reject" and a.get("valid") is True and h["source"] in ("closed-pr", "issue-not-planned"):
            r["close"].append(f"earlier decision {h['ref']} ({h.get('decision_url') or h.get('url')})")
        elif effect in ("conflict", "unknown-reason") and hid in dec["tradeoffs"]:
            r["notes"].append(f"earlier decision {h['ref']} ({h['source']}): {effect}; answered by the user: {dec['tradeoffs'][hid]}")
        elif effect in ("conflict", "unknown-reason", "reject"):
            r["decide"].append(f"earlier decision {h['ref']} ({h['source']}): {effect}")
        elif effect not in ("none", "support"):
            r["hold"].append(f"precedent {hid}: unknown effect {effect!r}")

    # Cases
    answered, open_tradeoffs = dec["tradeoffs"], {}
    for case in x.cases:
        if not isinstance(case, dict):
            r["hold"].append("cases.json has an entry that is not an object")
            continue
        cid = case.get("id")
        if case.get("type") not in CASE_TYPES or case.get("pr") not in CASE_STATES:
            r["hold"].append(f"case {cid}: unknown type or state ({case.get('type')!r}, pr {case.get('pr')!r})")
        if case.get("pr") == "n/a" and (case.get("type") == "request" or case.get("source") == "safety"):
            r["hold"].append(f"case {cid}: a request or safety case needs a run of the merge, not 'n/a'")
        if not has_ev(case.get("evidence"), RUN_STATES):
            r["hold"].append(f"case {cid}: no run for the current head and main")
        elif case.get("pr") in RUN_STATES and any(ev[i]["state"] != case["pr"] for i in as_list(case.get("evidence"))):
            r["hold"].append(f"case {cid}: 'pr' is {case['pr']!r}, but its run records say otherwise")
        t = case.get("tradeoff")
        if isinstance(t, str) and t:
            open_tradeoffs.setdefault(t, []).append(str(cid))
        if case.get("source") == "safety" and case.get("pr") == "fail":
            r["decide"].append(f"case {cid} weakens a safety property (two maintainers)")
        if case.get("type") == "request" and case.get("pr") == "fail":
            (r["followup"] if str(cid) in dec["followup"] else r["block"]).append(f"case {cid}: {str(case.get('ideal', ''))[:80]}")
        if case.get("type") == "main-defect":
            r["notes"].append(f"case {cid} is a defect on main: separate issue (security: user only)")
    for t, cids in open_tradeoffs.items():  # one question, also if several cases carry it
        where = f"case{'s' if len(cids) > 1 else ''} {', '.join(cids)}"
        if t in answered:
            r["notes"].append(f"trade-off {t} ({where}) answered by the user: {answered[t]}")
        else:
            r["decide"].append(f"trade-off {t} ({where})")
    if plan.get("partial_fix") is True:
        r["block"].append("partial fix: inputs with the same cause still fail")
    if plan.get("safety_weakened") is True:
        r["decide"].append("the change weakens a safety property (two maintainers)")
    reasons = plan.get("decide_reasons") if isinstance(plan.get("decide_reasons"), list) else as_list(plan.get("decide_reasons"))
    for d in reasons:
        if isinstance(d, dict) and isinstance(d.get("id"), str):
            text = f"{d['id']}: {d.get('text', '')}"
            if d["id"] in answered:
                r["notes"].append(f"{text} — answered by the user: {answered[d['id']]}")
            elif d["id"] not in open_tradeoffs:
                r["decide"].append(text)
        elif isinstance(d, str):
            r["decide"].append(d)
        else:
            r["hold"].append(f"plan.json: a decide reason is not a string or an object with an id: {d!r}")

    # Objections
    refuted = []
    for o in x.objections:
        if not isinstance(o, dict):
            r["hold"].append("objections.json has an entry that is not an object")
            continue
        sev, st = SEV.get(o.get("severity"), 2), o.get("status")
        security = o.get("security") not in (None, False, "false", "no", 0)
        if st not in OBJECTION_STATES:
            r["hold"].append(f"objection {o.get('id')}: unknown status {st!r}")
            continue
        if st == "confirmed-pr":
            (r["security"] if security else r["block"]).append(f"objection {o.get('id')}: {str(o.get('claim', ''))[:80]}")
        elif st == "refuted" and not (o.get("accepted_by") and has_ev(o.get("evidence"), RUN_STATES)):
            st = "disputed"
        elif st == "fixed" and not has_ev(o.get("evidence")):
            st = "disputed"
        if st == "refuted":  # the tools cannot tell who accepted it: verdict.md shows it to the user (#836)
            refuted.append(f"{o.get('id')} ({o.get('severity')}{', security' if security else ''}): {str(o.get('claim', ''))[:80]}"
                           f"; accepted by {o.get('accepted_by')}; evidence {o.get('evidence')}")
        if st in ("open", "disputed") and (sev >= 2 or security):
            r["hold"].append(f"objection {o.get('id')} ({o.get('severity')}) is {st}: {str(o.get('claim', ''))[:80]}")
        elif st in ("open", "disputed"):
            r["notes"].append(f"objection {o.get('id')} ({o.get('severity')}) is {st}")

    # #722: a review that requests changes shows what the PR fixes, so that the author keeps it. A missing gain
    # case is a gap in our input (Hold), not a request to the author.
    solves = next((row["state"] for row in rows if row["id"] == "Q1-solves"), None)
    gains = [c for c in x.cases if isinstance(c, dict) and c.get("main") == "fail" and c.get("pr") == "pass"]
    if r["block"] and x.cases and solves == "pass" and not gains:
        msg = "Q1-solves passes, but no case shows the PR's gain (main fail, PR pass): add it, so that the review keeps it"
        x.problems.append(msg)
        r["hold"].append(msg)

    # CI
    ci = plan.get("ci_explanations") if isinstance(plan.get("ci_explanations"), dict) else {}
    for name in pr.get("failing_checks", []):
        if not has_ev(ci.get(name)):
            r["hold"].append(f"CI check '{name}' fails without an explanation")
    for name in pr.get("pending_checks", []):
        r["hold"].append(f"CI check '{name}' is pending or did not conclude; run prepare.py fetch again later")

    # Verdict
    n = pr.get("number", "?")
    if r["blocked"]:
        verdict = "Blocked"
    elif r["security"]:
        verdict = "Security report (user only)"
    elif x.problems:
        verdict = "Hold"
    elif r["close"]:
        verdict = "Recommend close"
    else:
        parts = [f"Decide ({len(r['decide'])})"] if r["decide"] else []
        if r["block"]:
            parts.append("Request changes")
        elif r["hold"]:
            parts.append("Hold")
        else:
            ids = [f.split(":", 1)[0] if f.startswith("case ") else f.split(" ", 1)[0] for f in r["followup"]]  # "case 3: ..." (#836)
            extra = ([f"unverified: {', '.join(r['gaps'])}"] if r["gaps"] else []) + ([f"follow-up by the maintainer: {', '.join(ids)}"] if ids else [])
            parts.append("Approve" + (f" ({'; '.join(extra)})" if extra else ""))
        verdict = ", then ".join(parts)
    own = x.kind.get("own") is True
    if verdict.startswith("Recommend close"):
        command = f'gh pr close {n} --comment "$(cat close.md)"'
    elif verdict.startswith(("Blocked", "Security", "Hold")) or (verdict.startswith("Decide") and not dec["post_while_deciding"]):
        command = None
    elif "Request changes" in verdict and not own:
        command = f"gh pr review {n} --request-changes --body-file review.md"
    elif verdict.startswith("Approve") and dec["approve_ok"] and not own and not r["gaps"]:
        command = f"gh pr review {n} --approve --body-file review.md"
    else:
        command = f"gh pr review {n} --comment --body-file review.md"
    return {"verdict": verdict, "pr": n, "round": pr.get("round"), "head": pr.get("head"), "main": pr.get("main"),
            "kind": kind, "reasons": r, "checks": rows, "post_command": command, "evidence_counted": len(x.evidence),
            "decisions_used": raw_dec, "refuted_objections": refuted}


def render(v: dict) -> str:
    sha = lambda s: (s or "?")[:7]  # noqa: E731
    out = [f"#{v['pr']} — {v['verdict']}    head {sha(v['head'])} · main {sha(v['main'])} · round {v['round']} · kind {v['kind']}", ""]
    labels = [("blocked", "Blocked"), ("security", "Security (user only)"), ("close", "Close"), ("decide", "Decide"),
              ("block", "Request changes"), ("followup", "Maintainer follow-up (the user's decision)"), ("hold", "Hold (missing or not proven)"), ("gaps", "Accepted gaps"), ("notes", "Notes")]
    for key, label in labels:
        if v["reasons"][key]:
            out.append(f"{label}:")
            out += [f"  - {x}" for x in v["reasons"][key]]
    out += ["", "User decisions used (decisions.json): " + (json.dumps(v["decisions_used"], ensure_ascii=False) if v["decisions_used"] else "none")]
    out += ["", "Refuted objections (check each one at the post gate):"] + [f"  - {x}" for x in v["refuted_objections"]] if v["refuted_objections"] else []
    out += ["", "| Check | Q | Needed | Plan | State | Impact |", "|:--|:--|:--|:--|:--|:--|"]
    for c in v["checks"]:
        if c["needed"] or c["status"] != "not planned":
            out.append(f"| {c['id']} | {c['question']} | {'yes' if c['needed'] else 'no'} | {c['status']} | {c['state']} | {c['impact']} |")
    out += ["", f"Post command: {v['post_command'] or '(none: answer the questions or hold)'}"]
    return "\n".join(out) + "\n"


def write(rdir: str, name: str, text: str) -> None:
    fd, tmp = tempfile.mkstemp(dir=rdir, prefix=f".{name}.")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(text)
    os.replace(tmp, os.path.join(rdir, name))


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--round-dir", required=True)
    p.add_argument("--catalog", default=os.path.join(HERE, "..", "references", "catalog.json"))
    a = p.parse_args(argv)
    try:
        v = compute(a.round_dir, a.catalog)
    except Exception as e:  # fail closed: never leave an old verdict on disk
        err = f"verdict.py failed: {type(e).__name__}: {e}"
        write(a.round_dir, "verdict.json", json.dumps({"verdict": "Hold", "error": err}, indent=1))
        write(a.round_dir, "verdict.md", f"Hold — {err}\n")
        print(err, file=sys.stderr)
        return 1
    try:
        text, data = render(v), json.dumps(v, indent=1, ensure_ascii=False)
    except Exception as e:
        text, data = f"Hold — verdict.py could not render: {e}\n", json.dumps({"verdict": "Hold", "error": str(e)})
    write(a.round_dir, "verdict.json", data)
    write(a.round_dir, "verdict.md", text)
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
