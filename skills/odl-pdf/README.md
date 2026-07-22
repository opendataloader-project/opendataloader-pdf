# odl-pdf — Agent Skill for using OpenDataLoader PDF

A skill that helps an **AI coding assistant use [opendataloader-pdf](https://github.com/opendataloader-project/opendataloader-pdf) (ODL) correctly** — choosing the right mode/format for the user's goal, verifying that extraction actually succeeded (not just that the command exited zero), and diagnosing the silent failures ODL does not report (empty output on a clean exit, OCR for scanned PDFs, silently skipped enrichment, backend fallbacks that drop quality).

This README is for **humans** (how to install/enable the skill). `SKILL.md` is the instruction set the **agent** reads.

## What it is (and what it deliberately is not)

This skill is a **durable procedure, not a catalogue of ODL's current options.** ODL's option names, values, and defaults change between releases, so the skill never spells them out. Instead it teaches the agent to:

1. **Read the installed tool's own `--help` at runtime** — that output is the authority for the version actually in front of the user; the skill's own memory and any published reference are secondary.
2. **Translate the user's goal into a capability**, discover the option that expresses it from the installed help, and build the minimal command.
3. **Verify the extraction against the user's intent** — a zero exit code does not mean the extraction succeeded.
4. **Guard the silent-failure hazards** that no `--help` text or casual probe will warn about (enrichment skipped in mixed routing, a fallback that preserves completion but drops OCR/enrichment quality, structured outputs that never stream to stdout, a structure-tagged path pre-empting the backend, a parser crash that fires before any page-level mode can help).

Because option specifics are read at runtime, this skill does **not** go stale when ODL renames a flag or flips a default. What still needs a human release review is decision-critical *behavior* — see the sibling maintenance kit below.

It does **not** cover PDF/UA accessibility-compliance tagging, PDF merge/split/rotate, or Office-format conversion (out of scope).

## Install / enable

The skill is the folder `skills/odl-pdf/` in the [agentskills.io open format](https://agentskills.io) (`SKILL.md` + `references/` + `scripts/`).

- **Claude Code / claude.ai**: copy `skills/odl-pdf/` into your skills location (e.g. `~/.claude/skills/odl-pdf/` for user scope, or your project's `.claude/skills/`), or install it via a plugin/marketplace that bundles it.
- **Other agents that read Agent Skills** (e.g. Codex): point them at this folder per your tool's skill mechanism.
- **Agents that do not yet read `SKILL.md`** (e.g. Copilot, Gemini today): this skill does not auto-load there yet. A portable `llms.txt` / `AGENTS.md` derivation is planned as a follow-up.

No build step. The skill drives ODL's CLI/SDK directly; it does not require an MCP server.

## Requirements

The skill assumes the user has (or will install) **opendataloader-pdf** and its declared runtime prerequisite (a supported Java). The skill itself walks the user through discovering and installing these — it does not name a version or a vendor, because those belong to the installed package; see `references/installation-matrix.md`. The AI/OCR backend (used for OCR, complex tables, and enrichment) additionally needs the hybrid server; the skill explains when and how.

## Contents

| Path | For | Purpose |
|------|-----|---------|
| `SKILL.md` | agent | The runtime procedure + guardrails the agent follows (source-of-truth rule, VERIFY, silent-failure hazards) |
| `references/` | agent | Loaded on demand: installation, option interactions, hybrid backend, formats, integration, eval metrics |
| `scripts/` | agent | `detect-env.sh`, `hybrid-health.sh`, `verify-json.py`, `quick-eval.py` — helpers the skill runs at runtime |

This folder is the **complete installable skill** — copy it and nothing else. The maintenance kit (decision-correctness evals, the version-coupling lint, and the release-review checklist) is **not** part of the installed skill; it lives in the sibling `skills/odl-pdf-maintenance/` in the ODL repo. End users don't need it — to develop, update, or verify the skill, see that folder's `MAINTAINING.md`.
