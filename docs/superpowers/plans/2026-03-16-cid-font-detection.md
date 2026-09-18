# CID Font Extraction Failure Detection — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect pages where CID font extraction failed (high U+FFFD ratio), emit warning logs, and auto-route to OCR backend in hybrid mode.

**Architecture:** Measure replacement character ratio in ContentFilterProcessor before replacement, store in StaticLayoutContainers, consume in TriageProcessor as highest-priority signal. Warning log fires regardless of hybrid mode.

**Tech Stack:** Java 11+, JUnit Jupiter, veraPDF API (`ChunkParser.REPLACEMENT_CHARACTER_STRING`)

---

## File Structure

| File | Responsibility |
|---|---|
| `TextProcessor.java` | New `measureReplacementCharRatio()` method |
| `StaticLayoutContainers.java` | Per-page replacement ratio storage |
| `ContentFilterProcessor.java` | Measure + warn + store before replacement |
| `TriageProcessor.java` | Signal 0: route high-ratio pages to BACKEND |
| `TextProcessorTest.java` | Unit tests for measurement |
| `TriageProcessorTest.java` | Unit tests for Signal 0 routing |
| `CidFontDetectionTest.java` (new) | e2e test with synthetic PDF |
| `test/resources/cid-font-no-tounicode.pdf` (new) | Test fixture |
| `test/resources/generate-cid-test-pdf.py` (new) | Generation script (reference) |

All paths below are relative to `java/opendataloader-pdf-core/src/`.

---

## Chunk 1: Measurement + Storage

### Task 1: Add per-page ratio storage to StaticLayoutContainers

**Files:**
- Modify: `main/java/org/opendataloader/pdf/containers/StaticLayoutContainers.java`

- [ ] **Step 1: Add ThreadLocal map field and imports**

Add after line 40 (`imageFormat` field):

```java
private static final ThreadLocal<Map<Integer, Double>> replacementCharRatios = ThreadLocal.withInitial(HashMap::new);
```

Add to imports:

```java
import java.util.HashMap;
import java.util.Map;
```

- [ ] **Step 2: Add getter and setter**

Add after `setImageFormat()` (after line 145):

```java
public static void setReplacementCharRatio(int pageNumber, double ratio) {
    replacementCharRatios.get().put(pageNumber, ratio);
}

public static double getReplacementCharRatio(int pageNumber) {
    return replacementCharRatios.get().getOrDefault(pageNumber, 0.0);
}
```

- [ ] **Step 3: Clear in clearContainers()**

Add inside `clearContainers()` method, after line 51 (`imageFormat.set(...)`):

```java
replacementCharRatios.get().clear();
```

- [ ] **Step 4: Compile check**

Run: `cd java && mvn compile -pl opendataloader-pdf-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/containers/StaticLayoutContainers.java
git commit -m "feat: add per-page replacement char ratio storage to StaticLayoutContainers"
```

### Task 2: Add measureReplacementCharRatio to TextProcessor

**Files:**
- Modify: `main/java/org/opendataloader/pdf/processors/TextProcessor.java`
- Test: `test/java/org/opendataloader/pdf/processors/TextProcessorTest.java`

- [ ] **Step 1: Write failing tests**

Add to `TextProcessorTest.java` after the last test method (before closing `}`):

```java
@Test
public void testMeasureReplacementCharRatio_allReplacement() {
    List<IObject> contents = new ArrayList<>();
    contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
        "\uFFFD\uFFFD\uFFFD", 10, 10.0));

    double ratio = TextProcessor.measureReplacementCharRatio(contents);
    Assertions.assertEquals(1.0, ratio, 0.001);
}

@Test
public void testMeasureReplacementCharRatio_noReplacement() {
    List<IObject> contents = new ArrayList<>();
    contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
        "Hello World", 10, 10.0));

    double ratio = TextProcessor.measureReplacementCharRatio(contents);
    Assertions.assertEquals(0.0, ratio, 0.001);
}

@Test
public void testMeasureReplacementCharRatio_mixed() {
    List<IObject> contents = new ArrayList<>();
    // 3 replacement chars out of 10 total = 0.3
    contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
        "\uFFFD\uFFFD\uFFFDAbcdefg", 10, 10.0));

    double ratio = TextProcessor.measureReplacementCharRatio(contents);
    Assertions.assertEquals(0.3, ratio, 0.001);
}

@Test
public void testMeasureReplacementCharRatio_emptyContents() {
    List<IObject> contents = new ArrayList<>();

    double ratio = TextProcessor.measureReplacementCharRatio(contents);
    Assertions.assertEquals(0.0, ratio, 0.001);
}

@Test
public void testMeasureReplacementCharRatio_nonTextChunksIgnored() {
    List<IObject> contents = new ArrayList<>();
    contents.add(new ImageChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0)));
    contents.add(new TextChunk(new BoundingBox(1, 10.0, 30.0, 100.0, 40.0),
        "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD", 10, 10.0));

    double ratio = TextProcessor.measureReplacementCharRatio(contents);
    // Only TextChunks counted: 5/5 = 1.0
    Assertions.assertEquals(1.0, ratio, 0.001);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd java && mvn test -pl opendataloader-pdf-core -Dtest=TextProcessorTest#testMeasureReplacementCharRatio_allReplacement -q`
Expected: FAIL — `measureReplacementCharRatio` method not found

- [ ] **Step 3: Implement measureReplacementCharRatio**

Add to `TextProcessor.java` after `replaceUndefinedCharacters()` method (after line 53):

```java
public static double measureReplacementCharRatio(List<IObject> contents) {
    char replacementChar = ChunkParser.REPLACEMENT_CHARACTER_STRING.charAt(0);
    int totalChars = 0;
    int replacementChars = 0;
    for (IObject object : contents) {
        if (object instanceof TextChunk) {
            String value = ((TextChunk) object).getValue();
            totalChars += value.length();
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == replacementChar) {
                    replacementChars++;
                }
            }
        }
    }
    if (totalChars == 0) {
        return 0.0;
    }
    return (double) replacementChars / totalChars;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd java && mvn test -pl opendataloader-pdf-core -Dtest=TextProcessorTest -q`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/TextProcessor.java
git add java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/TextProcessorTest.java
git commit -m "feat: add measureReplacementCharRatio to TextProcessor

Counts U+FFFD replacement characters across TextChunks and returns
the ratio. Returns 0.0 for empty contents or pages with no text."
```

---

## Chunk 2: Warning Log + Triage Routing

### Task 3: Add warning log in ContentFilterProcessor

**Files:**
- Modify: `main/java/org/opendataloader/pdf/processors/ContentFilterProcessor.java`

- [ ] **Step 1: Add import for StaticLayoutContainers**

Add to imports:

```java
import org.opendataloader.pdf.containers.StaticLayoutContainers;
```

- [ ] **Step 2: Add measurement + warning before replaceUndefinedCharacters**

Insert immediately before line 74 (`TextProcessor.replaceUndefinedCharacters(...)`) in `getFilteredContents()`:

```java
double replacementCharRatio = TextProcessor.measureReplacementCharRatio(pageContents);
StaticLayoutContainers.setReplacementCharRatio(pageNumber, replacementCharRatio);
if (replacementCharRatio >= 0.3) {
    LOGGER.log(Level.WARNING,
        "Page {0}: {1,number,#.#%} of characters are replacement characters (U+FFFD). "
        + "This PDF likely contains CID-keyed fonts without ToUnicode mappings. "
        + "Text extraction may be incomplete. Consider using --hybrid-mode for OCR fallback.",
        new Object[]{pageNumber + 1, replacementCharRatio});
}
```

- [ ] **Step 3: Compile check**

Run: `cd java && mvn compile -pl opendataloader-pdf-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/ContentFilterProcessor.java
git commit -m "feat: detect CID font extraction failure and emit warning log

Measures U+FFFD ratio before replacement. Warns when >= 30% of
characters are replacement characters, suggesting hybrid mode."
```

### Task 4: Add Signal 0 to TriageProcessor

**Files:**
- Modify: `main/java/org/opendataloader/pdf/hybrid/TriageProcessor.java`
- Test: `test/java/org/opendataloader/pdf/hybrid/TriageProcessorTest.java`

- [ ] **Step 1: Write failing tests**

Add to `TriageProcessorTest.java` before the `// Helper methods` comment:

```java
@Test
public void testClassifyPage_highReplacementRatio_routesToBackend() {
    StaticLayoutContainers.clearContainers();
    StaticLayoutContainers.setReplacementCharRatio(0, 0.5);

    List<IObject> contents = new ArrayList<>();
    contents.add(createTextChunk(10, 100, 200, 120, "text"));

    TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

    Assertions.assertEquals(TriageDecision.BACKEND, result.getDecision());
    Assertions.assertEquals(1.0, result.getConfidence(), 0.001);
}

@Test
public void testClassifyPage_lowReplacementRatio_noEffect() {
    StaticLayoutContainers.clearContainers();
    StaticLayoutContainers.setReplacementCharRatio(0, 0.1);

    List<IObject> contents = new ArrayList<>();
    contents.add(createTextChunk(10, 100, 200, 120, "normal text"));

    TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

    Assertions.assertEquals(TriageDecision.JAVA, result.getDecision());
}

@Test
public void testClassifyPage_exactThreshold_routesToBackend() {
    StaticLayoutContainers.clearContainers();
    StaticLayoutContainers.setReplacementCharRatio(0, 0.3);

    List<IObject> contents = new ArrayList<>();
    contents.add(createTextChunk(10, 100, 200, 120, "text"));

    TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

    Assertions.assertEquals(TriageDecision.BACKEND, result.getDecision());
    Assertions.assertEquals(1.0, result.getConfidence(), 0.001);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd java && mvn test -pl opendataloader-pdf-core -Dtest=TriageProcessorTest#testClassifyPage_highReplacementRatio_routesToBackend -q`
Expected: FAIL — returns JAVA instead of BACKEND

- [ ] **Step 3: Add Signal 0 to classifyPage**

In `TriageProcessor.java`, in the `classifyPage()` method with `TriageThresholds` parameter, insert before the TableBorder check (before `// Signal 1: TableBorder presence`):

```java
// Signal 0: CID font extraction failure (highest priority)
// Only fires in hybrid mode (classifyPage is only called from HybridDocumentProcessor)
double replacementRatio = StaticLayoutContainers.getReplacementCharRatio(pageNumber);
if (replacementRatio >= 0.3) {
    return TriageResult.backend(pageNumber, 1.0, signals);
}
```

Add import at top of file:

```java
import org.opendataloader.pdf.containers.StaticLayoutContainers;
```

Also update the `classifyPage()` Javadoc (around line 617) to list Signal 0:

```
 * <p>Signal priority (highest to lowest):
 * <ol>
 *   <li>CID font extraction failure (replacement char ratio >= 30%)</li>
 *   <li>TableBorder presence</li>
 *   ...
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd java && mvn test -pl opendataloader-pdf-core -Dtest=TriageProcessorTest -q`
Expected: All tests PASS

- [ ] **Step 5: Run full test suite**

Run: `cd java && mvn test -pl opendataloader-pdf-core -q`
Expected: All tests PASS — no regressions

- [ ] **Step 6: Commit**

```bash
git add java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/TriageProcessor.java
git add java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/hybrid/TriageProcessorTest.java
git commit -m "feat: add CID font detection signal to TriageProcessor

Signal 0 (highest priority): routes pages with >= 30% replacement
characters to backend for OCR fallback in hybrid mode."
```

---

## Chunk 3: e2e Test + Test Fixture

### Task 5: Generate synthetic CID PDF test fixture

**Files:**
- Create: `java/opendataloader-pdf-core/src/test/resources/generate-cid-test-pdf.py`
- Create: `java/opendataloader-pdf-core/src/test/resources/cid-font-no-tounicode.pdf`

- [ ] **Step 1: Create test resources directory**

```bash
mkdir -p java/opendataloader-pdf-core/src/test/resources
```

- [ ] **Step 2: Write the generation script**

Create `java/opendataloader-pdf-core/src/test/resources/generate-cid-test-pdf.py`.

This script generates a minimal PDF with a Type0 (CID) font that has no ToUnicode CMap. The PDF must cause veraPDF to emit `\uFFFD` for the majority of text characters when parsed.

Approach: Use raw PDF syntax to embed a CID font referencing CID values without a ToUnicode mapping. Alternatively, use `reportlab` which provides low-level CID font control.

The implementer should:
1. Generate the PDF
2. Verify it with opendataloader-pdf: `cd java && mvn exec:java -pl opendataloader-pdf-cli -Dexec.mainClass="org.opendataloader.pdf.cli.CLIMain" -Dexec.args="../src/test/resources/cid-font-no-tounicode.pdf -f text" 2>&1`
3. Confirm output is mostly empty/whitespace (indicating `\uFFFD` → space replacement)
4. Confirm WARNING log about replacement characters appears

If generating a proper CID PDF proves difficult, search `odl-test-fixtures` for an existing PDF with CID font issues, or use a known CID-problematic PDF from the Korean pharmaceutical fixtures (pdf-003 through pdf-007).

- [ ] **Step 3: Commit fixture**

```bash
git add java/opendataloader-pdf-core/src/test/resources/cid-font-no-tounicode.pdf
git add java/opendataloader-pdf-core/src/test/resources/generate-cid-test-pdf.py
git commit -m "test: add synthetic CID font PDF test fixture

PDF with CID-keyed font without ToUnicode mapping for testing
replacement character detection. Generation script included."
```

### Task 6: Write e2e integration test

**Files:**
- Create: `test/java/org/opendataloader/pdf/processors/CidFontDetectionTest.java`

This test follows the pattern from `TriageProcessorIntegrationTest.java`:
`DocumentProcessor.preprocessing()` → `ContentFilterProcessor.getFilteredContents()` → assert ratio and warning.

- [ ] **Step 1: Write the integration test**

Create `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/CidFontDetectionTest.java`:

```java
/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Integration test for CID font extraction failure detection.
 *
 * Tests the full pipeline: PDF parsing → ContentFilterProcessor →
 * measurement → StaticLayoutContainers storage → warning log.
 */
public class CidFontDetectionTest {

    private static final Path CID_PDF_PATH = Paths.get(
        "src/test/resources/cid-font-no-tounicode.pdf");

    private static boolean pdfAvailable = false;

    @BeforeAll
    static void checkFixture() {
        pdfAvailable = Files.exists(CID_PDF_PATH) && Files.isRegularFile(CID_PDF_PATH);
        if (!pdfAvailable) {
            System.out.println("CID font test PDF not found: " + CID_PDF_PATH.toAbsolutePath());
            System.out.println("Skipping integration tests. Generate fixture first.");
        }
    }

    @Test
    public void testCidPdf_highReplacementRatio_detected() throws IOException {
        Assumptions.assumeTrue(pdfAvailable, "CID font test PDF not available");

        String pdfPath = CID_PDF_PATH.toAbsolutePath().toString();
        Config config = new Config();

        DocumentProcessor.preprocessing(pdfPath, config);
        StaticLayoutContainers.clearContainers();

        int numPages = StaticContainers.getDocument().getNumberOfPages();
        Assertions.assertTrue(numPages > 0, "PDF should have at least 1 page");

        // Process page 0 through ContentFilterProcessor
        List<IObject> filteredContents = ContentFilterProcessor.getFilteredContents(
            pdfPath,
            StaticContainers.getDocument().getArtifacts(0),
            0,
            config
        );

        // Verify ratio was stored
        double ratio = StaticLayoutContainers.getReplacementCharRatio(0);
        Assertions.assertTrue(ratio >= 0.3,
            "CID font PDF should have >= 30% replacement characters, got "
            + String.format("%.1f%%", ratio * 100));
    }

    @Test
    public void testCidPdf_warningLogEmitted() throws IOException {
        Assumptions.assumeTrue(pdfAvailable, "CID font test PDF not available");

        // Capture warning logs
        Logger logger = Logger.getLogger(ContentFilterProcessor.class.getCanonicalName());
        List<String> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel() == Level.WARNING) {
                    warnings.add(r.getMessage());
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);

        try {
            String pdfPath = CID_PDF_PATH.toAbsolutePath().toString();
            Config config = new Config();

            DocumentProcessor.preprocessing(pdfPath, config);
            StaticLayoutContainers.clearContainers();

            ContentFilterProcessor.getFilteredContents(
                pdfPath,
                StaticContainers.getDocument().getArtifacts(0),
                0,
                config
            );

            boolean hasReplacementWarning = warnings.stream()
                .anyMatch(w -> w.contains("replacement characters"));
            Assertions.assertTrue(hasReplacementWarning,
                "Expected WARNING log about replacement characters");
        } finally {
            logger.removeHandler(handler);
        }
    }

    /**
     * Unit-level boundary tests (no PDF fixture needed).
     */
    @Test
    public void testBoundary_belowThreshold_29percent() {
        // 29 replacement chars out of 100 = 0.29 (below threshold)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 29; i++) sb.append('\uFFFD');
        for (int i = 0; i < 71; i++) sb.append('A');

        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 500.0, 20.0),
            sb.toString(), 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertTrue(ratio < 0.3,
            "29% should be below threshold, got " + ratio);
    }

    @Test
    public void testBoundary_atThreshold_30percent() {
        // 30 replacement chars out of 100 = 0.30 (at threshold)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append('\uFFFD');
        for (int i = 0; i < 70; i++) sb.append('A');

        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 500.0, 20.0),
            sb.toString(), 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertTrue(ratio >= 0.3,
            "30% should be at threshold, got " + ratio);
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd java && mvn test -pl opendataloader-pdf-core -Dtest=CidFontDetectionTest -q`
Expected: Integration tests pass (or skip if fixture not yet generated). Boundary tests always pass.

- [ ] **Step 3: Run full test suite**

Run: `cd java && mvn test -pl opendataloader-pdf-core -q`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/processors/CidFontDetectionTest.java
git commit -m "test: add integration + boundary tests for CID font detection

Integration tests load cid-font-no-tounicode.pdf through full pipeline.
Boundary tests verify 29%/30% threshold behavior."
```

---

## Chunk 4: Final Verification

### Task 7: Full test suite + benchmark regression check

- [ ] **Step 1: Run full Java test suite**

Run: `cd java && mvn test -q`
Expected: All tests PASS across both core and cli modules

- [ ] **Step 2: Check benchmark regression (if benchmark script exists)**

Run: `./scripts/bench.sh --check-regression 2>/dev/null || echo "No benchmark script — skip"`
Expected: PASS or skip

- [ ] **Step 3: Review all changes**

Run: `git log --oneline main..HEAD`
Expected commits (newest first):
1. `test: add e2e tests for CID font detection pipeline`
2. `test: add synthetic CID font PDF test fixture`
3. `feat: add CID font detection signal to TriageProcessor`
4. `feat: detect CID font extraction failure and emit warning log`
5. `feat: add measureReplacementCharRatio to TextProcessor`
6. `feat: add per-page replacement char ratio storage to StaticLayoutContainers`

- [ ] **Step 4: Update issue #286 comment**

Update the previously posted enhancement comment with the actual fix status. The earlier `enhancement` label should be removed since this is now a fix.

```bash
gh issue edit 286 --repo opendataloader-project/opendataloader-pdf --remove-label enhancement
```
