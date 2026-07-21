# Output Format Guide

Which format to choose for which use case — version-stable guidance. For the authoritative
list of format *values* and per-option defaults (e.g. `--image-output`), use the discovery
sources in SKILL.md "Version & option authority" (`--help` / `options.json` / homepage), not
a table here.

> `tagged-pdf` is an **extraction output format** that emits a PDF carrying structure tags. It is **not** PDF/UA accessibility-compliance certification — that is out of scope for this tool (see SKILL.md Stage 0).
>
> `--markdown-with-html` is a **flag**, not a `--format` value. It allows raw HTML tags inside Markdown output for complex structures such as multi-row-span tables, and implies `--format markdown`. See Related Options below.
>
> Images are controlled by `--image-output` (`off` / `embedded` / `external`). `markdown-with-images` is a **deprecated `--format` alias** — still accepted (emits a warning, slated for removal) and not listed in `--help`; use `--image-output` instead.

## Downstream Use Mapping

Choose your format based on what you're building:

| Use Case | Recommended Format | Notes |
|---|---|---|
| RAG + source citation | `json` | Bounding boxes enable precise page/region references |
| RAG text chunking | `markdown` | Clean structure maps well to chunk boundaries |
| LangChain integration | `text` (or json/markdown/html) | Use with `langchain-opendataloader-pdf`; verify the loader's default and params in its package docs |
| Web display | `html` | Renders natively in browsers |
| Quality / extraction debugging | `pdf` + `json` | Annotated PDF shows what was detected; JSON shows coordinates |
| Plain text search | `text` | Smallest output, no markup overhead |
| Documentation with images | `markdown` + `--image-output` | Use `--image-output embedded` (inline Base64) or `external` (files in `--image-dir`) |
| Complex table fidelity | `markdown` + `--markdown-with-html` | The flag falls back to HTML `<table>` where Markdown syntax loses structure |
| Tagged-PDF extraction output | `tagged-pdf` | Structure-tagged PDF; not PDF/UA compliance |

## Related Options

These options affect output when using image-bearing or multi-page formats:

- `markdown-with-html` — Flag (not a `--format` value). Allows HTML tags inside Markdown output for complex structures such as multi-row-span tables. Implies `--format markdown`.
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

Use `--to-stdout` to write output directly to standard output instead of a file. Pass `-q` so log lines don't pollute the stream, and note it streams **text-like formats only** — `json`/`html` emit nothing to stdout on 2.5.0 (write those to files):

```
opendataloader-pdf input.pdf --format text --to-stdout -q | my-indexer
```

Note: `--to-stdout` streams **at most one** text-like format and does **not** error on multiple. `text` takes precedence over `markdown`; `json`/`html` never stream. So `text,markdown`→text, `json,text`→text, and `json,html`→**empty stdout** (exit 0). Pass exactly one text-like format.
