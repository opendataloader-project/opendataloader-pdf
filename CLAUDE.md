# CLAUDE.md

## Gotchas

After changing CLI options in Java, **must** run `npm run sync` — this regenerates `options.json` and all Python/Node.js bindings. Forgetting this silently breaks the wrappers.

When using `--enrich-formula` or `--enrich-picture-description` on the hybrid server, the client **must** use `--hybrid-mode full`. Otherwise enrichments are silently skipped (they only run on the backend, not in Java).

Processing uses `ForkJoinPool(availableProcessors)` for per-page parallelism. All `StaticContainers` and `StaticLayoutContainers` ThreadLocal state must be propagated to worker threads via `propagateState.run()` — missing a ThreadLocal causes silent data loss or NPE in parallel mode.

Hidden text detection (`--filter-hidden-text`) is **off by default** — it requires per-page PDF rendering via `ContrastRatioConsumer` which cannot be parallelized safely.

## Conventions

`content/docs/` auto-syncs to opendataloader.org on release. Edits here go live.

## Benchmark

- `./scripts/bench.sh` — Run benchmark (auto-clones opendataloader-bench for PDFs and evaluation logic)
- `./scripts/bench.sh --doc-id <id>` — Debug specific document
- `./scripts/bench.sh --check-regression` — CI mode with threshold check
- Benchmark code lives in [opendataloader-bench](https://github.com/opendataloader-project/opendataloader-bench)
- Metrics: **NID** (reading order), **TEDS** (table structure), **MHS** (heading structure), **Table Detection F1**, **Speed**

## Agent Skills

`skills/odl-pdf/` contains the public agent skill shipped with this project.

When adding or changing CLI options in Java:
1. Run `npm run sync` (regenerates options.json + Python/Node bindings)
2. Update `skills/odl-pdf/references/options-matrix.md` with the new option
3. CI (`skill-drift-check.yml`) will warn if step 2 is missed

The skill is written in English for external users. Do not include internal
team terminology or company-specific policies.
