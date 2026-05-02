# MCP Scientific Paper Understanding v1 — Design Spec

**Date:** 2026-05-02
**Plan:** D
**Status:** Approved
**Goal:** Make `opendataloader-pdf-mcp` a first-class tool for AI agents researching scientific papers — structured graph export, triage wiring, `describe_pdf` tool, codebase cleanup, and deployment readiness (local + PyPI).

---

## 1. Problem

Plans A–C built a complete Java enrichment pipeline (structured headings, numbered equations, resolved captions, bibliography entries, inline citation resolution, quality triage) and a Python MCP async job layer with MinerU fallback. However:

1. **The enriched graph is computed but never serialized.** `DocumentProcessor` discards `ExtractionResult.getEnrichedGraphNodes()` after building it. `ReferenceEntrySerializer` and `CitationSerializer` are registered in `ObjectMapperHolder` but never invoked. Agents receive bibliography as raw unstructured text and inline citations as plain brackets.
2. **`triage_decision` and `score` in MCP jobs are always `null`.** `opendataloader_pdf.convert()` returns `None`; there is no mechanism to pass Java's `TriageDecision` back to Python. The MinerU fallback condition (`job.triage_decision == "HARD_FAIL"`) therefore never fires in production.
3. **No lightweight entry-point tool for agents.** An agent must do a full conversion before knowing a paper's section structure, figure count, or reference count. There is no `describe_pdf` equivalent.
4. **`submit_pdf`, `get_job_status`, `cancel_job`, `get_artifact`, and `jobs://` resource are undocumented** in README. Agents relying on MCP tool descriptions have stale information.
5. **Dead/unwired code** exists (`JsonName.EQUATION_NUMBER` defined but unused in `FormulaSerializer`; tool docstrings vague). Needs audit before deployment.

---

## 2. Scope

**In scope:**
- New Java output format `graph-json`: serializes enriched graph + triage into a sidecar file
- `FormulaSerializer` wired to emit `equation_number`
- Python `describe_pdf` MCP tool (synchronous, returns structured paper overview)
- Triage wiring in `jobs._run()`: reads `graph-json` sidecar to populate `job.triage_decision` / `job.score`
- `graph-json` added to supported MCP formats (usable as primary format in `convert_pdf` / `submit_pdf`)
- Codebase audit: dead code removed, unwired code wired or explicitly marked stub
- README rewrite: all tools, resource, and formats documented
- Deployment: `uvx opendataloader-pdf-mcp` smoke-tested locally; `pyproject.toml` PyPI-ready
- Docs reorganization: past plan docs, handoffs, and CLAUDE.md updated for future agents

**Out of scope:**
- Real LLM integration (stub remains `NoOpLlmFallback`)
- Remote MinerU service
- Page-granularity Java/MinerU output merging
- Persistence across MCP server restarts
- Webhook / push notification on job completion

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  opendataloader-pdf-mcp  (Python MCP server)                │
│                                                             │
│  Tools:                                                     │
│    describe_pdf  ──────────────────┐                        │
│    convert_pdf   ──────────────────┤                        │
│    submit_pdf    ──────────────────┤──► opendataloader_pdf  │
│    get_job_status                  │    .convert()          │
│    cancel_job                      │         │              │
│    get_artifact                    │         ▼              │
│                                    │   Java CLI (JAR)       │
│  Resources:                        │         │              │
│    jobs://{job_id}                 │   Writes to tmp_dir:   │
│                                    │   {stem}.md / .json    │
│  jobs.py                           │   {stem}-graph.json ◄──┤
│    JobManager._run()               │         │              │
│      reads -graph.json ────────────┘         │              │
│      sets job.triage_decision                │              │
│      sets job.score                          │              │
│      triggers MinerU if HARD_FAIL            │              │
└──────────────────────────────────────────────┘─────────────┘

Java pipeline (opendataloader-pdf-core):
  DocumentProcessor.generateOutputs()
    ├── existing: JsonWriter (IObject stream → {stem}.json)
    ├── existing: MarkdownWriter, HtmlWriter, TextWriter
    └── NEW: GraphJsonWriter (EnrichedGraphNodes + TriageDecision → {stem}-graph.json)
              ├── FormulaSerializer  (+ equation_number now wired)
              ├── ReferenceEntrySerializer  (already exists)
              └── CitationSerializer  (already exists)
```

---

## 4. Java Changes

### 4.1 `GraphJsonWriter` (new class)

**Location:** `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/json/GraphJsonWriter.java`

Writes `{stem}-graph.json` when `format` contains `graph-json`. Uses the existing `ObjectMapper` from `ObjectMapperHolder`. Input: `ExtractionResult` (for enriched graph nodes) + `TriageDecision` (from `TriagePolicy`).

**Output schema:**

```json
{
  "title":      "string | null",
  "authors":    ["string"],
  "page_count": 12,
  "sections": [
    { "level": 1, "text": "Introduction", "page": 1 }
  ],
  "equations": [
    { "id": "eq-1", "latex": "E=mc^2", "number": "3.1", "page": 2, "display_mode": true }
  ],
  "figures": [
    { "id": "fig-1", "caption": "Architecture overview", "page": 3 }
  ],
  "references": [
    { "ref_id": "ref-1", "text": "Smith et al. ...", "metadata": { "year": "2019" } }
  ],
  "citations": [
    { "markers": ["[12]"], "resolved_ref_ids": ["ref-12"], "page": 4 }
  ],
  "triage": {
    "outcome":         "PASS",
    "composite_score": 0.91,
    "gate_failures":   []
  }
}
```

**Field sources:**

| Field | Source |
|---|---|
| `title`, `authors` | PDF metadata from `DocumentInfo` (already available in `DocumentProcessor`) |
| `page_count` | `contents.size()` |
| `sections` | `HeadingNode` entries from `enrichedGraphNodes` |
| `equations` | `EquationNode` entries — `latex`, `number` (now wired), `displayMode`, `page` |
| `figures` | `CaptionNode` entries with `kind=FIGURE` |
| `references` | `ReferenceEntryNode` entries (serializer already exists) |
| `citations` | `CitationNode` entries (serializer already exists) |
| `triage` | `TriageDecision.outcome`, `compositeScore`, `gateFailureReasons` |

### 4.2 `FormulaSerializer` fix

`JsonName.EQUATION_NUMBER` is defined but not emitted. Add one line to `FormulaSerializer.serialize()`:

```java
gen.writeStringField(JsonName.EQUATION_NUMBER, node.getNumber());
```

Called only when `node.getNumber() != null` (i.e., equation was successfully numbered by `EquationNumberEnricher`).

### 4.3 `DocumentProcessor` wiring

In `generateOutputs()`, after the existing format dispatch:

```java
if (formats.contains("graph-json")) {
    TriageDecision triage = TriagePolicy.decide(
        WeightedScorecard.fromFile(weightsPath), extraction
    );
    new GraphJsonWriter().write(stem, outputDir, extraction, triage);
}
```

`TriagePolicy` and `WeightedScorecard` already exist. `weightsPath` reads `benchmarks/config/scorecard-weights-v0.json` (same path `WeightedScorecard.fromFile` already knows).

**`graph-json` can be combined with other formats:** `--format markdown,graph-json` writes both `{stem}.md` and `{stem}-graph.json`.

### 4.4 `options.json` / CLI sync

Add `graph-json` to the `format` enum in `options.json`. Run `npm run sync` to regenerate Python/Node bindings. This makes `opendataloader_pdf.convert(format="graph-json")` valid.

---

## 5. Python MCP Changes

### 5.1 `describe_pdf` tool (`server.py`)

**Synchronous.** Calls `opendataloader_pdf.convert()` with `format="graph-json"` into a temp dir, reads the sidecar, returns structured dict. Does not store a job.

```python
@mcp.tool()
def describe_pdf(
    input_path: str,
    password: str | None = None,
    pages: str | None = None,
) -> dict:
    """Get a structured overview of a PDF — sections, equations, figures, and references.

    Returns a summary dict without full text content. Use convert_pdf or submit_pdf
    to get the full converted content.

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
```

**Error handling:**
- File not found → `FileNotFoundError` (same as `convert_pdf`)
- `graph-json` sidecar missing after convert → `RuntimeError("graph-json output not produced — check Java version")`
- Malformed sidecar JSON → returns partial dict, `triage` omitted, no raise

### 5.2 `jobs.py` — triage wiring

After `opendataloader_pdf.convert()` succeeds in `_run()`, look for `{stem}-graph.json` in `tmp_dir`. If present:

```python
graph_file = Path(tmp_dir) / f"{input_file.stem}-graph.json"
if graph_file.is_file():
    try:
        g = json.loads(graph_file.read_text(encoding="utf-8"))
        t = g.get("triage", {})
        job.triage_decision = t.get("outcome")   # "PASS" | "SOFT_FAIL" | "HARD_FAIL"
        job.score = t.get("composite_score")
    except Exception:
        pass  # silent — job continues to DONE
```

This requires `jobs._run()` to always request `graph-json` as a secondary format so triage is always available. Implementation: the internal convert kwargs always include `graph-json` appended to the format string (e.g., `"markdown,graph-json"`). The sidecar is read for triage; the primary artifact returned to the caller is still only the originally requested format. If the caller requested `graph-json` as the primary format, it is returned as the artifact directly and no separate sidecar read is needed.

### 5.3 `_SUPPORTED_FORMATS` and `_EXT_MAP` updates

Add `graph-json` to both:

```python
_SUPPORTED_FORMATS = {"json", "text", "html", "markdown",
                      "markdown-with-html", "markdown-with-images", "graph-json"}
_EXT_MAP = { ..., "graph-json": "-graph.json" }
```

### 5.4 Tool docstring improvements

All five tools get docstrings that explicitly state:

- What the tool returns (field names, types)
- When to use it vs. alternatives
- Recommended format for scientific papers: `markdown-with-images`

`submit_pdf` docstring example:

```
Submit a PDF for async conversion. Returns job_id immediately.
Poll with get_job_status. Retrieve result with get_artifact.

For scientific papers, use format="markdown-with-images" to capture figures.
Use enable_mineru_fallback=True for scanned or image-only PDFs.
```

---

## 6. Codebase Audit

| Item | Location | Finding | Action |
|---|---|---|---|
| `JsonName.EQUATION_NUMBER` | `json/JsonName.java` | Defined, not emitted | Wire in `FormulaSerializer` (§4.2) |
| `ReferenceEntrySerializer` | `json/serializers/` | Registered, never invoked for main output | Invoked by `GraphJsonWriter` (§4.1) |
| `CitationSerializer` | `json/serializers/` | Registered, never invoked for main output | Invoked by `GraphJsonWriter` (§4.1) |
| `LlmEnrichmentPass` / `NoOpLlmFallback` | `llm/` | Wired, always no-op | Keep; add Javadoc: "stub — real LLM integration deferred" |
| `FeatureConfidenceReport` | `quality/` | Used internally by `QualityGate` | Keep; no change |
| `jobs://` resource | `server.py` | Functional, undocumented | Document in README |
| `submit_pdf` / `get_job_status` / `cancel_job` / `get_artifact` | `server.py` | Functional, undocumented in README | Document in README |
| `bench-production-v0.sh` | `scripts/` | Functional, no README mention | Add to README under "Benchmarking" |

---

## 7. Documentation Reorganization

### 7.1 README rewrite (`python/opendataloader-pdf-mcp/README.md`)

Sections:

1. **Overview** — "MCP server for AI agents to read and understand scientific PDFs"
2. **Prerequisites** — Java 11+, Python 3.10+
3. **Installation** — `pip install opendataloader-pdf-mcp` / `uvx opendataloader-pdf-mcp`
4. **Client setup** — Claude Desktop, Claude Code, Cursor, Codex, Windsurf (existing)
5. **Tools** — all 6 tools documented with parameters and return shapes
6. **Resources** — `jobs://{job_id}` documented
7. **Formats** — table of all formats including `graph-json`
8. **Scientific paper workflow** — example: `describe_pdf` → `submit_pdf(format="markdown-with-images")` → `get_artifact`
9. **MinerU fallback** — when and how to enable
10. **License**

### 7.2 `CLAUDE.md` update

Add section: **MCP Server Architecture** — brief description of the 3-layer architecture (Java → Python wrapper → MCP tools), which files own which responsibility, and gotchas for future agents.

### 7.3 `docs/superpowers/` structure

After merge:
- All plan docs stay as-is (historical record)
- Add `docs/superpowers/OVERVIEW.md` — one-page map of all plans (A→D), what each built, and current state
- Handoff docs stay in `docs/handoff/` — no changes needed (they're historical)

---

## 8. Deployment Readiness

### 8.1 Local smoke test procedure

```bash
# 1. Build/install locally
cd python/opendataloader-pdf-mcp
pip install -e ".[dev]"

# 2. Test describe_pdf against a real arXiv paper
python -c "
from opendataloader_pdf_mcp.server import describe_pdf
r = describe_pdf('/path/to/paper.pdf')
print(r)
assert r['page_count'] > 0
assert len(r['sections']) > 0
"

# 3. Test uvx entry point (starts server in stdio mode; Ctrl-C to exit)
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"0"}}}' | uvx opendataloader-pdf-mcp

# 4. Test in Cursor/Claude Desktop by adding to mcp.json
```

### 8.2 `pyproject.toml` additions

```toml
[project]
version = "0.2.0"  # bump from 0.1.x
classifiers = [
    "Development Status :: 4 - Beta",
    "Intended Audience :: Science/Research",
    "Topic :: Scientific/Engineering :: Artificial Intelligence",
    "Topic :: Text Processing :: Markup",
]
keywords = ["pdf", "mcp", "scientific-papers", "ai-agents", "llm"]
```

---

## 9. Testing

### Java

| Test | File | What it verifies |
|---|---|---|
| `GraphJsonWriterTest.testWritesGraphJson` | `GraphJsonWriterTest.java` | `{stem}-graph.json` written, schema valid |
| `GraphJsonWriterTest.testEquationNumberEmitted` | same | `equation_number` present when enricher resolved it |
| `GraphJsonWriterTest.testReferencesStructured` | same | `references` array non-empty, `ref_id` + `text` present |
| `GraphJsonWriterTest.testCitationsResolved` | same | `citations[].resolved_ref_ids` populated |
| `GraphJsonWriterTest.testTriageBlock` | same | `triage.outcome` and `composite_score` present |
| `FormulaSerializerTest.testEquationNumberField` | `FormulaSerializerTest.java` | JSON contains `equation_number` field |

### Python

| Test | File | What it verifies |
|---|---|---|
| `test_describe_pdf_returns_structure` | `test_server_async_tools.py` | Returns dict with all required keys |
| `test_describe_pdf_file_not_found` | same | `FileNotFoundError` raised |
| `test_describe_pdf_no_graph_output` | same | `RuntimeError` when sidecar missing |
| `test_triage_wiring_populates_job` | `test_jobs.py` | `job.triage_decision` set after convert writes graph-json |
| `test_triage_wiring_mineru_triggers` | `test_jobs.py` | MinerU fallback fires when `triage_decision == "HARD_FAIL"` |
| `test_graph_json_format_in_supported` | `test_jobs.py` | `graph-json` accepted by `submit()` |
| `test_readme_documents_all_tools` | `test_readme.py` (new) | All `@mcp.tool()` names appear in README |

---

## 10. File Map

| File | Action | What changes |
|---|---|---|
| `java/.../json/GraphJsonWriter.java` | Create | New class — writes enriched graph + triage to sidecar |
| `java/.../json/serializers/FormulaSerializer.java` | Modify | Add `equation_number` field |
| `java/.../processors/DocumentProcessor.java` | Modify | Call `GraphJsonWriter` when `format` contains `graph-json`; run `TriagePolicy` |
| `java/.../pom.xml` | Modify | No new deps needed |
| `options.json` | Modify | Add `graph-json` to format enum |
| `python/opendataloader-pdf/src/.../convert_generated.py` | Regenerate | `npm run sync` after options.json change |
| `python/.../server.py` | Modify | Add `describe_pdf`; improve all docstrings; add `graph-json` to format list |
| `python/.../jobs.py` | Modify | Triage wiring from sidecar; `graph-json` in `_SUPPORTED_FORMATS` / `_EXT_MAP` |
| `python/.../pyproject.toml` | Modify | Version bump, classifiers, keywords |
| `python/.../README.md` | Rewrite | All tools, resource, formats, scientific paper workflow |
| `python/.../tests/test_server_async_tools.py` | Modify | Add `describe_pdf` tests |
| `python/.../tests/test_jobs.py` | Modify | Add triage wiring tests, graph-json format tests |
| `python/.../tests/test_readme.py` | Create | README completeness check |
| `java/.../tests/GraphJsonWriterTest.java` | Create | 5 unit tests |
| `java/.../tests/FormulaSerializerTest.java` | Create or modify | `equation_number` field test |
| `docs/superpowers/OVERVIEW.md` | Create | Plan A–D summary for future agents |
| `CLAUDE.md` | Modify | Add MCP server architecture section |

---

## 11. Acceptance Criteria

- `describe_pdf` on any text-based scientific PDF returns `page_count > 0`, `len(sections) > 0`, and (for papers with references) `reference_count > 0`
- `job.triage_decision` is non-null after `submit_pdf` completes on a text-based PDF
- MinerU fallback fires (in tests) when `triage_decision == "HARD_FAIL"` and `enable_mineru_fallback=True`
- `convert_pdf(format="graph-json")` returns valid JSON with all schema fields present
- `FormulaSerializer` emits `equation_number` when the enricher resolved it
- All `@mcp.tool()` names documented in README (enforced by `test_readme.py`)
- `uvx opendataloader-pdf-mcp` starts without error on a clean machine with Java 11+
- Java test suite: 528+ passing (no regressions)
- Python test suite: 60+ passing (all new tests green)
