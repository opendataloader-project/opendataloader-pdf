# Judging extraction quality — metrics & procedure

How to tell whether an extraction is good, which measure is weak, and what to do
about it — expressed as durable methodology. The metric *definitions* are stable;
the options you would reach for to improve a weak metric are named only as
**capabilities** here (find the current option in the installed `--help` — SKILL.md
"Source-of-truth rule"). No benchmark runner is bundled with this skill.

## ToC
- What to measure (metric definitions)
- Judging procedure
- Which measure is weak → what to try
- Quick self-check (bundled script)

## What to measure (metric definitions)

**NID — Normalized Indel similarity** (`1 − normalized Indel distance`). *Reading
order accuracy*: how well the extracted text preserves the correct reading
sequence. A two-column page must interleave text in the right column order, not
left-to-right across both columns. Range 0–1, higher is better. Weak on
multi-column layouts, merged-cell tables, inline footnotes, sidebars.

**TEDS — Tree-Edit Distance Similarity.** *Table structure accuracy*: structural
similarity between the extracted table tree and ground truth via tree edit
distance, normalized by tree size. Range 0–1, higher is better. Weak on borderless
tables, merged/spanning cells, nested tables, and tables that are actually images.

**MHS — Markdown Heading Similarity.** *Heading structure accuracy*: how well the
extracted heading hierarchy matches ground truth, penalizing both missing headings
and wrong level assignments. Range 0–1, higher is better. Weak when headings are
simulated with bold/large text (no semantic markup) or embedded in images.

**Table Detection F1.** *Table region detection*: harmonic mean of precision
(detected regions that are real tables) and recall (real tables that were
detected). Unlike TEDS it does not judge internal structure — only whether the
region was found. Range 0–1, higher is better. Weak on dense text blocks that
resemble tables, tables spanning page boundaries, very small tables.

**Speed.** *Throughput* in seconds per page; lower is better, not normalized to
0–1. Relative shape: local (no backend) fastest; per-page triage close to local for
most pages, paying the backend round-trip only on triaged pages; whole-document
backend routing slowest. Absolute figures depend on hardware and document set —
measure on your own corpus.

Reporting note: an "Overall" is often the average of NID / TEDS / MHS; Table
Detection F1 is reported per-document and not folded into that average. Hardcoded
snapshot scores drift whenever extraction code or the document set changes, so they
are intentionally not reproduced here.

## Judging procedure

1. Decide what "good" means for *this* goal before measuring — reading order (NID),
   table structure (TEDS), heading hierarchy (MHS), table region detection (F1),
   throughput (Speed). You rarely need all of them.
2. Inspect the actual output against that dimension; don't infer quality from a
   zero exit (SKILL.md "VERIFY").
3. If a dimension is weak, escalate **one capability at a time** (below), re-run,
   re-measure — one change per step so cause and effect stay legible.
4. For a numeric gut-check against a reference file, use the bundled quick
   self-check (below) — but know it measures only text similarity, not structure.

## Which measure is weak → what to try

Escalate least-invasive first, verify after each single change (SKILL.md "DIAGNOSE
by symptom").

**Low NID (reading order)** — columns/sections interleaved wrong, paragraphs out of
sequence, footnotes misplaced.
1. If the PDF is **tagged**, try the capability that reads reading order from the
   tag tree (confirm it is tagged first — on an untagged PDF that option is silently
   ignored, `option-interactions.md` §B.6).
2. Confirm the layout reading-order strategy is active (usually the default).
3. For layouts that still fail, route the document through the backend — it handles
   unusual layouts more robustly (`hybrid-guide.md`).

**Low TEDS (table structure)** — tables as plain text, merged cells wrong,
borderless tables missed.
1. Enable the borderless-table detection capability (costs throughput —
   `option-interactions.md` §B.9).
2. If insufficient, route to the backend (per-page triage first).
3. If triage still misses tables (classified simple), route the whole document to
   the backend.

**Low MHS (heading structure)** — headings unrecognized or at the wrong level.
1. Determine whether headings are **semantic** (in the tag tree) or merely visual
   (bold/large text). Inspect the tag tree in a reader/preflight; no tag tree ⇒
   visual only.
2. If tagged, try the tag-tree reading capability.
3. If untagged and headings are visual only, layout alone cannot recover the
   hierarchy reliably — consider whether the backend improves it for your document
   class.

**Low Table Detection F1 (table regions)** — tables missed (low recall) or
non-tables flagged (low precision).
1. Produce an **annotated output** (boxes over a copy of the input) alongside the
   structured data, and correlate each box with its element to see what is detected
   vs. missed (`format-guide.md`).
2. Low recall → enable borderless detection, then escalate to the backend (same
   ladder as Low TEDS).
3. Low precision (dense text read as tables) → confirm the column/reading-order
   strategy is active so column structure is recognized before table detection runs.

## Quick self-check (bundled script)

`scripts/quick-eval.py` gives a **rough text-similarity** score against a reference
file — it does **not** measure table structure, cell spans, reading order (NID), or
detection precision/recall. Use it as a smoke check, not a substitute for the
metrics above.

```bash
python scripts/quick-eval.py extracted.md ground-truth.md
python scripts/quick-eval.py extracted.md ground-truth.md --verbose   # diff snippets
```

It compares text similarity (difflib, or rapidfuzz if available) and reports
pass/fail against a configurable threshold. For full multi-metric benchmarking
across a corpus, apply the definitions above to whatever benchmark harness your
project maintains — none is bundled here.

---

**Cross-references:** SKILL.md "VERIFY", "DIAGNOSE by symptom";
`option-interactions.md` (§B.6 struct-tree no-op, §B.9 borderless cost);
`hybrid-guide.md`; `format-guide.md`; `scripts/quick-eval.py`.
