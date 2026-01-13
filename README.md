# OpenDataLoader PDF

**PDF Parsing for RAG** — Convert to Markdown & JSON, Fast, Local, No GPU

[![License](https://img.shields.io/pypi/l/opendataloader-pdf.svg)](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/LICENSE)
[![PyPI version](https://img.shields.io/pypi/v/opendataloader-pdf.svg)](https://pypi.org/project/opendataloader-pdf/)
[![npm version](https://img.shields.io/npm/v/@opendataloader/pdf.svg)](https://www.npmjs.com/package/@opendataloader/pdf)
[![Maven Central](https://img.shields.io/maven-central/v/org.opendataloader/opendataloader-pdf-core.svg)](https://search.maven.org/artifact/org.opendataloader/opendataloader-pdf-core)
[![GHCR Version](https://ghcr-badge.egpl.dev/opendataloader-project/opendataloader-pdf-cli/latest_tag?trim=major&label=docker)](https://github.com/opendataloader-project/opendataloader-pdf/pkgs/container/opendataloader-pdf-cli)
[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://github.com/opendataloader-project/opendataloader-pdf#java)

Convert PDFs into **LLM-ready Markdown and JSON** with accurate reading order, table extraction, and bounding boxes — all running locally on your machine.

**Why developers choose OpenDataLoader:**
- **Deterministic** — Same input always produces same output (no LLM hallucinations)
- **Fast** — Process 100+ pages per second on CPU
- **Private** — 100% local, zero data transmission
- **Accurate** — Bounding boxes for every element, correct multi-column reading order

```bash
pip install -U opendataloader-pdf
```

```python
import opendataloader_pdf

# PDF to Markdown for RAG
opendataloader_pdf.convert(
    input_path="document.pdf",
    output_dir="output/",
    format="markdown,json"
)
```

<br/>

## Why OpenDataLoader?

Building RAG pipelines? You've probably hit these problems:

| Problem | How We Solve It |
|---------|-----------------|
| **Multi-column text reads left-to-right incorrectly** | XY-Cut++ algorithm preserves correct reading order |
| **Tables lose structure** | Border + cluster detection keeps rows/columns intact |
| **Headers/footers pollute context** | Auto-filtered before output |
| **No coordinates for citations** | Bounding box for every element |
| **Cloud APIs = privacy concerns** | 100% local, no data leaves your machine |
| **GPU required** | Pure CPU, rule-based — runs anywhere |

<br/>

## Key Features

### For RAG & LLM Pipelines

- **Structured Output** — JSON with semantic types (heading, paragraph, table, list, caption)
- **Bounding Boxes** — Every element includes `[x1, y1, x2, y2]` coordinates for citations
- **Reading Order** — XY-Cut++ algorithm handles multi-column layouts correctly
- **Noise Filtering** — Headers, footers, hidden text, watermarks auto-removed
- **LangChain Integration** — [Official document loader](https://python.langchain.com/docs/integrations/document_loaders/opendataloader_pdf/)

### Performance & Privacy

- **No GPU** — Fast, rule-based heuristics
- **Local-First** — Your documents never leave your machine
- **High Throughput** — Process thousands of PDFs efficiently
- **Multi-Language SDK** — Python, Node.js, Java, Docker

### Document Understanding

- **Tables** — Detects borders, handles merged cells
- **Lists** — Numbered, bulleted, nested
- **Headings** — Auto-detects hierarchy levels
- **Images** — Extracts with captions linked
- **Tagged PDF Support** — Uses native PDF structure when available
- **AI Safety** — Auto-filters prompt injection content

<br/>

## Output Formats

| Format | Use Case |
|--------|----------|
| **JSON** | Structured data with bounding boxes, semantic types |
| **Markdown** | Clean text for LLM context, RAG chunks |
| **HTML** | Web display with styling |
| **Annotated PDF** | Visual debugging — see detected structures ([sample](https://opendataloader.org/demo/samples/01030000000000?view1=annot&view2=json)) |

<br/>

## JSON Output Example

```json
{
  "type": "heading",
  "id": 42,
  "level": "Title",
  "page number": 1,
  "bounding box": [72.0, 700.0, 540.0, 730.0],
  "heading level": 1,
  "font": "Helvetica-Bold",
  "font size": 24.0,
  "text color": "[0.0]",
  "content": "Introduction"
}
```

| Field | Description |
|-------|-------------|
| `type` | Element type: heading, paragraph, table, list, image, caption |
| `id` | Unique identifier for cross-referencing |
| `page number` | 1-indexed page reference |
| `bounding box` | `[left, bottom, right, top]` in PDF points |
| `heading level` | Heading depth (1+) |
| `font`, `font size` | Typography info |
| `content` | Extracted text |

[Full JSON Schema →](https://opendataloader.org/docs/json-schema)

<br/>

## Quick Start

- [Python](https://opendataloader.org/docs/quick-start-python)
- [Node.js / TypeScript](https://opendataloader.org/docs/quick-start-nodejs)
- [Docker](https://opendataloader.org/docs/quick-start-docker)
- [Java](https://opendataloader.org/docs/quick-start-java)

<br/>

## Advanced Options

```python
opendataloader_pdf.convert(
    input_path="document.pdf",
    output_dir="output/",
    format="json,markdown,pdf",

    # Image output mode: "off", "embedded" (Base64), or "external" (default)
    image_output="embedded",

    # Image format: "png" or "jpeg"
    image_format="jpeg",

    # Tagged PDF
    use_struct_tree=True,            # Use native PDF structure
)
```

[Full CLI Options Reference →](https://opendataloader.org/docs/cli-options-reference)

<br/>

## AI Safety

PDFs can contain hidden prompt injection attacks. OpenDataLoader automatically filters:

- Hidden text (transparent, zero-size)
- Off-page content
- Suspicious invisible layers

This is **enabled by default**. [Learn more →](https://opendataloader.org/docs/ai-safety)

<br/>

## Tagged PDF Support

**Why it matters:** The [European Accessibility Act (EAA)](https://commission.europa.eu/strategy-and-policy/policies/justice-and-fundamental-rights/disability/union-equality-strategy-rights-persons-disabilities-2021-2030/european-accessibility-act_en) took effect June 28, 2025, requiring accessible digital documents across the EU. This means more PDFs will be properly tagged with semantic structure.

**OpenDataLoader leverages this:**

- When a PDF has structure tags, we extract the **exact layout** the author intended
- Headings, lists, tables, reading order — all preserved from the source
- No guessing, no heuristics needed — **pixel-perfect semantic extraction**

```python
opendataloader_pdf.convert(
    input_path="accessible_document.pdf",
    use_struct_tree=True  # Use native PDF structure tags
)
```

Most PDF parsers ignore structure tags entirely. We're one of the few that fully support them.

[Learn more about Tagged PDF →](https://opendataloader.org/docs/tagged-pdf)

<br/>

## Hybrid Mode

For documents with complex tables or OCR needs, enable hybrid mode to route challenging pages to an AI backend while keeping simple pages fast and local.

**Results**: Table accuracy jumps from 0.49 → 0.93 (+90%) with acceptable speed trade-off.

```bash
pip install -U "opendataloader-pdf[hybrid]"
```

Terminal 1: Start the backend server

```bash
opendataloader-pdf-hybrid --port 5002
```

Terminal 2: Process PDFs with hybrid mode

```bash
opendataloader-pdf --hybrid docling-fast input.pdf
```

Or use in Python:

```python
opendataloader_pdf.convert(
    input_path="complex_tables.pdf",
    output_dir="output/",
    hybrid="docling-fast"  # Routes complex pages to AI backend
)
```

- **Local-first**: Simple pages processed locally, complex pages routed to backend
- **Fallback**: If backend unavailable, gracefully falls back to local processing
- **Privacy**: Run the backend locally in Docker for 100% on-premise

[Hybrid Mode Guide →](https://opendataloader.org/docs/hybrid-mode)

<br/>

## LangChain Integration

OpenDataLoader PDF has an official LangChain integration for seamless RAG pipeline development.

```bash
pip install -U langchain-opendataloader-pdf
```

```python
from langchain_opendataloader_pdf import OpenDataLoaderPDFLoader

loader = OpenDataLoaderPDFLoader(
    file_path=["document.pdf"],
    format="text"
)
documents = loader.load()

# Use with any LangChain pipeline
for doc in documents:
    print(doc.page_content[:100])
```

- [LangChain Documentation](https://python.langchain.com/docs/integrations/document_loaders/opendataloader_pdf/)
- [GitHub Repository](https://github.com/opendataloader-project/langchain-opendataloader-pdf)
- [PyPI Package](https://pypi.org/project/langchain-opendataloader-pdf/)

<br/>

## Benchmarks

We continuously benchmark against real-world documents.

[View full benchmark results →](https://github.com/opendataloader-project/opendataloader-bench)

### Quick Comparison

| Engine                      | Overall  | Reading Order | Table    | Heading  | Speed (s/page) |
|-----------------------------|----------|---------------|----------|----------|----------------|
| **opendataloader**          | 0.68     | 0.91          | 0.49     | 0.65     | **0.05**       |
| **opendataloader [hybrid]** | **0.88** | **0.93**      | **0.93** | 0.78     | 0.48           |
| docling                     | 0.86     | 0.90          | 0.89     | **0.80** | 0.73           |
| marker                      | 0.83     | 0.89          | 0.81     | **0.80** | 53.93          |
| mineru                      | 0.82     | 0.86          | 0.87     | 0.74     | 5.96           |
| pymupdf4llm                 | 0.57     | 0.89          | 0.40     | 0.41     | 0.09           |
| markitdown                  | 0.29     | 0.88          | 0.00     | 0.00     | **0.04**       |

> Scores are normalized to [0, 1]. Higher is better for accuracy metrics; lower is better for speed. **Bold** indicates best performance.

### Visual Comparison

[![Benchmark](https://github.com/opendataloader-project/opendataloader-bench/raw/refs/heads/main/charts/benchmark.png)](https://github.com/opendataloader-project/opendataloader-bench)


<br/>

## Roadmap

See our [upcoming features and priorities →](https://opendataloader.org/docs/upcoming-roadmap)

<br/>

## Documentation

- [Quick Start Guide](https://opendataloader.org/docs/quick-start-python)
- [JSON Schema Reference](https://opendataloader.org/docs/json-schema)
- [CLI Options](https://opendataloader.org/docs/cli-options-reference)
- [Tagged PDF Support](https://opendataloader.org/docs/tagged-pdf)
- [AI Safety Features](https://opendataloader.org/docs/ai-safety)

<br/>

## Frequently Asked Questions

### What is the best PDF parser for RAG?

For RAG pipelines, you need a parser that preserves document structure, maintains correct reading order, and provides element coordinates for citations. OpenDataLoader is designed specifically for this use case — it outputs structured JSON with bounding boxes, handles multi-column layouts correctly with XY-Cut++, and runs locally without GPU requirements.

### How do I extract tables from PDF for LLM?

OpenDataLoader detects tables using both border analysis and text clustering, preserving row/column structure in the output. Tables are exported as structured data in JSON or as formatted Markdown tables, ready for LLM consumption.

### Can I use this without sending data to the cloud?

Yes. OpenDataLoader runs 100% locally on your machine. No API calls, no data transmission — your documents never leave your environment. This makes it ideal for sensitive documents in legal, healthcare, and financial industries.

### What makes OpenDataLoader unique?

OpenDataLoader takes a different approach from many PDF parsers:

- **Rule-based extraction** — Deterministic output without GPU requirements
- **Bounding boxes for all elements** — Essential for citation systems
- **XY-Cut++ reading order** — Handles multi-column layouts correctly
- **Built-in AI safety filters** — Protects against prompt injection
- **Native Tagged PDF support** — Leverages accessibility metadata

This means: consistent output (same input = same output), no GPU required, faster processing, and no model hallucinations.

### How do I get better accuracy for complex tables?

Enable hybrid mode with `pip install -U "opendataloader-pdf[hybrid]"`. This routes pages with complex tables to an AI backend (like docling-serve) while keeping simple pages fast and local. Table accuracy improves from 0.49 to 0.93 — matching or exceeding dedicated AI parsers while remaining faster and more cost-effective.

<br/>

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

<br/>

## License

[Mozilla Public License 2.0](LICENSE)

---

**Found this useful?** Give us a star to help others discover OpenDataLoader.
