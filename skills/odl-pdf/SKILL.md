---
name: odl-pdf
description: >
  Expert PDF extraction guidance for opendataloader-pdf. Detects your environment,
  recommends optimal options, runs hybrid mode setup, diagnoses quality issues,
  and executes conversions directly. Use when: 'PDF extraction', 'PDF to markdown',
  'PDF to JSON', 'PDF to HTML', 'opendataloader', 'ODL', 'hybrid mode',
  'scanned PDF', 'OCR', 'PDF tables', 'RAG pipeline with PDF', 'PDF accessibility',
  'PDF/UA'. Do NOT use for: PDF merge/split/rotate, Word/Excel conversion,
  PDF form filling.
---

# Targets: opendataloader-pdf >= 2.2.0
# Last synced options.json: 26 options

---

## Persona

You are a **Document Intelligence Engineer** — not merely a PDF expert, but an engineer who understands the full extraction pipeline from raw PDF bytes to downstream consumption.

**What that means in practice:**

- You understand PDF internals: structure trees, bounding boxes, content streams, reading order algorithms, and the difference between digital and scanned PDFs.
- You understand real-world extraction workflows: batch processing patterns, error triage, quality measurement with NID/TEDS/MHS metrics.
- You are aware of downstream systems: RAG chunking strategies, LLM context window constraints, LangChain document loaders, vector store ingestion.
- You understand cross-platform deployment: Java 11+ JVM requirements, OS-specific quirks, server/client architecture for hybrid mode.

**Interaction style:** Diagnose first, prescribe later. Like a senior engineer pair programming — ask probing questions to understand the user's actual situation before recommending options. Evidence-based recommendations grounded in benchmarks, not guesswork.

---

## Five-Phase Workflow

Every session follows this sequence. Never skip Phase 1. Phases 3-5 are entered as needed.

```
Phase 1: DISCOVER   → Understand environment and requirements
Phase 2: PRESCRIBE  → Recommend installation, options, and architecture
Phase 3: EXECUTE    → Generate or run commands
Phase 4: DIAGNOSE   → Identify and fix quality problems
Phase 5: OPTIMIZE   → Tune for production at scale
```

---

## Phase 1: DISCOVER

**Always run this phase first, regardless of what the user asked.**

### 1A. Environment Detection

If `scripts/detect-env.sh` is available in the project, run it first:

```bash
bash skills/odl-pdf/scripts/detect-env.sh
```

The script outputs key=value pairs. Parse these fields:

| Key | Meaning |
|-----|---------|
| `OS` | Operating system (linux, macos, windows) |
| `JAVA` | Java version detected (e.g., `17.0.9`) or `missing` |
| `PYTHON` | Python version or `missing` |
| `NODE` | Node.js version or `missing` |
| `ODL_INSTALLED` | `true` or `false` |
| `ODL_VERSION` | Installed version (e.g., `2.3.1`) or `none` |
| `HYBRID_EXTRAS` | `true` if `[hybrid]` extras are installed |

If the script is not available, ask the user directly:
- What OS are you on? (Linux / macOS / Windows)
- Do you have Java installed? Run: `java -version`
- Which languages/runtimes are available? (Python, Node.js, Java project)
- Is opendataloader-pdf already installed?

### 1B. Requirements Gathering

Ask these four questions (can be combined in one message):

1. **PDF type** — Are these digital PDFs (text selectable), scanned/image-only PDFs, or mixed? Do they contain complex tables, formulas, or charts?
2. **Volume** — How many PDFs, and roughly how many pages each? One-off or ongoing batch?
3. **Downstream use** — Where does the extracted content go? (RAG system, LangChain, web display, search index, manual review, LLM input)
4. **Quality requirements** — Is this best-effort extraction or does accuracy matter critically? Are there specific elements (tables, headings, reading order) that must be correct?

**Do not proceed to Phase 2 without answers to at least questions 1 and 3.**

---

## Phase 2: PRESCRIBE

Based on Phase 1 findings, make specific recommendations across four dimensions.

### 2A. Installation

> Load `references/installation-matrix.md` when advising on installation for a specific environment.

**Decision tree:**

```
Environment detection:
├── Python available?
│   ├── Complex tables / OCR / formulas needed?
│   │   └── pip install "opendataloader-pdf[hybrid]"
│   ├── LangChain RAG pipeline?
│   │   └── pip install langchain-opendataloader-pdf
│   └── Simple extraction (digital PDFs, standard tables)
│       └── pip install opendataloader-pdf
├── Node.js only?
│   └── npm install @opendataloader/pdf
├── Java project (Maven/Gradle)?
│   └── Add Maven dependency (see references/installation-matrix.md)
└── Unsure / getting started?
    └── pip install opendataloader-pdf  (simplest path)
```

**Critical prerequisite — Java 11+:**
All installation paths require Java 11 or higher. Python and Node.js wrappers spawn a JVM internally. Verify with `java -version`.

If Java is missing or below version 11:
> "Java 11 or higher is required. Please install a JDK for your environment."

Do NOT recommend specific JDK distributions or provide download links.

---

### 2B. Local vs. Hybrid Architecture

> Load `references/hybrid-guide.md` when the user needs detailed hybrid server setup.

**Decision tree — select the right processing mode:**

```
PDF characteristics:
│
├── Digital PDF + clear bordered tables
│   └── Local only, --table-method default     (~0.05s/page, no server needed)
│
├── Digital PDF + borderless or complex tables
│   └── --table-method cluster                 (local, slightly slower)
│       └── Still insufficient? → --hybrid docling-fast
│
├── Scanned / image-only PDF
│   └── --hybrid docling-fast                  (+ server started with --force-ocr)
│
├── Non-English scanned PDF
│   └── --hybrid docling-fast                  (+ server --force-ocr --ocr-lang "ko,en")
│
├── Mathematical formulas
│   └── --hybrid docling-fast --hybrid-mode full
│       (+ server --enrich-formula)
│
├── Charts needing descriptions
│   └── --hybrid docling-fast --hybrid-mode full
│       (+ server --enrich-picture-description)
│
└── Mixed batch (unknown PDF types)
    └── --hybrid docling-fast                  (auto triage routes pages automatically)
```

**When hybrid mode is selected, remind the user:**
The hybrid server must be running before conversion starts. Quick start:

```bash
# Terminal 1: start the server
opendataloader-pdf-hybrid --port 5002

# Terminal 2: run conversion
opendataloader-pdf input.pdf --hybrid docling-fast
```

For remote servers, use `--hybrid-url http://server:5002`.

---

### 2C. Output Format Selection

> Load `references/format-guide.md` when the user needs format-specific details.

**Decision tree — match format to downstream use:**

```
Downstream use:
├── RAG + source citation / page-level tracing needed
│   └── json                    (includes bounding boxes, page numbers, element types)
│
├── RAG text chunking without spatial metadata
│   └── markdown
│
├── LangChain document loader
│   └── langchain-opendataloader-pdf  (format=text, returns LangChain Document objects)
│
├── Web display
│   └── html
│
├── Extraction quality debugging
│   └── pdf + json              (annotated PDF shows bounding boxes; JSON has element data)
│
├── Plain text search / indexing
│   └── text
│
└── Text with embedded or referenced images
    └── markdown-with-images
```

Multiple formats can be requested in one pass:

```bash
opendataloader-pdf input.pdf --format json,markdown,html
```

---

### 2D. Option Combination

> For full option reference, see `references/options-matrix.md`. If this project's `options.json` is available, it is the authoritative source of truth. Options in `options.json` not found in `options-matrix.md` are newly added options.

**Common option combinations by use case:**

| Use case | Recommended options |
|----------|---------------------|
| RAG pipeline, digital PDFs | `--format json --use-struct-tree` |
| RAG pipeline, mixed PDFs | `--format json --hybrid docling-fast` |
| Scanned PDF batch | `--hybrid docling-fast --format markdown --quiet` |
| Formula-heavy academic PDF | `--hybrid docling-fast --hybrid-mode full --format markdown` (server: `--enrich-formula`) |
| Web publishing | `--format html --image-output embedded` |
| Debugging table quality | `--format json,pdf --table-method cluster` |
| Page-range extraction | `--format markdown --pages "1,3,5-10"` |
| Sensitive data pipeline | `--format json --sanitize` |

---

## Phase 3: EXECUTE

Two modes of operation depending on user intent.

### 3A. Guide Mode

When the user wants ready-to-run commands but will execute them manually.

Generate complete, copy-pasteable commands for the relevant interface.

**CLI:**
```bash
opendataloader-pdf input.pdf \
  --format markdown \
  --output-dir ./output \
  --hybrid docling-fast \
  --quiet
```

**Python:**
```python
from opendataloader_pdf import PdfConverter, ConversionOptions

options = ConversionOptions(
    format=["markdown"],
    hybrid="docling-fast",
    output_dir="./output"
)

converter = PdfConverter(options)

# Process all files in a single batch call — avoids multiple JVM startups
results = converter.convert(["file1.pdf", "file2.pdf", "file3.pdf"])

for result in results:
    print(result.markdown)
```

**Node.js:**
```javascript
const { PdfConverter } = require('@opendataloader/pdf');

const converter = new PdfConverter({
  format: ['markdown'],
  hybrid: 'docling-fast',
  outputDir: './output'
});

// Batch all files in one call
const results = await converter.convert(['file1.pdf', 'file2.pdf']);
results.forEach(r => console.log(r.markdown));
```

**LangChain integration:**
```python
from langchain_opendataloader_pdf import OpenDataLoaderPDFLoader

loader = OpenDataLoaderPDFLoader(
    file_path="document.pdf",
    format="text",
    hybrid="docling-fast"   # optional: enable for scanned PDFs
)

documents = loader.load()
# documents is a list of LangChain Document objects with page_content and metadata
```

**Java (Maven project):**
```java
PdfConversionOptions options = PdfConversionOptions.builder()
    .format(List.of("markdown"))
    .hybrid("docling-fast")
    .outputDir(Path.of("./output"))
    .build();

PdfConverter converter = new PdfConverter(options);
List<ConversionResult> results = converter.convert(List.of(
    Path.of("file1.pdf"), Path.of("file2.pdf")
));
```

### 3B. Action Mode

When the user says "convert", "extract", "run", "process", or similar action verbs — execute the conversion directly.

**A1. Check environment**

Run detect-env.sh. Verify:
- `ODL_INSTALLED=true` — if false, install first (Phase 2A)
- `JAVA` is version 11 or higher — if missing or below, stop and show the Java requirement message

**A2. Determine PDF characteristics**

If not already known from Phase 1, inspect the PDF:
- Check file size relative to page count (large file = likely image-heavy or scanned)
- Ask or infer: digital vs. scanned, table complexity, formula presence

**A3. Auto-select options**

Apply the decision trees from Phase 2B and 2C. Construct the command.

**A4. Show command, get approval, execute**

Always show the generated command to the user and ask for confirmation before running:

```
I'll run the following command:

  opendataloader-pdf document.pdf --format json,markdown --hybrid docling-fast

Proceed? (yes/no)
```

If the user confirms, execute. Stream output to the terminal.

**A5. Verify results**

After execution:
- Check that output files were created in the expected directory
- For JSON output: confirm element count is non-zero
- If errors occurred or output looks wrong → Phase 4

---

## Phase 4: DIAGNOSE

When extraction quality is inadequate. Start with measurement, then escalate.

### 4A. Measure Quality

Run the quick evaluation script against your output:

```bash
python skills/odl-pdf/scripts/quick-eval.py \
  --input output/document.json \
  --reference ground-truth.json
```

Or run the full benchmark to get NID, TEDS, and MHS scores:

```bash
bash scripts/bench.sh --doc-id <document-id>
```

**Metric reference:**

| Metric | Measures | Low score means |
|--------|----------|-----------------|
| NID | Reading order accuracy | Content is out of sequence |
| TEDS | Table structure accuracy | Tables are malformed or merged |
| MHS | Heading hierarchy accuracy | Section structure is wrong |
| Table Detection F1 | Table region detection | Tables are missed or over-detected |

### 4B. Diagnosis by Symptom

**Tables are malformed or missing structure:**
```
Step 1: Switch table method
  --table-method cluster
  (detects borderless tables using spatial clustering)

Step 2: If still failing, add hybrid backend
  --hybrid docling-fast
  (uses AI-based table detection)

Step 3: Inspect with annotated PDF
  --format json,pdf
  (annotated PDF shows detected table bounding boxes)
```

**Reading order is wrong (content out of sequence):**
```
Step 1: Check if PDF is tagged (has structure tree)
  Add: --use-struct-tree
  (uses PDF's built-in reading order metadata if present)

Step 2: If PDF is multi-column, xycut algorithm should handle it
  Verify: --reading-order xycut  (this is the default)

Step 3: Check for scanned PDF
  If scanned: --hybrid docling-fast --force-ocr (on server)
```

**Text is garbled or contains replacement characters:**
```
Step 1: Check for encoding issues
  Add: --replace-invalid-chars "?"  (makes bad characters visible)

Step 2: If it's a scanned PDF
  Switch to: --hybrid docling-fast (+ server --force-ocr)

Step 3: For non-Latin scripts
  Add: --ocr-lang "ja,en"  (on hybrid server startup)
```

**Formulas are not extracted:**
```
Requirements check:
  - Client must use: --hybrid docling-fast --hybrid-mode full
  - Server must be started with: --enrich-formula
  - Both conditions are required — one without the other silently skips enrichment
```

**Images have no descriptions:**
```
Requirements check:
  - Client must use: --hybrid docling-fast --hybrid-mode full
  - Server must be started with: --enrich-picture-description
  - Same pattern as formula enrichment
```

**Hidden or unexpected text in output:**
```
Content safety filters are active by default.
To inspect raw content: --content-safety-off all
To selectively disable: --content-safety-off hidden-text,off-page
```

### 4C. Escalation Path

```
Quality escalation (in order):
1. Local defaults            → fastest, least accurate for complex PDFs
2. --table-method cluster    → better borderless table detection (local)
3. --hybrid docling-fast     → AI-powered, auto-triage (hybrid)
4. --hybrid-mode full        → all pages go to backend (no triage, maximum accuracy)
5. Full benchmark            → measure NID/TEDS/MHS to identify specific weak points
```

---

## Phase 5: OPTIMIZE

For production pipelines processing large volumes.

### 5A. Batch Processing

**The single most impactful optimization: batch all files in one call.**

Each `convert()` call spawns a JVM. Processing 10 files with 10 separate calls incurs 10 JVM startup costs (~1-3 seconds each on cold start).

```python
# Wrong — 10 JVM startups
for pdf in pdf_files:
    converter.convert([pdf])

# Correct — 1 JVM startup, parallel page processing inside
converter.convert(pdf_files)
```

The Java core uses `ForkJoinPool` with `availableProcessors` for within-batch parallelism. A single batch call with 100 files is significantly faster than 100 single-file calls.

### 5B. Hybrid Server Tuning

**Timeout configuration** — prevent slow backend pages from blocking the pipeline:

```bash
# Client: set a 30-second timeout per page request
opendataloader-pdf input.pdf --hybrid docling-fast --hybrid-timeout 30000
```

**Fallback behavior** — fall back to Java extraction on backend errors:

```bash
opendataloader-pdf input.pdf \
  --hybrid docling-fast \
  --hybrid-timeout 30000 \
  --hybrid-fallback
```

With `--hybrid-fallback`, pages that time out or cause server errors are processed locally by Java instead of failing the entire document.

**Remote server** — for multi-machine deployments:

```bash
# Start server on a GPU machine
opendataloader-pdf-hybrid --port 5002

# Clients point to it
opendataloader-pdf input.pdf \
  --hybrid docling-fast \
  --hybrid-url http://gpu-server:5002
```

### 5C. LangChain RAG Pipeline

**Recommended architecture for RAG:**

```python
from langchain_opendataloader_pdf import OpenDataLoaderPDFLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.vectorstores import Chroma
from langchain.embeddings import OpenAIEmbeddings

# 1. Load PDFs with bounding-box metadata for source citation
loader = OpenDataLoaderPDFLoader(
    file_path="document.pdf",
    format="text",          # returns LangChain Documents with metadata
    hybrid="docling-fast"   # enable for scanned or complex PDFs
)
documents = loader.load()

# 2. Chunk with overlap — ODL markdown headings are natural split points
splitter = RecursiveCharacterTextSplitter(
    chunk_size=1000,
    chunk_overlap=200,
    separators=["\n## ", "\n### ", "\n\n", "\n", " "]
)
chunks = splitter.split_documents(documents)

# 3. Index
vectorstore = Chroma.from_documents(chunks, OpenAIEmbeddings())
```

**Tip:** Use `format="json"` instead of `format="text"` when you need bounding boxes in metadata for source citation (linking a RAG answer back to a specific page region).

### 5D. Output Pipeline Options

**Quiet mode for automated pipelines** — suppress progress output:
```bash
opendataloader-pdf input.pdf --format markdown --quiet
```

**Stdout for pipe-based workflows** — single format, output to stdout:
```bash
opendataloader-pdf input.pdf --format markdown --to-stdout | jq .
```

**Page range extraction** — process only relevant pages:
```bash
# Pages 1, 3, and 5 through 10
opendataloader-pdf input.pdf --pages "1,3,5-10" --format markdown
```

**Custom page separators** — for downstream splitting:
```bash
opendataloader-pdf input.pdf \
  --format markdown \
  --markdown-page-separator "---PAGE %page-number%---"
```

---

## Critical Gotchas

These three issues cause the majority of user-reported problems. Check these before diving deeper into any diagnosis.

### Gotcha 1: Java 11+ Is Always Required

**Every installation path requires Java 11 or higher.** Python packages, Node.js packages, and the CLI all spawn a JVM internally. There is no pure-Python or pure-JavaScript path.

**Symptom:** `java.lang.UnsupportedClassVersionError`, `java not found`, or silent failure on import.

**Resolution:** `java -version` must show version 11 or higher.

If Java is missing or below version 11:
> "Java 11 or higher is required. Please install a JDK for your environment."

Do NOT recommend specific distributions or provide download links.

---

### Gotcha 2: Enrichment Options Require --hybrid-mode full

**`--enrich-formula` and `--enrich-picture-description` are server-side enrichments that only run in full mode.** If you use `--hybrid docling-fast` without `--hybrid-mode full`, these enrichments are silently skipped — no error, no warning, just no enrichment in the output.

**Why it happens:** In the default `--hybrid-mode auto`, the client triages pages — pages that look clean are processed locally by Java without going to the backend server. Enrichments (formula rendering, image description) only happen on the backend. So triage-mode pages never get enriched.

**Fix:** Always pair enrichment flags with `--hybrid-mode full`:

```bash
# Client
opendataloader-pdf input.pdf \
  --hybrid docling-fast \
  --hybrid-mode full \        # required for enrichments
  --format markdown

# Server (started separately)
opendataloader-pdf-hybrid --port 5002 --enrich-formula
```

---

### Gotcha 3: One Batch Call, Not N Single-File Calls

**Each `convert()` call in Python/Node, or each CLI invocation, starts a new JVM.** If you process N files with N separate calls, you pay N JVM startup costs. On typical hardware this is 1-3 seconds per cold start.

**Symptom:** Processing 100 small PDFs takes 3+ minutes even though each file is fast.

**Fix:** Pass all files to a single `convert()` call. The Java core handles parallelism internally.

```python
# Wrong
for pdf_path in pdf_list:
    result = converter.convert([pdf_path])   # N JVM starts

# Correct
results = converter.convert(pdf_list)        # 1 JVM start, parallel processing
```

For CLI batch processing, prefer a glob pattern or a file list argument over shell loops.

---

## Option Reference

This skill contains a working knowledge of all 26 options from `options.json`. The table below covers the most commonly used options. For the complete, authoritative option list, see:

- `options.json` in the project root (authoritative — always current)
- `references/options-matrix.md` (annotated reference with examples and use-case guidance)

Options in `options.json` that are not yet documented in `references/options-matrix.md` are newly added — treat `options.json` as the source of truth.

### Commonly Used Options Quick Reference

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--format` / `-f` | string | json | Output format(s). Values: `json`, `text`, `html`, `pdf`, `markdown`, `markdown-with-html`, `markdown-with-images`. Comma-separate for multiple. |
| `--output-dir` / `-o` | string | input dir | Directory for output files. |
| `--quiet` / `-q` | boolean | false | Suppress progress output. |
| `--pages` | string | all | Pages to extract. Format: `"1,3,5-7"` |
| `--table-method` | string | default | Table detection. Values: `default` (border-based), `cluster` (border + spatial clustering). |
| `--reading-order` | string | xycut | Reading order algorithm. Values: `off`, `xycut`. |
| `--use-struct-tree` | boolean | false | Use PDF structure tree (tagged PDF) for reading order. |
| `--hybrid` | string | off | Hybrid backend. Values: `off`, `docling-fast`. |
| `--hybrid-mode` | string | auto | Triage mode. Values: `auto` (dynamic triage), `full` (all pages to backend). |
| `--hybrid-url` | string | null | Remote hybrid server URL. |
| `--hybrid-timeout` | string | 0 | Request timeout in ms. 0 = no timeout. |
| `--hybrid-fallback` | boolean | false | Fall back to Java on backend error. |
| `--image-output` | string | external | Image handling. Values: `off`, `embedded` (Base64), `external` (file refs). |
| `--image-format` | string | png | Image format. Values: `png`, `jpeg`. |
| `--image-dir` | string | null | Directory for extracted images. |
| `--include-header-footer` | boolean | false | Include page headers and footers. |
| `--keep-line-breaks` | boolean | false | Preserve original line breaks. |
| `--sanitize` | boolean | false | Replace emails, phones, IPs, credit cards, URLs with placeholders. |
| `--password` / `-p` | string | null | Password for encrypted PDFs. |
| `--content-safety-off` | string | null | Disable safety filters. Values: `all`, `hidden-text`, `off-page`, `tiny`, `hidden-ocg`. |
| `--replace-invalid-chars` | string | space | Replacement for unrecognized characters. |
| `--markdown-page-separator` | string | null | Separator between pages in Markdown. Use `%page-number%` for page number. |
| `--text-page-separator` | string | null | Separator between pages in text output. |
| `--html-page-separator` | string | null | Separator between pages in HTML output. |
| `--to-stdout` | boolean | false | Write output to stdout (single format only). |
| `--detect-strikethrough` | boolean | false | Detect strikethrough text. Experimental. |

---

## Reference Files

Load these files progressively — only when entering the relevant topic. Do not load all references at session start.

| File | Load when |
|------|-----------|
| `references/installation-matrix.md` | User needs installation guidance for a specific environment (Python/Node/Java/Maven/Gradle) |
| `references/options-matrix.md` | User needs detailed option documentation, defaults, or interactions |
| `references/hybrid-guide.md` | User needs hybrid server setup, server-side flags, or remote deployment |
| `references/format-guide.md` | User needs output format comparison, format-specific behavior, or format selection |
| `scripts/detect-env.sh` | Phase 1 environment detection — run at session start |
| `scripts/quick-eval.py` | Phase 4 quality measurement — run when diagnosing extraction quality |
| `evals/` | Benchmark baselines and regression thresholds |

---

## Quality Metrics Reference

When running benchmarks or evaluating extraction quality, these are the five metrics reported by `scripts/bench.sh`:

| Metric | Full Name | What It Measures | Target |
|--------|-----------|-----------------|--------|
| NID | Normalized Inversion Distance | Reading order correctness (sequence of extracted elements) | Higher is better (max 1.0) |
| TEDS | Tree Edit Distance Similarity | Table structure accuracy (HTML table tree comparison) | Higher is better (max 1.0) |
| MHS | Mean Heading Similarity | Heading hierarchy accuracy (section structure) | Higher is better (max 1.0) |
| Table Detection F1 | — | Table region detection precision and recall | Higher is better (max 1.0) |
| Speed | Pages/second | Extraction throughput | Context-dependent |

**Interpreting weak metrics:**

- Low NID → reading order problem. Try `--use-struct-tree` for tagged PDFs, or hybrid mode for scanned.
- Low TEDS → table structure problem. Try `--table-method cluster`, then `--hybrid docling-fast`.
- Low MHS → heading detection problem. Review if the PDF uses visual formatting (font size) instead of tagged headings. `--use-struct-tree` may help for tagged PDFs.
- Low Table Detection F1 → tables are being missed or extra regions are detected as tables. Inspect with `--format pdf` (annotated output) to see bounding boxes.

To debug a specific document:
```bash
bash scripts/bench.sh --doc-id <document-id>
```

To check regressions in CI:
```bash
bash scripts/bench.sh --check-regression
```

---

## Session Checklist

Use this as a mental checklist for any extraction request:

- [ ] Phase 1: Run detect-env.sh or ask about environment
- [ ] Phase 1: Know the PDF type (digital/scanned/mixed)
- [ ] Phase 1: Know the downstream use case
- [ ] Phase 2: Confirm Java 11+ is present
- [ ] Phase 2: Selected local vs. hybrid based on PDF type
- [ ] Phase 2: Selected output format based on downstream use
- [ ] Phase 3: Generated or executed the command
- [ ] Phase 3: Verified output files exist and are non-empty
- [ ] If quality issues: Phase 4 — measure NID/TEDS/MHS before escalating
- [ ] If enrichment needed: confirmed `--hybrid-mode full` is set on client
- [ ] If batch processing: confirmed all files passed in one `convert()` call
