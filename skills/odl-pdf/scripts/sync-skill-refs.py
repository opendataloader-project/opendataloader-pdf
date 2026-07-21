#!/usr/bin/env python3
"""sync-skill-refs.py — verify the option surface the skill *references* resolves.

The skill does not carry an option inventory; the option truth is the installed
CLI --help (authority), a checkout options.json (SSOT), or the homepage reference.
This check guards the small surface the skill's prose actually names:
  Tier 1 — every referenced `--<option>` resolves to a category+source.
  Tier 2 — decision-critical option VALUES exist in options.json (see task 2).

Categories (no single catch-all ignore list; every token has a source + reason):
  client     -> name present in options.json
  server     -> opendataloader-pdf-hybrid flag (SERVER_OPTIONS; validate vs a
                hybrid --help snapshot when available, else listed explicitly)
  excluded   -> validly-referenced non-client token: standard CLI (--help/--version),
                third-party (tesseract --help-extra), or bundled-script flag (EXCLUDED_TOKENS,
                each with a reason; kept minimal)
  unknown    -> FAIL

Usage: sync-skill-refs.py [--skill-dir DIR] [--options-json PATH]
Exit: 0 all referenced tokens (+ registered values, task 2) resolve; 1 otherwise.
"""
import argparse
import json
import re
import sys
from pathlib import Path

_SCRIPT_DIR = Path(__file__).parent.resolve()
_BUNDLE = _SCRIPT_DIR.parent                       # skills/odl-pdf
_PROJECT_ROOT = _BUNDLE.parent.parent              # repo root (has options.json)

# --- registries: small + reasoned. Not an inventory. ---
# Server (opendataloader-pdf-hybrid) flags the skill names; not in the client
# options.json. Reason: server-scoped, validated against `--help` on the server.
SERVER_OPTIONS = {
    "force-ocr", "no-ocr", "ocr-engine", "ocr-lang", "psm", "device",
    "enrich-formula", "enrich-picture-description", "host", "port",
    # server-config flags documented in hybrid-guide.md (Server Configuration / troubleshooting):
    "log-level", "max-file-size", "no-enrich-formula",
    "no-enrich-picture-description", "picture-description-prompt",
}
# Validly-referenced tokens that are NOT ODL client options. Each carries a reason and is
# reviewed on every addition — this is NOT a catch-all for unresolved tokens.
EXCLUDED_TOKENS = {
    "help",        # standard CLI --help (the authority mechanism itself)
    "version",     # referenced only to state that ODL has NO --version flag
    "help-extra",  # tesseract's flag, cited in an OCR troubleshooting note
    "verbose",     # flag of the bundled quick-eval.py, documented in eval-metrics.md
}
# NOTE: deprecated FORMAT VALUES (markdown-with-html/-images) are prose-documented values,
# not `--` option tokens, so they are out of scope for this token check (no category needed).

# Files scanned (agent-facing prose only).
def _scanned_files(skill_dir: Path):
    files = [skill_dir / "SKILL.md"]
    files += sorted((skill_dir / "references").glob("*.md"))
    return [f for f in files if f.is_file()]

# Match exact option tokens; normalize --name=value; ignore wildcard/meta like --enrich-*.
_TOKEN_RE = re.compile(r"--([a-z0-9][a-z0-9-]*)")

def scan_referenced_tokens(skill_dir: Path):
    out = []
    for f in _scanned_files(skill_dir):
        for i, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            for m in _TOKEN_RE.finditer(line):
                tok = m.group(1)
                # ignore wildcard/meta (e.g. --enrich-* rendered as enrich- then *)
                after = line[m.end():m.end() + 1]
                if tok.endswith("-") or after == "*":
                    continue
                out.append((f.relative_to(skill_dir).as_posix(), i, tok))
    return out

def load_client_option_names(options_json_path: Path) -> set[str]:
    data = json.loads(options_json_path.read_text(encoding="utf-8"))
    return {o["name"] for o in data["options"]}

def categorize(tok: str, client_names: set[str]) -> str:
    if tok in client_names:
        return "client"
    if tok in SERVER_OPTIONS:
        return "server"
    if tok in EXCLUDED_TOKENS:
        return "excluded"
    return "unknown"

def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Verify referenced option tokens/values resolve.")
    p.add_argument("--skill-dir", type=Path, default=_BUNDLE)
    p.add_argument("--options-json", type=Path, default=_PROJECT_ROOT / "options.json")
    return p.parse_args(argv)

def main(argv=None) -> int:
    args = parse_args(argv)
    client = load_client_option_names(args.options_json)
    tokens = scan_referenced_tokens(args.skill_dir)
    unknown = [(f, ln, t) for (f, ln, t) in tokens if categorize(t, client) == "unknown"]
    # (Task 2 inserts Tier-2 value validation here.)
    if unknown:
        print("UNKNOWN option tokens (no client/server/deprecated/excluded source):")
        for f, ln, t in unknown:
            print(f"  {f}:{ln}  --{t}  category=unknown  expected=options.json|SERVER_OPTIONS|EXCLUDED_TOKENS")
        return 1
    print(f"OK: {len(tokens)} referenced option tokens all resolve to a known source.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
