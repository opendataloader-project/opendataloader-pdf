# Hybrid Mode Reference Guide

Hybrid mode extends opendataloader-pdf by routing complex PDF pages to an external AI backend while keeping simple pages on the fast local Java path. This gives you the speed of the Java engine for most content, with AI-quality output for tables, scanned pages, formulas, and charts.

---

## Overview

By default, opendataloader-pdf processes everything locally in Java. Hybrid mode adds a second processing path and routes pages between the two based on complexity. Two backends are available (`--hybrid`):

- **`docling-fast`** — a built-in Python server (`opendataloader-pdf-hybrid`) that uses the docling library internally. This is the neutral, open default; prefer it unless you specifically need the other backend.
- **`hancom-ai`** — a vendor backend selected on the client. Do not steer users to it unasked.

**When you need hybrid mode:**

- PDFs with scanned or image-based pages (OCR required)
- Complex table structures that the Java heuristics miss
- Documents containing mathematical formulas (LaTeX extraction)
- Charts or images that need AI-generated descriptions
- Non-English documents requiring language-specific OCR

---

## Quick Setup

Hybrid mode requires two running processes: the server and the client.

**Terminal 1 — Start the hybrid server:**

```bash
# Install with hybrid extras (includes the server)
pip install "opendataloader-pdf[hybrid]"

# Start the hybrid server on loopback (unauthenticated — do not bind 0.0.0.0 for local use)
opendataloader-pdf-hybrid --host 127.0.0.1 --port 5002
```

**Terminal 2 — Run the client:**

```bash
# Basic hybrid: per-page triage, docling-fast backend
opendataloader-pdf --hybrid docling-fast input.pdf

# Full mode: send all pages to the backend
opendataloader-pdf --hybrid docling-fast --hybrid-mode full input.pdf
```

The client connects to `http://localhost:5002` by default. No additional configuration is needed for a local setup.

---

## OCR paths

There are **two distinct OCR paths**, depending on the backend. They differ in *where* OCR runs and *how* it is configured. Verify every flag against `--help` before using it.

**(a) `docling-fast` — OCR runs on the server.**
- Client: `--hybrid docling-fast` (add `--hybrid-mode full` to force all pages through the backend).
- Server: `--force-ocr` (or `--no-ocr`; mutually exclusive), `--ocr-engine` (default `easyocr`), `--ocr-lang` (code system depends on the engine — see Server Configuration), `--psm` (Tesseract engines only).
- **This is the path with explicit language control.** Prefer it when the document language must be set — e.g. Korean: `--ocr-lang "ko,en"` (EasyOCR supports the `ko` code; pass it explicitly — Korean is not guaranteed in the engine's default language set).

**(b) `hancom-ai` — OCR *strategy* is a client flag; the OCR runs on the backend.**
- Client: `--hybrid hancom-ai --hybrid-hancom-ai-ocr-strategy force` (values `off` / `auto` / `force`) selects the strategy. The OCR itself is executed by the **Hancom AI backend service**, which must be reachable — the PDF is sent to it (same privacy boundary as any hybrid backend).
- **No language-code control is exposed** on this path.

The `--help` output does not rank backends by accuracy, and neither does this guide. Choose the path that matches your control needs; when the document language matters, use `docling-fast`.

---

## Triage Modes

Control how pages are routed with `--hybrid-mode`.

| Mode | Flag | Behavior |
|------|------|----------|
| auto | `--hybrid-mode auto` | Per-page triage. Simple pages stay on Java; complex pages go to the backend. **Default.** |
| full | `--hybrid-mode full` | All pages go to the backend. Required for enrichment features. |

### When to use `auto`

`auto` is the default and works well for mixed documents. The triage strategy is conservative: it prefers to send borderline pages to the backend (minimizing missed complex content) at the cost of some extra backend calls.

Expected throughput shape:
- Simple pages (Java path): fastest
- Complex pages (backend path): varies by content and hardware
- Overall for a mixed document: between the two extremes

Actual numbers depend on your documents and hardware; measure on your own corpus rather than relying on fixed figures.

### When to use `full`

Use `full` when you need enrichment features (`--enrich-formula`, `--enrich-picture-description`) or when the entire document is scanned and you want consistent OCR output across all pages.

Expected throughput with `full`: noticeably slower than Java-only or `auto`, depending on backend and GPU availability.

> **Important:** `--enrich-formula` and `--enrich-picture-description` are server-side options, but they only take effect when the client is running with `--hybrid-mode full`. In `auto` mode, enrichments are silently skipped — no warning or error is shown. If your output is missing formulas or image descriptions, check that you have `--hybrid-mode full` set on the client side.

---

## Client Options

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--hybrid <name>` | `off`, `docling-fast`, `hancom-ai` | `off` | Select the backend. `off` disables hybrid mode entirely. |
| `--hybrid-mode <mode>` | `auto`, `full` | `auto` | Page routing strategy. |
| `--hybrid-url <url>` | Any URL | unset (client falls back to `http://localhost:5002` for docling-fast; `hancom-ai` defaults to `http://localhost:18008`) | Override the server URL for remote or non-default setups. |
| `--hybrid-timeout <ms>` | Integer | `0` (no timeout) | Request timeout in milliseconds. `0` means no timeout. |
| `--hybrid-fallback` | Flag | Disabled | Fall back to the Java path if the backend returns an error. ⚠ Preserves completion, **not** quality — the run "succeeds" but requested OCR/enrichment did not happen. When those are mandatory, VERIFY them explicitly (or fail closed). |

### `hancom-ai` client flags

These flags apply **only** when `--hybrid hancom-ai` is selected (they are client-side *configuration*; the OCR itself is executed by the Hancom AI backend service, which must be reachable). `docling-fast` ignores them.

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--hybrid-hancom-ai-ocr-strategy <s>` | `off`, `auto`, `force` | `auto` | OCR strategy. `off` = stream-only; `auto` = stream first, OCR fallback; `force` = OCR-only. No language-code control is exposed on this path. |
| `--hybrid-hancom-ai-regionlist-strategy <s>` | `table-first`, `list-only` | `table-first` | DLA label 7 (regionlist) handling. `table-first` = check TSR overlap; `list-only` = skip TSR, always treat as list. |
| `--hybrid-hancom-ai-image-cache <s>` | `memory`, `disk` | `memory` | Page image cache backing. |

---

## Server Configuration

All options are passed when starting `opendataloader-pdf-hybrid` (the `docling-fast` backend server).

| Option | Default | Description |
|--------|---------|-------------|
| `--host <addr>` | `0.0.0.0` | Host to bind to. Default binds all interfaces and the server has **no auth** — restrict to a trusted network / firewall, or bind `127.0.0.1` for local-only. |
| `--port <n>` | `5002` | Port the server listens on. |
| `--log-level <lvl>` | `info` | Log level. Values: `debug`, `info`, `warning`, `error`. |
| `--device <name>` | `auto` | Accelerator for model inference. Values: `auto`, `cpu`, `cuda`, `mps`, `xpu`. `auto` selects the best available device. Use `mps` explicitly on Apple Silicon, or `cpu` to force CPU-only processing. |
| `--force-ocr` | Off | Run OCR on every page, even pages with embedded text. Use for scanned PDFs where embedded text is unreliable. **Mutually exclusive with `--no-ocr`.** |
| `--no-ocr` | Off | Disable OCR entirely. Use when input PDFs already have reliable embedded text — prevents duplicate text extraction from images. **Mutually exclusive with `--force-ocr`.** |
| `--ocr-engine <name>` | `easyocr` | OCR engine. Values: `auto`, `easyocr`, `ocrmac`, `rapidocr`, `tesseract`, `tesserocr`. `auto` delegates per-page engine choice to docling. Each engine has its own license, language coverage, and accuracy; the server does not validate engine accuracy. |
| `--ocr-lang "<langs>"` | engine default | Comma-separated OCR language codes. **The code system depends on `--ocr-engine`:** EasyOCR uses its own codes (ISO 639-1-like for many — `ko,en` — but `ch_sim`/`ch_tra` for Chinese), Tesseract uses ISO 639-2 (`kor,eng`), RapidOCR uses `english,chinese`, ocrmac uses BCP-47 (`en-US`). If omitted, the engine's default languages are used — there is no fixed `en` default. |
| `--psm <n>` | — | Tesseract Page Segmentation Mode. Applied **only** when `--ocr-engine` is `tesseract` or `tesserocr`; ignored otherwise. See `tesseract --help-extra` for valid values. |
| `--max-file-size <MB>` | `0` | Maximum upload file size in MB. `0` means no limit. |
| `--enrich-formula` / `--no-enrich-formula` | Off | Extract mathematical formulas as LaTeX. **Requires `--hybrid-mode full` on the client.** |
| `--enrich-picture-description` / `--no-enrich-picture-description` | Off | Generate AI descriptions (alt text) for charts and images using SmolVLM. **Requires `--hybrid-mode full` on the client.** |
| `--picture-description-prompt "<text>"` | docling default | Custom prompt for picture description. Blank/whitespace-only falls back to docling's default prompt. |

**Example — scanned Korean document with formula extraction (docling-fast path):**

```bash
# Server (EasyOCR uses its own codes, e.g. ko, en, ch_sim)
opendataloader-pdf-hybrid --host 127.0.0.1 --port 5002 --force-ocr --ocr-engine easyocr --ocr-lang "ko,en" --enrich-formula

# Client (must use --hybrid-mode full)
opendataloader-pdf --hybrid docling-fast --hybrid-mode full input.pdf
```

---

## Troubleshooting

### "Connection refused" or server not reachable

The server is not running or is on a different port/host.

1. Confirm the server started without errors in Terminal 1.
2. Check the port matches on both sides (`--port` on server, `--hybrid-url` on client).
3. For a remote server, ensure the host is reachable and the firewall allows the port.

```bash
# Test connectivity manually
curl http://localhost:5002/health
```

### Request timeout

The backend is taking longer than the configured timeout.

- Increase the timeout: `--hybrid-timeout 30000` (30 seconds)
- Or disable it: `--hybrid-timeout 0`
- If this is persistent, check backend resource usage (CPU/GPU).

### Formulas or image descriptions missing from output

This is the most common silent failure. Enrichment options on the server are only applied when the client sends the page to the backend.

- In `auto` mode, pages classified as simple stay on Java — enrichments are never applied to them.
- **Fix:** Add `--hybrid-mode full` to your client command.

No error or warning is emitted when enrichments are skipped. This is by design (the server processes what it receives), but it can be surprising.

### Output quality is lower than expected for complex tables

In `auto` mode, the triage heuristic may occasionally classify a complex table as simple. Switch to `--hybrid-mode full` to force all pages through the backend.

---

## Backend Registry

| Backend | Status | OCR location | Features |
|---------|--------|--------------|----------|
| `docling-fast` | Available | Server | OCR (engine + language configurable), formula extraction (LaTeX), chart descriptions, table enhancement |
| `hancom-ai` | Available | Server (Hancom AI backend) | OCR strategy via client flag `--hybrid-hancom-ai-ocr-strategy`, but executed on the backend; regionlist strategy; image-cache control. No language-code control. |

Backends are selected with `--hybrid <name>`. Only one backend can be active per run. `docling-fast` is the neutral, open default; `hancom-ai` is a vendor backend — do not steer users to it unasked.

---

## Performance Notes

Relative throughput:

- **Java only (no hybrid)**: fastest path
- **Hybrid `auto`** (mixed document): close to Java speed for most pages; only triaged pages pay the backend round-trip
- **Hybrid `full`**: slowest path; GPU-accelerated backend recommended

Latency figures depend on document complexity, available hardware, and backend configuration. Running the hybrid server on a machine with a GPU significantly reduces the per-page time in `full` mode. Measure against your own corpus for representative numbers.

For throughput-sensitive workloads, use `auto` mode and reserve `full` mode for documents where enrichment or uniform OCR quality is required.

**Large documents** — do not pre-split on assumption. There is no client-side batching flag, and how pages are sent to the backend can vary by backend and version. Test a representative large document first and watch memory, upload limits, timeouts, and backend behavior; split only when an observed operational limit requires it.
