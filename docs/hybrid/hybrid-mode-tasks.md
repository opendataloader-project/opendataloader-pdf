# Hybrid Mode Implementation Tasks

Each task is independently executable. A new Claude Code session can reference this document to perform the task.

---

## Decision Points (Runtime-Dependent)

These decisions **require execution results** before they can be made.

### After Phase -1 (based on docling API test results)

| Decision | How to Verify | Impact |
|----------|---------------|--------|
| **API endpoint** | Check available endpoints in OpenAPI spec | Task 6 client implementation |
| **Page filtering support** | Check if API options include page filter | Full PDF send required if unsupported |
| **Response page structure** | Analyze sample response JSON structure | Task 7 per-page separation logic |
| **Coordinate system** | Check bbox value ranges in sample response | Task 7 coordinate conversion formula |
| **docling element types** | Extract actual type values from sample response | Task 1 mapping table |

### After Phase 2 (based on benchmark results)

| Decision | How to Verify | Impact |
|----------|---------------|--------|
| **Triage threshold tuning** | Check triage_fn (missed tables) count | Lower thresholds if recall < 95% |

### After Phase 4 (based on evaluation results)

| Decision | How to Verify | Impact |
|----------|---------------|--------|
| **Triage re-tuning needed** | Analyze FN case signal patterns | Phase 2 rework |

---

## Progress Tracker

| Task | Status | Completed | Notes |
|------|--------|-----------|-------|
| Task -1: Pre-research | ✅ completed | 2026-01-02 | See docs/hybrid/research/ |
| Task 0: docling-api skill | ⬜ not_started | - | Depends on Task -1 |
| Task 1: schema-mapping skill | ⬜ not_started | - | Depends on Task -1 |
| Task 2: triage-criteria skill | ⬜ not_started | - | Depends on Task -1 |
| Task 3: HybridConfig | ⬜ not_started | - | - |
| Task 4: CLI Options | ⬜ not_started | - | Depends on Task 3 |
| Task 5: TriageProcessor | ⬜ not_started | - | Depends on Task 3 |
| Task 6: DoclingClient | ⬜ not_started | - | Depends on Task 0, 3 |
| Task 7: SchemaTransformer | ⬜ not_started | - | Depends on Task 1, 6 |
| Task 8: HybridDocumentProcessor | ⬜ not_started | - | Depends on Task 5, 6, 7 |
| Task 9: Triage Logging | ⬜ not_started | - | Depends on Task 8 |
| Task 10: Triage Evaluator | ⬜ not_started | - | Depends on Task 9 |
| Task 11: Triage Analyzer Agent | ⬜ not_started | - | Depends on Task 2, 10 |

**Status Legend:**
- ⬜ `not_started` - Not yet begun
- 🔄 `in_progress` - Currently working
- ✅ `completed` - Done and verified
- ⏸️ `blocked` - Waiting on dependency or issue

---

## Task -1: Pre-research & Data Collection

### Goal
Collect all required data and specifications before implementation begins.

### Prerequisites
- Docker installed
- Access to test PDFs in `tests/benchmark/pdfs/`

### Research Steps

#### 1. Start docling-serve and collect OpenAPI spec
```bash
# Start docling-serve (official container image)
# Reference: https://github.com/docling-project/docling-serve
docker run -d -p 5001:5001 --name docling-serve \
  -e DOCLING_SERVE_ENABLE_UI=1 \
  quay.io/docling-project/docling-serve

# Wait for startup (model loading takes time)
sleep 30

# Verify server is running (check API docs page)
curl -s http://localhost:5001/docs | head -20

# Access UI playground at: http://localhost:5001/ui

# Collect OpenAPI specification
curl http://localhost:5001/openapi.json > docs/hybrid/research/docling-openapi.json

# Check available endpoints
cat docs/hybrid/research/docling-openapi.json | jq '.paths | keys'

# Alternative: Using pip (if Docker not available)
# pip install "docling-serve[ui]"
# docling-serve run --enable-ui
```

#### 2. Test API and collect sample response
```bash
# Convert using /v1/convert/source endpoint (official API)
# Using file URL source
curl -X POST http://localhost:5001/v1/convert/source \
  -H "Content-Type: application/json" \
  -d '{
    "sources": [{"kind": "file", "path": "samples/pdf/1901.03003.pdf"}],
    "options": {"to_formats": ["json", "md"], "do_table_structure": true}
  }' \
  > docs/hybrid/research/docling-sample-response.json

# If file path doesn't work, try with base64 or HTTP URL
# Alternative: Use multipart form if available
curl -X POST http://localhost:5001/v1/convert/source \
  -F "file=@samples/pdf/1901.03003.pdf" \
  > docs/hybrid/research/docling-sample-response.json

# Extract response structure
cat docs/hybrid/research/docling-sample-response.json | jq 'keys'
cat docs/hybrid/research/docling-sample-response.json | jq '.document | keys' 2>/dev/null || \
  cat docs/hybrid/research/docling-sample-response.json | jq '.[0] | keys'

# Check element types in response
cat docs/hybrid/research/docling-sample-response.json | jq '[.. | .type? // empty] | unique' 2>/dev/null
```

#### 3. Extract documents with tables (for triage evaluation)
```bash
# List documents containing tables
cat tests/benchmark/ground-truth/reference.json | \
  jq -r 'to_entries[] | select(.value[]?.category == "Table") | .key' | \
  sort | uniq > docs/hybrid/research/documents-with-tables.txt

# Count
wc -l docs/hybrid/research/documents-with-tables.txt
```

#### 4. Parse same PDF with OpenDataLoader Java
```bash
# Build Java CLI
./scripts/build-java.sh

# Parse the same PDF with Java (JSON output)
java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar \
  --format json \
  -o docs/hybrid/research/ \
  samples/pdf/1901.03003.pdf

# Rename for clarity
mv docs/hybrid/research/1901.03003.json docs/hybrid/research/opendataloader-sample-response.json

# Also generate markdown for comparison
java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar \
  --format md \
  -o docs/hybrid/research/ \
  samples/pdf/1901.03003.pdf

mv docs/hybrid/research/1901.03003.md docs/hybrid/research/opendataloader-sample-response.md
```

#### 5. Document IObject class structure
```bash
# Find all semantic types
grep -r "class Semantic" java/opendataloader-pdf-core/ --include="*.java" -l

# Find TableBorder structure
grep -r "class TableBorder" java/opendataloader-pdf-core/ --include="*.java" -A 20

# List all IObject implementations
grep -r "implements.*IObject" java/opendataloader-pdf-core/ --include="*.java"
```

#### 6. Compare docling vs OpenDataLoader output
```bash
# Compare element counts
echo "=== Docling elements ==="
cat docs/hybrid/research/docling-sample-response.json | jq '[.document.content[].type] | group_by(.) | map({type: .[0], count: length})'

echo "=== OpenDataLoader elements ==="
cat docs/hybrid/research/opendataloader-sample-response.json | jq '[.kids[].semanticType] | group_by(.) | map({type: .[0], count: length})'
```

### Files to Create
```
docs/hybrid/research/
├── docling-openapi.json              # Full OpenAPI spec
├── docling-sample-response.json      # Docling conversion response
├── opendataloader-sample-response.json  # OpenDataLoader JSON output
├── opendataloader-sample-response.md    # OpenDataLoader markdown output
├── documents-with-tables.txt         # List of docs with tables
└── iobject-structure.md              # IObject class hierarchy summary
```

### Success Criteria
- [ ] docling-serve running and accessible
- [ ] OpenAPI spec saved
- [ ] Docling sample response JSON saved (with tables, headings, figures)
- [ ] OpenDataLoader sample response JSON saved (same PDF)
- [ ] OpenDataLoader sample markdown saved
- [ ] Documents with tables list extracted (should be ~42 docs)
- [ ] IObject class structure documented
- [ ] Element type comparison between docling and OpenDataLoader completed

### Test Method
```bash
# Verify all files exist
ls -la docs/hybrid/research/

# Verify docling response has expected structure
cat docs/hybrid/research/docling-sample-response.json | jq '.document.content | length'
```

### Dependencies
- None (first task)

### Output
This research enables:
- Task 0: docling-api skill (uses OpenAPI spec + sample response)
- Task 1: schema-mapping skill (uses sample response + IObject structure)
- Task 2: triage-criteria skill (uses documents-with-tables list)

---

## Task 0: Docling API Skill Setup

### Goal
Create Claude skill for docling-serve API specification so Claude can correctly generate API integration code.

### Prerequisites
- docling-serve running locally or accessible endpoint
- curl or HTTP client for API testing

### Required Research
1. Fetch docling-serve official documentation
2. Make actual API calls to capture request/response structure
3. Collect JSON output schema samples from real responses

### Research Steps
```bash
# 1. Start docling-serve
docker run -p 5001:5001 ds4sd/docling-serve

# 2. Check available endpoints
curl http://localhost:5001/docs  # OpenAPI spec

# 3. Test conversion API
curl -X POST http://localhost:5001/v1/convert/file \
  -F "file=@tests/benchmark/pdfs/01030000000001.pdf" \
  -F "options={\"to_formats\":[\"json\",\"md\"]}" \
  > docling-response-sample.json

# 4. Extract schema structure
cat docling-response-sample.json | jq 'keys'
cat docling-response-sample.json | jq '.document.content[0]'
```

### Files to Create
```
.claude/skills/docling-api/
├── SKILL.md              # API specification and usage guide
├── request-schema.json   # Request format reference
└── response-schema.json  # Response structure reference
```

### SKILL.md Template
```markdown
---
name: docling-api
description: docling-serve REST API specification. Use when implementing DoclingClient or calling docling API.
---

# docling-serve API Reference

## Base URL
`http://localhost:5001`

## Endpoints

### POST /v1/convert/file
Convert PDF file to structured output.

**Request:**
- Content-Type: multipart/form-data
- file: PDF binary
- options: JSON string with conversion options

**Options:**
```json
{
  "to_formats": ["json", "md"],
  "do_table_structure": true,
  "do_ocr": false
}
```

**Response:**
See response-schema.json for full structure.

## Element Types
| Type | Description |
|------|-------------|
| paragraph | Text paragraph |
| table | Table with cells |
| heading | Section heading (level 1-6) |
| list | Bulleted or numbered list |
| figure | Image or diagram |
```

### Success Criteria
- [ ] API endpoints documented with request/response examples
- [ ] Response JSON schema captured from real API call
- [ ] Skill auto-applies when Claude handles docling-related tasks
- [ ] New Claude session can generate correct DoclingClient code using skill

### Test Method
```bash
# In new Claude Code session:
claude "Write a Java method to call docling-serve API"
# Expected: Claude uses SKILL.md to generate correct endpoint, headers, request format
```

### Dependencies
- None (first task, enables other tasks)

---

## Task 1: Schema Mapping Skill Setup

### Goal
Create Claude skill documenting the mapping between docling output schema and Java IObject hierarchy.

### Prerequisites
- Task 0 completed (docling response schema available)
- Understanding of existing IObject types in codebase

### Required Research
```bash
# 1. Get docling element types from response
cat docling-response-sample.json | jq '.document.content[].type' | sort | uniq

# 2. List existing IObject types
grep -r "class.*implements IObject" java/ --include="*.java"
grep -r "class Semantic" java/ --include="*.java"

# 3. Compare field structures
# docling table cell structure vs TableBorderCell
# docling paragraph structure vs SemanticParagraph
```

### Files to Create
```
.claude/skills/schema-mapping/
├── SKILL.md                    # Mapping rules and guidelines
├── docling-elements.json       # Docling element type samples
└── iobject-types.md            # IObject type reference
```

### SKILL.md Template
```markdown
---
name: schema-mapping
description: Mapping between docling output and Java IObject types. Use when implementing DoclingSchemaTransformer.
---

# Schema Mapping: Docling → IObject

## Type Mapping

| Docling Type | IObject Type | Key Fields |
|--------------|--------------|------------|
| `paragraph` | `SemanticParagraph` | text, bbox |
| `table` | `TableBorder` | cells[][], bbox |
| `heading` | `SemanticHeading` | text, level, bbox |
| `list` | `PDFList` | items[], bbox |
| `figure` | `ImageChunk` | bbox, metadata |

## Field Mapping Details

### Table Mapping
```
docling:
  cells: [{row, col, text, rowspan, colspan}]

IObject (TableBorder):
  rows: [TableBorderRow]
    cells: [TableBorderCell]
      contents: List<IObject>
      colSpan, rowSpan
```

### Bounding Box
```
docling: {x, y, width, height} (normalized 0-1)
IObject: BoundingBox(left, bottom, right, top) (PDF points)

Conversion: multiply by page dimensions
```
```

### Success Criteria
- [ ] All docling element types mapped to IObject types
- [ ] Field-level mapping documented
- [ ] Coordinate system conversion documented
- [ ] Skill auto-applies when implementing transformer

### Test Method
```bash
# In new Claude Code session:
claude "Transform this docling table JSON to TableBorder"
# Expected: Claude uses mapping rules to generate correct transformation code
```

### Dependencies
- Task 0 (docling response schema)

---

## Task 2: Triage Criteria Skill Setup

### Goal
Create Claude skill documenting triage decision rules for routing pages to Java vs Docling.

### Prerequisites
- Understanding of page content signals (LineChunk, TextChunk, etc.)
- Knowledge of table detection patterns

### Required Research
```bash
# 1. Analyze page content types
grep -r "LineChunk\|TextChunk\|TableBorder" java/ --include="*.java" | head -20

# 2. Find existing table detection logic
grep -r "detectTable\|TableBorder" java/opendataloader-pdf-core/ --include="*.java"

# 3. Review ground truth for table presence patterns
cat tests/benchmark/ground-truth/reference.json | jq '[.[][] | select(.category=="Table")] | length'
```

### Files to Create
```
.claude/skills/triage-criteria/
├── SKILL.md              # Triage rules and thresholds
└── signals.md            # Signal extraction methods
```

### SKILL.md Template
```markdown
---
name: triage-criteria
description: Page triage decision rules. Use when implementing or tuning TriageProcessor.
---

# Triage Criteria

## Strategy
**Conservative**: Minimize false negatives (missing tables). Accept false positives (unnecessary docling calls).

## Decision Signals

| Signal | Extraction | Threshold | Action |
|--------|------------|-----------|--------|
| Line/Text ratio | lineChunks.size() / textChunks.size() | > 0.3 | → DOCLING |
| Grid pattern | aligned horizontal + vertical lines | >= 3 groups | → DOCLING |
| TableBorder detected | existing detector finds border | any | → DOCLING |
| Default | - | - | → JAVA |

## Threshold Tuning Guide

### If FN (missed tables) is high:
- Lower line/text ratio threshold
- Lower grid pattern threshold
- Add more signals

### If too slow (too many docling calls):
- Raise thresholds
- Add early-exit conditions for simple pages

## Benchmark Metrics
- `triage_recall`: Tables correctly sent to docling (target: >= 0.95)
- `triage_fn`: Tables missed (target: <= 5)
```

### Success Criteria
- [ ] All triage signals documented
- [ ] Threshold values specified with rationale
- [ ] Tuning guidelines included
- [ ] Skill auto-applies when working on TriageProcessor

### Test Method
```bash
# In new Claude Code session:
claude "The triage FN is too high, how should I adjust thresholds?"
# Expected: Claude references skill to suggest specific threshold changes
```

### Dependencies
- None (can be created from codebase analysis)

---

## Task 3: Config Extension (HybridConfig)

### Goal
Add configuration classes for hybrid processing.

### Context
- Current `Config.java` has no hybrid concept
- Need `HybridConfig` to store backend connection settings
- Design for extensibility (docling first, then azure, google, etc.)

### Files to Modify
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/api/Config.java`

### Files to Create
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/HybridConfig.java`

### Implementation Details
```java
// HybridConfig.java
public class HybridConfig {
    private String url;                  // null = use backend default
    private int timeoutMs = 30000;
    private boolean fallbackToJava = true;
    private int maxConcurrentRequests = 4;
    // getters, setters, builder pattern

    // Backend-specific default URLs
    public static String getDefaultUrl(String hybrid) {
        return switch (hybrid) {
            case "docling" -> "http://localhost:5001";
            case "hancom" -> null;  // requires explicit URL
            case "azure" -> null;   // requires explicit URL
            case "google" -> null;  // requires explicit URL
            default -> null;
        };
    }
}

// Config.java additions
public static final String HYBRID_OFF = "off";
public static final String HYBRID_DOCLING = "docling";
public static final String HYBRID_HANCOM = "hancom";
public static final String HYBRID_AZURE = "azure";
public static final String HYBRID_GOOGLE = "google";
private static Set<String> hybridOptions = new HashSet<>();

private String hybrid = HYBRID_OFF;
private HybridConfig hybridConfig = new HybridConfig();

static {
    hybridOptions.add(HYBRID_OFF);
    hybridOptions.add(HYBRID_DOCLING);
    // hancom, azure, google added when implemented
}

public boolean isHybridEnabled() {
    return !HYBRID_OFF.equals(hybrid);
}
```

### Success Criteria
- [ ] `HybridConfig` class created with all fields
- [ ] `Config.java` has `hybrid` field with validation
- [ ] `Config.java` has `HybridConfig` field
- [ ] `isHybridEnabled()` helper method
- [ ] Existing tests pass: `./scripts/test-java.sh`

### Test Method
```bash
./scripts/test-java.sh
```

### Dependencies
- None

---

## Task 4: CLI Options for Hybrid

### Goal
Add CLI options to enable hybrid processing.

### Context
- Current CLI has no `--hybrid` option
- Need to configure backend URL, timeout from command line
- Follow existing `OptionDefinition` pattern in `CLIOptions.java`

### Files to Modify
- `java/opendataloader-pdf-cli/src/main/java/org/opendataloader/pdf/cli/CLIOptions.java`

### Implementation Details
```
New options:
  --hybrid <off|docling|hancom|...> Hybrid backend to use (default: off)
  --hybrid-url <url>                Backend server URL (default: backend-specific)
  --hybrid-timeout <ms>             Request timeout in ms (default: 30000)
  --hybrid-fallback                 Fallback to Java on error (default: true)
```

```java
// Add to OPTION_DEFINITIONS list
new OptionDefinition("hybrid", null, "string", "off",
    "Hybrid backend for AI processing. Values: off (default), docling, hancom", true),
new OptionDefinition("hybrid-url", null, "string", null,
    "Hybrid backend server URL (overrides default)", true),
new OptionDefinition("hybrid-timeout", null, "string", "30000",
    "Hybrid backend request timeout in milliseconds", true),
new OptionDefinition("hybrid-fallback", null, "boolean", true,
    "Fallback to Java on hybrid backend error", true),
```

### Success Criteria
- [ ] `--hybrid` option parsing and Config reflection
- [ ] `--hybrid-url` option parsing
- [ ] `--hybrid-timeout` option parsing
- [ ] `--hybrid-fallback` option parsing
- [ ] `--help` shows new options
- [ ] Options exported to JSON (`--export-options`)
- [ ] Existing tests pass

### Test Method
```bash
# Build
./scripts/build-java.sh

# Check options
java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar --help

# Test run (hybrid off, default behavior)
java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar \
  --hybrid off \
  tests/benchmark/pdfs/01030000000001.pdf

# Verify JSON export includes new options
java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar --export-options | jq '.options[] | select(.name | startswith("hybrid"))'
```

### Dependencies
- Task 3 (HybridConfig)

---

## Task 5: TriageProcessor Implementation

### Goal
Implement page-level triage decision logic (JAVA vs BACKEND routing).

### Context
- **Conservative strategy**: Minimize FN (missed tables), accept FP (unnecessary backend calls)
- Fast heuristics (microseconds, not milliseconds)
- Runs after `ContentFilterProcessor.getFilteredContents()`, before table processing

### Files to Create
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/TriageProcessor.java`

### Implementation Details
```java
public class TriageProcessor {
    public enum TriageDecision { JAVA, BACKEND }

    public record TriageResult(
        int pageNumber,
        TriageDecision decision,
        double confidence,
        TriageSignals signals
    ) {}

    public record TriageSignals(
        int lineChunkCount,
        int textChunkCount,
        double lineToTextRatio,
        int alignedLineGroups,
        boolean hasTableBorder
    ) {}

    /**
     * Classify a page for processing path.
     * Conservative: bias toward BACKEND when uncertain.
     */
    public static TriageResult classifyPage(
        List<IObject> filteredContents,
        int pageNumber,
        HybridConfig config
    ) {
        // Extract signals from content
        // Apply thresholds (from triage-criteria skill)
        // Return decision with confidence
    }

    /**
     * Batch triage for all pages.
     */
    public static Map<Integer, TriageResult> triageAllPages(
        Map<Integer, List<IObject>> pageContents,
        HybridConfig config
    ) {
        // Triage all pages, return map of results
    }
}
```

### Triage Heuristics (Initial - Conservative)
| Signal | Threshold | Action |
|--------|-----------|--------|
| LineChunk / TextChunk ratio | > 0.3 | → BACKEND |
| Aligned line groups (grid pattern) | >= 3 | → BACKEND |
| TableBorder detected | any | → BACKEND |
| Default | - | → JAVA |

### Success Criteria
- [ ] `TriageProcessor` class created
- [ ] `TriageResult`, `TriageSignals` records defined
- [ ] `classifyPage()` method implemented
- [ ] `triageAllPages()` batch method implemented
- [ ] Unit tests written and passing
- [ ] Conservative thresholds set (minimize FN)

### Test Method
```bash
cd java && mvn test -Dtest=TriageProcessorTest
./scripts/test-java.sh
```

### Dependencies
- Task 3 (HybridConfig)
- Skill: triage-criteria (Task 2)

---

## Task 6: DoclingClient Implementation

### Goal
Implement REST API client for docling-serve with batch processing support.

### Context
- Uses docling-serve official API
- **Batch processing**: Send multiple pages in one request for efficiency
- Async support for parallel processing with Java path
- First backend implementation (template for future backends)

### Files to Create
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/HybridClient.java` (interface)
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/DoclingClient.java`

### Implementation Details
```java
// HybridClient.java - interface for all hybrid backends
public interface HybridClient {
    record HybridRequest(
        byte[] pdfBytes,
        Set<Integer> pageNumbers,  // 1-indexed, pages to process
        boolean doTableStructure,
        boolean doOcr
    ) {}

    record HybridResponse(
        String markdown,
        JsonNode json,              // Full structured output
        Map<Integer, JsonNode> pageContents  // Per-page content
    ) {}

    HybridResponse convert(HybridRequest request) throws IOException;
    CompletableFuture<HybridResponse> convertAsync(HybridRequest request);
    boolean isAvailable();
}

// DoclingClient.java - docling-serve implementation
public class DoclingClient implements HybridClient {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutMs;

    public DoclingClient(HybridConfig config) { ... }

    // Implements HybridClient interface
}

// Factory for creating hybrid clients
public class HybridClientFactory {
    public static HybridClient create(String hybrid, HybridConfig config) {
        return switch (hybrid) {
            case "docling" -> new DoclingClient(config);
            // case "hancom" -> new HancomClient(config);
            // case "azure" -> new AzureClient(config);
            default -> throw new IllegalArgumentException("Unknown hybrid backend: " + hybrid);
        };
    }
}
```

### Success Criteria
- [ ] `HybridClient` interface created
- [ ] `DoclingClient` class implements interface
- [ ] `HybridClientFactory` for creating clients
- [ ] `convert()` method implemented (HTTP request)
- [ ] `convertAsync()` method for parallel processing
- [ ] `isAvailable()` health check implemented
- [ ] Timeout handling
- [ ] Error handling (IOException, retry logic)
- [ ] Integration test (mock or real server)

### Test Method
```bash
# Start docling-server
docker run -p 5001:5001 ds4sd/docling-serve

# Integration test
cd java && mvn test -Dtest=DoclingClientIntegrationTest

# Manual test
curl -X POST http://localhost:5001/v1/convert/file \
  -F "file=@tests/benchmark/pdfs/01030000000001.pdf"
```

### Dependencies
- Task 3 (HybridConfig)
- Skill: docling-api (Task 0)

---

## Task 7: DoclingSchemaTransformer Implementation

### Goal
Transform docling JSON output to IObject hierarchy.

### Context
- docling JSON response → Java IObject conversion
- Must produce same output schema as Java path for downstream compatibility
- Handle Table, Paragraph, Heading, List, Figure
- First transformer implementation (template for future backends)

### Files to Create
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/HybridSchemaTransformer.java` (interface)
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/DoclingSchemaTransformer.java`

### Implementation Details
```java
// HybridSchemaTransformer.java - interface for all hybrid backends
public interface HybridSchemaTransformer {
    Map<Integer, List<IObject>> transformAll(
        HybridResponse response,
        Map<Integer, BoundingBox> pageBoundingBoxes
    );
}

// DoclingSchemaTransformer.java - docling implementation
public class DoclingSchemaTransformer implements HybridSchemaTransformer {

    @Override
    public Map<Integer, List<IObject>> transformAll(
        HybridResponse response,
        Map<Integer, BoundingBox> pageBoundingBoxes
    ) { ... }

    /**
     * Transform single page content.
     */
    public List<IObject> transformPage(
        JsonNode pageContent,
        int pageNumber,
        BoundingBox pageBoundingBox
    ) { ... }

    // Type-specific transformers
    private TableBorder transformTable(JsonNode tableNode, int pageNumber);
    private SemanticParagraph transformParagraph(JsonNode paragraphNode, int pageNumber);
    private SemanticHeading transformHeading(JsonNode headingNode, int level, int pageNumber);
    private PDFList transformList(JsonNode listNode, int pageNumber);
    private ImageChunk transformFigure(JsonNode figureNode, int pageNumber);

    // Coordinate conversion
    private BoundingBox convertBoundingBox(JsonNode bbox, BoundingBox pageBox);
}
```

### Success Criteria
- [ ] `HybridSchemaTransformer` interface created
- [ ] `DoclingSchemaTransformer` class implements interface
- [ ] `transformAll()` batch method implemented
- [ ] Table transformation (TableBorder creation)
- [ ] Paragraph transformation
- [ ] Heading transformation
- [ ] List transformation
- [ ] Bounding box coordinate conversion
- [ ] Unit tests with sample JSON → IObject

### Test Method
```bash
cd java && mvn test -Dtest=DoclingSchemaTransformerTest
```

### Dependencies
- Task 6 (DoclingClient - response structure)
- Skill: schema-mapping (Task 1)

---

## Task 8: HybridDocumentProcessor Implementation

### Goal
Implement hybrid processing pipeline with parallel execution.

### Context
- **Parallel processing**: Java path and Hybrid path run concurrently
- Batch all hybrid pages in single API call
- Merge results maintaining page order

### Architecture
```
                    ┌─ Java pages (parallel) ────────────┐
                    │  ExecutorService                   │
All Pages Triage ───┤                                    ├──→ Merge
                    │                                    │
                    └─ Hybrid pages (batch async) ───────┘
                       Single API call
```

### Files to Create
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/HybridDocumentProcessor.java`

### Files to Modify
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/DocumentProcessor.java`

### Implementation Details
```java
public class HybridDocumentProcessor {

    public static List<List<IObject>> processDocument(
        String inputPdfName,
        Config config,
        Set<Integer> pagesToProcess
    ) throws IOException {

        // Phase 1: Filter all pages + Triage
        Map<Integer, List<IObject>> filteredContents = filterAllPages(pagesToProcess);
        Map<Integer, TriageResult> triageResults = TriageProcessor.triageAllPages(
            filteredContents, config.getHybridConfig()
        );

        // Phase 2: Split by decision
        Set<Integer> javaPages = filterByDecision(triageResults, JAVA);
        Set<Integer> hybridPages = filterByDecision(triageResults, BACKEND);

        // Phase 3: Process in parallel
        HybridClient client = HybridClientFactory.create(
            config.getHybrid(), config.getHybridConfig()
        );

        CompletableFuture<Map<Integer, List<IObject>>> hybridFuture =
            CompletableFuture.supplyAsync(() ->
                processHybridPath(inputPdfName, hybridPages, client, config)
            );

        Map<Integer, List<IObject>> javaResults =
            processJavaPathParallel(filteredContents, javaPages, config);

        Map<Integer, List<IObject>> hybridResults = hybridFuture.join();

        // Phase 4: Merge results
        return mergeResults(javaResults, hybridResults, pagesToProcess);
    }

    private static Map<Integer, List<IObject>> processHybridPath(
        String pdfPath,
        Set<Integer> pageNumbers,
        HybridClient client,
        Config config
    ) {
        if (pageNumbers.isEmpty()) return Map.of();

        byte[] pdfBytes = Files.readAllBytes(Path.of(pdfPath));

        HybridResponse response = client.convert(new HybridRequest(
            pdfBytes, pageNumbers, true, false
        ));

        // Get appropriate transformer for the hybrid backend
        HybridSchemaTransformer transformer = getTransformer(config.getHybrid());
        return transformer.transformAll(response, pageBoundingBoxes);
    }
}
```

### Success Criteria
- [ ] `HybridDocumentProcessor` class created
- [ ] Batch triage for all pages
- [ ] Parallel Java path processing (ExecutorService)
- [ ] Async Hybrid batch processing
- [ ] Concurrent execution of both paths
- [ ] Result merge with page order preservation
- [ ] `DocumentProcessor.processFile()` hybrid branching
- [ ] Fallback handling (hybrid failure → Java)

### Test Method
```bash
# Full test suite
./scripts/test-java.sh

# E2E test with docling hybrid
docker run -p 5001:5001 ds4sd/docling-serve

java -jar java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar \
  --hybrid docling \
  --hybrid-url http://localhost:5001 \
  tests/benchmark/pdfs/01030000000001.pdf
```

### Dependencies
- Task 3, 4, 5, 6, 7 (all prior implementation tasks)

---

## Task 9: Triage Logging

### Goal
Log triage decisions to JSON for benchmark evaluation.

### Context
- Record each page's triage decision and signals
- Used by benchmark to evaluate triage accuracy

### Files to Modify
- `java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/processors/HybridDocumentProcessor.java`

### Output Format
```json
{
  "document": "01030000000001.pdf",
  "hybrid": "docling",
  "triage": [
    {
      "page": 1,
      "decision": "JAVA",
      "confidence": 0.95,
      "signals": {
        "lineChunkCount": 2,
        "textChunkCount": 45,
        "lineToTextRatio": 0.04,
        "alignedLineGroups": 0,
        "hasTableBorder": false
      }
    },
    {
      "page": 2,
      "decision": "BACKEND",
      "confidence": 0.82,
      "signals": {
        "lineChunkCount": 28,
        "textChunkCount": 32,
        "lineToTextRatio": 0.875,
        "alignedLineGroups": 4,
        "hasTableBorder": true
      }
    }
  ],
  "summary": {
    "totalPages": 10,
    "javaPages": 8,
    "hybridPages": 2
  }
}
```

### Success Criteria
- [ ] Triage results JSON serialization
- [ ] File output to prediction directory (`triage.json`)
- [ ] All pages recorded
- [ ] Summary statistics included

### Test Method
```bash
java -jar ... --hybrid docling input.pdf -o output/
cat output/triage.json | jq '.summary'
```

### Dependencies
- Task 8 (HybridDocumentProcessor)

---

## Task 10: Triage Evaluator (Python)

### Goal
Add Python evaluator for triage accuracy measurement.

### Context
- Ground truth: `reference.json` table presence per page
- Prediction: `triage.json` decisions
- **Critical metric**: `triage_fn` (tables missed by triage)

### Files to Create
- `tests/benchmark/src/evaluator_triage.py`

### Files to Modify
- `tests/benchmark/run.py` (integrate triage evaluation)
- `tests/benchmark/thresholds.json` (add thresholds)

### Implementation Details
```python
# evaluator_triage.py
from dataclasses import dataclass
from pathlib import Path
import json

@dataclass
class TriageMetrics:
    recall: float       # Table pages correctly sent to hybrid
    precision: float    # Hybrid pages that actually had tables
    fn_count: int       # Tables missed (sent to JAVA)
    fp_count: int       # Non-table pages sent to hybrid
    java_pages: int
    hybrid_pages: int

def get_pages_with_tables(reference_path: Path) -> dict[str, set[int]]:
    """Extract page numbers with tables from ground truth."""
    ...

def evaluate_triage(
    reference_path: Path,
    triage_path: Path
) -> TriageMetrics:
    """Evaluate triage accuracy against ground truth."""
    # 1. Extract page-level table presence from reference.json
    # 2. Compare with triage.json decisions
    # 3. Calculate FN, FP, recall, precision
    ...
```

### Thresholds Addition
```json
{
  "triage_recall": 0.95,
  "triage_fn_max": 5
}
```

### Success Criteria
- [ ] `evaluator_triage.py` created
- [ ] Page-level table extraction from ground truth
- [ ] Comparison with triage decisions
- [ ] `triage_recall`, `triage_fn` calculation
- [ ] Integration with `run.py`
- [ ] Thresholds added to `thresholds.json`

### Test Method
```bash
# Run benchmark with docling hybrid
./scripts/bench.sh --hybrid docling

# Or test evaluator directly
cd tests/benchmark
python -c "
from src.evaluator_triage import evaluate_triage
from pathlib import Path
result = evaluate_triage(
    Path('ground-truth/reference.json'),
    Path('prediction/opendataloader-hybrid-docling/triage.json')
)
print(result)
"
```

### Dependencies
- Task 9 (Triage Logging)

---

## Task 11: Triage Analyzer Agent

### Goal
Create Claude agent for analyzing triage accuracy and identifying improvement opportunities.

### Files to Create
- `.claude/agents/triage-analyzer.md`

### Agent Definition
```markdown
---
name: triage-analyzer
description: Analyze triage accuracy, identify false negative cases, suggest threshold adjustments
tools: Read, Grep, Glob, Bash(python:*)
---

# Triage Analyzer

Analyze triage results and identify improvement opportunities.

## Capabilities
1. Compare triage.json with reference.json
2. List all FN cases (missed tables)
3. Analyze common patterns in FN cases
4. Suggest threshold adjustments
5. Generate tuning recommendations

## Analysis Workflow
1. Load triage results and ground truth
2. Identify FN cases
3. For each FN, extract page signals
4. Find common signal patterns
5. Recommend threshold changes

## Output Format
- FN case list with signals
- Pattern analysis
- Specific threshold adjustment recommendations
```

### Success Criteria
- [ ] Agent file created with proper frontmatter
- [ ] Clear capability description
- [ ] Workflow documented
- [ ] Can be invoked in Claude Code session

### Test Method
```bash
# In Claude Code:
claude "Analyze triage results and find why FN is high"
# Expected: Agent is used to analyze and provide recommendations
```

### Dependencies
- Task 10 (Triage Evaluator)
- Task 2 (triage-criteria skill)

---

## Execution Order

```
Phase -1: Pre-research
└── Task -1: Data Collection ─────────┐
                                      │
Phase 0: Tool Setup (Skills)          ▼
├── Task 0: docling-api skill ────────┐
├── Task 1: schema-mapping skill ─────┤  (parallel)
└── Task 2: triage-criteria skill ────┘
                                      │
Phase 1: Infrastructure               ▼
├── Task 3: HybridConfig ─────────────┬──→ Task 4: CLI Options
│                                     │
Phase 2: Core Components              │
├── Task 5: TriageProcessor ──────────┤
├── Task 6: DoclingClient ────────────┤  (parallel)
└── Task 7: SchemaTransformer ────────┘
                                      │
Phase 3: Integration                  ▼
└── Task 8: HybridDocumentProcessor ──┬──→ Task 9: Triage Logging
                                      │
Phase 4: Evaluation                   ▼
└── Task 10: Triage Evaluator ────────┬──→ Task 11: Triage Analyzer Agent
```

### Parallelizable Tasks
- Task 0, 1, 2 (skill setup - after Task -1)
- Task 5, 6, 7 (core components - after Task 3)

### Sequential Dependencies
- Task 0, 1, 2 → Task -1 (needs research data)
- Task 4 → Task 3
- Task 6 → Task 0 (needs API skill)
- Task 7 → Task 1 (needs mapping skill)
- Task 8 → Task 5, 6, 7
- Task 9 → Task 8
- Task 10 → Task 9
- Task 11 → Task 10, Task 2
