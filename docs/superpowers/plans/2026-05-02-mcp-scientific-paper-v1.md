# MCP Scientific Paper Understanding v1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing Java enriched graph into a new `graph-json` output format, surface triage decisions into MCP jobs, add a `describe_pdf` tool, clean dead code, rewrite README, and ship a PyPI-ready MCP server for scientific paper understanding.

**Architecture:** A new `GraphJsonWriter` Java class serializes `EnrichedGraphNodes` + `TriageDecision` into a `{stem}-graph.json` sidecar file whenever `format` includes `graph-json`. Python's `jobs._run()` always requests this sidecar alongside the primary format, reads it after convert completes, and sets `job.triage_decision` / `job.score`. A new synchronous `describe_pdf` MCP tool calls convert with `format=graph-json` and returns a structured paper overview dict. Default weights are baked into `WeightedScorecard` so triage works without an external config file.

**Tech Stack:** Java 11+, Jackson 2.x (already in pom.xml), Python 3.10+, FastMCP, pytest, Maven (`/usr/bin/mvn`), `npm run sync` (regenerates Python bindings after options.json changes).

**Run all Java tests from:** `java/opendataloader-pdf-core/` with `mvn test`
**Run all Python tests from:** `python/opendataloader-pdf-mcp/` with `.venv/bin/python -m pytest tests/ -v`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/json/GraphJsonWriter.java` | Create | Writes `{stem}-graph.json` from enriched graph + triage |
| `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/json/serializers/FormulaSerializer.java` | Modify | Add `equation_number` field emission |
| `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/quality/WeightedScorecard.java` | Modify | Add `defaultWeights()` factory; use defaults when config file missing |
| `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/DocumentProcessor.java` | Modify | Call `GraphJsonWriter` when format contains `graph-json` |
| `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/json/GraphJsonWriterTest.java` | Create | 5 unit tests for graph-json output |
| `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/json/serializers/FormulaSerializerTest.java` | Create | `equation_number` field test |
| `options.json` | Modify | Add `graph-json` to format enum |
| `python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py` | Regenerate | `npm run sync` after options.json change |
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py` | Modify | Triage wiring; `graph-json` in `_SUPPORTED_FORMATS` / `_EXT_MAP`; always request sidecar |
| `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py` | Modify | Add `describe_pdf`; improve all tool docstrings |
| `python/opendataloader-pdf-mcp/tests/test_jobs.py` | Modify | Triage wiring tests; graph-json format tests |
| `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py` | Modify | `describe_pdf` tests |
| `python/opendataloader-pdf-mcp/tests/test_readme.py` | Create | README completeness check |
| `python/opendataloader-pdf-mcp/README.md` | Rewrite | All tools, resource, formats, scientific paper workflow |
| `python/opendataloader-pdf-mcp/pyproject.toml` | Modify | Version bump, classifiers, keywords |
| `docs/superpowers/OVERVIEW.md` | Create | Plan A–D summary for future agents |
| `CLAUDE.md` | Modify | Add MCP server architecture section |

---

## Task 1: `FormulaSerializer` — emit `equation_number`

**Files:**
- Modify: `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/json/serializers/FormulaSerializer.java`
- Create: `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/json/serializers/FormulaSerializerTest.java`

All commands run from `java/opendataloader-pdf-core/`.

- [ ] **Step 1: Read the current serializer to understand the structure**

```bash
cat src/main/java/org/opendataloader/pdf/json/serializers/FormulaSerializer.java
```

Locate where `gen.writeStringField(...)` calls are made. Find the line that serializes the formula `content` (LaTeX). The `equation_number` field should be written immediately after it.

Also check `JsonName.java` for the constant name — it's `EQUATION_NUMBER` (value: `"equation number"`).

- [ ] **Step 2: Write the failing test**

Create `src/test/java/org/opendataloader/pdf/json/serializers/FormulaSerializerTest.java`:

```java
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.graph.EquationNode;
import org.opendataloader.pdf.json.ObjectMapperHolder;

import static org.junit.jupiter.api.Assertions.*;

class FormulaSerializerTest {

    private final ObjectMapper mapper = ObjectMapperHolder.get();

    @Test
    void testEquationNumberEmittedWhenResolved() throws Exception {
        EquationNode node = new EquationNode("eq-1", 1,
            new double[]{0, 0, 100, 20}, "E=mc^2", true);
        node.setNumber("3.1");

        String json = mapper.writeValueAsString(node);
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("equation number"),
            "equation number field should be present when resolved");
        assertEquals("3.1", root.get("equation number").asText());
    }

    @Test
    void testEquationNumberAbsentWhenNull() throws Exception {
        EquationNode node = new EquationNode("eq-2", 1,
            new double[]{0, 0, 100, 20}, "F=ma", true);
        // number not set — remains null

        String json = mapper.writeValueAsString(node);
        JsonNode root = mapper.readTree(json);

        assertFalse(root.has("equation number"),
            "equation number field should be absent when not resolved");
    }
}
```

- [ ] **Step 3: Run test to confirm it fails**

```bash
mvn test -pl . -Dtest=FormulaSerializerTest -q 2>&1 | tail -20
```

Expected: FAIL — `equation number` field not present in JSON.

- [ ] **Step 4: Add `equation_number` emission to `FormulaSerializer`**

In `FormulaSerializer.serialize()`, after the line that writes the LaTeX content field, add:

```java
if (node.getNumber() != null) {
    gen.writeStringField(JsonName.EQUATION_NUMBER, node.getNumber());
}
```

The exact location depends on current file content (read in Step 1). It must be inside the serialization block, after `content` is written.

- [ ] **Step 5: Run tests to confirm they pass**

```bash
mvn test -pl . -Dtest=FormulaSerializerTest -q 2>&1 | tail -5
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Run full Java suite to confirm no regressions**

```bash
mvn test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/opendataloader/pdf/json/serializers/FormulaSerializer.java \
        src/test/java/org/opendataloader/pdf/json/serializers/FormulaSerializerTest.java
git commit -m "fix(java): emit equation_number field in FormulaSerializer"
```

---

## Task 2: `WeightedScorecard` — default weights factory

**Files:**
- Modify: `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/quality/WeightedScorecard.java`

All commands run from `java/opendataloader-pdf-core/`.

The `GraphJsonWriter` (Task 3) needs to run `TriagePolicy` without requiring an external JSON file. `WeightedScorecard` currently only loads from file. We add a `withDefaultWeights()` factory that uses the same weights as `scorecard-weights-v0.json` (equation 0.4, caption 0.3, citation 0.3).

- [ ] **Step 1: Read the current WeightedScorecard to understand construction**

```bash
cat src/main/java/org/opendataloader/pdf/quality/WeightedScorecard.java
```

Find the constructor signature and how weights are set. The weights file uses `equationWeight`, `captionWeight`, `citationWeight`.

- [ ] **Step 2: Add `withDefaultWeights()` static factory**

In `WeightedScorecard.java`, add after the existing constructors/factories:

```java
/**
 * Returns a scorecard with default weights (equation=0.4, caption=0.3, citation=0.3).
 * Used when no external weights file is available.
 */
public static WeightedScorecard withDefaultWeights() {
    return new WeightedScorecard(0.4, 0.3, 0.3);
}
```

The constructor `new WeightedScorecard(double, double, double)` already exists (check Step 1 output for exact parameter names — adjust if needed).

- [ ] **Step 3: Add a test for the default factory**

Append to `src/test/java/org/opendataloader/pdf/quality/WeightedScorecardTest.java`:

```java
@Test
void testWithDefaultWeightsDoesNotThrow() {
    WeightedScorecard scorecard = WeightedScorecard.withDefaultWeights();
    assertNotNull(scorecard);
}

@Test
void testWithDefaultWeightsSumsToOne() {
    // Verify the defaults are internally consistent by scoring a mock report
    WeightedScorecard scorecard = WeightedScorecard.withDefaultWeights();
    // A perfect parity report should score 100.0
    ParityReport perfect = ParityReport.builder()
        .equationResolvedRate(1.0)
        .captionResolvedRate(1.0)
        .citationResolvedRate(1.0)
        .build();
    double score = scorecard.score(perfect);
    assertEquals(100.0, score, 0.001);
}
```

Check `ParityReport` builder API in `src/main/java/org/opendataloader/pdf/quality/ParityReport.java` before writing — use whatever the actual builder or constructor pattern is.

- [ ] **Step 4: Run tests**

```bash
mvn test -pl . -Dtest=WeightedScorecardTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/opendataloader/pdf/quality/WeightedScorecard.java \
        src/test/java/org/opendataloader/pdf/quality/WeightedScorecardTest.java
git commit -m "feat(java): add WeightedScorecard.withDefaultWeights() factory"
```

---

## Task 3: `GraphJsonWriter` — new class

**Files:**
- Create: `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/json/GraphJsonWriter.java`
- Create: `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/json/GraphJsonWriterTest.java`

All commands run from `java/opendataloader-pdf-core/`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/org/opendataloader/pdf/json/GraphJsonWriterTest.java`:

```java
package org.opendataloader.pdf.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.graph.*;
import org.opendataloader.pdf.json.GraphJsonWriter;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.quality.TriageDecision;
import org.opendataloader.pdf.quality.WeightedScorecard;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphJsonWriterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ExtractionResult makeResult(List<GraphNode> enrichedNodes) {
        // ExtractionResult with no raw contents, just enriched nodes for testing
        return ExtractionResult.ofEnrichedNodes(enrichedNodes);
    }

    @Test
    void testWritesSidecarFile(@TempDir Path tmpDir) throws Exception {
        List<GraphNode> nodes = List.of(
            new HeadingNode("h1", 1, new double[]{0,0,200,20}, 1, "Introduction")
        );
        TriageDecision triage = TriageDecision.pass(85.0);

        new GraphJsonWriter().write("paper", tmpDir, makeResult(nodes), triage);

        Path sidecar = tmpDir.resolve("paper-graph.json");
        assertTrue(sidecar.toFile().exists(), "sidecar file should be written");
    }

    @Test
    void testSectionsPresent(@TempDir Path tmpDir) throws Exception {
        List<GraphNode> nodes = List.of(
            new HeadingNode("h1", 1, new double[]{0,0,200,20}, 1, "Introduction"),
            new HeadingNode("h2", 2, new double[]{0,0,200,20}, 2, "Method")
        );
        new GraphJsonWriter().write("paper", tmpDir, makeResult(nodes),
            TriageDecision.pass(90.0));

        JsonNode root = mapper.readTree(tmpDir.resolve("paper-graph.json").toFile());
        assertTrue(root.has("sections"), "sections array should be present");
        assertEquals(2, root.get("sections").size());
        assertEquals("Introduction", root.get("sections").get(0).get("text").asText());
    }

    @Test
    void testEquationNumberInOutput(@TempDir Path tmpDir) throws Exception {
        EquationNode eq = new EquationNode("eq-1", 1, new double[]{0,0,100,20},
            "E=mc^2", true);
        eq.setNumber("3.1");

        new GraphJsonWriter().write("paper", tmpDir,
            makeResult(List.of(eq)), TriageDecision.pass(80.0));

        JsonNode root = mapper.readTree(tmpDir.resolve("paper-graph.json").toFile());
        assertEquals(1, root.get("equations").size());
        assertEquals("3.1",
            root.get("equations").get(0).get("equation number").asText());
    }

    @Test
    void testReferencesStructured(@TempDir Path tmpDir) throws Exception {
        ReferenceEntryNode ref = new ReferenceEntryNode("ref-1", 5,
            new double[]{0,0,300,15}, "Smith et al. 2019. Gradient Descent.");
        ref.addMetadata("year", "2019");

        new GraphJsonWriter().write("paper", tmpDir,
            makeResult(List.of(ref)), TriageDecision.pass(80.0));

        JsonNode root = mapper.readTree(tmpDir.resolve("paper-graph.json").toFile());
        assertEquals(1, root.get("references").size());
        JsonNode refNode = root.get("references").get(0);
        assertEquals("ref-1", refNode.get("ref_id").asText());
        assertTrue(refNode.get("text").asText().contains("Smith"));
    }

    @Test
    void testTriageBlockPresent(@TempDir Path tmpDir) throws Exception {
        new GraphJsonWriter().write("paper", tmpDir,
            makeResult(List.of()), TriageDecision.pass(91.5));

        JsonNode root = mapper.readTree(tmpDir.resolve("paper-graph.json").toFile());
        assertTrue(root.has("triage"), "triage block should be present");
        assertEquals("PASS", root.get("triage").get("outcome").asText());
        assertEquals(91.5, root.get("triage").get("composite_score").asDouble(), 0.001);
    }
}
```

**Before writing the implementation**, check the actual constructors of `HeadingNode`, `EquationNode`, `ReferenceEntryNode` by reading those files — adjust constructor calls if the signatures differ. Also check `TriageDecision` for how to create a PASS instance.

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -pl . -Dtest=GraphJsonWriterTest -q 2>&1 | tail -10
```

Expected: compilation error — `GraphJsonWriter` does not exist yet.

- [ ] **Step 3: Implement `GraphJsonWriter`**

Create `src/main/java/org/opendataloader/pdf/json/GraphJsonWriter.java`:

```java
package org.opendataloader.pdf.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendataloader.pdf.graph.*;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.quality.TriageDecision;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the enriched document graph to a {@code {stem}-graph.json} sidecar file.
 *
 * <p>This is the primary mechanism for surfacing structured scientific paper data
 * (sections, equations with numbers, figures, references, citations, triage outcome)
 * to Python MCP clients.
 */
public class GraphJsonWriter {

    private final ObjectMapper mapper = ObjectMapperHolder.get();

    public void write(String stem, Path outputDir, ExtractionResult extraction,
                      TriageDecision triage) throws IOException {
        ObjectNode root = mapper.createObjectNode();

        // Metadata placeholders — populated by DocumentProcessor which has access
        // to DocumentInfo; here we write empty defaults that get overwritten.
        root.putNull("title");
        root.set("authors", mapper.createArrayNode());
        root.put("page_count", extraction.getPageCount());

        List<GraphNode> nodes = extraction.getEnrichedGraphNodes();

        ArrayNode sections   = mapper.createArrayNode();
        ArrayNode equations  = mapper.createArrayNode();
        ArrayNode figures    = mapper.createArrayNode();
        ArrayNode references = mapper.createArrayNode();
        ArrayNode citations  = mapper.createArrayNode();

        for (GraphNode node : nodes) {
            if (node instanceof HeadingNode h) {
                ObjectNode s = mapper.createObjectNode();
                s.put("level", h.getLevel());
                s.put("text",  h.getText());
                s.put("page",  h.getPage());
                sections.add(s);

            } else if (node instanceof EquationNode eq) {
                ObjectNode e = mapper.createObjectNode();
                e.put("id",           eq.getRawId());
                e.put("latex",        eq.getLatex());
                e.put("display_mode", eq.isDisplayMode());
                e.put("page",         eq.getPage());
                if (eq.getNumber() != null) {
                    e.put("equation number", eq.getNumber());
                }
                equations.add(e);

            } else if (node instanceof CaptionNode cap
                       && "FIGURE".equals(cap.getKind())) {
                ObjectNode f = mapper.createObjectNode();
                f.put("id",      cap.getRawId());
                f.put("caption", cap.getText());
                f.put("page",    cap.getPage());
                figures.add(f);

            } else if (node instanceof ReferenceEntryNode ref) {
                // Delegate to registered serializer via full serialization
                ObjectNode r = mapper.valueToTree(ref);
                references.add(r);

            } else if (node instanceof CitationNode cit) {
                ObjectNode c = mapper.valueToTree(cit);
                citations.add(c);
            }
        }

        root.set("sections",   sections);
        root.set("equations",  equations);
        root.set("figures",    figures);
        root.set("references", references);
        root.set("citations",  citations);

        // Triage block
        if (triage != null) {
            ObjectNode t = mapper.createObjectNode();
            t.put("outcome",         triage.getOutcome().name());
            t.put("composite_score", triage.getCompositeScore());
            ArrayNode failures = mapper.createArrayNode();
            if (triage.getGateFailureReasons() != null) {
                triage.getGateFailureReasons().forEach(failures::add);
            }
            t.set("gate_failures", failures);
            root.set("triage", t);
        }

        Path out = outputDir.resolve(stem + "-graph.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
    }
}
```

**Note:** Check actual getter names on `EquationNode`, `CaptionNode`, `ReferenceEntryNode`, `CitationNode`, `TriageDecision` by reading those source files — adjust method names if they differ (e.g., `getRawId()` vs `getId()`). Also check `ExtractionResult` for a `getPageCount()` method — add one if missing (returns `contents.size()`).

- [ ] **Step 4: Add `getPageCount()` to `ExtractionResult` if missing**

Read `src/main/java/org/opendataloader/pdf/processors/ExtractionResult.java`. If no `getPageCount()` exists, add:

```java
public int getPageCount() {
    return contents != null ? contents.size() : 0;
}
```

Also add a static factory `ofEnrichedNodes(List<GraphNode>)` for testing if not present:

```java
public static ExtractionResult ofEnrichedNodes(List<GraphNode> nodes) {
    return new ExtractionResult(List.of(), nodes, 0L, null);
}
```

Adjust constructor call to match existing constructors.

- [ ] **Step 5: Run the failing tests**

```bash
mvn test -pl . -Dtest=GraphJsonWriterTest -q 2>&1 | tail -20
```

Fix any compilation errors (getter names, constructor signatures). All 5 tests should pass.

- [ ] **Step 6: Run full Java suite**

```bash
mvn test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/opendataloader/pdf/json/GraphJsonWriter.java \
        src/main/java/org/opendataloader/pdf/processors/ExtractionResult.java \
        src/test/java/org/opendataloader/pdf/json/GraphJsonWriterTest.java
git commit -m "feat(java): add GraphJsonWriter — enriched graph + triage sidecar"
```

---

## Task 4: Wire `GraphJsonWriter` into `DocumentProcessor`

**Files:**
- Modify: `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/DocumentProcessor.java`

All commands run from `java/opendataloader-pdf-core/`.

- [ ] **Step 1: Read `generateOutputs()` in `DocumentProcessor`**

```bash
grep -n "generateOutputs\|format\|graph" \
  src/main/java/org/opendataloader/pdf/processors/DocumentProcessor.java | head -40
```

Locate the method `generateOutputs()` (or equivalent) and find where format dispatch happens (the `if (formats.contains("json"))` / `if (formats.contains("markdown"))` pattern).

- [ ] **Step 2: Add `DocumentProcessorGraphJsonTest`**

Create `src/test/java/org/opendataloader/pdf/processors/DocumentProcessorGraphJsonTest.java`:

```java
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class DocumentProcessorGraphJsonTest {

    @Test
    void testGraphJsonSidecarWrittenWhenFormatRequested(@TempDir Path tmpDir)
            throws Exception {
        // Use a minimal PDF fixture — the test PDFs used in other tests
        File pdf = new File("src/test/resources/fixtures/simple.pdf");
        if (!pdf.exists()) {
            // Skip gracefully if fixture not available in this environment
            return;
        }

        DocumentProcessor processor = new DocumentProcessor();
        // Use the existing processFileWithResult or equivalent API
        // to request graph-json output
        processor.processFile(pdf.getAbsolutePath(),
            tmpDir.toString(), "graph-json", null);

        File sidecar = tmpDir.resolve("simple-graph.json").toFile();
        assertTrue(sidecar.exists(), "graph-json sidecar should be written");
        assertTrue(sidecar.length() > 10, "sidecar should be non-empty");
    }
}
```

Check the actual `DocumentProcessor` API (public methods, parameter order) in Step 1 before finalising this test.

- [ ] **Step 3: Add graph-json dispatch to `DocumentProcessor.generateOutputs()`**

In the format dispatch block inside `generateOutputs()`, add after existing format branches:

```java
if (formats.contains("graph-json")) {
    WeightedScorecard scorecard;
    try {
        Path weightsPath = Path.of("benchmarks/config/scorecard-weights-v0.json");
        scorecard = Files.exists(weightsPath)
            ? WeightedScorecard.fromFile(weightsPath)
            : WeightedScorecard.withDefaultWeights();
    } catch (Exception e) {
        scorecard = WeightedScorecard.withDefaultWeights();
    }
    TriageDecision triage = TriagePolicy.decide(scorecard, extraction);
    new GraphJsonWriter().write(stem, outputDir, extraction, triage);
}
```

Add required imports at top of file:
```java
import org.opendataloader.pdf.json.GraphJsonWriter;
import org.opendataloader.pdf.quality.TriageDecision;
import org.opendataloader.pdf.quality.TriagePolicy;
import org.opendataloader.pdf.quality.WeightedScorecard;
import java.nio.file.Files;
import java.nio.file.Path;
```

The variable names (`formats`, `stem`, `outputDir`, `extraction`) must match the actual variable names in the method — read Step 1 output carefully.

- [ ] **Step 4: Run full Java suite**

```bash
mvn test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/opendataloader/pdf/processors/DocumentProcessor.java \
        src/test/java/org/opendataloader/pdf/processors/DocumentProcessorGraphJsonTest.java
git commit -m "feat(java): wire GraphJsonWriter into DocumentProcessor for graph-json format"
```

---

## Task 5: Add `graph-json` to `options.json` + regenerate Python bindings

**Files:**
- Modify: `options.json` (repo root)
- Regenerate: `python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py`

All commands run from repo root.

- [ ] **Step 1: Find the format enum in `options.json`**

```bash
grep -n "graph-json\|format\|markdown" options.json | head -20
```

Locate the `format` option and its `enum` or `values` array.

- [ ] **Step 2: Add `graph-json` to the format values**

Find the format option entry (it will look like):
```json
{
  "name": "format",
  "values": ["json", "text", "html", "markdown", "markdown-with-html", "markdown-with-images", ...]
}
```

Add `"graph-json"` to that array. Edit with your editor — do not reformat the entire file.

- [ ] **Step 3: Regenerate Python/Node bindings**

```bash
npm run sync
```

Expected: no errors. `python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py` is updated.

- [ ] **Step 4: Verify `graph-json` appears in generated code**

```bash
grep "graph-json" python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py
```

Expected: at least one match (in the format docstring or validation).

- [ ] **Step 5: Commit**

```bash
git add options.json python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py
git commit -m "feat: add graph-json to format enum and regenerate Python bindings"
```

---

## Task 6: Python — `graph-json` in `_SUPPORTED_FORMATS` + triage wiring in `jobs.py`

**Files:**
- Modify: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py`
- Modify: `python/opendataloader-pdf-mcp/tests/test_jobs.py`

All commands run from `python/opendataloader-pdf-mcp/`.

- [ ] **Step 1: Add `graph-json` to `_SUPPORTED_FORMATS` and `_EXT_MAP`**

In `jobs.py`, update the two module-level dicts:

```python
_SUPPORTED_FORMATS = {
    "json", "text", "html", "markdown",
    "markdown-with-html", "markdown-with-images", "graph-json",
}
_EXT_MAP = {
    "json":                  ".json",
    "text":                  ".txt",
    "html":                  ".html",
    "markdown":              ".md",
    "markdown-with-html":    ".md",
    "markdown-with-images":  ".md",
    "graph-json":            "-graph.json",
}
```

- [ ] **Step 2: Add triage wiring to `_run()`**

In `jobs.py`, the `_run()` method calls `opendataloader_pdf.convert(**kwargs)`. After that call succeeds (before `job.artifact = ...`), add the graph-json sidecar request. The convert kwargs already strip `enable_mineru_fallback`. Now also always append `graph-json` to the format:

```python
# Always request the graph-json sidecar for triage wiring
internal_format = f"{job.format},graph-json" if job.format != "graph-json" else "graph-json"
kwargs: dict[str, Any] = {
    "input_path": str(input_file),
    "output_dir": tmp_dir,
    "format": internal_format,       # ← changed from job.format
    "quiet": True,
    **{k: v for k, v in job.kwargs.items() if k != "enable_mineru_fallback"},
}
opendataloader_pdf.convert(**kwargs)
```

Then after the convert call, read the sidecar:

```python
# Read triage from graph-json sidecar
import json as _json
graph_file = Path(tmp_dir) / f"{input_file.stem}-graph.json"
if graph_file.is_file():
    try:
        g = _json.loads(graph_file.read_text(encoding="utf-8"))
        t = g.get("triage", {})
        job.triage_decision = t.get("outcome")
        job.score = t.get("composite_score")
    except Exception:
        pass  # silent — triage stays None, job continues to DONE
```

Add `import json as _json` at the top of `jobs.py` (alongside existing imports).

- [ ] **Step 3: Write failing tests**

Append to `tests/test_jobs.py`:

```python
class TestGraphJsonFormat:
    def test_graph_json_accepted_by_submit(self, manager, pdf_file):
        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = MagicMock()
            # Should not raise ValueError
            job_id = manager.submit(str(pdf_file), "graph-json")
        assert isinstance(job_id, str)

    def test_graph_json_in_ext_map(self):
        from opendataloader_pdf_mcp.jobs import _EXT_MAP
        assert "graph-json" in _EXT_MAP
        assert _EXT_MAP["graph-json"] == "-graph.json"


class TestTriageWiring:
    def test_triage_decision_populated_from_sidecar(self, manager, pdf_file):
        import json

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            # Write primary artifact
            (Path(output_dir) / f"{stem}.md").write_text("# Content", encoding="utf-8")
            # Write graph-json sidecar
            sidecar = Path(output_dir) / f"{stem}-graph.json"
            sidecar.write_text(json.dumps({
                "triage": {"outcome": "PASS", "composite_score": 0.88}
            }), encoding="utf-8")

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            job_id = manager.submit(str(pdf_file), "markdown")
            for _ in range(100):
                if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                    break
                time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.DONE
        assert job.triage_decision == "PASS"
        assert abs(job.score - 0.88) < 0.001

    def test_triage_missing_sidecar_does_not_fail_job(self, manager, pdf_file):
        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            (Path(output_dir) / f"{stem}.md").write_text("# Content", encoding="utf-8")
            # No sidecar written

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            job_id = manager.submit(str(pdf_file), "markdown")
            for _ in range(100):
                if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                    break
                time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.DONE
        assert job.triage_decision is None  # graceful — not an error

    def test_mineru_triggers_on_hard_fail_triage(self, manager, pdf_file):
        import json
        from opendataloader_pdf_mcp.mineru import MinerUResult

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            (Path(output_dir) / f"{stem}.md").write_text("# Poor output", encoding="utf-8")
            sidecar = Path(output_dir) / f"{stem}-graph.json"
            sidecar.write_text(json.dumps({
                "triage": {"outcome": "HARD_FAIL", "composite_score": 0.1}
            }), encoding="utf-8")

        mock_result = MinerUResult(
            markdown="# MinerU output", json_str="{}", exit_code=0, stderr=""
        )

        with patch("opendataloader_pdf_mcp.jobs.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            with patch("opendataloader_pdf_mcp.jobs.MinerURunner") as mock_runner:
                mock_runner.return_value.run.return_value = mock_result
                job_id = manager.submit(str(pdf_file), "markdown",
                                        enable_mineru_fallback=True)
                for _ in range(100):
                    if manager.get(job_id).status in (JobStatus.DONE, JobStatus.FAILED):
                        break
                    time.sleep(0.05)

        job = manager.get(job_id)
        assert job.status == JobStatus.DONE
        assert job.triage_decision == "HARD_FAIL"
        assert job.fallback_source == "mineru"
        assert job.artifact == "# MinerU output"
        mock_runner.return_value.run.assert_called_once()
```

- [ ] **Step 4: Run tests to confirm they fail**

```bash
.venv/bin/python -m pytest tests/test_jobs.py::TestGraphJsonFormat \
    tests/test_jobs.py::TestTriageWiring -v 2>&1 | tail -20
```

Expected: failures (format not in `_SUPPORTED_FORMATS`, triage fields not populated).

- [ ] **Step 5: Run tests after implementation**

```bash
.venv/bin/python -m pytest tests/test_jobs.py -v 2>&1 | tail -10
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/jobs.py \
        python/opendataloader-pdf-mcp/tests/test_jobs.py
git commit -m "feat(mcp): add graph-json format support and triage wiring in jobs._run()"
```

---

## Task 7: `describe_pdf` MCP tool

**Files:**
- Modify: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py`
- Modify: `python/opendataloader-pdf-mcp/tests/test_server_async_tools.py`

All commands run from `python/opendataloader-pdf-mcp/`.

- [ ] **Step 1: Write failing tests**

Append a new `TestDescribePdf` class to `tests/test_server_async_tools.py`:

```python
class TestDescribePdf:
    def test_describe_pdf_missing_file_raises(self):
        from opendataloader_pdf_mcp.server import describe_pdf
        with pytest.raises(FileNotFoundError):
            describe_pdf(input_path="/no/such/file.pdf")

    def test_describe_pdf_returns_required_keys(self, pdf_file):
        import json
        from opendataloader_pdf_mcp.server import describe_pdf

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            sidecar = Path(output_dir) / f"{stem}-graph.json"
            sidecar.write_text(json.dumps({
                "title": "Test Paper",
                "authors": ["Alice", "Bob"],
                "page_count": 10,
                "sections": [{"level": 1, "text": "Introduction", "page": 1}],
                "equations": [],
                "figures": [{"id": "fig-1", "caption": "Arch", "page": 2}],
                "references": [{"ref_id": "ref-1", "text": "Smith 2019"}],
                "citations": [],
                "triage": {"outcome": "PASS", "composite_score": 0.9},
            }), encoding="utf-8")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            result = describe_pdf(input_path=str(pdf_file))

        required_keys = {"title", "authors", "page_count", "section_count",
                         "sections", "equation_count", "figure_count",
                         "reference_count", "triage"}
        assert required_keys.issubset(result.keys())
        assert result["page_count"] == 10
        assert result["section_count"] == 1
        assert result["figure_count"] == 1
        assert result["reference_count"] == 1
        assert result["triage"]["outcome"] == "PASS"

    def test_describe_pdf_no_sidecar_raises(self, pdf_file):
        from opendataloader_pdf_mcp.server import describe_pdf

        def fake_convert(input_path, output_dir, **kwargs):
            pass  # writes nothing

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            with pytest.raises(RuntimeError, match="graph-json output not produced"):
                describe_pdf(input_path=str(pdf_file))

    def test_describe_pdf_malformed_sidecar_returns_partial(self, pdf_file):
        from opendataloader_pdf_mcp.server import describe_pdf

        def fake_convert(input_path, output_dir, **kwargs):
            stem = Path(input_path).stem
            sidecar = Path(output_dir) / f"{stem}-graph.json"
            sidecar.write_text("NOT JSON", encoding="utf-8")

        with patch("opendataloader_pdf_mcp.server.opendataloader_pdf") as mock_odl:
            mock_odl.convert = fake_convert
            result = describe_pdf(input_path=str(pdf_file))

        # Should not raise; returns empty/partial dict
        assert isinstance(result, dict)
        assert result.get("triage") is None
```

Also add `from pathlib import Path` to test imports if not already present.

- [ ] **Step 2: Run tests to confirm they fail**

```bash
.venv/bin/python -m pytest tests/test_server_async_tools.py::TestDescribePdf -v 2>&1 | tail -10
```

Expected: `ImportError` — `describe_pdf` not yet in `server.py`.

- [ ] **Step 3: Implement `describe_pdf` in `server.py`**

Add this import at the top of `server.py` (alongside existing imports):

```python
import json as _json
```

Add the tool after the existing `convert_pdf` function and before `_collect_kwargs`:

```python
@mcp.tool()
def describe_pdf(
    input_path: str,
    password: str | None = None,
    pages: str | None = None,
) -> dict:
    """Get a structured overview of a PDF — sections, equations, figures, and references.

    Returns a summary without full text content. Use this first to understand a paper's
    structure before deciding how to convert it. For scientific papers, call this before
    submit_pdf to know section count, equation count, and reference count.

    Args:
        input_path: Path to the PDF file.
        password: Password for encrypted PDFs.
        pages: Page range to analyse (e.g. "1-10"). Default: all pages.

    Returns:
        {
          "title": str | None,
          "authors": list[str],
          "page_count": int,
          "section_count": int,
          "sections": [{"level": int, "text": str, "page": int}],
          "equation_count": int,
          "figure_count": int,
          "reference_count": int,
          "triage": {"outcome": str, "composite_score": float} | None
        }
    """
    input_file = Path(input_path).expanduser().resolve()
    if not input_file.is_file():
        raise FileNotFoundError(f"Input file not found: {input_path}")

    with tempfile.TemporaryDirectory() as tmp_dir:
        kwargs: dict[str, Any] = {
            "input_path": str(input_file),
            "output_dir": tmp_dir,
            "format": "graph-json",
            "quiet": True,
        }
        if password is not None:
            kwargs["password"] = password
        if pages is not None:
            kwargs["pages"] = pages

        opendataloader_pdf.convert(**kwargs)

        stem = input_file.stem
        graph_file = Path(tmp_dir) / f"{stem}-graph.json"

        if not graph_file.is_file():
            raise RuntimeError(
                "graph-json output not produced — ensure opendataloader-pdf Java "
                "library is up to date and supports the graph-json format."
            )

        try:
            data = _json.loads(graph_file.read_text(encoding="utf-8"))
        except Exception:
            return {"triage": None}  # malformed — return partial

        sections   = data.get("sections",   [])
        equations  = data.get("equations",  [])
        figures    = data.get("figures",    [])
        references = data.get("references", [])

        return {
            "title":           data.get("title"),
            "authors":         data.get("authors", []),
            "page_count":      data.get("page_count", 0),
            "section_count":   len(sections),
            "sections":        sections,
            "equation_count":  len(equations),
            "figure_count":    len(figures),
            "reference_count": len(references),
            "triage":          data.get("triage"),
        }
```

Also add `from typing import Any` if not already imported in `server.py`.

- [ ] **Step 4: Run tests**

```bash
.venv/bin/python -m pytest tests/test_server_async_tools.py -v 2>&1 | tail -15
```

Expected: all pass (existing + 4 new).

- [ ] **Step 5: Run full Python suite**

```bash
.venv/bin/python -m pytest tests/ -q 2>&1 | grep -E "passed|failed"
```

Expected: all passed, 0 failed.

- [ ] **Step 6: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py \
        python/opendataloader-pdf-mcp/tests/test_server_async_tools.py
git commit -m "feat(mcp): add describe_pdf tool — structured paper overview"
```

---

## Task 8: Improve tool docstrings in `server.py`

**Files:**
- Modify: `python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py`

No tests needed for this task — covered by Task 9's README completeness test.

All commands run from `python/opendataloader-pdf-mcp/`.

- [ ] **Step 1: Replace all tool docstrings**

Replace each existing tool docstring in `server.py` with the improved versions below.

**`convert_pdf`:**
```python
"""Convert a PDF file synchronously and return the content as a string.

Blocks until conversion is complete. For large PDFs or background processing,
use submit_pdf instead.

For scientific papers, use format="markdown-with-images" to capture figures,
or format="graph-json" to get structured sections, equations, and references.

Args:
    input_path: Path to the input PDF file.
    format: Output format. One of: json, text, html, markdown (default),
        markdown-with-html, markdown-with-images, graph-json.
        Use graph-json to get structured paper data (sections, equations,
        references, citations, triage score).
    pages: Pages to extract, e.g. "1,3,5-7". Default: all pages.
    password: Password for encrypted PDFs.
    image_output: How to handle images: off, embedded (base64), external (files).
    image_dir: Directory to save extracted images when image_output=external.
    enable_mineru_fallback: Not available on convert_pdf — use submit_pdf instead.

Returns:
    Converted content as a string (markdown, HTML, JSON, text, or graph-json).
"""
```

**`submit_pdf`:**
```python
"""Submit a PDF for async conversion. Returns immediately with a job_id.

Poll status with get_job_status. Retrieve result with get_artifact.
Cancel with cancel_job.

For scientific papers, use format="markdown-with-images" to capture figures.
Use enable_mineru_fallback=True for scanned or image-only PDFs that produce
poor Java extraction quality (detected automatically via triage).

Args:
    input_path: Path to the PDF file.
    format: Output format. One of: json, text, html, markdown (default),
        markdown-with-html, markdown-with-images, graph-json.
    enable_mineru_fallback: If True, automatically re-extracts with MinerU
        when Java extraction quality is HARD_FAIL. MinerU must be installed.
    pages: Pages to extract, e.g. "1,3,5-7". Default: all pages.
    password: Password for encrypted PDFs.

Returns:
    {"job_id": str, "status": "pending", "content_hash": str}
"""
```

**`get_job_status`:**
```python
"""Get the current status and quality metadata of a submitted conversion job.

Args:
    job_id: The job ID returned by submit_pdf.

Returns:
    {
      "job_id": str,
      "status": "pending" | "running" | "done" | "failed" | "cancelled",
      "triage_decision": "PASS" | "SOFT_FAIL" | "HARD_FAIL" | None,
      "score": float | None,   # composite quality score 0.0–100.0
      "submitted_at": str,     # ISO-8601
      "completed_at": str | None
    }
"""
```

**`cancel_job`:**
```python
"""Cancel a pending or running conversion job. Idempotent on terminal jobs.

Args:
    job_id: The job ID returned by submit_pdf.

Returns:
    {"job_id": str, "status": "cancelled" | current_status_if_terminal}
"""
```

**`get_artifact`:**
```python
"""Retrieve converted content for a completed job. Raises if not done.

Args:
    job_id: The job ID returned by submit_pdf.
    source: Which artifact to return:
        "primary" (default) — the main converted output (markdown, JSON, etc.)
        "java"              — original Java output when MinerU fallback ran
        "mineru-json"       — MinerU's content_list.json when fallback ran

Returns:
    Content string. Raises RuntimeError if job is not done.
"""
```

**`get_job_resource`:**
```python
"""MCP resource handler: returns primary artifact for a completed job.

URI: jobs://{job_id}

Returns artifact text for done jobs. Returns an error string (not raises)
for unknown job IDs or non-done jobs — MCP resource protocol requires a
valid response.
"""
```

- [ ] **Step 2: Commit**

```bash
git add python/opendataloader-pdf-mcp/src/opendataloader_pdf_mcp/server.py
git commit -m "docs(mcp): improve all tool docstrings for agent discoverability"
```

---

## Task 9: `test_readme.py` + README rewrite

**Files:**
- Create: `python/opendataloader-pdf-mcp/tests/test_readme.py`
- Rewrite: `python/opendataloader-pdf-mcp/README.md`

All commands run from `python/opendataloader-pdf-mcp/`.

- [ ] **Step 1: Write the README completeness test first**

Create `tests/test_readme.py`:

```python
"""Ensures README documents every @mcp.tool() registered in server.py."""
import re
from pathlib import Path

import pytest


def get_registered_tools() -> list[str]:
    server_src = Path(__file__).parent.parent / "src" / "opendataloader_pdf_mcp" / "server.py"
    text = server_src.read_text(encoding="utf-8")
    # Match @mcp.tool() followed by def <name>
    return re.findall(r"@mcp\.tool\(\)\s+def\s+(\w+)", text)


def get_readme_text() -> str:
    readme = Path(__file__).parent.parent / "README.md"
    return readme.read_text(encoding="utf-8")


class TestReadmeCompleteness:
    def test_all_tools_mentioned_in_readme(self):
        tools = get_registered_tools()
        readme = get_readme_text()
        missing = [t for t in tools if t not in readme]
        assert not missing, (
            f"Tools registered in server.py but missing from README: {missing}"
        )

    def test_jobs_resource_mentioned(self):
        readme = get_readme_text()
        assert "jobs://" in readme, "jobs:// resource should be documented in README"

    def test_graph_json_format_mentioned(self):
        readme = get_readme_text()
        assert "graph-json" in readme, "graph-json format should be documented in README"
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
.venv/bin/python -m pytest tests/test_readme.py -v 2>&1 | tail -15
```

Expected: failures — README doesn't yet document all tools.

- [ ] **Step 3: Rewrite `README.md`**

Replace the entire contents of `README.md` with:

```markdown
# OpenDataLoader PDF MCP Server

MCP server for [OpenDataLoader PDF](https://github.com/opendataloader-project/opendataloader-pdf).

Enables AI agents to understand and convert scientific PDFs — extracting structured sections,
numbered equations, figures, bibliography, and citations via MCP tools.

## Prerequisites

- Java 11+
- Python 3.10+

## Installation

```bash
pip install opendataloader-pdf-mcp
```

Or run directly without installing:

```bash
uvx opendataloader-pdf-mcp
```

## Client Setup

### Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "uvx",
      "args": ["opendataloader-pdf-mcp"]
    }
  }
}
```

### Claude Code

```bash
claude mcp add opendataloader-pdf -- uvx opendataloader-pdf-mcp
```

### Cursor

Add to `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "opendataloader-pdf": {
      "command": "uvx",
      "args": ["opendataloader-pdf-mcp"]
    }
  }
}
```

### OpenAI Codex / Windsurf / Other MCP clients

Use `uvx opendataloader-pdf-mcp` as the server command.

## Tools

### `describe_pdf` — paper overview (start here)

Get structured metadata before committing to a full conversion.

```
describe_pdf(input_path, password=None, pages=None) -> dict
```

Returns: `title`, `authors`, `page_count`, `section_count`, `sections` list,
`equation_count`, `figure_count`, `reference_count`, `triage` (quality score).

### `convert_pdf` — synchronous conversion

Converts and returns content immediately. Blocks until done.

```
convert_pdf(input_path, format="markdown", pages=None, password=None, ...) -> str
```

### `submit_pdf` — async conversion

Submits a job and returns `job_id` immediately. Use for large PDFs.

```
submit_pdf(input_path, format="markdown", enable_mineru_fallback=False, ...) -> dict
```

Returns: `{"job_id": str, "status": "pending", "content_hash": str}`

### `get_job_status` — poll job

```
get_job_status(job_id) -> dict
```

Returns: `job_id`, `status` (pending/running/done/failed/cancelled),
`triage_decision` (PASS/SOFT_FAIL/HARD_FAIL), `score` (0–100), timestamps.

### `cancel_job` — cancel async job

```
cancel_job(job_id) -> dict
```

Idempotent — safe to call on completed jobs.

### `get_artifact` — retrieve async result

```
get_artifact(job_id, source="primary") -> str
```

`source` values:
- `"primary"` — main converted output (default)
- `"java"` — original Java output when MinerU fallback ran
- `"mineru-json"` — MinerU's structured JSON when fallback ran

## Resources

### `jobs://{job_id}`

MCP resource URI. Returns artifact content for completed jobs.
Returns an error string (not raises) for unknown or incomplete jobs.

## Output Formats

| Format | Description |
|--------|-------------|
| `markdown` | Markdown text (default) |
| `markdown-with-images` | Markdown with embedded or external images |
| `markdown-with-html` | Markdown with HTML for complex elements |
| `html` | HTML |
| `text` | Plain text |
| `json` | Structured JSON (IObject stream) |
| `graph-json` | **Scientific paper graph**: sections, numbered equations, figures, bibliography, citations, triage score |

## Scientific Paper Workflow

Recommended agent workflow for researching a paper:

```
1. describe_pdf("paper.pdf")
   → learn structure, section count, reference count

2. submit_pdf("paper.pdf", format="markdown-with-images",
              enable_mineru_fallback=True)
   → job_id

3. get_job_status(job_id)  [poll until done]
   → check triage_decision; if HARD_FAIL, MinerU ran automatically

4. get_artifact(job_id)
   → full markdown with figures

5. get_artifact(job_id, source="java")       # original Java output (if MinerU ran)
6. get_artifact(job_id, source="mineru-json")  # structured MinerU JSON (if MinerU ran)
```

For structured bibliography and citation graph:
```
convert_pdf("paper.pdf", format="graph-json")
→ JSON with sections, equations (numbered), figures, references, citations
```

## MinerU Fallback

When `enable_mineru_fallback=True` is set on `submit_pdf`, the server automatically
re-extracts the PDF with [MinerU](https://github.com/opendataloader-project/MinerU)
if Java extraction quality is classified as `HARD_FAIL` (typically scanned PDFs
with no text layer). MinerU must be installed separately:

```bash
pip install mineru
```

## License

Apache-2.0
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
.venv/bin/python -m pytest tests/test_readme.py -v 2>&1 | tail -10
```

Expected: all 3 pass.

- [ ] **Step 5: Run full Python suite**

```bash
.venv/bin/python -m pytest tests/ -q 2>&1 | grep -E "passed|failed"
```

Expected: all passed.

- [ ] **Step 6: Commit**

```bash
git add python/opendataloader-pdf-mcp/README.md \
        python/opendataloader-pdf-mcp/tests/test_readme.py
git commit -m "docs(mcp): rewrite README with all tools, formats, scientific paper workflow"
```

---

## Task 10: `pyproject.toml` — version bump + PyPI classifiers

**Files:**
- Modify: `python/opendataloader-pdf-mcp/pyproject.toml`

All commands run from `python/opendataloader-pdf-mcp/`.

- [ ] **Step 1: Read current `pyproject.toml`**

```bash
cat pyproject.toml
```

Note the current `version` and any existing `classifiers`.

- [ ] **Step 2: Update `pyproject.toml`**

Update the `[project]` section:

```toml
version = "0.2.0"

classifiers = [
    "Development Status :: 4 - Beta",
    "Intended Audience :: Science/Research",
    "Intended Audience :: Developers",
    "License :: OSI Approved :: Apache Software License",
    "Programming Language :: Python :: 3",
    "Programming Language :: Python :: 3.10",
    "Programming Language :: Python :: 3.11",
    "Programming Language :: Python :: 3.12",
    "Topic :: Scientific/Engineering :: Artificial Intelligence",
    "Topic :: Text Processing :: Markup",
]

keywords = ["pdf", "mcp", "scientific-papers", "ai-agents", "llm", "model-context-protocol"]
```

Keep all other existing fields unchanged.

- [ ] **Step 3: Verify package still installs**

```bash
pip install -e ".[dev]" --quiet && echo "OK"
```

Expected: `OK`

- [ ] **Step 4: Commit**

```bash
git add pyproject.toml
git commit -m "chore(mcp): version bump to 0.2.0, add PyPI classifiers and keywords"
```

---

## Task 11: Docs reorganization + `CLAUDE.md` update

**Files:**
- Create: `docs/superpowers/OVERVIEW.md`
- Modify: `CLAUDE.md`

All commands run from repo root.

- [ ] **Step 1: Create `docs/superpowers/OVERVIEW.md`**

```bash
cat > docs/superpowers/OVERVIEW.md << 'EOF'
# Superpowers Plans — Overview

This directory contains all implementation specs and plans for the agent-driven
development work on opendataloader-pdf. Each plan followed TDD + two-stage review
(spec compliance → code quality).

## Plans A–D

| Plan | Date | Spec | Plan | Status | Summary |
|------|------|------|------|--------|---------|
| A | 2026-04-20 | specs/2026-04-20-extraction-reliability-design.md | plans/2026-04-20-extraction-reliability.md | ✅ merged | Java enrichment pipeline: canonical graph, equation numbering, caption linking, citation resolution, quality gate, triage, weighted scorecard, benchmark harness |
| B | 2026-04-24 | specs/2026-04-24-mcp-ingestion-v0-design.md | plans/2026-04-24-mcp-ingestion-v0.md | ✅ merged | Python MCP async job layer: submit_pdf, get_job_status, cancel_job, get_artifact, jobs:// resource, content-hash dedup |
| C | 2026-04-28 | specs/2026-04-28-mineru-fallback-design.md | plans/2026-04-28-mineru-fallback.md | ✅ merged | MinerU fallback: MinerURunner subprocess wrapper, JobManager fallback branch, enable_mineru_fallback param, get_artifact source param |
| D | 2026-05-02 | specs/2026-05-02-mcp-scientific-paper-v1-design.md | plans/2026-05-02-mcp-scientific-paper-v1.md | 🚧 in progress | Wire enriched graph to graph-json format, describe_pdf tool, triage wiring, codebase cleanup, README rewrite, PyPI deployment |

## What's deferred (Plan E+)

- Real LLM integration (currently `NoOpLlmFallback` stub)
- Remote MinerU service (currently local subprocess only)
- Persistence across MCP server restarts
- Webhook / push notification on job completion
- Page-granularity Java/MinerU output merging

## Key architectural invariants

- Java enrichment runs in `ExtractionResult` constructor via `EnrichmentPipeline`
- `GraphJsonWriter` (Plan D) is the only path for structured graph data to reach Python
- MinerU fallback only triggers when `triage_decision == "HARD_FAIL"` AND `enable_mineru_fallback=True`
- `LlmEnrichmentPass` is wired but `NoOpLlmFallback` always returns empty — it is a stub
- Python tests run with `.venv/bin/python -m pytest tests/` from `python/opendataloader-pdf-mcp/`
- Java tests run with `mvn test` from `java/opendataloader-pdf-core/`
EOF
```

- [ ] **Step 2: Update `CLAUDE.md`**

Append the following section to `CLAUDE.md` (repo root):

```markdown

## MCP Server Architecture

The Python MCP server (`python/opendataloader-pdf-mcp/`) wraps the Java CLI in three layers:

1. **Java CLI** (`opendataloader_pdf.convert()`) — does the actual PDF processing. Writes output files to a temp dir. Returns `None`. The graph-json sidecar (`{stem}-graph.json`) carries triage + enriched graph data back to Python.

2. **`jobs.py`** — `JobManager` owns async job lifecycle. `_run()` calls `convert()`, reads the graph-json sidecar for triage/score, triggers MinerU fallback if `HARD_FAIL`.

3. **`server.py`** — thin FastMCP registration layer. Tools: `describe_pdf`, `convert_pdf`, `submit_pdf`, `get_job_status`, `cancel_job`, `get_artifact`. Resource: `jobs://{job_id}`.

**Key gotcha:** `jobs.py` always appends `graph-json` to the internal format string so triage is always populated, even when the caller requests `markdown`. The graph sidecar is never returned as the artifact unless the caller explicitly requested `format="graph-json"`.

**Key gotcha:** `WeightedScorecard.withDefaultWeights()` (equation=0.4, caption=0.3, citation=0.3) is used when `benchmarks/config/scorecard-weights-v0.json` is not present — which is the case for end-users who installed via pip.
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/OVERVIEW.md CLAUDE.md
git commit -m "docs: add superpowers OVERVIEW.md and MCP architecture section to CLAUDE.md"
```

---

## Task 12: Local smoke test + deployment verification

**Files:** none created — this is a verification task.

All commands run from `python/opendataloader-pdf-mcp/`.

- [ ] **Step 1: Install locally in dev mode**

```bash
pip install -e ".[dev]" --quiet && echo "installed OK"
```

- [ ] **Step 2: Run full Java test suite**

```bash
cd ../../java/opendataloader-pdf-core && mvn test -q 2>&1 | tail -5
cd ../../python/opendataloader-pdf-mcp
```

Expected: `BUILD SUCCESS`, 528+ tests passing.

- [ ] **Step 3: Run full Python test suite**

```bash
.venv/bin/python -m pytest tests/ -v 2>&1 | tail -15
```

Expected: 65+ passed, 0 failed.

- [ ] **Step 4: Test MCP server stdio handshake**

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"0"}}}' \
  | timeout 5 .venv/bin/python -m opendataloader_pdf_mcp.server 2>/dev/null \
  | python3 -c "import sys,json; d=json.loads(sys.stdin.readline()); print('OK' if 'result' in d else 'FAIL')"
```

Expected: `OK`

- [ ] **Step 5: Test `describe_pdf` end-to-end on a real PDF**

Download a small arXiv PDF and test:

```bash
# Download a small open-access paper (adjust URL to any accessible PDF)
curl -sL "https://arxiv.org/pdf/1706.03762" -o /tmp/attention.pdf 2>/dev/null || \
  echo "Network unavailable — skip end-to-end test"

# If downloaded:
.venv/bin/python -c "
from opendataloader_pdf_mcp.server import describe_pdf
r = describe_pdf('/tmp/attention.pdf')
print('page_count:', r['page_count'])
print('sections:', r['section_count'])
print('equations:', r['equation_count'])
print('references:', r['reference_count'])
assert r['page_count'] > 0, 'page_count should be > 0'
print('PASS')
"
```

- [ ] **Step 6: Test `uvx` entry point**

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"0"}}}' \
  | timeout 5 uvx opendataloader-pdf-mcp 2>/dev/null \
  | python3 -c "import sys,json; d=json.loads(sys.stdin.readline()); print('uvx OK' if 'result' in d else 'uvx FAIL')"
```

Expected: `uvx OK`

- [ ] **Step 7: Final commit — mark Plan D complete**

```bash
git commit --allow-empty -m "chore: Plan D complete — MCP scientific paper understanding v1"
```

---

## Final Verification Checklist

- [ ] `mvn test` passes in `java/opendataloader-pdf-core/` — 528+ tests, BUILD SUCCESS
- [ ] `.venv/bin/python -m pytest tests/` passes in `python/opendataloader-pdf-mcp/` — 65+ tests, 0 failures
- [ ] `{stem}-graph.json` sidecar written when `format=graph-json` passed to Java
- [ ] `job.triage_decision` non-null after `submit_pdf` completes on a text PDF
- [ ] MinerU fallback fires (in test) on `HARD_FAIL` + `enable_mineru_fallback=True`
- [ ] `describe_pdf` returns `section_count > 0` on a multi-section PDF
- [ ] `FormulaSerializer` emits `equation number` field for resolved equations
- [ ] `test_readme.py` passes — all tool names in README
- [ ] `uvx opendataloader-pdf-mcp` handshakes successfully
- [ ] `docs/superpowers/OVERVIEW.md` committed
- [ ] `CLAUDE.md` MCP architecture section committed
