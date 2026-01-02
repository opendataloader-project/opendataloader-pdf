---
name: triage-lab
description: Triage logic experiment records and optimization history
---

# Triage Lab - Experiment Records

This skill manages experiment records and optimization history for triage logic.

## Current Implementation

**File**: `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/TriageProcessor.java`

### Signal Priority (classifyPage method)
1. `hasTableBorder` - TableBorder presence (confidence: 1.0)
2. `hasVectorTableSignal` - Grid lines, border lines, line art (confidence: 0.95)
3. `hasTextTablePattern` - Text patterns with consecutive validation (confidence: 0.9)
4. `hasSuspiciousPattern` - Y-overlap or large gap detection (confidence: 0.85)
5. `lineToTextRatio > 0.3` - High line chunk ratio (confidence: 0.8)
6. `alignedLineGroups >= 5` - Aligned baseline groups (confidence: 0.7)

### Key Thresholds
| Parameter | Value | Location |
|-----------|-------|----------|
| LINE_RATIO_THRESHOLD | 0.3 | TriageProcessor:41 |
| ALIGNED_LINE_GROUPS_THRESHOLD | 5 | TriageProcessor:46 |
| GRID_GAP_MULTIPLIER | 3.0 | TriageProcessor:49 |
| MIN_LINE_COUNT_FOR_TABLE | 8 | TriageProcessor:57 |
| MIN_GRID_LINES | 3 | TriageProcessor:60 |
| MIN_CONSECUTIVE_PATTERNS | 2 | TriageProcessor:79 |

---

## Experiment History

### Experiment 001 (2026-01-03): FP Cause Analysis

**Goal**: Identify root causes of high False Positive rate

**Baseline**:
- Documents: 200 (42 with tables)
- TP: 41, TN: 48, FP: 110, FN: 1
- Precision: 27.15%, Recall: 97.62%, F1: 42.49%

**FP by Signal**:
| Signal | Count | % |
|--------|-------|---|
| hasSuspiciousPattern | 65 | 59.1% |
| hasVectorTableSignal | 23 | 20.9% |
| hasTableBorder | 14 | 12.7% |
| hasTextTablePattern | 5 | 4.5% |
| alignedLineGroups | 2 | 1.8% |
| highLineRatio | 1 | 0.9% |

**Root Cause**: Y-overlap check in `hasSuspiciousPattern` is too sensitive
- Condition `previous.getTopY() < current.getBottomY()` triggers on normal multi-column layouts

**Experiments**:
| Config | Precision | Recall | F1 | FP | FN |
|--------|-----------|--------|-----|-----|-----|
| Baseline | 27.15% | 97.62% | 42.49% | 110 | 1 |
| Disable Y-overlap | 36.28% | 97.62% | 52.90% | ~69 | 1 |
| Only Reliable Signals | 50.67% | 90.48% | 64.96% | ~38 | 4 |
| Disable SuspiciousPattern | 39.22% | 95.24% | 55.56% | ~64 | 2 |
| Require 3+ patterns | 37.38% | 95.24% | 53.69% | ~67 | 2 |

**Recommendation**:
- To maintain recall: Remove Y-overlap check (Precision +9%, Recall unchanged)
- To optimize F1: Use only reliable signals (F1 +22%, Recall -7%)

**FN Documents**:
- `01030000000110`: Missed by all experiments (needs investigation)
- `01030000000122`, `01030000000116`, `01030000000117`: Only detected by SuspiciousPattern

**Applied**: Y-overlap check removed (2026-01-03)

---

### Experiment 002 (2026-01-03): Further FP Reduction

**Goal**: Reduce remaining 72 FPs after Y-overlap removal

**Current FP by Signal** (after Experiment 001):
| Signal | Count | % |
|--------|-------|---|
| hasSuspiciousPattern | 21 | 29.2% |
| hasTableBorder | 14 | 19.4% |
| hasVectorTableSignal | 13 | 18.1% |
| alignedLineGroups | 10 | 13.9% |
| unknown | 8 | 11.1% |
| hasTextTablePattern | 5 | 6.9% |
| highLineRatio | 1 | 1.4% |

**Experiment 2A: Gap Multiplier** (hasSuspiciousPattern)
| Gap | Precision | Recall | F1 | FP | FN |
|-----|-----------|--------|-----|-----|-----|
| 3.0 (current) | 37.86% | 92.86% | 53.79% | 64 | 3 |
| 4.0 | 37.86% | 92.86% | 53.79% | 64 | 3 |
| 5.0 | 37.86% | 92.86% | 53.79% | 64 | 3 |
| 6.0 | 37.86% | 92.86% | 53.79% | 64 | 3 |

→ No effect (Y-overlap removal already optimized this signal)

**Experiment 2B: AlignedLineGroups Threshold**
| Threshold | Precision | Recall | F1 | FP | FN |
|-----------|-----------|--------|-----|-----|-----|
| 3 (current) | 37.86% | 92.86% | 53.79% | 64 | 3 |
| 4 | 39.39% | 92.86% | 55.32% | 60 | 3 |
| **5** | **39.80%** | **92.86%** | **55.71%** | **59** | **3** |
| 6 | 39.80% | 92.86% | 55.71% | 59 | 3 |

→ **Recommended**: Threshold 5 (FP -5, Recall maintained)

**Experiment 2C: Vector Signal Criteria**
| LineCount | GridLines | Precision | Recall | F1 | FP | FN |
|-----------|-----------|-----------|--------|-----|-----|-----|
| 8, 3 (current) | | 37.86% | 92.86% | 53.79% | 64 | 3 |
| 10, 4 | | 38.24% | 92.86% | 54.17% | 63 | 3 |
| 12, 4 | | 37.62% | 90.48% | 53.15% | 63 | 4 |

→ Minimal effect (FP -1, higher values reduce Recall)

**Recommendation**:
- Apply `alignedLineGroups` threshold 3 → 5
- Expected: FP 64 → 59 (-5), Recall 92.86% (maintained), F1 +1.92%

**Applied**: alignedLineGroups threshold 3 → 5 (2026-01-03)

**Actual Results**:
| Metric | Before (Exp 001) | After (Exp 002) | Change |
|--------|------------------|-----------------|--------|
| FP | 72 | 67 | -5 |
| FN | 1 | 1 | 0 |
| Precision | 36.28% | 37.96% | +1.68% |
| Recall | 97.62% | 97.62% | 0 |
| F1 | 52.90% | 54.67% | +1.77% |

**Next Steps**:
- Investigate `hasTableBorder` FPs (14 cases, external library)
- Investigate `unknown` FPs (8 cases)

---

## Template for New Experiments

```markdown
### Experiment XXX (YYYY-MM-DD): [Title]

**Goal**: [What are you trying to improve?]

**Changes**: [What did you modify?]

**Results**:
| Config | Precision | Recall | F1 | FP | FN |
|--------|-----------|--------|-----|-----|-----|
| Before | | | | | |
| After | | | | | |

**Conclusion**: [What did you learn? Should this be applied?]

**Next Steps**: [What to try next?]
```

---

## How to Run Experiments

```bash
# Run triage accuracy test
./scripts/test-java.sh -Dtest=TriageProcessorIntegrationTest#testTriageAccuracyOnBenchmarkPDFs

# Debug specific document
./scripts/bench.sh --doc-id 01030000000110
```

## Related Files
- [TriageProcessor.java](../../java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/TriageProcessor.java)
- [TriageProcessorIntegrationTest.java](../../java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/hybrid/TriageProcessorIntegrationTest.java)
