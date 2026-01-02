---
name: bench
description: Run benchmark and analyze PDF parsing performance
---

# /bench

Builds Java and runs the full benchmark suite, then analyzes results.

## Execution Steps

1. Run `./scripts/bench.sh`
2. Extract metrics from `tests/benchmark/prediction/opendataloader/evaluation.json` using:
   ```bash
   jq '{summary, metrics, table_detection, speed}' tests/benchmark/prediction/opendataloader/evaluation.json
   ```
3. Output summary:
   - NID (reading order)
   - TEDS (table structure)
   - MHS (heading structure)
   - Table Detection F1/Precision/Recall
   - Speed (s/doc, total time)
4. Compare with thresholds in `tests/benchmark/thresholds.json`
5. Warn if regression detected

## Options

- `/bench --doc-id 01030000000189` - Run for a specific document only

## Notes

- Benchmark won't run if Java build fails
- First run installs Python dependencies via uv sync
