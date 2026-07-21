#!/usr/bin/env python3
"""sync-skill-refs.py — verify the option surface the skill *references* resolves.

The skill does not carry an option inventory; the option truth is the installed
CLI --help (authority), a checkout options.json (SSOT), or the homepage reference.
This check guards the small surface the skill's prose actually names:
  Tier 1 — every referenced `--<option>` resolves to a category+source.
  Tier 2 — decision-critical option VALUES exist in options.json.

Categories (no single catch-all ignore list; every token has a source + reason):
  client     -> name present in options.json
  server     -> opendataloader-pdf-hybrid flag (SERVER_OPTIONS; validate vs a
                hybrid --help snapshot when available, else listed explicitly)
  excluded   -> validly-referenced non-client token: standard CLI (--help/--version)
                or bundled-script flag (EXCLUDED_TOKENS, each with a reason; kept minimal;
                add a third-party entry like tesseract --help-extra only if the skill cites it)
  unknown    -> FAIL

Usage: sync-skill-refs.py [--skill-dir DIR] [--options-json PATH]
Exit: 0 all referenced tokens (+ registered values) resolve; 1 drift found;
      2 input/config error (bad --skill-dir, missing/corrupt --options-json).
"""
import argparse
import json
import re
import sys
from pathlib import Path

_SCRIPT_DIR = Path(__file__).parent.resolve()
_BUNDLE = _SCRIPT_DIR.parent                       # skills/odl-pdf
_PROJECT_ROOT = _BUNDLE.parent.parent              # repo root (has options.json)

class ConfigError(Exception):
    """Bad input/config (missing --skill-dir, unreadable/corrupt --options-json, etc.).
    Caught in main() and reported as exit code 2 — distinct from exit 1 (drift found)."""

# --- registries: small + reasoned. Not an inventory. ---
# Server (opendataloader-pdf-hybrid) flags the skill names; not in the client
# options.json. Reason: server-scoped, validated against `--help` on the server.
SERVER_OPTIONS = {
    # Hybrid-server (opendataloader-pdf-hybrid) flags the skill names by hand. The server is
    # a separate package NOT in options.json, so these resolve names only — hybrid-guide.md
    # points to `opendataloader-pdf-hybrid --help` as the value/default authority. Keep this
    # set to flags the skill actually references (a server rename is caught by release review,
    # not CI — see MAINTAINING.md).
    "force-ocr", "no-ocr", "ocr-engine", "ocr-lang", "psm",
    "enrich-formula", "enrich-picture-description", "host", "port",
    "picture-description-prompt",
}
# Validly-referenced tokens that are NOT ODL client options. Each carries a reason and is
# reviewed on every addition — this is NOT a catch-all for unresolved tokens.
EXCLUDED_TOKENS = {
    "help",        # standard CLI --help (the authority mechanism itself)
    "version",     # referenced only to state that ODL has NO --version flag
    "verbose",     # flag of the bundled quick-eval.py, documented in eval-metrics.md
}
# NOTE: deprecated FORMAT VALUES (markdown-with-html/-images) are prose-documented values,
# not `--` option tokens, so they are out of scope for this token check (no category needed).

# Files scanned (agent-facing prose only). Raises ConfigError if skill_dir doesn't look
# like a skill bundle (no SKILL.md) — an empty/nonexistent --skill-dir must not silently
# scan 0 files and report a false PASS.
def _scanned_files(skill_dir: Path):
    skill_md = skill_dir / "SKILL.md"
    if not skill_md.is_file():
        raise ConfigError(f"no SKILL.md found under {skill_dir} (nothing to check)")
    files = [skill_md]
    files += sorted((skill_dir / "references").glob("*.md"))
    return [f for f in files if f.is_file()]

# Match exact option tokens; normalize --name=value; ignore wildcard/meta like --enrich-*.
_TOKEN_RE = re.compile(r"--([a-z0-9][a-z0-9-]*)")

def scan_referenced_tokens(skill_dir: Path):
    out = []
    for f in _scanned_files(skill_dir):
        try:
            text = f.read_text(encoding="utf-8")
        except OSError as e:
            raise ConfigError(f"{f}: {e}") from e
        for i, line in enumerate(text.splitlines(), 1):
            for m in _TOKEN_RE.finditer(line):
                tok = m.group(1)
                # ignore wildcard/meta (e.g. --enrich-* rendered as enrich- then *)
                after = line[m.end():m.end() + 1]
                if tok.endswith("-") or after == "*":
                    continue
                out.append((f.relative_to(skill_dir).as_posix(), i, tok))
    return out

def _load_options_json(options_json_path: Path) -> dict:
    try:
        return json.loads(options_json_path.read_text(encoding="utf-8"))
    except OSError as e:
        raise ConfigError(f"{options_json_path}: {e}") from e
    except json.JSONDecodeError as e:
        raise ConfigError(f"{options_json_path}: invalid JSON ({e})") from e

def load_client_option_names(options_json_path: Path) -> set[str]:
    data = _load_options_json(options_json_path)
    try:
        return {o["name"] for o in data["options"]}
    except (KeyError, TypeError) as e:
        raise ConfigError(f"{options_json_path}: missing/malformed 'options' ({e})") from e

# Parse the "Values: a (..), b, c. Default: .." segment of an option description.
def parse_allowed_values(description: str) -> set[str]:
    if not description or "Values:" not in description:
        return set()
    seg = description.split("Values:", 1)[1]
    seg = re.split(r"\bDefault:", seg, 1)[0]        # drop the Default: tail
    seg = re.sub(r"\([^)]*\)", "", seg)             # drop parenthetical prose (and its commas)
    vals = set()
    for chunk in seg.split(","):
        m = re.match(r"\s*([a-z0-9][a-z0-9-]*)", chunk)
        if m:
            vals.add(m.group(1))
    return vals

def load_client_option_values(options_json_path: Path) -> dict[str, set[str]]:
    data = _load_options_json(options_json_path)
    try:
        options = data["options"]
    except (KeyError, TypeError) as e:
        raise ConfigError(f"{options_json_path}: missing/malformed 'options' ({e})") from e
    out = {}
    for o in options:
        vals = parse_allowed_values(o.get("description", ""))
        if o.get("default") is not None:
            vals.add(str(o["default"]))
        out[o["name"]] = vals
    return out

# Decision-critical option VALUES the skill's prose depends on. SMALL + reasoned.
# Each must exist in options.json; a removed/renamed value fails CI.
REFERENCED_VALUES = {
    "table-method": {"default", "cluster"},
    "reading-order": {"xycut"},
    "hybrid": {"off", "docling-fast", "hancom-ai"},
    "hybrid-mode": {"auto", "full"},
    "image-output": {"off", "embedded", "external"},
    "format": {"json", "text", "html", "pdf", "markdown", "tagged-pdf"},
}

# Defaults the skill's decision prose relies on (e.g. Gotcha 2 depends on hybrid-mode=auto,
# format-guide on image-output=external). A flip is semantic drift Tier-2 value-existence
# misses; options.json carries `default`, so validate it directly. Small + reasoned.
REFERENCED_DEFAULTS = {
    "table-method": "default",
    "reading-order": "xycut",
    "hybrid": "off",
    "hybrid-mode": "auto",
    "image-output": "external",
    "threads": "1",   # SKILL.md batch note relies on sequential-by-default (>1 opts into parallelism)
}

def load_client_option_defaults(options_json_path: Path) -> dict:
    data = _load_options_json(options_json_path)
    try:
        options = data["options"]
    except (KeyError, TypeError) as e:
        raise ConfigError(f"{options_json_path}: missing/malformed 'options' ({e})") from e
    return {o["name"]: (None if o.get("default") is None else str(o["default"])) for o in options}

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
    try:
        client = load_client_option_names(args.options_json)
        tokens = scan_referenced_tokens(args.skill_dir)
        values = load_client_option_values(args.options_json)
        defaults = load_client_option_defaults(args.options_json)
    except ConfigError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 2

    unknown = [(f, ln, t) for (f, ln, t) in tokens if categorize(t, client) == "unknown"]

    value_errs = []
    for opt, needed in REFERENCED_VALUES.items():
        declared = values.get(opt, set())
        missing = needed - declared
        if missing:
            value_errs.append((opt, sorted(missing), sorted(declared)))

    if unknown:
        print("UNKNOWN option tokens (no client/server/excluded source):")
        for f, ln, t in unknown:
            print(f"  {f}:{ln}  --{t}  category=unknown  expected=options.json|SERVER_OPTIONS|EXCLUDED_TOKENS")
    if value_errs:
        print("Decision-critical VALUES not found in options.json:")
        for opt, miss, decl in value_errs:
            print(f"  --{opt}: missing {miss}  (options.json declares {decl})  source=REFERENCED_VALUES")

    default_errs = []
    for opt, expected in REFERENCED_DEFAULTS.items():
        actual = defaults.get(opt)
        if actual != expected:
            default_errs.append((opt, expected, actual))
    if default_errs:
        print("Default drift — options.json default differs from what the skill assumes:")
        for opt, exp, act in default_errs:
            print(f"  --{opt}: skill assumes default '{exp}' but options.json default is '{act}'  source=REFERENCED_DEFAULTS")

    if unknown or value_errs or default_errs:
        return 1
    print(f"OK: {len(tokens)} referenced option tokens all resolve to a known source.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
