# ODL-PDF Option Interactions & Combinations

This file documents **how options interact and combine** — the version-stable knowledge.
It does **not** list the option inventory. For the current option names, values, and defaults,
use the discovery sources (SKILL.md "Version & option authority"): the installed CLI's `--help`
(authority for the user's version), a checkout's `options.json`, or the homepage CLI Options
Reference. Option names/values referenced below are guarded by `scripts/sync-skill-refs.py`.

---

## Categories

### IO — Input / Output Control

Controls where data comes from and where results are written.

---

### Quality — Extraction Quality

Controls the accuracy and structure of the extracted content.

---

### Safety — Security and Privacy

Controls content filtering and sensitive data handling.

---

### Hybrid — AI Backend

Options for routing pages through an optional AI enrichment server (e.g. formula OCR, picture descriptions).

---

### Output — Output Formatting

Controls how images and page separators appear in output files.

---

### Text — Text Processing

Fine-grained control over how extracted text is cleaned and formatted.

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

Setting `--hybrid docling-fast` (or any non-`off` value) without a reachable hybrid server makes
requests fail — **unless `--hybrid-fallback` is set, in which case the run completes via the local
Java path and returns fallback output (no hybrid OCR/enrichment), which is easy to mistake for a
successful hybrid extraction** (VERIFY the enrichments actually landed). Quick start:

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

This is the **client** command; the enrichments only run if the hybrid **server** was started with the matching flags (`--enrich-formula` and/or `--enrich-picture-description`) — `--hybrid-mode full` alone routes pages to the backend but enables neither (Gotcha 2). Do **not** add `--hybrid-fallback` here: this example's goal is enrichment, and on a backend error fallback would yield enrichment-less output that still "succeeds." Add it only when partial local output beats failure — and then verify the enrichments actually landed.

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
