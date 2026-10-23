#!/usr/bin/env python3
"""Write the brief of a second panel round (SKILL.md, "Reviewers and the panel").

Usage: panel_brief.py --round-dir WORK/<n>/r<k> --out FILE [--catalog PATH]

A second round checks the corrections of the first round and the verdict. It does not review the
whole work again: each new round of a full review finds new items about the review's own text, and
the verdict does not change. The brief lists:
  - the verdict that verdict.py computes now, with its Decide, Request changes, and Hold reasons
  - each objection that was fixed, confirmed, or refuted, with its claim and the record of the result
  - the objections that stay open (known items: the panel does not report them again)
  - the rules of the round
It does not quote the plan, the cases, or the drafts. The panel reads the files that a record names.
"""

from __future__ import annotations

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import verdict as V  # noqa: E402

RULES = """## Rules for this round

- For each correction: is it right and complete? Do the files that it changed now agree with its record?
- For each confirmed item: does the verdict follow from its record?
- For each refutation: accept it or not. A refutation counts only if you accept it.
- Report a new item only if it changes the verdict, or if it is major or higher. List other new items as nits, at most five.
- An item needs a claim, an input or a record that shows it, and the expected result. Without these it is a question.
"""


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--round-dir", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--catalog", default=os.path.join(HERE, "..", "references", "catalog.json"))
    a = p.parse_args(argv)
    v = V.compute(a.round_dir, a.catalog)
    ev = {r.get("id"): r for r in V.Inputs(a.round_dir).evidence if isinstance(r.get("id"), str)}
    with open(os.path.join(a.round_dir, "objections.json"), encoding="utf-8") as f:
        objections = [o for o in json.load(f) if isinstance(o, dict)]

    def record(o: dict) -> str:
        ids = o.get("evidence") if isinstance(o.get("evidence"), list) else [o.get("evidence")]
        parts = []
        for i in ids:
            r = ev.get(i) if isinstance(i, str) else None
            parts.append(f"{i}: {r.get('state')}, {str(r.get('claim', ''))[:300]}" if r else f"{i}: no record for the current head and main")
        return "; ".join(parts) or "no record"

    def item(o: dict, with_record: bool) -> str:
        head = f"- {o.get('id')} ({o.get('severity')}, {o.get('panel')}): {str(o.get('claim', ''))[:300]}"
        return head + (f"\n  status {o.get('status')} · record {record(o)}" if with_record else "")

    sha = lambda s: (s or "?")[:7]  # noqa: E731
    out = [f"# Panel brief, next round — #{v['pr']} · head {sha(v['head'])} · main {sha(v['main'])}", "",
           f"Verdict now (verdict.py): {v['verdict']}"]
    for key, label in (("decide", "Decide"), ("block", "Request changes"), ("hold", "Hold")):
        out += [f"{label}:"] + [f"  - {x}" for x in v["reasons"][key]] if v["reasons"][key] else []
    sections = (("Corrections to check", ("fixed",), True), ("Confirmed against the pull request", ("confirmed-pr",), True),
                ("Refuted (accept or not)", ("refuted",), True), ("Known open items (do not report them again)", ("open",), False))
    for title, states, with_record in sections:
        rows = [item(o, with_record) for o in objections if o.get("status") in states]
        out += ["", f"## {title}", ""] + (rows or ["- none"])
    out += ["", RULES]
    with open(a.out, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    print(a.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
