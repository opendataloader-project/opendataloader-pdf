# Triage Detection Known Issues

This document tracks known issues with the triage detection system based on benchmark evaluation results.

## Benchmark Summary (opendataloader-hybrid-docling)

| Metric | Value | Description |
|--------|-------|-------------|
| Precision | 60.87% | TP / (TP + FP) |
| Recall | 66.67% | TP / (TP + FN) |
| F1 Score | 63.64% | 2 * P * R / (P + R) |
| True Positive | 28 | Tables detected correctly |
| False Positive | 18 | Non-tables misrouted to backend |
| False Negative | 14 | Tables missed (critical!) |
| True Negative | 140 | Non-tables correctly to Java |

## Key Insight

**False Negatives are critical** - tables that are not detected will be processed by Java path and produce TEDS=0 (completely wrong table output).

**False Positives are acceptable** - non-table pages routed to backend are processed correctly, just with unnecessary latency.

---

## False Negative Documents (14 cases)

These documents contain tables but triage incorrectly routed them to JAVA path.
All have TEDS=0 in evaluation results.

| Document ID | Status | Root Cause | Notes |
|-------------|--------|------------|-------|
| 01030000000064 | TODO | Unknown | Needs analysis |
| 01030000000078 | TODO | Unknown | Needs analysis |
| 01030000000110 | TODO | Unknown | Needs analysis |
| 01030000000116 | TODO | Unknown | Needs analysis |
| 01030000000117 | TODO | Unknown | Needs analysis |
| 01030000000122 | TODO | Unknown | Needs analysis |
| 01030000000132 | TODO | Unknown | Needs analysis |
| 01030000000165 | TODO | Unknown | Needs analysis |
| 01030000000182 | TODO | Unknown | Needs analysis |
| 01030000000187 | TODO | Unknown | Needs analysis |
| 01030000000189 | TODO | Unknown | Needs analysis |
| 01030000000190 | TODO | Unknown | Needs analysis |
| 01030000000197 | TODO | Unknown | Needs analysis |
| 01030000000200 | TODO | Unknown | Needs analysis |

### Possible Root Causes for FN

1. **Borderless tables** - No LineChunks or TableBorder detected
2. **Sparse tables** - Large gaps not meeting threshold
3. **Complex layouts** - Table mixed with other content
4. **Single-column tables** - No horizontal gap pattern
5. **Vertical text** - Baseline detection fails

---

## False Positive Documents (18 cases)

These documents do NOT contain tables but triage incorrectly routed them to BACKEND.
Lower priority since backend still processes correctly (just slower).

| Document ID | Status | Likely Cause | Notes |
|-------------|--------|--------------|-------|
| 01030000000016 | Known | Aligned text columns | Multi-column layout |
| 01030000000036 | Known | Grid-like layout | Form or structured content |
| 01030000000037 | Known | Grid-like layout | Form or structured content |
| 01030000000038 | Known | Aligned sections | Header/data pattern |
| 01030000000044 | Known | Multi-column layout | Two-column text |
| 01030000000055 | Known | Form-like structure | Input fields pattern |
| 01030000000061 | Known | Header/footer pattern | Repeated structure |
| 01030000000067 | Known | Aligned data | List with values |
| 01030000000070 | Known | List with columns | Definition list |
| 01030000000071 | Known | Multi-column text | Newspaper style |
| 01030000000072 | Known | Multi-column text | Newspaper style |
| 01030000000073 | Known | Multi-column text | Newspaper style |
| 01030000000076 | Known | Aligned sections | Structured layout |
| 01030000000148 | Known | Form structure | Form fields |
| 01030000000155 | Known | Multi-column layout | Two-column |
| 01030000000171 | Known | Grid pattern | Chart or diagram |
| 01030000000172 | Known | Grid pattern | Chart or diagram |
| 01030000000183 | Known | Aligned sections | Structured content |

### Possible Solutions for FP

1. **Stricter grid threshold** - Require more aligned rows (currently 3)
2. **Column width analysis** - Real tables have consistent column widths
3. **Cell content density** - Tables have content in most cells
4. **Header row detection** - Tables typically have distinct headers

---

## Improvement Priorities

### High Priority (Reduce FN)

1. Analyze all 14 FN documents to identify patterns
2. Add cluster table detection as fallback signal
3. Lower thresholds for suspicious pattern detection
4. Consider ML-based table detection for ambiguous cases

### Medium Priority (Reduce FP)

1. Add column width consistency check
2. Implement header row detection
3. Check for cell content density
4. Distinguish multi-column text from tables

### Low Priority (Performance)

1. Cache triage results for multi-page documents
2. Optimize grid pattern detection algorithm
3. Parallel processing for signal extraction

---

## Test Coverage

See `TriageProcessorBenchmarkTest.java` for:
- Document classification tests
- Precision/Recall/F1 calculations
- Known issue documentation

Run tests:
```bash
cd java && mvn test -Dtest=TriageProcessorBenchmarkTest
```

---

## Related Files

- `TriageProcessor.java` - Main triage logic
- `TriageProcessorTest.java` - Unit tests
- `TriageProcessorBenchmarkTest.java` - Benchmark-based tests
- `signals.md` - Signal extraction documentation
