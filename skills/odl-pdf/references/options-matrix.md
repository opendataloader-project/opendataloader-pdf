# ODL-PDF CLI Options Matrix

This file contains a built-in summary of every CLI option for the `opendataloader-pdf` tool.
If `options.json` is present in the project root, prefer it over the descriptions here — it is the
machine-readable option surface **exported from the Java CLI's option definitions** (`CLIOptions.java`;
the CLI is not generated from this file), so it is the accurate snapshot for that checkout. Ground
truth for the user's actual environment is still the installed `--help`. This document exists so the
agent skill can reason about options without loading the full JSON on every invocation, and adds **category groupings**,
**Interaction Rules**, and **Common Combinations** that the raw schema does not express.

---

## Categories

### IO — Input / Output Control

Controls where data comes from and where results are written.

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `output-dir` | `-o` | string | null (input file dir) | Directory where output files are written. Defaults to the same directory as the input file. |
| `to-stdout` | — | boolean | false | Write output to stdout instead of a file. Streams one text-like format only (`text`/`markdown`); multiple formats → silently one; `json`/`html` → empty stdout. Pass `-q`. |
| `quiet` | `-q` | boolean | false | Suppress all console logging output. |
| `password` | `-p` | string | null | Password for encrypted PDF files. |
| `pages` | — | string | null (all) | Pages to extract, e.g. `"1,3,5-7"`. Defaults to all pages. |
| `format` | `-f` | string | json | Output format(s), comma-separated. Values: `json`, `text`, `html`, `pdf`, `markdown`, `tagged-pdf`. For HTML inside Markdown use the `markdown-with-html` flag; for image extraction use `image-output`. |
| `threads` | — | string | 1 | Worker threads for per-page processing. Default `1` (sequential, stable). Values >1 (experimental) run pages in parallel; output may vary slightly on some PDFs. Capped at available CPU cores. Native Java pipeline only; ignored in `--hybrid` mode. |

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
| `content-safety-off` | — | string | null | Disable specific content safety filters. Values: `all`, `hidden-text`, `off-page`, `tiny`, `hidden-ocg`. ⚠ Do not disable on untrusted PDFs — re-exposes hidden/injected content (see SKILL.md "Caution — content-safety filters"). |
| `sanitize` | — | boolean | false | Replace emails, phone numbers, IP addresses, credit card numbers, and URLs with placeholders. |

---

### Hybrid — AI Backend

Options for routing pages through an optional AI enrichment server (e.g. formula OCR, picture descriptions).

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `hybrid` | — | string | `off` | Hybrid backend to use. Values: `off`, `docling-fast`, `hancom-ai`. Requires a running hybrid server. |
| `hybrid-mode` | — | string | `auto` | Triage mode. `auto` = dynamic page-level triage; `full` = send all pages to the backend (required for server-side enrichments). |
| `hybrid-url` | — | string | null | Override the default hybrid server URL. |
| `hybrid-timeout` | — | string | `0` | Per-request timeout in milliseconds (`0` = no timeout). |
| `hybrid-fallback` | — | boolean | false | Fall back to the Java extraction path if the hybrid backend returns an error. |
| `hybrid-hancom-ai-ocr-strategy` | — | string | `auto` | OCR strategy. Requires `--hybrid=hancom-ai`. Values: `off` (stream-only), `auto` (default; stream first, OCR fallback), `force` (OCR-only). |
| `hybrid-hancom-ai-regionlist-strategy` | — | string | `table-first` | DLA label 7 (regionlist) handling. Requires `--hybrid=hancom-ai`. Values: `table-first` (default; check TSR overlap), `list-only` (skip TSR, always treat as list). |
| `hybrid-hancom-ai-image-cache` | — | string | `memory` | Page image cache backing. Requires `--hybrid=hancom-ai`. Values: `memory` (default), `disk`. |

---

### Output — Output Formatting

Controls how images and page separators appear in output files.

| Option | Short | Type | Default | Description |
|---|---|---|---|---|
| `markdown-with-html` | — | boolean | false | Allow HTML tags inside Markdown output for complex structures such as multi-row-span tables. Implies `--format markdown`. This is a flag, not a `--format` value. |
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
opendataloader-pdf-hybrid --host 127.0.0.1 --port 5002
```

Then pass `--hybrid docling-fast --hybrid-url http://localhost:5002` to the client.

**3. `--to-stdout` streams one text-like format — two silent traps**

`--to-stdout` writes extracted content to standard output, both exit-0-with-no-error traps:
(a) with multiple comma-separated `--format` values it emits **one** and silently drops the rest
— pass exactly one; (b) `json` and `html` produce **no stdout** on 2.5.0 — only `text`/`markdown`
stream, so write `json`/`html` to a file with `-o` instead. Also pass `-q` so log lines don't
pollute the stream.

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
PDFs with a structure tag tree. On a PDF without one the option is ignored and the default layout
analysis is used instead. To check whether a PDF is tagged, inspect its document properties or
run a preflight check before enabling this option. On a *tagged* PDF, `--use-struct-tree` also **takes precedence over `--hybrid`**: when both are set the hybrid backend is not called (a warning is emitted), so do not combine them expecting hybrid enrichment (see SKILL.md "Option interactions").

---

## Common Combinations

### RAG pipeline (retrieval-augmented generation)

Extract clean, structured text with accurate reading order for vector indexing:

```bash
opendataloader-pdf input.pdf \
  --format json \
  --reading-order xycut \
  --table-method cluster \
  --image-output off
```

Add `--sanitize` **only** when the PDF may contain PII that must not enter the vector store. It replaces emails / phone numbers / URLs / etc. with placeholders, which can drop citation URLs and reduce fidelity — so it is **not** included by default here.

---

### Extract using a tagged PDF's own structure

Leverage the PDF's tag tree to preserve author-intended reading order and export HTML from that structure (not an accessibility-conformance guarantee):

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

Export a Markdown file with images written to a directory, suitable for wikis or documentation sites. Images are controlled by `--image-output` (`markdown-with-images` is a deprecated `--format` alias that still warns; prefer `--image-output`):

```bash
opendataloader-pdf input.pdf \
  --format markdown \
  --image-output external \
  --image-format png \
  --image-dir ./images \
  --output-dir ./output
```

For self-contained Markdown (no separate image files), use `--image-output embedded` to inline images as Base64 data URIs.

---

### AI-enriched extraction (hybrid mode)

Extract all pages through the hybrid backend for formula OCR and picture descriptions:

```bash
opendataloader-pdf input.pdf \
  --format markdown \
  --hybrid docling-fast \
  --hybrid-mode full \
  --hybrid-url http://127.0.0.1:5002
```

Do **not** add `--hybrid-fallback` here: this example's goal is enrichment, and on a backend error fallback would yield enrichment-less output that still "succeeds." Add it only when partial local output beats failure — and then verify the enrichments actually landed.

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
