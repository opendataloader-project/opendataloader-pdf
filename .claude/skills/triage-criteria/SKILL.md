---
name: triage-criteria
description: Triage decision rules for routing PDF pages to Java vs Docling backend in hybrid mode
---

# Triage Criteria for Hybrid PDF Processing

This document defines the triage decision rules for routing pages to either the fast Java path or the Docling backend in hybrid mode.

## Strategy: Conservative (Minimize False Negatives)

The triage system uses a **conservative strategy** that prioritizes sending uncertain pages to the backend rather than missing complex content:

| Priority | Goal |
|----------|------|
| Primary | Minimize False Negatives (FN) - never miss pages that need backend processing |
| Secondary | Accept False Positives (FP) - acceptable to send simple pages to backend |
| Trade-off | Slower processing acceptable to ensure table accuracy |

### Why Conservative?

- **FN cost is high**: Missing a complex table means poor quality output
- **FP cost is low**: Backend can still process simple pages correctly, just slower
- **User expectation**: When hybrid mode is enabled, users expect accurate table extraction

## Decision Signals

| Signal | How to Extract | Threshold | Action if TRUE |
|--------|----------------|-----------|----------------|
| **TableBorder Presence** | `StaticContainers.getTableBordersCollection().getTableBorders(pageNumber)` is non-empty | Any tables detected | Route to BACKEND |
| **Suspicious Text Patterns** | `AbstractTableProcessor.getPagesWithPossibleTables()` includes page | Page identified as suspicious | Route to BACKEND |
| **High LineChunk Ratio** | Count `LineChunk` instances / total content count | > 0.3 (30%) | Route to BACKEND |
| **Grid Pattern Detection** | Multiple TextChunks with aligned baselines and large horizontal gaps | Gap > 3x text height | Route to BACKEND |
| **Cluster Table Detection** | `ClusterTableProcessor.processClusterDetectionTables()` returns non-empty | Any cluster tables found | Route to BACKEND |

### Signal Priority Order

Signals are evaluated in order. If any signal triggers routing to BACKEND, stop evaluation:

1. **TableBorder Presence** - Most reliable signal (border-based tables)
2. **Suspicious Text Patterns** - Catches tables without borders
3. **High LineChunk Ratio** - Indicates grid/border elements
4. **Grid Pattern Detection** - Spatial analysis of text layout
5. **Cluster Table Detection** - Machine learning-based detection (if enabled)

## Default Routing

If no signals trigger BACKEND routing:

| Condition | Action |
|-----------|--------|
| No table signals detected | Route to JAVA |
| Text-only content | Route to JAVA |
| Simple paragraphs/headings | Route to JAVA |

## Threshold Tuning Guide

### If False Negatives (FN) are High

Tables are being missed and processed incorrectly by Java:

| Adjustment | Effect |
|------------|--------|
| Lower LineChunk ratio threshold (e.g., 0.2) | More pages flagged for backend |
| Reduce gap threshold in grid detection (e.g., 2x height) | Detect looser table layouts |
| Enable cluster table detection | Catch borderless tables |
| Always route pages with images near text | Catch figure+table combinations |

### If Processing is Too Slow

Too many pages are being sent to backend unnecessarily:

| Adjustment | Effect |
|------------|--------|
| Raise LineChunk ratio threshold (e.g., 0.4) | Fewer pages flagged |
| Increase gap threshold in grid detection | Only strict table layouts |
| Require multiple signals to trigger BACKEND | Higher confidence threshold |
| Skip cluster detection for performance | Faster triage but less accurate |

**Warning**: Always verify FN rate before reducing sensitivity. Speed improvements should not come at the cost of table accuracy.

## Benchmark Metrics to Monitor

| Metric | Target | Description |
|--------|--------|-------------|
| `triage_recall` | >= 0.95 | Percentage of true table pages correctly routed to backend |
| `triage_fn` | <= 5 | Maximum number of table pages incorrectly routed to Java |
| `triage_fp_rate` | < 0.30 | Percentage of non-table pages sent to backend (acceptable overhead) |
| `triage_latency` | < 10ms/page | Time to make triage decision |

### Monitoring Commands

```bash
# Check triage performance after benchmark run
jq '.triage' tests/benchmark/prediction/opendataloader/evaluation.json

# Count documents with tables in benchmark set
cat docs/hybrid/research/documents-with-tables.txt | wc -l
# Result: 42 documents (from 200 total)
```

## Implementation Checklist

- [ ] Create `TriageProcessor` class in `org.opendataloader.pdf.processors`
- [ ] Implement `triageAllPages(List<List<IObject>> contents): Map<Integer, TriageResult>`
- [ ] Add `TriageResult` enum: `JAVA`, `BACKEND`
- [ ] Extract signals from existing processor code (see signals.md)
- [ ] Add triage metrics to benchmark evaluation
- [ ] Tune thresholds based on benchmark results

## Related Files

- **Signal extraction methods**: [signals.md](signals.md)
- **Design document**: [hybrid-mode-design.md](../../docs/hybrid/hybrid-mode-design.md)
- **Documents with tables**: [documents-with-tables.txt](../../docs/hybrid/research/documents-with-tables.txt)
