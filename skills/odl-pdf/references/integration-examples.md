# Integration examples — worked handoffs

Copy-shaped code for calling ODL from each interface and handing its output to a
downstream pipeline.

**Read the convention first.** Every ODL-specific option is written as a
placeholder — `<the … option help lists>` — which you replace with the real
name/value from the installed `--help` (SKILL.md "Source-of-truth rule"); never
type the placeholder literally. Output-schema field names (where the text, page,
and bounding box live) are **confirmed by inspecting one of your own output files**
(a probe), not treated as fixed facts — they vary by version. The file-handling
*structure* of these examples is real and concrete; the ODL syntax inside is yours
to fill in from help.

Runtime prerequisites (which runtimes, how their floors are declared):
`installation-matrix.md`. Prefer the local path; add a backend only after a
verified local run shows it is needed and a server is reachable (`hybrid-guide.md`;
SKILL.md "Representative workflow").

## ToC
- CLI
- Python / Node — batch in one call
- The RAG handoff (structured output → file → parse → citations)
- Framework loaders (LangChain / LlamaIndex)
- Java (in-process)
- Pipe patterns & remote backend

## CLI

```bash
# Local, minimal. Fill each <…> from the installed --help.
opendataloader-pdf <input> <the output-format option help lists> <the output-destination option> <the quiet option>
```

Add a backend selector only when a verified local run is insufficient **and** the
server is reachable (`hybrid-guide.md`).

## Python / Node — batch in one call

Each invocation spawns a JVM, so repeated one-file calls are slow: pass all files
to a single call. For crash-isolation or memory limits, split into a few
reasonably-sized batches rather than one giant call — ordinary per-file errors are
recorded and the run continues (non-zero exit at the end), but a JVM-level crash or
out-of-memory can still take down the whole call. (Confirm the current parameter
names/values against the installed package and its `--help`.)

```python
import opendataloader_pdf

opendataloader_pdf.convert(
    input_path=["file1.pdf", "file2.pdf", "file3.pdf"],
    output_dir="./output",
    # remaining keyword arguments name capabilities (output format, backend, …);
    # confirm the current parameter names/values against the installed package.
)
```

```javascript
import { convert } from '@opendataloader/pdf';

// Same JVM-per-call concern: pass all files to one convert() call.
await convert(['file1.pdf', 'file2.pdf'], {
  outputDir: './output',
  // other options name capabilities — confirm names/values against the package.
});
```

## The RAG handoff — route structured output through a file, then parse it

This is the concrete method. A structured output that carries **position metadata**
(page + region) is what lets a retrieved chunk cite its exact source. Two hazards
shape the method (SKILL.md "Silent-failure hazards"; `option-interactions.md` §A.3):

- the structured format may **not stream to stdout** — so write it to a **file**
  and read the file;
- chunking on rendered markup (e.g. heading separators) **drops** the position
  metadata — so chunk from the structured file, not from the markup.

**Step 1 — produce the structured file** (not stdout):

```bash
opendataloader-pdf <input> <the structured-format option help lists> <the output-destination option> <the quiet option>
# then read the written file; if you must pipe, parse the file and pipe the PARSED result:
#   … && jq . <the written output file>
```

**Step 2 — confirm the field names by probing your own output.** Open one output
file and see where the element **text**, **page number**, and **bounding box**
actually live and what they are called — do not assume spellings. The helpers below
try several spellings so they survive version differences, but eyeball one real
file first.

**Step 3 — flatten the element tree to `(text, page, bbox)` and pack into
size-bounded chunks that carry the metadata:**

```python
import json

# Field names vary by version — confirm against your own output (Step 2).
# Each accessor tries the likely spellings; keep whichever your file actually uses.
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
        separator_len = 1 if buf else 0   # the "\n" that will join this element
        if buf_len + separator_len + len(text) > max_chars and buf:
            chunks.append({"text": "\n".join(t for t, _ in buf),
                           "citations": [m for _, m in buf]})
            buf, buf_len = [], 0
            separator_len = 0
        buf.append((text, meta))
        buf_len += separator_len + len(text)
    if buf:
        chunks.append({"text": "\n".join(t for t, _ in buf),
                       "citations": [m for _, m in buf]})
    return chunks

# Each chunk carries the (page, bbox) of every element it contains, so a retrieved
# chunk can cite the exact page and region it came from. max_chars bounds size
# BETWEEN elements: a single element longer than max_chars becomes its own chunk
# that exceeds the bound (elements are never split, to keep each citation intact).
```

**Step 4 — wrap chunks for your framework, keeping the page/bbox pairs intact:**

```python
import json
from langchain_core.documents import Document

chunks = chunk_with_citations("<the written output file>")
docs = [
    Document(
        page_content=c["text"],
        # Keep each (page, bbox) PAIR. A chunk can span pages, so one scalar "page"
        # plus a flat bbox list loses which region is on which page. Many vector
        # stores allow only scalar metadata, so serialize the pairs to a JSON string.
        metadata={
            "page": next((m["page"] for m in c["citations"] if m["page"] is not None), None),
            "citations": json.dumps(c["citations"]),
        },
    )
    for c in chunks
]
# Retrieval: json.loads(doc.metadata["citations"]) -> [{"page":.., "bbox":..}, …]
```

LlamaIndex is the same shape — emit `TextNode(text=…, metadata=…)` with the
identical serialized-pairs metadata and feed the nodes into your index. Embedding
and the vector store itself are your app's choice and outside this skill's scope
(SKILL.md "Where the human decides").

## Framework loaders (LangChain / LlamaIndex)

If you use the framework's own ODL loader instead of the CLI, its constructor takes
a file path and a format-like parameter; confirm the parameter names and their
defaults in the **loader package's** docs (they are the loader's surface, not the
CLI's). Enable a backend in the loader only for scanned/complex PDFs and only after
pre-flighting the server (`hybrid-guide.md`).

```python
from langchain_opendataloader_pdf import OpenDataLoaderPDFLoader

loader = OpenDataLoaderPDFLoader(
    file_path="document.pdf",
    # format / backend parameters: confirm names + defaults in the loader's docs
)
documents = loader.load()   # list of Document objects with page_content + metadata
```

## Java (in-process)

The Java library runs inside your application's own JVM (no wrapper process). Its
output is controlled by per-format toggles on a config object, **not** a single
format string, and at least one kind is on by default — so disable the ones you do
not want. Confirm the exact setter names and which defaults are on against the
**Javadoc/source of the Maven artifact you pinned** (a Java consumer uses that API,
not the installed CLI; CLI option names and Java setters are related but not
interchangeable).

```java
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;

Config config = new Config();
config.setOutputFolder("./output");
// Toggle the output kinds you want via the config's setters — confirm names and
// which defaults are on against the Javadoc for your pinned artifact version.

OpenDataLoaderPDF.processFile("file1.pdf", config);
```

Selecting a backend is a separate opt-in on the same config, only after a verified
local run needs it and a server is reachable. The backend **name string the client
accepts may differ from the constant the API documents** — confirm the accepted
value by probing (a wrong name fails at runtime), and see `hybrid-guide.md`. The
Maven/Gradle dependency block is in `installation-matrix.md`.

## Pipe patterns & remote backend

- **Quiet for automated pipelines:** find the quiet/no-log option in `--help` so
  progress output doesn't pollute the pipe.
- **Piping:** stream a text-like output to the next tool; but a structured output
  may not stream (write a file, then parse it — the RAG handoff above). VERIFY the
  pipe carried real content.
- **Page range / separators:** find the page-selection and per-page-separator
  options in `--help` when a downstream splitter needs them.
- **Remote backend:** run the server on a private address with no built-in auth;
  point the client's server-address option at it; treat the hop as a privacy
  boundary and protect it (firewall / private network / reverse-proxy auth).
  Details + reachability probe: `hybrid-guide.md`.

---

**Cross-references:** SKILL.md "Representative workflow", "VERIFY", "Where the human
decides"; `installation-matrix.md`; `hybrid-guide.md`; `format-guide.md`;
`option-interactions.md` (§A.3 stdout trap).
