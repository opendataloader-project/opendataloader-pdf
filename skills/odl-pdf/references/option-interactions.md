# Interaction & silent-hazard principles

The fuller catalog behind SKILL.md's five headline hazards. Every entry is a
durable, **capability-level principle** — it never names an option, value, or
default, because those change between releases; discover the current names from
the installed `--help` (SKILL.md "Source-of-truth rule"). These are the effects
that return a *false success*: a clean exit while what the user asked for was
quietly dropped. `--help` may name the underlying mechanism — some of these
precedences are even spelled out in an option's own help text — but it never
names the silent-failure *consequence*, and a casual probe often looks fine
because the trap succeeds silently. So VERIFY the specific consequence each
principle names, regardless of what help says.

**How to use:** match the user's intent to the relevant principle *before* you
run; then find the current option expressing that capability in `--help`; then
VERIFY the specific thing the principle says could be silently missing.

## ToC
- A. Silent-failure hazards (A.1 enrichment routing · A.2 fallback hides quality ·
  A.3 structured output doesn't stream · A.4 tagged path pre-empts backend ·
  A.5 crash before page handling)
- B. Quieter cross-option trade-offs (B.6 struct-tree no-op · B.7 sanitize fidelity
  cost · B.8 inline vs. external images · B.9 borderless-table throughput)
- Applying a principle

## A. Silent-failure hazards (false success)

### A.1 Enrichment is silently skipped unless the whole document is routed to the backend
(routing / enrichment interplay.) In a mixed / per-page routing mode, pages the
tool judges "simple" stay on the local path and never reach the AI backend, so
any enrichment requested for those pages quietly does not happen — no error, clean
exit. Requesting an enrichment is necessary but not sufficient; you must also route
the *whole* document to the backend. **VERIFY** the enriched content (formula
markup, figure descriptions) actually appears in the output, not merely that a
file was produced. See `hybrid-guide.md` for the routing/enrichment mechanics.

### A.2 A fallback can preserve completion while dropping requested quality
If the backend errors and a fallback to the local path is in effect, the run still
produces an output file and exits zero — but the OCR or enrichment you required did
**not** occur. Completion is preserved; quality is not. When OCR/enrichment is
mandatory, do not rely on the exit code or the file's existence; VERIFY the
required content is present, or fail closed.

### A.3 Some structured outputs never stream to stdout
Certain output kinds are only ever written to files. Asking to stream one yields
**empty stdout on a zero exit** — and a zero exit with an empty pipe is not
success. Route such an output through a file and read the file (or parse the file
and pipe the parsed *result*). A related trap: a stream that carries "at most one"
text-like output will **silently drop the rest** if you request several at once —
expect exactly one, and confirm what actually arrived.

### A.4 A structure-tagged input path can pre-empt the backend
When the source already carries a usable structure tree and you *also* request the
backend, the tool may honor the existing structure and **not call the backend**
(often with only a warning). Only one of the two runs. The installed `--help`
documents this precedence — it is named in the descriptions of both the
structure-tree and the backend options — but help documenting the precedence does
not tell you which path *actually ran* on your document. Decide which you want —
author-intended structure from the tag tree, or backend processing — do not force
both at once, and VERIFY which one actually ran.

### A.5 A parser/preprocessing crash happens before page-level handling
A malformed font or a parse failure aborts *before* any page-level mode, page
selection, or OCR decision is reached. Those options operate at a later stage the
run never gets to, so switching mode, selecting pages, or enabling OCR **cannot
bypass** the failure. Treat it as a file-specific upstream defect: report the file
(and any captured stack) to the maintainers; as a workaround, repair/flatten or
rasterize the file with another tool and re-run. For a single (non-batch) file this
yields zero output — report it honestly rather than cycling other modes.

## B. Quieter cross-option interactions (quality / size trade-offs, not hard failures)

### B.6 A structure-tree option is silently ignored on an untagged source
The option that reads semantic order from the PDF's tag tree only does something
when a tag tree exists. On an untagged PDF it is quietly ignored and default layout
analysis runs instead — no error, and you may wrongly credit it for the result.
Confirm the PDF is actually tagged (inspect its document properties / run a
preflight) before attributing any improvement to this path. On a *tagged* source
this same option can pre-empt the backend — hazard A.4.

### B.7 A content-safety / sanitize capability trades fidelity for privacy
A capability that redacts sensitive data (emails, phone numbers, URLs, and the
like) will also drop legitimate content that happens to match — citation URLs, for
instance — reducing fidelity. Enable it only when the input may carry data that
must not flow downstream, and treat it as a fidelity cost, not free. Never disable
a safety filter merely to "get more content," especially on untrusted input — that
re-exposes the hidden-text / injection vectors the filter removes (SKILL.md "Where
the human decides").

### B.8 Inlining images vs. writing them externally is a size / portability trade
A mode that embeds each image inline (as a data URI) makes the output
self-contained but can balloon its size on image-heavy documents; a mode that
writes images to a directory keeps the output small but non-self-contained. Choose
by whether the consumer needs one portable file or many small ones — neither is a
failure, but the inline path can surprise a downstream store with huge documents.

### B.9 A borderless-table capability costs throughput
A stronger table-detection capability that finds borderless tables adds processing
time over the default bordered-table path. Escalate to it when recall on borderless
tables matters; prefer the cheaper default when throughput matters and the tables
are conventionally bordered. (Escalation order for weak tables: default → borderless
detection → backend → whole-document backend routing; one change at a time — SKILL.md
"DIAGNOSE by symptom".)

## Applying a principle

1. Identify which principle the user's intent touches.
2. Find the current option(s) expressing the relevant capability in the installed
   `--help`.
3. Run the minimal command.
4. VERIFY the specific content the principle warns could be silently missing — not
   just that the command exited zero (SKILL.md "VERIFY").

---

**Cross-references:** SKILL.md "Silent-failure hazards", "VERIFY", "DIAGNOSE by
symptom", "Where the human decides"; `hybrid-guide.md` (backend setup +
OCR-language hazard); `format-guide.md` (which output capability fits which use);
`eval-metrics.md` (judging a weak extraction).
