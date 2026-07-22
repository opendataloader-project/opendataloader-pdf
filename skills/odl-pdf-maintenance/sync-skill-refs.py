#!/usr/bin/env python3
"""sync-skill-refs.py — version-coupling LINT over the skill's agent-facing prose.

The odl-pdf skill is a *durable procedure*: it tells the agent to discover the
installed tool's interface at runtime (read `--help`), never baking option names,
values, or versions as fact. This lint is the mechanical floor that guards that
contract. It is a **tripwire, not proof**: it catches the coarse coupling regexes
can see; authoring discipline + release review are the real guard.

Scanned: SKILL.md + references/*.md (agent-facing prose only; not scripts).

Checks (any violation -> exit 1):
  1. Baked semantic version  \\d+\\.\\d+\\.\\d+  -> FAIL.
     EXCLUDES dotted-quad IPv4 (127.0.0.1, 0.0.0.0, ...) which is safety advice,
     not a version. (The naive semver regex matches the 127.0.0 prefix of an IP,
     so we classify the *whole* dotted-numeric run, not a 3-group substring.)
  2. Baked ODL option name  --<flag>  -> FAIL, minus a small reasoned ALLOWLIST
     of legitimate non-ODL / meta flags (below). A regex cannot tell an ODL flag
     from a pip/jq/git flag, so an allowlist is unavoidable; it is derived from
     what the reshaped prose actually uses, not from any ODL option inventory.
  3. Source-of-truth concept present in SKILL.md (LOOSE phrase check, not a
     brittle exact substring) -> absent means the durable-procedure thesis was
     edited out.
  4. Structural: referenced references/… and scripts/… paths exist; markdown
     code-fence count is even per file; SKILL.md frontmatter is delimited + names
     the skill.

Exit: 0 clean; 1 coupling/violation found; 2 input/config error (bad --skill-dir,
      unreadable file).

HONEST LIMIT: this catches only long `--flag` forms. It does NOT catch `-short`
flags, bare-word option names, baked option *values*, or backend/engine names
mentioned as fact — nor does it validate exception/crash-class names (a name
denylist would be flaky and is deliberately omitted). A 4-component dotted number
(x.y.z.w, each octet <=255) is treated as an IPv4 and NOT flagged as a version
(negligible; no real version looks like a dotted quad). Those are the job of
authoring discipline and human release review. The lint is a floor, not a ceiling.
"""
import argparse
import re
import sys
from pathlib import Path

_SCRIPT_DIR = Path(__file__).parent.resolve()      # skills/odl-pdf-maintenance (not shipped)
_BUNDLE = _SCRIPT_DIR.parent / "odl-pdf"           # the distributed skill (sibling dir)

# --- Allowlist of legitimate long `--flags` (check 2). Small + reasoned. ---
# Derived by scanning the reshaped prose for what it actually uses — NOT from any
# ODL option list (baking ODL names is the coupling this lint removes). If install
# prose later cites a pip/venv flag (e.g. PEP-668 `--break-system-packages`), add it
# here WITH a reason; do not broaden speculatively.
ALLOWED_FLAGS = {
    "--help",     # the source-of-truth mechanism itself (standard CLI convention)
    "-h",         # short form of --help; single-dash, so never matched by the long-form
                  # regex below — listed for allowlist completeness / reviewer clarity.
    "--verbose",  # flag of the bundled scripts/quick-eval.py (see references/eval-metrics.md)
}

# LOOSE Source-of-truth signal phrases (check 3). Presence of ANY one satisfies it.
_SOT_PATTERNS = [
    r"source[- ]of[- ]truth",
    r"read the installed",
    r"installed[^.\n]{0,40}help",   # "installed help", "installed tool's own help"
    r"installed interface",
]

# A long `--flag` token: two dashes + a lowercase/digit start. Matches long forms
# only (see HONEST LIMIT). Underscores are not valid ODL flag chars. The `(?<!...)`
# guard requires the `--` NOT be preceded by an alphanumeric, so mid-word double
# hyphens in prose ("high--level") are not misread as a flag — while a flag written
# the normal way (start-of-line, after whitespace, or wrapped in `backticks`/(parens))
# is still matched.
_FLAG_RE = re.compile(r"(?<![A-Za-z0-9])--[a-z0-9][a-z0-9-]*")
# A run of dot-separated integers (semver OR IPv4); classified after matching.
_DOTTED_RE = re.compile(r"\d+(?:\.\d+)+")
# Referenced bundle paths the prose points at (must exist under the skill dir).
_PATH_RE = re.compile(r"\b((?:references|scripts)/[A-Za-z0-9_.-]+\.(?:md|py|sh))\b")


class ConfigError(Exception):
    """Bad input/config (missing SKILL.md, unreadable file). Reported as exit 2,
    distinct from exit 1 (a coupling/structural violation in the authored prose)."""


def _scanned_files(skill_dir: Path):
    """Agent-facing prose: SKILL.md + references/*.md. ConfigError if the dir does
    not look like a skill bundle (no SKILL.md) — an empty/wrong --skill-dir must not
    silently scan 0 files and report a false PASS."""
    skill_md = skill_dir / "SKILL.md"
    if not skill_md.is_file():
        raise ConfigError(f"no SKILL.md found under {skill_dir} (nothing to lint)")
    files = [skill_md] + sorted((skill_dir / "references").glob("*.md"))
    return [f for f in files if f.is_file()]


def _read(f: Path) -> str:
    try:
        return f.read_text(encoding="utf-8")
    except OSError as e:
        raise ConfigError(f"{f}: {e}") from e


def _is_ipv4(run: str) -> bool:
    """A dotted run that is a valid dotted-quad IPv4 (4 octets, each 0-255)."""
    parts = run.split(".")
    return len(parts) == 4 and all(p.isdigit() and 0 <= int(p) <= 255 for p in parts)


def check_versions(rel: str, text: str, violations: list):
    """Check 1 — baked semver, excluding dotted-quad IPv4."""
    for i, line in enumerate(text.splitlines(), 1):
        for m in _DOTTED_RE.finditer(line):
            run = m.group(0)
            if _is_ipv4(run):
                continue                       # IPv4 safety advice, not a version
            if run.count(".") >= 2:            # 3+ components -> semver-like
                violations.append(
                    f"{rel}:{i}  baked version '{run}'  "
                    f"(defer versions to the installed tool; state requirements as capabilities)"
                )


def check_flags(rel: str, text: str, violations: list):
    """Check 2 — baked ODL option name (long --flag), minus the allowlist.

    Fixture (run with ``python -m doctest sync-skill-refs.py -v``): a mid-word
    double hyphen must NOT be read as a flag, while a real baked flag still is:

    >>> v = []; check_flags("f", "a high--level overview", v); v
    []
    >>> v = []; check_flags("f", "pass `--hybrid` to route", v); len(v)
    1
    """
    for i, line in enumerate(text.splitlines(), 1):
        for m in _FLAG_RE.finditer(line):
            tok = m.group(0)
            after = line[m.end():m.end() + 1]
            if tok.endswith("-") or after == "*":
                continue                       # wildcard/meta, e.g. --enrich-*
            if tok in ALLOWED_FLAGS:
                continue
            violations.append(
                f"{rel}:{i}  baked option '{tok}'  "
                f"(express intent as a capability; discover the flag from the installed --help)"
            )


def check_sot(skill_md_text: str, violations: list):
    """Check 3 — Source-of-truth concept present in SKILL.md (loose)."""
    if not any(re.search(p, skill_md_text, re.IGNORECASE) for p in _SOT_PATTERNS):
        violations.append(
            "SKILL.md  missing the source-of-truth concept "
            "(expected a phrase like 'source-of-truth' / 'read the installed ... help')"
        )


def check_fences(rel: str, text: str, violations: list):
    """Check 4a — markdown code-fence balance (even count of ``` per file)."""
    n = len(re.findall(r"```", text))
    if n % 2 != 0:
        violations.append(f"{rel}  unbalanced code fences (found {n} ``` markers; expected an even count)")


def check_paths(skill_dir: Path, rel: str, text: str, violations: list):
    """Check 4b — referenced references/… and scripts/… paths exist under the bundle."""
    for i, line in enumerate(text.splitlines(), 1):
        for m in _PATH_RE.finditer(line):
            ref = m.group(1)
            if not (skill_dir / ref).is_file():
                violations.append(f"{rel}:{i}  references missing path '{ref}' (not found under the skill bundle)")


def check_frontmatter(skill_md_text: str, violations: list):
    """Check 4c — SKILL.md frontmatter parses (light, dependency-free): opens with
    a `---` delimiter, has a closing `---`, and names the skill (`name:`)."""
    lines = skill_md_text.splitlines()
    if not lines or lines[0].strip() != "---":
        violations.append("SKILL.md  missing opening frontmatter delimiter '---' on line 1")
        return
    closing = next((i for i in range(1, len(lines)) if lines[i].strip() == "---"), None)
    if closing is None:
        violations.append("SKILL.md  frontmatter has no closing '---' delimiter")
        return
    block = lines[1:closing]
    if not any(re.match(r"\s*name\s*:", ln) for ln in block):
        violations.append("SKILL.md  frontmatter has no 'name:' key")


def lint(skill_dir: Path) -> list:
    """Run every check; return a flat list of violation strings (empty == clean)."""
    files = _scanned_files(skill_dir)
    violations: list[str] = []
    skill_md_text = _read(skill_dir / "SKILL.md")

    for f in files:
        rel = f.relative_to(skill_dir).as_posix()
        text = _read(f)
        check_versions(rel, text, violations)
        check_flags(rel, text, violations)
        check_fences(rel, text, violations)
        check_paths(skill_dir, rel, text, violations)

    check_sot(skill_md_text, violations)
    check_frontmatter(skill_md_text, violations)
    return violations


def parse_args(argv=None):
    p = argparse.ArgumentParser(
        description="Version-coupling lint over the skill's agent-facing prose (tripwire, not proof).")
    p.add_argument("--skill-dir", type=Path, default=_BUNDLE,
                   help="skill bundle to lint (default: the sibling ../odl-pdf)")
    return p.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    try:
        violations = lint(args.skill_dir)
    except ConfigError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 2

    if violations:
        print("Version-coupling / structural violations found:")
        for v in violations:
            print(f"  {v}")
        print(f"\n{len(violations)} violation(s). This lint is a tripwire, not proof — "
              "authoring discipline + release review remain the real guard.")
        return 1

    print(f"OK: {args.skill_dir.name} prose is free of baked versions/options, "
          "keeps the source-of-truth concept, and passes structural checks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
