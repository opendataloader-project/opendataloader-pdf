#!/usr/bin/env python3
"""Run the odl-pdf skill evaluations against multiple Claude models.

Loads `evals/evals.json` scenarios and sends each `user_input` to each
target model with `SKILL.md` as the system prompt. Checks each response
for `must_mention` (all phrases present) and `must_not_mention` (none
present). Writes a JSON report to `evals/runs/<utc-timestamp>.json`.

Exit codes:
    0  all runs passed
    1  at least one run failed (missing required or leaked forbidden)
    2  setup error (missing API key, missing files, SDK not installed)

Usage:
    export ANTHROPIC_API_KEY=...
    python scripts/run-evals.py
    python scripts/run-evals.py --model claude-haiku-4-5-20251001
    python scripts/run-evals.py --skip-cache --max-tokens 4096
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_MODELS = [
    "claude-haiku-4-5-20251001",
    "claude-sonnet-4-6",
    "claude-opus-4-7",
]

SKILL_DIR = Path(__file__).resolve().parent.parent
SKILL_MD = SKILL_DIR / "SKILL.md"
EVALS_JSON = SKILL_DIR / "evals" / "evals.json"
RUNS_DIR = SKILL_DIR / "evals" / "runs"


def load_skill_system_prompt() -> str:
    text = SKILL_MD.read_text(encoding="utf-8")
    return (
        "You are using the `odl-pdf` agent skill to help a user with "
        "opendataloader-pdf. The skill content follows. Treat it as "
        "authoritative guidance and answer the user's question by applying "
        "the workflow and recommendations defined below.\n\n"
        "---\n\n" + text
    )


def check_phrase(phrase: str, haystack: str) -> bool:
    return phrase.lower() in haystack.lower()


def evaluate_response(eval_case: dict, response_text: str) -> dict:
    required = eval_case.get("must_mention", [])
    forbidden = eval_case.get("must_not_mention", [])
    missing = [p for p in required if not check_phrase(p, response_text)]
    leaked = [p for p in forbidden if check_phrase(p, response_text)]
    return {
        "pass": not missing and not leaked,
        "missing_required": missing,
        "leaked_forbidden": leaked,
        "required_total": len(required),
        "forbidden_total": len(forbidden),
    }


def run_one(client, model: str, system_text: str, user_input: str,
            use_cache: bool, max_tokens: int) -> tuple[str, dict]:
    system_block = {"type": "text", "text": system_text}
    if use_cache:
        system_block["cache_control"] = {"type": "ephemeral"}

    resp = client.messages.create(
        model=model,
        max_tokens=max_tokens,
        system=[system_block],
        messages=[{"role": "user", "content": user_input}],
    )
    text = "".join(b.text for b in resp.content if getattr(b, "type", "") == "text")
    usage = {
        "input_tokens": resp.usage.input_tokens,
        "output_tokens": resp.usage.output_tokens,
        "cache_creation_input_tokens": getattr(resp.usage, "cache_creation_input_tokens", 0),
        "cache_read_input_tokens": getattr(resp.usage, "cache_read_input_tokens", 0),
    }
    return text, usage


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", action="append", default=None,
                    help="Model ID to run. Repeatable. Defaults to Haiku 4.5, Sonnet 4.6, Opus 4.7.")
    ap.add_argument("--skip-cache", action="store_true",
                    help="Do not set cache_control on the system prompt (useful for one-shot checks).")
    ap.add_argument("--max-tokens", type=int, default=2048,
                    help="Max output tokens per call (default: 2048).")
    ap.add_argument("--output", type=Path, default=None,
                    help="Report path. Defaults to evals/runs/<utc-timestamp>.json.")
    args = ap.parse_args()

    try:
        from anthropic import Anthropic
    except ImportError:
        print("ERROR: the `anthropic` package is not installed. Run `pip install anthropic`.", file=sys.stderr)
        return 2

    if not os.getenv("ANTHROPIC_API_KEY"):
        print("ERROR: ANTHROPIC_API_KEY is not set.", file=sys.stderr)
        return 2

    if not EVALS_JSON.exists():
        print(f"ERROR: missing {EVALS_JSON}.", file=sys.stderr)
        return 2
    if not SKILL_MD.exists():
        print(f"ERROR: missing {SKILL_MD}.", file=sys.stderr)
        return 2

    models = args.model if args.model else list(DEFAULT_MODELS)
    evals_data = json.loads(EVALS_JSON.read_text(encoding="utf-8"))
    cases = evals_data.get("evals", [])
    if not cases:
        print("ERROR: evals.json contains no scenarios.", file=sys.stderr)
        return 2

    system = load_skill_system_prompt()
    client = Anthropic()

    started = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    results = []

    for model in models:
        for case in cases:
            t0 = time.time()
            try:
                text, usage = run_one(
                    client, model, system, case["user_input"],
                    use_cache=not args.skip_cache,
                    max_tokens=args.max_tokens,
                )
                score = evaluate_response(case, text)
                error = None
            except Exception as exc:  # noqa: BLE001
                text = ""
                usage = {}
                error = repr(exc)
                score = {
                    "pass": False,
                    "missing_required": case.get("must_mention", []),
                    "leaked_forbidden": [],
                    "required_total": len(case.get("must_mention", [])),
                    "forbidden_total": len(case.get("must_not_mention", [])),
                }

            results.append({
                "model": model,
                "eval_id": case["id"],
                "scenario": case["scenario"],
                "pass": score["pass"],
                "missing_required": score["missing_required"],
                "leaked_forbidden": score["leaked_forbidden"],
                "elapsed_s": round(time.time() - t0, 2),
                "usage": usage,
                "error": error,
                "response_preview": text[:500],
            })

            status = "PASS" if score["pass"] else "FAIL"
            print(f"[{status}] {model} :: {case['id']}  "
                  f"({len(score['missing_required'])} missing, "
                  f"{len(score['leaked_forbidden'])} leaked, "
                  f"{results[-1]['elapsed_s']}s)")

    finished = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    summary = {
        "pass": sum(1 for r in results if r["pass"]),
        "fail": sum(1 for r in results if not r["pass"]),
        "total": len(results),
    }

    report = {
        "started_utc": started,
        "finished_utc": finished,
        "skill": evals_data.get("skill", "odl-pdf"),
        "models": models,
        "cache_enabled": not args.skip_cache,
        "max_tokens": args.max_tokens,
        "summary": summary,
        "results": results,
    }

    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    out_path = args.output or (RUNS_DIR / f"{started.replace(':', '-')}.json")
    out_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"\nSummary: {summary['pass']}/{summary['total']} passed across {len(models)} model(s).")
    print(f"Report:  {out_path}")

    return 0 if summary["fail"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
