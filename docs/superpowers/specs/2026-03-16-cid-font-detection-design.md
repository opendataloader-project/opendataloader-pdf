# CID Font Extraction Failure Detection

Issue: [#286](https://github.com/opendataloader-project/opendataloader-pdf/issues/286)

## Problem

PDFs with CID-keyed fonts that lack ToUnicode mappings produce no usable text from veraPDF extraction. veraPDF replaces unmappable characters with U+FFFD (replacement character), which `TextProcessor.replaceUndefinedCharacters()` then converts to spaces. The result is empty or whitespace-only output with no indication to the user of what went wrong.

Users currently resort to external tools (e.g., pdfplumber) to pre-screen PDFs for CID issues before passing them to opendataloader-pdf.

## Solution

Detect pages with high replacement character ratios and:

1. **Always**: emit a WARNING log explaining the problem and suggesting `--hybrid-mode`
2. **When hybrid mode is on**: automatically route affected pages to OCR backend via TriageProcessor

No new CLI options. Hybrid mode setting is respected as-is.

## Design

### Detection: Replacement Character Ratio

`TextProcessor.measureReplacementCharRatio(List<IObject>)` counts `\uFFFD` characters across all TextChunks on a page and returns the ratio (0.0–1.0).

**Threshold**: 30%. CID-affected pages typically show 90%+ replacement characters. 30% catches real problems while avoiding false positives from PDFs with occasional unmappable glyphs.

**Measurement point**: Inside `ContentFilterProcessor.getFilteredContents()`, immediately before `replaceUndefinedCharacters()` is called (line 74). At this point veraPDF has already inserted `\uFFFD` but the characters haven't been replaced with spaces yet, so measurement is accurate.

**Safety of measurement point**: The prior processing steps (`mergeCloseTextChunks`, `trimTextChunksWhiteSpaces`, `filterConsecutiveSpaces`, `splitTextChunksByWhiteSpaces`) do not affect U+FFFD characters. U+FFFD is not whitespace, so it is not trimmed, compressed, or used as a split boundary. The count is accurate at this position.

**Zero-text pages**: When a page has no TextChunk objects (e.g., image-only pages), the method returns 0.0 to avoid division by zero. This correctly avoids triggering the CID warning on non-text pages.

The method uses `ChunkParser.REPLACEMENT_CHARACTER_STRING` constant (not a hardcoded `"\uFFFD"` literal) to stay consistent with `replaceUndefinedCharacters()`.

### Data Flow

The measured ratio is stored in `StaticLayoutContainers` per page:

```
ContentFilterProcessor.getFilteredContents()
  │
  ├─ TextProcessor.measureReplacementCharRatio() → ratio
  ├─ StaticLayoutContainers.setReplacementCharRatio(pageNumber, ratio)
  ├─ if ratio >= 0.3: LOGGER.warning(...)
  └─ TextProcessor.replaceUndefinedCharacters()  // existing call
```

Note: `StaticLayoutContainers` currently stores global `ThreadLocal` scalars and lists, not per-page maps. Per-page data (e.g., bounding boxes) lives in `DocumentProcessor`. This change introduces a new per-page `Map<Integer, Double>` pattern to `StaticLayoutContainers`. We place it here rather than `DocumentProcessor` because it is layout-metadata consumed by `TriageProcessor`, keeping the triage data path self-contained. The existing `clearContainers()` method **must** be updated to clear this map to prevent cross-document data leakage in multi-document processing.

### Warning Log

Emitted from `ContentFilterProcessor` when ratio >= 0.3:

```
WARNING: Page 3: 94% of characters are replacement characters (U+FFFD).
This PDF likely contains CID-keyed fonts without ToUnicode mappings.
Text extraction may be incomplete. Consider using --hybrid-mode for OCR fallback.
```

This fires regardless of hybrid mode setting.

### Triage Routing

In `TriageProcessor.classifyPage()`, a new **Signal 0** is inserted before all existing signals (before TableBorder check). This signal only fires when hybrid mode is active, since `classifyPage()` is only called from `HybridDocumentProcessor`. In non-hybrid mode, only the warning log (from `ContentFilterProcessor`) is emitted:

```java
double replacementRatio = StaticLayoutContainers.getReplacementCharRatio(pageNumber);
if (replacementRatio >= 0.3) {
    return TriageResult.backend(pageNumber, 1.0, signals);
}
```

Priority is highest (confidence 1.0) because a page with mostly broken text extraction gains nothing from Java-path processing.

### Behavior Matrix

| Hybrid Mode | Ratio >= 30% | Result |
|---|---|---|
| OFF | Yes | Warning log. Java path produces incomplete text. |
| OFF | No | No change. Normal processing. |
| ON (auto) | Yes | Warning log + auto-route to BACKEND (OCR). |
| ON (auto) | No | No change. Normal triage. |
| ON (full) | Yes | Warning log. All pages already go to BACKEND. |
| ON (full) | No | No change. All pages already go to BACKEND. |

## Changes

### Modified Files

| File | Change |
|---|---|
| `TextProcessor.java` | Add `measureReplacementCharRatio()` static method |
| `ContentFilterProcessor.java` | Call measurement before `replaceUndefinedCharacters()`, store result, emit warning |
| `StaticLayoutContainers.java` | Add `replacementCharRatios` map with getter/setter, clear in `clearContainers()` |
| `TriageProcessor.java` | Add Signal 0: replacement ratio check before TableBorder signal |

### New Files

| File | Purpose |
|---|---|
| `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/CidFontDetectionTest.java` | e2e test using synthetic CID PDF |
| `java/opendataloader-pdf-core/src/test/resources/cid-font-no-tounicode.pdf` | Pre-generated test fixture (CID font, no ToUnicode) |
| `java/opendataloader-pdf-core/src/test/resources/generate-cid-test-pdf.py` | Generation script for reference |

### Modified Test Files

| File | Change |
|---|---|
| `TextProcessorTest.java` | 5 unit tests for `measureReplacementCharRatio()` |
| `TriageProcessorTest.java` | 3 unit tests for Signal 0 routing |

## Test Plan

### Unit Tests (TextProcessorTest)

- `testMeasureReplacementCharRatio_allReplacement` — all U+FFFD → 1.0
- `testMeasureReplacementCharRatio_noReplacement` — normal text → 0.0
- `testMeasureReplacementCharRatio_mixed` — 30% U+FFFD → 0.3
- `testMeasureReplacementCharRatio_emptyContents` — empty list → 0.0
- `testMeasureReplacementCharRatio_nonTextChunksIgnored` — non-text objects skipped

### Unit Tests (TriageProcessorTest)

- `testClassifyPage_highReplacementRatio_routesToBackend` — ratio 0.5 → BACKEND
- `testClassifyPage_lowReplacementRatio_noEffect` — ratio 0.1 → JAVA (default)
- `testClassifyPage_exactThreshold_routesToBackend` — ratio 0.3 → BACKEND

### Boundary Tests

- `testWarningNotEmitted_belowThreshold` — ratio 0.29 → no warning log emitted
- `testWarningEmitted_atThreshold` — ratio 0.30 → warning log emitted

### e2e Test (CidFontDetectionTest)

- Load pre-generated `cid-font-no-tounicode.pdf`
- Run through `ContentFilterProcessor.getFilteredContents()`
- Assert: `StaticLayoutContainers.getReplacementCharRatio(0) >= 0.3`
- Assert: warning log contains "replacement characters"

### Benchmark Regression

- Existing benchmark PDFs are normal documents with near-zero replacement ratios
- New logic does not affect existing test/benchmark results

## Not In Scope

- New CLI options (no `--cid-fallback` or similar)
- `npm run sync` not required (no CLI option changes)
- API signature changes (backward compatible)
- Benchmark threshold changes
