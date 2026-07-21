# odl-pdf — Agent Skill for using OpenDataLoader PDF

A skill that helps an **AI coding assistant use [opendataloader-pdf](https://github.com/opendataloader-project/opendataloader-pdf) (ODL) correctly** — choosing the right mode/format, verifying that extraction actually succeeded, and diagnosing failures (empty output on exit 0, OCR for scanned PDFs, silent enrichment skips, slow batches).

This README is for **humans** (how to install/enable the skill). `SKILL.md` is the instruction set the **agent** reads.

## What it does

Given a user asking to extract/parse a PDF with ODL, an agent that has this skill will: discover the environment → decide mode (local / hybrid / OCR) and format → run it → **verify the output is real** (not just exit 0) → diagnose problems. Options are read at runtime from the installed CLI's `--help`, so **option specifics** don't go stale between releases (decision prose and server-side behavior still get a release review).

Validated against **ODL 2.5.0**. It does **not** cover PDF/UA accessibility-compliance, PDF merge/split, or Office conversion (out of scope).

## Install / enable

The skill is the folder `skills/odl-pdf/` in the [agentskills.io open format](https://agentskills.io) (`SKILL.md` + `references/` + `scripts/`).

- **Claude Code / claude.ai**: copy `skills/odl-pdf/` into your skills location (e.g. `~/.claude/skills/odl-pdf/` for user scope, or your project's `.claude/skills/`), or install it via a plugin/marketplace that bundles it.
- **Other agents that read Agent Skills** (e.g. Codex): point them at this folder per your tool's skill mechanism.
- **Agents that do not yet read `SKILL.md`** (e.g. Copilot, Gemini today): this skill does not auto-load there yet. A portable `llms.txt` / `AGENTS.md` derivation is planned as a follow-up.

No build step. The skill drives ODL's CLI/SDK directly; it does not require an MCP server.

## Requirements

The skill assumes the user has (or will install) **opendataloader-pdf** and **Java 11+**. The skill itself walks the user through installing these — see `references/installation-matrix.md`. Hybrid mode (OCR / complex tables / enrichment) additionally needs the `opendataloader-pdf-hybrid` server; the skill explains when and how.

## Contents

| Path | For | Purpose |
|------|-----|---------|
| `SKILL.md` | agent | The workflow + guardrails the agent follows |
| `references/` | agent | Loaded on demand: installation, options, hybrid, formats, integration, eval metrics |
| `scripts/` | agent + CI | `detect-env.sh`, `hybrid-health.sh`, `verify-json.py`, `quick-eval.py` (helpers the skill runs) · `sync-skill-refs.py` (CI/maintainer drift-check vs `options.json`) |
| `evals/` | maintainers | Decision-correctness eval scenarios (agent-judged, no automated runner) |

## Maintainer note

Option names and decision-critical values referenced anywhere in the skill (SKILL.md + references) are checked against the repo's `options.json` by the `skill-drift-check` CI workflow (`scripts/sync-skill-refs.py`): every referenced `--option` must resolve to a known source (client/server/standard-CLI) and each registered decision-critical value must exist in `options.json`. The skill does not carry an option inventory; runtime authority is the installed CLI's `--help`.

### If an ODL option changes — what it touches and how to fix

Because the skill lives in the ODL repo, changing a client option regenerates `options.json` (`npm run sync`) and runs `skill-drift-check` on that same PR. Use this map when an option changes; `MAINTAINING.md` has the full behavioral release-review checklist.

| What changed on the ODL side | Drift-check catches it? | What to fix in the skill |
|------------------------------|-------------------------|--------------------------|
| **Client** option renamed or removed (it lives in `options.json`) | ✅ CI fails on the PR — the old `--name` no longer resolves | Rename/remove it wherever the skill mentions it (SKILL.md + `references/`); if it was decision-critical, update the guidance and the eval that covers it |
| **Client** option default flipped, or a value dropped, that the skill relies on | ✅ CI fails **iff** it's registered in `REFERENCED_DEFAULTS` / `REFERENCED_VALUES` | Update the stated default/value in the prose; re-check any decision branch that assumed the old one |
| Client value/default the skill states but **hasn't registered** | ❌ silent | Add it to `REFERENCED_VALUES` / `REFERENCED_DEFAULTS` in `scripts/sync-skill-refs.py` (with a one-line reason), then fix the prose — now it's guarded next time |
| **Server** (hybrid) option renamed/removed | ❌ silent — the server isn't in `options.json` | `references/hybrid-guide.md` points to `opendataloader-pdf-hybrid --help` as the value/default authority, so it self-heals for readers; update the decision-critical note if an *interaction* changed, and update `SERVER_OPTIONS` if a name the skill still cites changed |
| Option **behavior/semantics** change (same name, same default) | ❌ silent — a name/value contract can't see it | Update the affected prose + eval; this is exactly the case the `MAINTAINING.md` release-review list exists for (stdout precedence, enrichment↔full-mode, hidden-text default, veraPDF crash class, batch semantics, …) |
| You want the skill to **use a new option** | n/a | Reference it by name only where a decision needs it — do **not** re-introduce a full inventory; the installed `--help` stays the runtime authority |

Rule of thumb: **names and registered values/defaults fail loudly at the source PR; behavior and the server surface need the human release review.** Keep the registries in `sync-skill-refs.py` small and reasoned — they are the decision-critical surface, not a duplicated inventory.
