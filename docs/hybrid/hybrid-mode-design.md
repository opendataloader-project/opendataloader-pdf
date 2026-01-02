# Hybrid PDF Processing System - Design Document

## Overview

Hybrid PDF processing system combining Java heuristics + external AI backends.
Routes pages via per-page Triage: simple pages to fast Java path, complex tables/OCR to AI backend.

## Key Decisions

| Item | Decision |
|------|----------|
| CLI Option | `--hybrid <off\|docling\|hancom\|...>` |
| Default | `off` (Java-only, no external dependency) |
| First Backend | `docling` (docling-serve REST API) |
| Automation | Semi-automatic (benchmark/analysis auto, code changes require approval) |
| Triage Strategy | Conservative (minimize FN, accept FP, route uncertain pages to backend) |

---

## CLI Usage

```bash
# Default: Java-only processing
opendataloader-pdf input.pdf
opendataloader-pdf --hybrid off input.pdf

# Use docling backend
opendataloader-pdf --hybrid docling input.pdf

# With custom backend URL
opendataloader-pdf --hybrid docling --hybrid-url http://localhost:5001 input.pdf

# Future backends
opendataloader-pdf --hybrid hancom input.pdf
```

## Hybrid Options

| Option | Description |
|--------|-------------|
| `--hybrid <name>` | Hybrid backend: `off` (default), `docling`, `hancom`, etc. |
| `--hybrid-url <url>` | Backend server URL (overrides default) |
| `--hybrid-timeout <ms>` | Request timeout in milliseconds (default: 30000) |
| `--hybrid-fallback` | Fallback to Java on backend error (default: true) |

## Supported Backends

| Backend | Status | Description |
|---------|--------|-------------|
| `off` | ✅ Default | Java-only, no external calls |
| `docling` | 🔄 Planned | docling-serve (local/Docker) |
| `hancom` | 📋 Future (Priority) | Hancom Document AI |
| `azure` | 📋 Future | Azure Document Intelligence |
| `google` | 📋 Future | Google Document AI |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        PDF Input                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ContentFilterProcessor                         │
│                   (existing: text filtering)                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  TriageProcessor.triageAllPages()                │
│   - Batch triage all pages                                       │
│   - Output: Map<PageNumber, TriageResult>                        │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│      JAVA Path          │     │     BACKEND Path        │
│  (parallel processing)  │     │  (single batch API call)│
│                         │     │                         │
│  ExecutorService        │     │  BackendClient          │
│  - TableBorderProcessor │     │  - Send all pages once  │
│  - TextLineProcessor    │     │  - Receive all results  │
│  - ParagraphProcessor   │     │  SchemaTransformer      │
└─────────────────────────┘     └─────────────────────────┘
              │                               │
              │         CONCURRENT            │
              └───────────────┬───────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Result Merger                                 │
│                  (preserve page order)                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Post-processing & Output Generation                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Backend unavailable | `--hybrid-fallback` (default: true) |
| Triage FN (missed tables) | Conservative threshold, benchmark monitoring |
| Schema mismatch | Step-by-step validation, type checking |
| Slow processing | Parallel execution, batch API calls |

---

## Related Documents

- **Implementation Tasks**: [hybrid-mode-tasks.md](hybrid-mode-tasks.md)
