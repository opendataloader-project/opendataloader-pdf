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

    # Reading order
    reading_order="xycut",           # XY-Cut++ for multi-column

    # Images
    embed_images=True,               # Base64 in output
    image_format="png",

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

| Engine             | Accuracy |      | Speed (s/page) |      | Reading Order |      | Table    |      | Heading  |      |
|--------------------|----------|------|----------------|------|---------------|------|----------|------|----------|------|
| **opendataloader** | 0.82     | #2   | **0.05**       | #1   | **0.91**      | #1   | 0.49     | #2   | 0.65     | #2   |
| docling            | **0.88** | #1   | 0.73           | #4   | 0.90          | #2   | **0.89** | #1   | **0.80** | #1   |
| pymupdf4llm        | 0.73     | #3   | 0.09           | #2   | 0.89          | #3   | 0.40     | #3   | 0.41     | #3   |
| markitdown         | 0.58     | #4   | **0.04**       | #1   | 0.88          | #4   | 0.00     | #4   | 0.00     | #4   |

> Scores are normalized to [0, 1]. Higher is better for accuracy metrics; lower is better for speed. **Bold** indicates best performance.

### When to Use Each Engine

| Use Case                 | Recommended Engine | Why                                                    |
|--------------------------|--------------------|--------------------------------------------------------|
| Best overall balance     | **opendataloader** | Fast (0.05s/page) with high reading order accuracy     |
| Maximum accuracy         | docling            | Highest scores for tables and headings, but 16x slower |
| Speed-critical pipelines | markitdown         | Fastest, but no table/heading extraction               |
| PyMuPDF ecosystem        | pymupdf4llm        | Good balance if already using PyMuPDF                  |

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

<br/>

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

<br/>

## License

[Mozilla Public License 2.0](LICENSE)

---

**Found this useful?** Give us a star to help others discover OpenDataLoader.
