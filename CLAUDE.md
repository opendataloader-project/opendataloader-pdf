# CLAUDE.md

## Gotchas

After changing CLI options in Java, **must** run `npm run sync` — this regenerates `options.json` and all Python/Node.js bindings. Forgetting this silently breaks the wrappers.

When using `--enrich-formula` or `--enrich-picture-description` on the hybrid server, the client **must** use `--hybrid-mode full`. Otherwise enrichments are silently skipped (they only run on the backend, not in Java).

## Conventions

`content/docs/` auto-syncs to opendataloader.org on release. Edits here go live.

## Benchmark

- `/bench` — Run benchmark and analyze results
- `/bench-debug <doc_id>` — Debug specific document failures
- CI fails if scores drop below `tests/benchmark/thresholds.json`
- Metrics: **NID** (reading order), **TEDS** (table structure), **MHS** (heading structure), **Table Detection F1**, **Speed**
