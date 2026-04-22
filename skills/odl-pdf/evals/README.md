# odl-pdf Skill Evaluations

This directory holds the scenario-based evaluations for the `odl-pdf` skill and the results of running them against Claude models.

## Files

| File | Purpose |
|---|---|
| `evals.json` | Scenario definitions: user inputs, expected recommendations, required phrases, forbidden phrases |
| `runs/<utc-timestamp>.json` | One report per evaluation run. Committed when the run is meaningful evidence (e.g., after a significant skill change) |

## Running the Evaluations

The runner lives at `scripts/run-evals.py`. It loads `SKILL.md` as the system prompt, sends each scenario's `user_input` as a user message to each target model, and checks the response against `must_mention` (all phrases must appear) and `must_not_mention` (none may appear).

### Prerequisites

```bash
pip install anthropic
export ANTHROPIC_API_KEY=sk-ant-...
```

### Default run — Haiku 4.5, Sonnet 4.6, Opus 4.7

```bash
python scripts/run-evals.py
```

Writes `evals/runs/<timestamp>.json` and exits `0` if all pass, `1` if any fail.

### Run against one model

```bash
python scripts/run-evals.py --model claude-sonnet-4-6
```

The `--model` flag can be repeated to target a specific subset.

### Other flags

- `--max-tokens <n>` — raise the per-call output limit (default 2048)
- `--skip-cache` — disable `cache_control` on the system prompt (useful for one-shot checks)
- `--output <path>` — override the default report path

## Interpreting Reports

Each run report contains:

- `summary.pass` / `summary.fail` / `summary.total` — aggregate counts across all (model × scenario) cells
- `results[]` — one entry per cell with `pass`, `missing_required`, `leaked_forbidden`, token `usage`, elapsed time, and a `response_preview` (first 500 chars)

Typical failure modes:

- **Missing required phrase** — the model did not surface a concept the skill should have prompted (e.g., failed to mention `--hybrid-mode full` for enrichment scenarios)
- **Leaked forbidden phrase** — the model proposed an approach the skill explicitly warns against (e.g., looping `convert()` per file)
- **API error** — the `error` field is set; no tokens were consumed on that cell

## CI

The evaluation runner is also invocable via GitHub Actions (`.github/workflows/skill-evals.yml`). The workflow is **manual-trigger only** (`workflow_dispatch`) because each run consumes Anthropic API credits; it is not wired to every PR. Maintainers should run it:

- After substantive `SKILL.md` or reference edits
- Before tagging a release
- On request when a new model becomes available

The workflow reads `ANTHROPIC_API_KEY` from a repository secret of the same name.

## Adding a New Scenario

1. Append an entry to `evals.json` under `evals[]` with a fresh `id` (e.g., `eval-006`).
2. Include `scenario`, `user_input`, `expected_recommendations`, `must_mention`, and `must_not_mention`.
3. Run `python scripts/run-evals.py` locally and confirm the new case passes on at least one model before committing.
4. If the case reveals a gap, update `SKILL.md` or a reference file first — do not lower the bar in `must_mention` to make a failing case pass.

Scenario coverage should include:

- **Normal** — straightforward use of a core feature
- **Error** — recoverable failure mode (missing prerequisite, silent-skip trap)
- **Boundary** — edge conditions (very large input, unusual OS, password-protected PDF, etc.)
