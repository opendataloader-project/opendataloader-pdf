# ODL-PDF CLI Options Matrix

This file contains a built-in summary of all 26 CLI options for the `opendataloader-pdf` tool.
If `options.json` is present in the project root, that file is the authoritative source — always
prefer it over the descriptions here. This document exists so the agent skill can reason about
options without loading the full JSON on every invocation.

---

## Categories

### IO — Input / Output Control

Controls where data comes from and where results are written.

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `output-dir` | `-o` | string | null (input file dir) | Directory where output files are written. Defaults to the same directory as the input file. |
| `to-stdout` | — | boolean | false | Write output to stdout instead of a file. Only valid with a single format. |
| `quiet` | `-q` | boolean | false | Suppress all console logging output. |
| `password` | `-p` | string | null | Password for encrypted PDF files. |
| `pages` | — | string | null (all) | Pages to extract, e.g. `"1,3,5-7"`. Defaults to all pages. |
| `format` | `-f` | string | json | Output format(s), comma-separated. Values: `json`, `text`, `html`, `pdf`, `markdown`, `markdown-with-html`, `markdown-with-images`. |

---

### Quality — Extraction Quality

Controls the accuracy and structure of the extracted content.

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `table-method` | — | string | `default` | Table detection method. `default` = border-based; `cluster` = border + borderless cluster detection (slower). |
| `reading-order` | — | string | `xycut` | Reading order algorithm. `xycut` = XY-cut layout analysis; `off` = no reordering. |
| `use-struct-tree` | — | boolean | false | Use the PDF structure tree (tagged PDF) for reading order and semantic structure. Only effective on tagged PDFs. |

---

### Safety — Security and Privacy

Controls content filtering and sensitive data handling.

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `content-safety-off` | — | string | null | Disable specific content safety filters. Values: `all`, `hidden-text`, `off-page`, `tiny`, `hidden-ocg`. |
| `sanitize` | — | boolean | false | Replace emails, phone numbers, IP addresses, credit card numbers, and URLs with placeholders. |

---

### Hybrid — AI Backend

Options for routing pages through an optional AI enrichment server (e.g. formula OCR, picture descriptions).

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `hybrid` | — | string | `off` | Hybrid backend to use. Values: `off`, `docling-fast`. Requires a running hybrid server. |
| `hybrid-mode` | — | string | `auto` | Triage mode. `auto` = dynamic page-level triage; `full` = send all pages to the backend (required for server-side enrichments). |
| `hybrid-url` | — | string | null | Override the default hybrid server URL. |
| `hybrid-timeout` | — | string | `0` | Per-request timeout in milliseconds (`0` = no timeout). |
| `hybrid-fallback` | — | boolean | false | Fall back to the Java extraction path if the hybrid backend returns an error. |

---

### Output — Output Formatting

Controls how images and page separators appear in output files.

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `image-output` | — | string | `external` | Image output mode. `off` = skip images; `embedded` = Base64 data URIs inline; `external` = write separate image files and embed references. |
| `image-format` | — | string | `png` | Format for extracted images. Values: `png`, `jpeg`. |
| `image-dir` | — | string | null | Directory for extracted image files (used when `image-output` is `external`). |
| `markdown-page-separator` | — | string | null | String inserted between pages in Markdown output. Use `%page-number%` to include the page number. |
| `text-page-separator` | — | string | null | String inserted between pages in plain-text output. Use `%page-number%` for page numbers. |
| `html-page-separator` | — | string | null | String inserted between pages in HTML output. Use `%page-number%` for page numbers. |

---

### Text — Text Processing

Fine-grained control over how extracted text is cleaned and formatted.

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `keep-line-breaks` | — | boolean | false | Preserve the original line breaks from the PDF. By default, soft line breaks are merged. |
| `replace-invalid-chars` | — | string | `" "` (space) | Replacement character for invalid or unrecognized characters in the extracted text. |
| `include-header-footer` | — | boolean | false | Include page headers and footers in the output. Excluded by default. |
| `detect-strikethrough` | — | boolean | false | Detect strikethrough text (experimental). |

---

## Interaction Rules

These rules document option combinations that have non-obvious or silent failure modes.

**1. Hybrid enrichments require `--hybrid-mode full`**

Server-side enrichments such as `--enrich-formula` and `--enrich-picture-description` run on the
hybrid backend. On the client side, they are only applied if `--hybrid-mode full` is set. With the
default `auto` mode, pages that the triage step classifies as "simple" bypass the backend entirely,
and any enrichment instructions for those pages are silently ignored. If enrichments are missing
from the output, check that `--hybrid-mode full` is set.

**2. `--hybrid` requires a running server**

Setting `--hybrid docling-fast` (or any non-`off` value) without a reachable hybrid server will
cause requests to fail. Quick start:

```bash
pip install "opendataloader-pdf[hybrid]"
opendataloader-pdf-hybrid --port 5002
```

Then pass `--hybrid docling-fast --hybrid-url http://localhost:5002` to the client.

**3. `--to-stdout` only works with a single format**

`--to-stdout` writes the extracted content to standard output. It cannot be combined with
comma-separated `--format` values (e.g. `--format json,text`). Passing multiple formats with
`--to-stdout` will produce an error. When streaming output to another process, specify exactly one
format.

**4. `--image-output embedded` produces large output for image-heavy PDFs**

`embedded` mode encodes each image as a Base64 data URI and inlines it in the output document.
For PDFs with many or large images this can produce very large output files. Prefer `external`
(the default) unless the consumer requires self-contained output.

**5. `--table-method cluster` may be slower**

The `cluster` method adds borderless table detection on top of the default border-based approach.
It improves recall on tables without visible borders but increases processing time. Use `default`
when throughput matters and the PDFs have standard bordered tables.

**6. `--use-struct-tree` has no effect on untagged PDFs**

The structure tree option reads semantic order from the PDF's tag tree, which is only present in
tagged (accessible) PDFs. On untagged PDFs the option is silently ignored and the default layout
analysis is used instead. To check whether a PDF is tagged, inspect its document properties or
run a preflight check before enabling this option.

---

## Common Combinations

### RAG pipeline (retrieval-augmented generation)

Extract clean, structured text with accurate reading order for vector indexing:

```bash
opendataloader-pdf input.pdf \
  --format json \
  --reading-order xycut \
  --table-method cluster \
  --image-output off \
  --sanitize
```

Use `--sanitize` when the PDF may contain PII that should not enter the vector store.

---

### Accessibility audit (tagged PDF)

Leverage the PDF's tag tree to validate semantic structure and export accessible HTML:

```bash
opendataloader-pdf input.pdf \
  --format html \
  --use-struct-tree \
  --include-header-footer \
  --html-page-separator "<!-- page %page-number% -->"
```

---

### Quick plain-text extraction

Minimal options for fast extraction of readable prose:

```bash
opendataloader-pdf input.pdf \
  --format text \
  --quiet \
  --to-stdout
```

Pipe directly to downstream tools: `opendataloader-pdf input.pdf -f text -q --to-stdout | wc -w`

---

### Markdown with images for documentation

Export a Markdown file with embedded images, suitable for wikis or documentation sites:

```bash
opendataloader-pdf input.pdf \
  --format markdown-with-images \
  --image-output external \
  --image-format png \
  --image-dir ./images \
  --output-dir ./output
```

---

### AI-enriched extraction (hybrid mode)

Extract all pages through the hybrid backend for formula OCR and picture descriptions:

```bash
opendataloader-pdf input.pdf \
  --format markdown \
  --hybrid docling-fast \
  --hybrid-mode full \
  --hybrid-url http://localhost:5002 \
  --hybrid-fallback
```

`--hybrid-fallback` ensures that if the server is temporarily unavailable, extraction continues
with the local Java backend rather than failing.

---

### Selective page extraction for large PDFs

Extract only a specific page range to reduce processing time:

```bash
opendataloader-pdf large-report.pdf \
  --pages "1,5-10,15" \
  --format json \
  --output-dir ./extracted \
  --quiet
```
