# Integration Examples

Ready-to-run code for each supported interface. Load this file when the user asks for copy-pasteable examples in a specific language or framework.

Every path requires **Java 11+** at runtime (current floor per `java/pom.xml`). Language wrappers additionally require **Python 3.10+** (pip, per `pyproject.toml`) or **Node.js 20.19+** (npm, per `package.json`). See `installation-matrix.md` § Prerequisites for details.

---

## CLI

```bash
# Local (default). Add --hybrid docling-fast only when DECIDE selected hybrid AND the server is running.
opendataloader-pdf input.pdf \
  --format markdown \
  --output-dir ./output \
  --quiet
```

For multiple formats in one pass:

```bash
opendataloader-pdf input.pdf --format json,markdown,html
```

---

## Python

Batch all files in **one** `convert()` call — each call spawns a JVM, so repeated calls are slow (see Gotcha 3 in SKILL.md). For crash-isolation or memory limits, split into a few reasonably sized batches rather than one giant call: ordinary per-file errors are recorded and the run continues to the remaining files (non-zero exit at the end), but a JVM-level crash or out-of-memory can still take down the whole call.

```python
import opendataloader_pdf

opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "file3.pdf"],
    output_dir="./output",
    format="markdown",
    # hybrid="docling-fast",  # add only when DECIDE selected hybrid AND the server is running
)
```

---

## Node.js

Same JVM-spawn concern — pass all files to one `convert()` call (optionally chunked into a few batches for crash-isolation).

```javascript
import { convert } from '@opendataloader/pdf';

await convert(['file1.pdf', 'file2.pdf'], {
  outputDir: './output',
  format: 'markdown',
  // hybrid: 'docling-fast',  // add only when DECIDE selected hybrid AND the server is running
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
    # hybrid="docling-fast",  # local-first: enable ONLY for scanned/complex PDFs,
                              # and only after pre-flighting the hybrid server
)

documents = loader.load()
# documents is a list of LangChain Document objects with page_content and metadata
```

### Full RAG pipeline

Load → chunk → hand off. Use `format="json"` and the "JSON Citation Chunking" recipe below when you need page + bounding box for citation.

```python
from langchain_opendataloader_pdf import OpenDataLoaderPDFLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter

# 1. Load PDFs as markdown so the heading separators below are meaningful.
#    Add hybrid="docling-fast" only when DECIDE selected hybrid AND the server is running.
loader = OpenDataLoaderPDFLoader(file_path="document.pdf", format="markdown")
documents = loader.load()

# 2. Chunk with overlap on structural separators.
splitter = RecursiveCharacterTextSplitter(
    chunk_size=1000,
    chunk_overlap=200,
    separators=["\n## ", "\n### ", "\n\n", "\n", " "],
)
chunks = splitter.split_documents(documents)

# 3. Embed + index with the vector store and embeddings YOUR app chose —
#    those integrations are outside this skill's scope (SKILL.md Stage 6).
```

---

## JSON Citation Chunking (page + bounding box)

When citations matter (SKILL.md Stage 6), extract with `--format json` and keep each element's **page number** and **bounding box** on the chunk. Chunking on markdown headers (`\n## `) drops that spatial metadata — JSON preserves it.

> ODL 2.5.0 emits `content` (text), `page number`, and `bounding box` on each element (under `kids`). The helpers below try those real keys first, then fall back to alias spellings for other/older versions. Confirm against one of your own output files before trusting the field names.

### Generic (no framework)

Parse the JSON, flatten the element tree to `(text, page, bbox)`, then pack elements into character-bounded chunks (bounded by `max_chars`) while carrying the metadata:

```python
import json

# ODL 2.5.0 keys are "content" / "page number" / "bounding box".
# The alias spellings after them are fallbacks for other/older versions.
def _page_of(el):
    return (el.get("page number") or el.get("page")
            or el.get("pageNumber") or el.get("page_number"))

def _bbox_of(el):
    return (el.get("bounding box") or el.get("bbox")
            or el.get("boundingBox") or el.get("bounding_box"))

def _text_of(el):
    return el.get("content") or el.get("text") or ""

def iter_elements(node):
    """Depth-first walk yielding every typed element dict in the ODL JSON tree."""
    if isinstance(node, dict):
        if isinstance(node.get("type"), str) and (_page_of(node) is not None or _text_of(node).strip()):
            yield node
        for v in node.values():
            yield from iter_elements(v)
    elif isinstance(node, list):
        for v in node:
            yield from iter_elements(v)

def chunk_with_citations(json_path, max_chars=1000):
    with open(json_path, encoding="utf-8") as f:
        doc = json.load(f)
    chunks, buf, buf_len = [], [], 0
    for el in iter_elements(doc):
        text = _text_of(el).strip()
        if not text:
            continue
        meta = {"page": _page_of(el), "bbox": _bbox_of(el)}
        if buf_len + len(text) > max_chars and buf:
            chunks.append({"text": "\n".join(t for t, _ in buf),
                           "citations": [m for _, m in buf]})
            buf, buf_len = [], 0
        buf.append((text, meta))
        buf_len += len(text)
    if buf:
        chunks.append({"text": "\n".join(t for t, _ in buf),
                       "citations": [m for _, m in buf]})
    return chunks

# Each chunk carries the (page, bbox) of every element it contains, so a
# retrieved chunk can cite the exact page and region it came from.
# max_chars bounds size BETWEEN elements: a single element longer than max_chars
# becomes its own chunk that exceeds the bound (elements are not split, to keep
# each citation's (page, bbox) intact).
```

### LangChain

Wrap each chunk as a `Document`, keeping page/bbox in `metadata` so retrieval can surface the citation:

```python
import json
from langchain_core.documents import Document

chunks = chunk_with_citations("output.json")
docs = [
    Document(
        page_content=c["text"],
        # Keep each (page, bbox) PAIR. A chunk can span pages, so a single "page"
        # plus a flat "bboxes" list loses which region is on which page. Many vector
        # stores allow only scalar metadata, so serialize the pairs to a JSON string.
        metadata={
            "page": next((m["page"] for m in c["citations"] if m["page"] is not None), None),
            "citations": json.dumps(c["citations"]),
        },
    )
    for c in chunks
]
# Retrieval: json.loads(doc.metadata["citations"]) -> [{"page":.., "bbox":..}, ..];
# metadata["page"] is a convenience for coarse filtering only.
```

### LlamaIndex

Emit `TextNode`s with the same metadata:

```python
import json
from llama_index.core.schema import TextNode

chunks = chunk_with_citations("output.json")
nodes = [
    TextNode(
        text=c["text"],
        # Same pairing concern as LangChain above: keep the (page, bbox) pairs
        # intact (serialized) instead of collapsing to one page + a flat bbox list.
        metadata={
            "page": next((m["page"] for m in c["citations"] if m["page"] is not None), None),
            "citations": json.dumps(c["citations"]),
        },
    )
    for c in chunks
]
# Feed nodes into a VectorStoreIndex; json.loads(node.metadata["citations"])
# recovers the exact (page, bbox) of every element in the chunk.
```

---

## Java (Maven)

Local (default). Output formats are per-format toggles, **not** a `setFormat`
string, and **JSON is on by default** — turn it off if you only want Markdown.
The Java library runs in your application's own JVM (no wrapper process).

```java
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;

Config config = new Config();
config.setOutputFolder("./output");
config.setGenerateJSON(false);      // JSON defaults to true — disable if unwanted
config.setGenerateMarkdown(true);

OpenDataLoaderPDF.processFile("file1.pdf", config);
```

Hybrid is a separate opt-in — only after DECIDE selected it **and** a server is
reachable (local-first otherwise). Set the backend on the same `Config`:

```java
// requires a running hybrid server — see hybrid-guide.md
config.setHybrid("docling-fast");   // in 2.5.0 the client factory only wires "docling-fast"; passing "docling" (which the Config constants name as canonical) throws "Unknown hybrid backend" at runtime
```

Verify the exact setters against the **API for your pinned library version**
(Javadoc/source of the Maven artifact — a Java consumer uses that, not the installed
CLI). CLI option names and Java setter names are related conceptually but are not
interchangeable. See `installation-matrix.md` for the Maven dependency block.

---

## Output Pipeline Patterns

**Quiet mode for automated pipelines** — suppress progress output:

```bash
opendataloader-pdf input.pdf --format markdown --quiet
```

**Stdout for pipe-based workflows** — text-like formats only, one at a time. On 2.5.0 `--to-stdout` emits **nothing for `json`/`html`** (they must go to files); use `text`/`markdown`, and pass `-q` so log lines don't pollute stdout:

```bash
opendataloader-pdf input.pdf --format text --to-stdout -q | my-indexer
# JSON does NOT stream to stdout — write a file, then read it:
opendataloader-pdf input.pdf --format json --output-dir ./out -q && jq . ./out/input.json
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
# GPU host — bind an explicit private address; the server has NO built-in auth.
# Set this to the host's actual private address (quoted so it is not parsed as a shell redirect).
HYBRID_HOST=10.0.0.5
opendataloader-pdf-hybrid --host "$HYBRID_HOST" --port 5002

# Client
opendataloader-pdf input.pdf \
  --hybrid docling-fast \
  --hybrid-url "http://$HYBRID_HOST:5002" \
  --hybrid-timeout 30000
```

**Security:** the hybrid server has no authentication — restrict access with a firewall, a private network, or reverse-proxy auth. `--hybrid-fallback` is optional and preserves *completion*, not quality; omit it when OCR/enrichment is mandatory, or verify those outputs explicitly.
