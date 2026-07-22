# Output format guide — goal → capability

Pick the output by what you're building, expressed as a **capability**. The current
*names* of the formats and the options that modify them come from the installed
`--help` (SKILL.md "Source-of-truth rule") — this guide never spells them, because
they change between releases. Match your goal to a capability here, then find the
option that provides it in the installed help.

Scope note: producing a structure-tagged PDF as an extraction *output* is a format
capability; it is **not** PDF/UA accessibility-compliance certification, which is
out of scope for this tool (SKILL.md frontmatter / "Purpose").

## Goal → capability

| Your goal | Capability you need | How to find it |
|---|---|---|
| RAG with source citation (page + region) | a structured format that carries per-element **position metadata** (page number, bounding box) | find the structured/data output format in `--help`; confirm it carries position by probing one output (`integration-examples.md`) |
| RAG text chunking on structure | a format whose **structure maps to chunk boundaries** (headings/sections) | the rich-text / markup output format |
| Plain-text search, smallest output | a **plain-text** format, no markup | the text output format |
| Web display | a **browser-renderable markup** format | the HTML-family output format |
| Quality / detection debugging | an **annotated** output (boxes over a copy of the input) plus the structured data to correlate | the annotated-PDF output + the structured format together (`eval-metrics.md`) |
| Docs with images | a markup format **plus an image-handling capability** (self-contained vs. referenced) | the markup format + the image-output option; see the size/portability trade in `option-interactions.md` §B.8 |
| Complex-table fidelity in markup | a markup format that can **fall back to richer table markup** where plain syntax loses structure | the markup format + its rich-table modifier |
| Framework loader (LangChain / LlamaIndex) | the **loader's** own format parameter | the loader package's docs; verify its default there (`integration-examples.md`) |

## Capabilities are not all values of one format option

A durable distinction that survives renames: **the choice of output file kind is
one thing; modifiers that change how a kind is rendered are usually separate
options.** Image handling (inlined / external / dropped), rich-table-in-markup, and
per-page separators are typically their own options, not values of the format
selector — and some values that once lived on the format selector migrate to
dedicated options over releases (old spellings may linger as deprecated aliases
that emit a warning). Read the current `--help` to see which capability is a format
*value* and which is a separate option; don't assume a value you remember is still
one.

## Producing several formats at once

The tool can usually emit multiple output kinds in a single pass — parsing the PDF
once and keeping the outputs consistent. Find the multi-value form in `--help`. But
see the streaming hazard next.

## Streaming to stdout — a hazard, not just a convenience

Streaming output to stdout is handy for piping, but two silent traps apply (SKILL.md
"Silent-failure hazards"; `option-interactions.md` §A.3):

- Some output kinds (typically the structured / markup-heavy ones) **never stream**
  — you get empty stdout on a zero exit. Write those to a file and read the file.
- A stdout stream carries **at most one** text-like kind; request several and the
  rest are silently dropped.

Also suppress the tool's own log lines (find the quiet option in `--help`) so they
don't pollute the stream, and VERIFY the pipe actually carried non-empty, parseable
content.

---

**Cross-references:** SKILL.md "Representative workflow", "VERIFY", "Silent-failure
hazards"; `option-interactions.md` (§B.8 image size trade, §A.3 stdout trap);
`integration-examples.md` (citation-carrying RAG handoff); `eval-metrics.md`
(annotated-output debugging).
