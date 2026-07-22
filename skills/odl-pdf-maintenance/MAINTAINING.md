# Maintaining the odl-pdf skill

**This directory (`skills/odl-pdf-maintenance/`) is the skill's maintenance kit — it is NOT
part of the installed skill.** The distributed skill is the sibling `../odl-pdf/`
(`SKILL.md` + `references/` + the runtime `scripts/`); end users copy only that folder. This
kit holds what developers need and users don't: the version-coupling lint
(`sync-skill-refs.py`), decision-correctness evals (`evals/evals.json`), and this checklist.
All of it stays in the ODL repo so it travels with the tool, but none of it ships to users.

The skill deliberately carries **no option inventory** — option names, values, and defaults are
resolved at runtime from the installed tool's own `--help` (see `../odl-pdf/SKILL.md`
"Source-of-truth rule"). That removes per-release inventory maintenance: an ODL flag rename,
value change, or default flip does not break the skill, because the skill never spelled the flag
in the first place. This file covers what remains: the one thing that stays true across releases,
what CI guards automatically, and what a human must re-check.

Last release-reviewed against **ODL 2.5.0** (provenance only — this is the release the
category-B checklist below was last verified against, not a version the skill requires).

## What to keep vs. what to defer (the one durable rule)

The redefinition splits the skill's content in two, and this split is the whole maintenance model:

- **Syntax — DEFER to the installed `--help`.** Option names, accepted values, defaults, and
  versions are version-specific. They must **not** appear as fact in the skill's prose; the agent
  reads them from the installed tool at runtime. Keeping them out is exactly what the lint below
  enforces.
- **Category-B behaviors — KEEP as principles.** The decision-critical behaviors that `--help`
  never reveals (silent enrichment skips, quality-dropping fallbacks, non-streaming outputs, a
  structure-tagged path pre-empting the backend, a parser crash before page handling) are the
  reason the skill exists over "just read `--help`." These stay in the skill, written as
  *principles* (what happens and why it's a trap), **not** tied to a specific flag name. When ODL
  changes, you re-verify the behavior (checklist below); you don't re-list a flag.

## What CI guards automatically — the version-coupling lint

`sync-skill-refs.py` runs in `.github/workflows/skill-drift-check.yml` and is triggered when
`../odl-pdf/SKILL.md`, `../odl-pdf/references/**`, or the script itself changes. It is a
**tripwire, not proof** — the mechanical floor under the "defer syntax" rule above. It scans the
agent-facing prose (`SKILL.md` + `references/*.md`; not the scripts) and fails (exit 1) on:

- **A baked semantic version** (`\d+\.\d+\.\d+`), **excluding dotted-quad IPv4** (`127.0.0.1`,
  `0.0.0.0` are safety advice, not versions). State requirements as capabilities; defer the
  version to the installed tool.
- **A baked ODL option name** (a long `--flag`), **minus a small reasoned allowlist** of
  legitimate non-ODL / meta flags (`--help`, `-h`, and the bundled `quick-eval.py --verbose`). A
  regex cannot tell an ODL flag from a pip/jq/git flag, so an allowlist is unavoidable; it is
  derived from what the reshaped prose actually uses, not from any ODL option inventory. If
  install prose later cites a pip/venv flag (e.g. PEP-668 `--break-system-packages`), add it to
  `ALLOWED_FLAGS` **with a one-line reason** — do not broaden speculatively.
- **A missing source-of-truth concept** in SKILL.md — a loose phrase check (e.g. "source-of-truth"
  / "read the installed … help"), so that the durable-procedure thesis can't be quietly edited out.
- **Structural** checks: referenced `references/…` and `scripts/…` paths exist; markdown code
  fences balance; SKILL.md frontmatter is delimited and names the skill.

Exit codes: **0** clean, **1** coupling/structural violation, **2** input/config error. The lint
defaults to resolving the sibling skill (`../odl-pdf`), so CI runs it with **no arguments**.

**Honest limit.** The lint catches only long `--flag` forms. It does **not** catch `-short` flags,
bare-word option names, baked option *values*, or backend/engine names asserted as fact — nor does
it validate behavior. Those are the job of **authoring discipline and human release review**; the
lint is a floor, not a ceiling. Never read a green lint as "the skill is current."

## What CI does NOT catch — re-check these category-B behaviors on an ODL release

The lint sees coupling in the *text*; it cannot see a **behavior/semantics** change in ODL itself.
When cutting an ODL release, re-verify the skill's principles against current behavior for:

- **stdout streaming** — which output kinds stream vs. only write to a file, and their precedence.
  Re-check if stdout routing changes (SKILL.md silent-failure hazard + format-guide).
- **Enrichment gating** — enrichment (formula, figure description) only runs when the *whole*
  document is routed to the AI backend; pages judged "simple" in a mixed/auto mode silently skip it.
- **Structure-tagged input pre-empting the backend** — a source that already carries a usable
  structure tree can cause the tool to honor it and **not call the backend** (often only a warning).
- **Fallback preserves completion, not quality** — a backend error can fall back to the local path
  and still write an output file, so the run "succeeds" while the required OCR/enrichment did not.
- **content-safety filters** — the default on/off posture of the off-page/tiny/hidden-OCG and
  hidden-text filters; the safety caution ("never disable filters to get more content") depends on it.
- **Backend/OCR runs server-side** — OCR/enrichment execute on the hybrid backend, not the client,
  and need a reachable server; a "success" with no backend is a silent local fallback.
- **Parser/preprocessing crash class** — the crash-before-page-handling hazard assumes a malformed
  font/parse failure aborts *before* any page-level mode or OCR decision, so switching mode or
  enabling OCR cannot bypass it. Re-check if the engine changes.
- **batch failure semantics** — ordinary per-file failures are recorded and the run continues (exit
  1 aggregate); a JVM/OOM crash aborts the whole call.
- **Java prerequisite** — the skill must not install a JDK, name a vendor, or hand over a
  package-manager command; re-check the required Java floor against the installed package's own
  declaration.
- **`--version` still does not exist** — the CLI exposes only `-h`/`--help` as the source of truth.

If any of these changed, update the affected principle in `../odl-pdf/` and the eval that covers it.
Keep the prose as a *principle* — do not fix a behavior drift by baking the new flag name back in.

## Behavioral evals (`evals/evals.json`)

`evals/evals.json` is the decision-correctness spec: each scenario carries the expected decision,
required evidence/actions, and forbidden actions, under a **frozen** scoring contract. It is
**agent-judged and needs a real ODL** — run it on demand with a behavioral eval runner (a blind
executor drives the skill against an installed ODL on each scenario; an independent judge scores the
answer against the frozen rubric) before a release or after a substantive SKILL.md change. Wiring
this into CI is **deliberately deferred** — it needs an LLM + agent + a live tool (cost/flakiness).
Until a real behavior-drift incident justifies it, the release-review checklist above plus on-demand
eval runs are the mechanism.

## Authoring discipline (the real guard)

The lint and the checklist are a floor. The actual guarantee comes from how the skill is written:
express every intent as a **capability** the agent discovers at runtime, keep category-B traps as
**principles** rather than flag lists, and run a human release review against current ODL behavior.
The only hand-maintained list in the lint is `ALLOWED_FLAGS` — keep it **small and reasoned**, one
line of justification per entry; it is not a place to re-introduce an option inventory.
