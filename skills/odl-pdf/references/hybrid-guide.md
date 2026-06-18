# Hybrid Mode Reference Guide

Hybrid mode extends opendataloader-pdf by routing complex PDF pages to an external AI backend while keeping simple pages on the fast local Java path. This gives you the speed of the Java engine for most content, with AI-quality output for tables, scanned pages, formulas, and charts.

---

## Overview

By default, opendataloader-pdf processes everything locally in Java. Hybrid mode adds a second processing path — a built-in Python server (`opendataloader-pdf-hybrid`) that uses the docling library internally — and routes pages between the two based on complexity.

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

# Start the hybrid server (port 5002)
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

Expected throughput shape:
- Simple pages (Java path): fastest
- Complex pages (backend path): varies by content and hardware
- Overall for a mixed document: between the two extremes

For current numbers, run `./scripts/bench.sh`.

### When to use `full`

Use `full` when you need enrichment features (`--enrich-formula`, `--enrich-picture-description`) or when the entire document is scanned and you want consistent OCR output across all pages.

Expected throughput with `full`: noticeably slower than Java-only or `auto`, depending on backend and GPU availability. Run `./scripts/bench.sh` for current per-page timings.

> **Important:** `--enrich-formula` and `--enrich-picture-description` are server-side options, but they only take effect when the client is running with `--hybrid-mode full`. In `auto` mode, enrichments are silently skipped — no warning or error is shown. If your output is missing formulas or image descriptions, check that you have `--hybrid-mode full` set on the client side.

---

## Client Options

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--hybrid <name>` | `off`, `docling-fast` | `off` | Select the backend. `off` disables hybrid mode entirely. |
| `--hybrid-mode <mode>` | `auto`, `full` | `auto` | Page routing strategy. |
| `--hybrid-url <url>` | Any URL | `http://localhost:5002` | Override the server URL for remote or non-default setups. |
| `--hybrid-timeout <ms>` | Integer | `0` (no timeout) | Request timeout in milliseconds. `0` means no timeout. |
| `--hybrid-fallback` | Flag | Disabled | Fall back to the Java path if the backend returns an error. |

---

## Server Configuration

All options are passed when starting `opendataloader-pdf-hybrid`.

| Option | Default | Description |
|--------|---------|-------------|
| `--port <n>` | `5002` | Port the server listens on. |
| `--device <name>` | `auto` | Accelerator for model inference. Values: `auto`, `cpu`, `cuda`, `mps`, `xpu`. `auto` selects the best available device (checks CUDA, then MPS, then XPU, then CPU). Use `mps` explicitly on Apple Silicon if the auto-selected device is suboptimal, or `cpu` to force CPU-only processing. |
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

Relative throughput:

- **Java only (no hybrid)**: fastest path
- **Hybrid `auto`** (mixed document): close to Java speed for most pages; only triaged pages pay the backend round-trip
- **Hybrid `full`**: slowest path; GPU-accelerated backend recommended

Latency figures depend on document complexity, available hardware, and backend configuration. Running the hybrid server on a machine with a GPU significantly reduces the per-page time in `full` mode. Run `./scripts/bench.sh` against your own corpus for representative numbers.

For throughput-sensitive workloads, use `auto` mode and reserve `full` mode for documents where enrichment or uniform OCR quality is required.

**Large-document auto-chunking (2.2.1+)** — The Java client automatically splits backend-routed pages into 50-page chunks before sending them to the server. Processing a 200-page scanned PDF in `--hybrid-mode full` no longer hangs the backend. The AI model is loaded once at server startup (singleton), so chunking adds no per-chunk startup cost. No client-side flag; the server's existing `page_ranges` support handles it. Pre-2.2.1 users who manually split large PDFs before processing no longer need to.
