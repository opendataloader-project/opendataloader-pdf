# Integration Examples

Ready-to-run code for each supported interface. Load this file when the user asks for copy-pasteable examples in a specific language or framework.

Every path requires **Java 11+** at runtime — see `installation-matrix.md`.

---

## CLI

```bash
opendataloader-pdf input.pdf \
  --format markdown \
  --output-dir ./output \
  --hybrid docling-fast \
  --quiet
```

For multiple formats in one pass:

```bash
opendataloader-pdf input.pdf --format json,markdown,html
```

---

## Python

Batch all files in one `convert()` call — each call spawns a JVM, so repeated calls are slow (see Gotcha 3 in SKILL.md).

```python
import opendataloader_pdf

opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "file3.pdf"],
    output_dir="./output",
    format="markdown",
    hybrid="docling-fast"
)
```

---

## Node.js

Same JVM-spawn concern — pass all files to one `convert()` call.

```javascript
import { convert } from '@opendataloader/pdf';

await convert(['file1.pdf', 'file2.pdf'], {
  outputDir: './output',
  format: 'markdown',
  hybrid: 'docling-fast'
});
```

---

## LangChain

Basic loader:

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

### Full RAG pipeline

Load → chunk → embed → index. Use `format="json"` instead of `"text"` when you need bounding boxes in metadata for source citation.

```python
from langchain_opendataloader_pdf import OpenDataLoaderPDFLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.vectorstores import Chroma
from langchain.embeddings import OpenAIEmbeddings

# 1. Load PDFs. ODL markdown headings are natural chunk boundaries.
loader = OpenDataLoaderPDFLoader(
    file_path="document.pdf",
    format="text",
    hybrid="docling-fast"
)
documents = loader.load()

# 2. Chunk with overlap on structural separators.
splitter = RecursiveCharacterTextSplitter(
    chunk_size=1000,
    chunk_overlap=200,
    separators=["\n## ", "\n### ", "\n\n", "\n", " "]
)
chunks = splitter.split_documents(documents)

# 3. Index.
vectorstore = Chroma.from_documents(chunks, OpenAIEmbeddings())
```

---

## Java (Maven)

```java
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;

Config config = new Config();
config.setOutputDir("./output");
config.setFormat("markdown");
config.setHybrid("docling-fast");

OpenDataLoaderPDF.processFile("file1.pdf", config);
```

See `installation-matrix.md` for the Maven dependency block.

---

## Output Pipeline Patterns

**Quiet mode for automated pipelines** — suppress progress output:

```bash
opendataloader-pdf input.pdf --format markdown --quiet
```

**Stdout for pipe-based workflows** — single format only:

```bash
opendataloader-pdf input.pdf --format json --to-stdout | jq .
```

**Page range extraction**:

```bash
opendataloader-pdf input.pdf --pages "1,3,5-10" --format markdown
```

**Custom page separators** for downstream splitting:

```bash
opendataloader-pdf input.pdf \
  --format markdown \
  --markdown-page-separator "---PAGE %page-number%---"
```

---

## Remote Hybrid Server

For multi-machine deployments, run the server on a GPU host and point clients at it.

```bash
# GPU host
opendataloader-pdf-hybrid --port 5002

# Client
opendataloader-pdf input.pdf \
  --hybrid docling-fast \
  --hybrid-url http://gpu-server:5002 \
  --hybrid-timeout 30000 \
  --hybrid-fallback
```

`--hybrid-fallback` routes failing pages back to the local Java path so a single backend hiccup does not fail the document.
