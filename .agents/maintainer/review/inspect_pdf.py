#!/usr/bin/env python3
"""Check the structure tree of tagged PDFs. Reads files only; needs qpdf on PATH.

Usage: inspect_pdf.py [--json] FILE.pdf [FILE.pdf ...]

For each file it follows the structure tree from the catalog (orphan objects are not
counted) and reports:
  types         count of structure elements by /S
  ids           elements with /ID, duplicate /ID values (ISO 32000-1 Table 323: unique)
  idtree        /IDTree in the StructTreeRoot (Table 322: required if any element has /ID)
  dangling      marked-content IDs (MCIDs) that elements reference but the page content lacks
  empty         elements without /K (no content and no children)
  table         Table > TR > TH/TD (TR also in THead, TBody, TFoot), and each TH and TD has a child
Exit code 1 if any file has no structure tree, duplicate IDs, IDs without IDTree, dangling MCIDs,
or a table problem.
Limitations: MCIDs in named property lists (BDC /Tag /Name) are not resolved. The MCIDs of a page
are the "/MCID <n>" byte matches in its /Contents, so a match in a text string also counts. An MCR
with /Stm (content in a form XObject) is checked against the page content and can show as dangling.
Types that /RoleMap maps are not resolved.
"""

from __future__ import annotations

import base64
import collections
import json
import re
import subprocess
import sys

MCID_RE = re.compile(rb"/MCID\s+(\d+)")


def load(path: str) -> dict:
    cmd = ["qpdf", "--json", "--json-key=qpdf", "--json-stream-data=inline", "--decode-level=generalized", path]
    res = subprocess.run(cmd, capture_output=True)
    if res.returncode not in (0, 3):  # 3 = warnings
        raise SystemExit(f"{path}: qpdf failed: {res.stderr.decode(errors='replace')[:300]}")
    return json.loads(res.stdout)["qpdf"][1]


def deref(objs: dict, value):
    while isinstance(value, str) and value.endswith(" R"):
        entry = objs.get(f"obj:{value}", {})
        value = entry.get("value", entry.get("stream", {}).get("dict"))
    return value


def stream_data(objs: dict, ref: str) -> bytes:
    entry = objs.get(f"obj:{ref}", {}).get("stream", {})
    data = entry.get("data")
    return base64.b64decode(data) if data else b""


def page_mcids(objs: dict, page_ref: str) -> set[int]:
    page = deref(objs, page_ref) or {}
    contents = page.get("/Contents")
    refs = contents if isinstance(contents, list) else [contents] if contents else []
    found: set[int] = set()
    for ref in refs:
        found.update(int(m) for m in MCID_RE.findall(stream_data(objs, ref)))
    return found


def inspect(path: str) -> dict:
    objs = load(path)
    trailer = objs["trailer"]["value"]
    catalog = deref(objs, trailer["/Root"]) or {}
    root_ref = catalog.get("/StructTreeRoot")
    result = {"file": path, "tagged": bool(root_ref), "types": {}, "with_id": 0, "dup_ids": {},
              "idtree": False, "dangling": [], "empty": collections.Counter(), "table": []}
    if not root_ref:
        return result
    root = deref(objs, root_ref) or {}
    result["idtree"] = "/IDTree" in root
    types, ids = collections.Counter(), collections.defaultdict(list)
    refs_by_page: dict[str, set[int]] = collections.defaultdict(set)
    seen: set[str] = set()

    def walk(node, parent_pg, parent_s=None):
        if isinstance(node, list):
            for kid in node:
                walk(kid, parent_pg, parent_s)
            return
        if isinstance(node, int):
            if parent_pg:
                refs_by_page[parent_pg].add(node)
            return
        key = node if isinstance(node, str) else None
        if key:
            if key in seen:
                return
            seen.add(key)
        d = deref(objs, node)
        if not isinstance(d, dict):
            return
        if d.get("/Type") == "/MCR":
            pg = d.get("/Pg", parent_pg)
            if pg and "/MCID" in d:
                refs_by_page[pg].add(d["/MCID"])
            return
        if d.get("/Type") == "/OBJR":
            return
        s = d.get("/S")
        if s:
            types[s] += 1
            if "/ID" in d:
                ids[d["/ID"]].append(s)
            if "/K" not in d:
                result["empty"][s] += 1
            if s in ("/TH", "/TD") and parent_s != "/TR":
                result["table"].append(f"{s.lstrip('/')} in {str(parent_s).lstrip('/')}, not in TR")
            elif s == "/TR" and parent_s not in ("/Table", "/THead", "/TBody", "/TFoot"):
                result["table"].append(f"TR in {str(parent_s).lstrip('/')}, not in Table")
            if s in ("/TH", "/TD") and d.get("/K") in (None, []):
                result["table"].append(f"{s.lstrip('/')} without a child")
        walk(d.get("/K", []), d.get("/Pg", parent_pg), s or parent_s)

    walk(root.get("/K", []), None)
    result["types"] = dict(types)
    result["with_id"] = sum(len(v) for v in ids.values())
    result["dup_ids"] = {k: v for k, v in ids.items() if len(v) > 1}
    for pg, mcids in refs_by_page.items():
        present = page_mcids(objs, pg)
        result["dangling"] += [{"page": pg, "mcid": m} for m in sorted(mcids - present)]
    result["empty"] = dict(result["empty"])
    return result


def summary(r: dict) -> str:
    if not r["tagged"]:
        return f"{r['file']}: not tagged"
    t = ", ".join(f"{k.lstrip('/')} {v}" for k, v in sorted(r["types"].items()))
    lines = [f"{r['file']}: {t}",
             f"  ids: {r['with_id']} elements, {len(r['dup_ids'])} duplicate values; IDTree: {'yes' if r['idtree'] else 'no'}",
             f"  dangling MCIDs: {len(r['dangling'])}; elements without /K: {sum(r['empty'].values())}; table problems: {len(r['table'])}"]
    for value, kinds in list(r["dup_ids"].items())[:5]:
        lines.append(f"    duplicate /ID {value}: {', '.join(k.lstrip('/') for k in kinds)}")
    for d in r["dangling"][:5]:
        lines.append(f"    dangling MCID {d['mcid']} on page {d['page']}")
    for problem in r["table"][:5]:
        lines.append(f"    table: {problem}")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    as_json = "--json" in argv
    files = [a for a in argv if a != "--json"]
    if not files:
        print(__doc__)
        return 2
    results = [inspect(f) for f in files]
    print(json.dumps(results, indent=1) if as_json else "\n".join(summary(r) for r in results))
    bad = any(not r["tagged"] or r["dup_ids"] or (r["with_id"] and not r["idtree"]) or r["dangling"] or r["table"]
              for r in results)  # a tagged output that lost its structure tree fails (#836)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
