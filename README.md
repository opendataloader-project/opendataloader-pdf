# OpenDataLoader PDF

**PDF to Markdown & JSON for RAG** — Fast, Local, No GPU Required

[![License](https://img.shields.io/pypi/l/opendataloader-pdf.svg)](https://github.com/opendataloader-project/opendataloader-pdf/blob/main/LICENSE)
[![PyPI version](https://img.shields.io/pypi/v/opendataloader-pdf.svg)](https://pypi.org/project/opendataloader-pdf/)
[![npm version](https://img.shields.io/npm/v/@opendataloader/pdf.svg)](https://www.npmjs.com/package/@opendataloader/pdf)
[![Maven Central](https://img.shields.io/maven-central/v/org.opendataloader/opendataloader-pdf-core.svg)](https://search.maven.org/artifact/org.opendataloader/opendataloader-pdf-core)

Convert PDFs into **LLM-ready Markdown and JSON** with accurate reading order, table extraction, and bounding boxes — all running locally on your machine.

**Why developers choose OpenDataLoader:**
- **Deterministic** — Same input always produces same output (no LLM hallucinations)
- **Fast** — Process 100+ pages per second on CPU
- **Private** — 100% local, zero data transmission
- **Accurate** — Bounding boxes for every element, correct multi-column reading order

```bash
pip install opendataloader-pdf
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
- **Page Numbers** — Track which page each element came from

### Performance & Privacy

- **No GPU** — Fast, rule-based heuristics
- **Local-First** — Your documents never leave your machine
- **High Throughput** — Process thousands of PDFs efficiently
- **Multi-Language SDK** — Python, Node.js, Java, Docker
- **LangChain Integration** — [Official document loader](https://python.langchain.com/docs/integrations/document_loaders/opendataloader_pdf/)

### Document Understanding

- **Tables** — Detects borders, handles merged cells
- **Lists** — Numbered, bulleted, nested
- **Headings** — Auto-detects hierarchy levels
- **Images** — Extracts with captions linked
- **Tagged PDF Support** — Uses native PDF structure when available

<br/>

## Output Formats

| Format | Use Case |
|--------|----------|
| **JSON** | Structured data with bounding boxes, semantic types |
| **Markdown** | Clean text for LLM context, RAG chunks |
| **HTML** | Web display with styling |
| **Annotated PDF** | Visual debugging — see detected structures |

<br/>

## JSON Output Example

```json
{
  "type": "heading",
  "level": 1,
  "page number": 1,
  "bounding box": [72.0, 700.5, 540.0, 730.2],
  "content": "Introduction"
}
```

Every element includes:
- `type` — heading, paragraph, table, list, image, caption
- `page number` — 1-indexed page reference
- `bounding box` — `[left, bottom, right, top]` in PDF points
- `content` — extracted text

<br/>

## Quick Start

### Python

```bash
pip install opendataloader-pdf
```

```python
import opendataloader_pdf

opendataloader_pdf.convert(
    input_path=["doc1.pdf", "folder/"],
    output_dir="output/",
    format="json,markdown"
)
```

### Node.js

```bash
npm install @opendataloader/pdf
```

```javascript
import { convert } from '@opendataloader/pdf';

await convert({
  inputPath: ['doc1.pdf'],
  outputDir: 'output/',
  format: 'json,markdown'
});
```

### Docker

```bash
docker run -v $(pwd):/data ghcr.io/opendataloader-project/opendataloader-pdf-cli \
  --input /data/document.pdf \
  --output /data/output \
  --format json,markdown
```

### Java

```xml
<dependency>
  <groupId>org.opendataloader</groupId>
  <artifactId>opendataloader-pdf-core</artifactId>
  <version>LATEST</version>
</dependency>
```

<br/>

## Advanced Options

```python
opendataloader_pdf.convert(
    input_path="document.pdf",
    output_dir="output/",
    format="json,markdown,pdf",

    # Reading order
    reading_order="xycut",           # XY-Cut++ for multi-column

    # Content filtering
    content_safety_off=None,         # Keep AI-safety filters on

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

[![Benchmark](https://github.com/opendataloader-project/opendataloader-bench/raw/refs/heads/main/charts/benchmark.png)](https://github.com/opendataloader-project/opendataloader-bench)

[View full benchmark results →](https://github.com/opendataloader-project/opendataloader-bench)

<br/>

## Roadmap

**Coming Soon:**
- OCR for scanned PDFs
- Table AI for borderless/merged cells

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

### How does OpenDataLoader compare to other PDF parsers?

OpenDataLoader is the only open-source PDF parser that combines: rule-based extraction (no GPU needed), bounding boxes for every element, XY-Cut++ reading order algorithm, built-in AI safety filters, and native Tagged PDF support. Most alternatives require GPU, lack coordinates, or ignore PDF structure tags.

### What makes OpenDataLoader different from Docling or MinerU?

OpenDataLoader uses deterministic rule-based heuristics instead of deep learning. This means: consistent output (same input = same output), no GPU required, faster processing, and no model hallucinations. It's designed for production RAG pipelines where reliability matters.

<br/>

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

<br/>

## License

[Mozilla Public License 2.0](LICENSE)

---

**Found this useful?** Give us a star to help others discover OpenDataLoader.
