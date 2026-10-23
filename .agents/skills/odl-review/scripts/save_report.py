#!/usr/bin/env python3
"""Save the final report of a subagent from its transcript, without printing the report.

Usage: save_report.py <agent transcript .jsonl> <out file> [--index N]

The agent harness can refuse report files that a subagent writes (#722), so a reviewer, the critic,
and each panel member return the report as their final message. This tool writes that message to
<out file> (for example rev-<role>/report.md or panel-<round>-<member>.md): the text of the last
SubagentHandback call, or else the text of the last assistant message. A resumed agent can hand
back more than once (#722: the report, then a one-line answer); the tool lists the sizes, and
--index N chooses one (0 is the first). It prints only sizes and the first line, so the report does
not go into the agent's context a second time. Exit 1: the transcript has no report.
"""

from __future__ import annotations

import json
import os
import sys


def reports_of(path: str) -> tuple[list[str], str | None]:
    handbacks, last_text = [], None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            try:
                rec = json.loads(line)
            except ValueError:
                continue
            msg = rec.get("message") if isinstance(rec, dict) else None
            if not isinstance(msg, dict) or rec.get("type") != "assistant" or not isinstance(msg.get("content"), list):
                continue
            texts = []
            for block in msg["content"]:
                if not isinstance(block, dict):
                    continue
                if block.get("type") == "tool_use" and block.get("name") == "SubagentHandback":
                    text = (block.get("input") or {}).get("message")
                    if isinstance(text, str) and text.strip():
                        handbacks.append(text)
                elif block.get("type") == "text" and isinstance(block.get("text"), str):
                    texts.append(block["text"])
            if "".join(texts).strip():
                last_text = "".join(texts)
    return handbacks, last_text


def main(argv: list[str]) -> int:
    index = None
    if "--index" in argv:
        i = argv.index("--index")
        index = int(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]
    if len(argv) != 2:
        print(__doc__)
        return 2
    src, out = argv
    handbacks, last_text = reports_of(src)
    if len(handbacks) > 1:
        print(f"{len(handbacks)} hand-backs, characters: {[len(h) for h in handbacks]}; writing index "
              f"{len(handbacks) - 1 if index is None else index} (choose with --index)")
    if handbacks:
        text = handbacks[-1] if index is None else handbacks[index]
    elif index is None:
        text = last_text
    else:
        text = None
    if text is None:
        print(f"no report in {src}")
        return 1
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(text)
    first = next((line for line in text.splitlines() if line.strip()), "")
    print(f"written {out}: {len(text)} characters; first line: {first[:80]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
