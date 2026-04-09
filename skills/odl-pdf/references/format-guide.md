# Output Format Guide

opendataloader-pdf supports 7 output formats via the `format` option. This guide helps you choose the right format for your use case.

## Format Overview

| Format | Best For | Bounding Boxes | Tables | Images |
|---|---|---|---|---|
| `json` | Programmatic processing, source citation | Yes | Structured | As references |
| `text` | Plain text extraction, search indexing | No | Flattened | Omitted |
| `html` | Web display | No | Native `<table>` | Inline |
| `pdf` | Visual debugging of extraction results | Yes (annotated) | Highlighted | Preserved |
| `markdown` | Documentation, RAG chunking | No | Markdown syntax | Omitted |
| `markdown-with-html` | Complex tables in Markdown | No | HTML `<table>` | Omitted |
| `markdown-with-images` | Documentation with visuals | No | Markdown syntax | Embedded/external |

## Downstream Use Mapping

Choose your format based on what you're building:

| Use Case | Recommended Format | Notes |
|---|---|---|
| RAG + source citation | `json` | Bounding boxes enable precise page/region references |
| RAG text chunking | `markdown` | Clean structure maps well to chunk boundaries |
| LangChain integration | `text` | Use with `langchain-opendataloader-pdf`; format=text is the default |
| Web display | `html` | Renders natively in browsers |
| Quality / extraction debugging | `pdf` + `json` | Annotated PDF shows what was detected; JSON shows coordinates |
| Plain text search | `text` | Smallest output, no markup overhead |
| Documentation with images | `markdown-with-images` | Images embedded inline or written to a directory |
| Complex table fidelity | `markdown-with-html` | Falls back to HTML tables where Markdown syntax loses structure |

## Related Options

These options affect output when using image-bearing or multi-page formats:

- `image-output` — Controls whether images are off, embedded (base64), or written to external files. Values: `off`, `embedded`, `external` (default).
- `image-format` — Image encoding format for extracted images. Values: `png` (default), `jpeg`.
- `image-dir` — Directory path for externalized images when `image-output=external`.
- `*-page-separator` — Format-specific option to insert a custom separator between pages (e.g., `markdown-page-separator`, `text-page-separator`).

## Tips

**Multiple formats in one call**

You can produce multiple formats in a single invocation by passing a comma-separated list:

```
opendataloader-pdf input.pdf --format markdown,json
```

This avoids parsing the PDF twice and ensures both outputs are consistent.

**Piping output with `--to-stdout`**

Use `--to-stdout` to write output directly to standard output instead of a file. Useful for piping into other tools:

```
opendataloader-pdf input.pdf --format text --to-stdout | my-indexer
```

Note: When using `--to-stdout` with multiple formats, only single-format output is supported.
