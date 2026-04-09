# Hybrid Mode Reference Guide

Hybrid mode extends opendataloader-pdf by routing complex PDF pages to an external AI backend while keeping simple pages on the fast local Java path. This gives you the speed of the Java engine for most content, with AI-quality output for tables, scanned pages, formulas, and charts.

---

## Overview

By default, opendataloader-pdf processes everything locally in Java. Hybrid mode adds a second processing path — a Python-based server running [docling-serve](https://github.com/DS4SD/docling-serve) — and routes pages between the two based on complexity.

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
# Install the server component
pip install opendataloader-pdf-hybrid

# Start with defaults (port 5002)
opendataloader-pdf-hybrid --port 5002
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

## Triage Modes

Control how pages are routed with `--hybrid-mode`.

| Mode | Flag | Behavior |
|------|------|----------|
| auto | `--hybrid-mode auto` | Per-page triage. Simple pages stay on Java; complex pages go to the backend. **Default.** |
| full | `--hybrid-mode full` | All pages go to the backend. Required for enrichment features. |

### When to use `auto`

`auto` is the default and works well for mixed documents. The triage strategy is conservative: it prefers to send borderline pages to the backend (minimizing missed complex content) at the cost of some extra backend calls.

Expected throughput:
- Simple pages (Java path): ~0.015 s/page
- Complex pages (backend path): varies by content and hardware
- Overall for a mixed document: between the two extremes

### When to use `full`

Use `full` when you need enrichment features (`--enrich-formula`, `--enrich-picture-description`) or when the entire document is scanned and you want consistent OCR output across all pages.

Expected throughput with `full`: approximately 0.5 s/page (depends on backend and GPU availability).

> **Important:** `--enrich-formula` and `--enrich-picture-description` are server-side options, but they only take effect when the client is running with `--hybrid-mode full`. In `auto` mode, enrichments are silently skipped — no warning or error is shown. If your output is missing formulas or image descriptions, check that you have `--hybrid-mode full` set on the client side.

---

## Client Options

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--hybrid <name>` | `off`, `docling-fast` | `off` | Select the backend. `off` disables hybrid mode entirely. |
| `--hybrid-mode <mode>` | `auto`, `full` | `auto` | Page routing strategy. |
| `--hybrid-url <url>` | Any URL | `http://localhost:5002` | Override the server URL for remote or non-default setups. |
| `--hybrid-timeout <ms>` | Integer | — | Request timeout in milliseconds. Set to `0` to disable timeout. |
| `--hybrid-fallback` | Flag | Disabled | Fall back to the Java path if the backend returns an error. |

---

## Server Configuration

All options are passed when starting `opendataloader-pdf-hybrid`.

| Option | Default | Description |
|--------|---------|-------------|
| `--port <n>` | `5002` | Port the server listens on. |
| `--force-ocr` | Off | Run OCR on every page, even if the page has selectable text. Use this for scanned PDFs where embedded text is unreliable. |
| `--ocr-lang "<langs>"` | `"en"` | Comma-separated language codes for OCR (e.g., `"ko,en"`). Improves accuracy for non-English documents. |
| `--enrich-formula` | Off | Extract mathematical formulas as LaTeX. **Requires `--hybrid-mode full` on the client.** |
| `--enrich-picture-description` | Off | Generate AI descriptions for charts and images. **Requires `--hybrid-mode full` on the client.** |

**Example — scanned Korean document with formula extraction:**

```bash
# Server
opendataloader-pdf-hybrid --port 5002 --force-ocr --ocr-lang "ko,en" --enrich-formula

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

| Backend | Status | Features |
|---------|--------|----------|
| `docling-fast` | Available | OCR, formula extraction (LaTeX), chart descriptions, table enhancement |
| `hancom` | Planned | Hancom Document AI integration |
| `azure` | Planned | Azure AI Document Intelligence |
| `google` | Planned | Google Document AI |

Backends are selected with `--hybrid <name>`. Only one backend can be active per run.

---

## Performance Notes

| Processing path | Approximate throughput |
|-----------------|----------------------|
| Java only (no hybrid) | ~0.015 s/page |
| Hybrid `auto` (mixed document) | Varies; most pages stay at Java speed |
| Hybrid `full` | ~0.5 s/page (GPU-accelerated backend recommended) |

Latency figures are approximate and depend on document complexity, available hardware, and backend configuration. Running the hybrid server on a machine with a GPU significantly reduces the per-page time in `full` mode.

For throughput-sensitive workloads, use `auto` mode and reserve `full` mode for documents where enrichment or uniform OCR quality is required.
