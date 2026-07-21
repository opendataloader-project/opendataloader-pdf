# Maintaining the odl-pdf skill

This skill deliberately carries **no option inventory** — the option surface is resolved at
runtime from the installed CLI's `--help`, a checkout's `options.json`, or the homepage
CLI Options Reference (see SKILL.md "Version & option authority"). That removes per-release
inventory maintenance. This file covers what remains: what CI guards automatically, and the
small set of version-specific *behaviors* a human must re-check when ODL changes.

Validated against **ODL 2.5.0**.

## What CI guards automatically (fails loudly at the source change)

`scripts/sync-skill-refs.py` runs in `skill-drift-check.yml` and is triggered when
`options.json`, `SKILL.md`, `references/**`, or the script changes. Because this skill lives
in the ODL repo, changing a CLI option (which regenerates `options.json` via `npm run sync`)
runs this check on that same PR. It is a lightweight consumer-driven contract:

- **Tier 1 — referenced option names.** Every `--option` the skill names must resolve to a
  category: client (in `options.json`), server (`SERVER_OPTIONS`), or a small `EXCLUDED_TOKENS`
  set (standard CLI `--help`/`--version`, tesseract `--help-extra`, the bundled `quick-eval.py`
  `--verbose`). An unknown token fails the check.
- **Tier 2 — decision-critical values (`REFERENCED_VALUES`) and defaults (`REFERENCED_DEFAULTS`).**
  Each registered value must exist in `options.json`, and each registered default must match
  `options.json`'s `default` field. A removed value or a flipped default fails the check.

So an ODL option **rename, removal, value removal, or default flip** that the skill depends on
is caught on the option-changing PR. Exit codes: 0 = clean, 1 = drift, 2 = input/config error.

## What CI does NOT catch — re-check these on an ODL release

A name/value/default contract cannot see **semantic/behavior** changes or the **server** flag
surface. When cutting an ODL release, re-verify the skill's prose against current behavior for:

- **`--to-stdout` streaming** — text/markdown only, `text` precedence over `markdown`, `json`/`html`
  emit nothing (SKILL.md Stage 2, format-guide). Re-check if stdout routing changes.
- **Enrichment gating** — `--enrich-formula`/`--enrich-picture-description` (server) only run with
  client `--hybrid-mode full`; silently skipped in `auto` (Gotcha 2).
- **`--use-struct-tree` precedence over `--hybrid`** on tagged PDFs — hybrid backend not called, a
  warning is emitted (Gotcha; issue #633).
- **`--hybrid-fallback`** preserves completion, not quality (falls back to local Java).
- **content-safety filters** — off-page/tiny/hidden-OCG on by default, **hidden-text OFF by default**
  (`FilterConfig`); the safety caution depends on this.
- **hancom-ai OCR runs on the backend** (not the client) and needs a reachable server.
- **veraPDF/font-parser crash class** — the Stage 5 crash branch assumes it happens in
  preprocessing, before page triage (so OCR/hybrid can't bypass). Re-check if the engine changes.
- **batch failure semantics** — ordinary per-file failures are recorded and the run continues
  (exit 1 aggregate); a JVM/OOM crash aborts the whole call.
- **server (`opendataloader-pdf-hybrid`) flags** — these live in `SERVER_OPTIONS` (hand-listed;
  they are NOT in the client `options.json`, so a *renamed server flag* is NOT caught by CI).
- **`--version`** still does not exist (the CLI exposes only `-h`/`--help`).

If any of these changed, update the relevant SKILL.md/references prose and the eval that covers it.

## Future option (deferred)

The gap above (semantic/behavior drift) could be closed by running a small behavioral eval —
an agent actually driving ODL through a scenario — in the ODL CI, so a behavior change fails at
the source. This needs an LLM + agent in CI (cost/flakiness), so it is **deliberately deferred**
until a real behavior-drift incident justifies it (do not build it speculatively). Until then,
the release-review checklist above is the mechanism.

## Registries

Keep `SERVER_OPTIONS`, `EXCLUDED_TOKENS`, `REFERENCED_VALUES`, and `REFERENCED_DEFAULTS` in
`scripts/sync-skill-refs.py` **small and reasoned** — they are the decision-critical surface the
skill depends on, not a re-duplicated inventory. Every addition should carry a reason.
