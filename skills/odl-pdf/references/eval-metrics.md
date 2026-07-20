# Evaluation Metrics Reference

This document explains the metrics used in opendataloader-pdf benchmarks, how to interpret them, and how to diagnose quality problems using them.

---

## Metrics

### NID — Normalized Indel similarity (project reports `1 − normalized Indel distance`)

**What it measures:** Reading order accuracy. Quantifies how well the extracted text preserves the correct reading sequence compared to the ground truth.

**Intuition:** A PDF with two side-by-side columns must interleave text in the right column order, not left-to-right line by line across both columns. NID penalizes any reordering of the logical reading sequence.

**Range:** 0–1. Higher is better. A score of 1.0 means extracted order exactly matches ground truth.

**Typical failure modes:** Multi-column layouts, tables with merged cells, footnotes that appear inline, sidebars.

---

### TEDS — Tree-Edit Distance Similarity

**What it measures:** Table structure accuracy. Measures the structural similarity between extracted table trees and ground-truth table trees using tree edit distance.

**Intuition:** A table with 3 rows and 4 columns must be reconstructed with the correct cell boundaries, spanning cells, and hierarchy. TEDS counts the minimum number of insertions, deletions, and substitutions needed to convert the extracted tree into the ground truth, then normalizes by tree size.

**Range:** 0–1. Higher is better. A score of 1.0 means the extracted table structure is identical to ground truth.

**Typical failure modes:** Borderless tables, merged/spanning cells, nested tables, tables that are actually images.

---

### MHS — Markdown Heading Similarity

**What it measures:** Heading structure accuracy. Measures how well the extracted heading hierarchy (h1, h2, h3) matches the ground truth.

**Intuition:** A document with a clear section/subsection structure should produce headings at the correct levels. MHS compares the heading tree of the extracted output against the ground truth, penalizing both missing headings and incorrect level assignments.

**Range:** 0–1. Higher is better. A score of 1.0 means all headings are correctly detected and assigned to the right level.

**Typical failure modes:** PDFs that simulate headings using bold text (no semantic markup), decorative section dividers, heading text embedded in images.

---

### Table Detection F1

**What it measures:** Precision and recall of table boundary detection. Precision = fraction of detected tables that are real tables. Recall = fraction of real tables that were detected.

**Intuition:** F1 is the harmonic mean of precision and recall, balancing false positives (detecting non-tables as tables) against false negatives (missing tables entirely). Unlike TEDS, Table Detection F1 does not evaluate the internal structure — only whether the table region was found.

**Range:** 0–1. Higher is better.

**Typical failure modes:** Dense text blocks that resemble tables, tables that span page boundaries, very small tables.

---

### Speed

**What it measures:** Processing throughput in seconds per page.

**Interpretation:** Lower is better. Relative shape:

- **Local (no hybrid)**: fastest — pure Java layout analysis
- **Hybrid `auto`**: varies with document complexity; most pages stay at Java speed, only triaged pages pay the backend round-trip
- **Hybrid `full`**: slowest — every page goes to the backend

Speed is not normalized to 0–1. It is an absolute wall-clock measurement averaged over a document set. Measure on your own corpus — published figures can lag the latest code and depend heavily on hardware.

---

## Benchmark Reference Scores

These metrics are the ones reported by the project's benchmark methodology. Their per-metric shape:

- **Overall** — the average of NID / TEDS / MHS
- **NID** / **TEDS** / **MHS** / **Table Detection F1** — 0–1 scale, higher is better
- **Speed** — absolute seconds per page

Table Detection F1 is reported per-document and is not folded into the Overall average.

Hardcoded snapshot scores are intentionally not reproduced here — they drift whenever extraction code or the document set changes. No benchmark runner is bundled with this skill. For your own quality checks against a reference file, use the bundled `scripts/quick-eval.py` (see below).

---

## Diagnostic Guide: Which Metric Is Weak?

Use this guide when extraction quality is below expectations. Start by identifying which metric is low, then follow the recommended steps.

---

### Low NID — Reading Order Problems

**Symptoms:** Text from different columns or sections is interleaved incorrectly. Paragraphs appear out of sequence. Footnotes appear in the wrong position.

**Steps:**

1. Check if the PDF is tagged. If it is, try `--use-struct-tree`. Tagged PDFs contain an explicit reading order tree that is usually more reliable than layout analysis.

   ```bash
   opendataloader-pdf input.pdf --use-struct-tree
   ```

2. For multi-column layouts, verify that the XY-Cut algorithm is active (it is the default). Ensure `--reading-order xycut` is set.

3. For complex layouts where XY-Cut still fails, route the document through hybrid mode — the AI backend handles unusual layouts more robustly.

   ```bash
   opendataloader-pdf --hybrid docling-fast input.pdf
   ```

---

### Low TEDS — Table Quality Problems

**Symptoms:** Tables are extracted as plain text. Cells are merged incorrectly. Columns are misaligned. Borderless tables are missed entirely.

**Escalation path — try each step in order and stop when quality is acceptable:**

1. **Enable cluster detection.** The default table method detects bordered tables. The `cluster` method adds borderless table detection.

   ```bash
   opendataloader-pdf input.pdf --table-method cluster
   ```

2. **Switch to hybrid mode.** If `cluster` is insufficient, route the document through the AI backend. Use `auto` mode first — it sends complex pages to the backend while keeping simple pages on the fast local path.

   ```bash
   opendataloader-pdf --hybrid docling-fast input.pdf
   ```

3. **Use hybrid full mode.** If `auto` mode still misses tables (because the triage step classifies them as simple), force all pages through the backend.

   ```bash
   opendataloader-pdf --hybrid docling-fast --hybrid-mode full input.pdf
   ```

---

### Low MHS — Heading Detection Problems

**Symptoms:** Document headings are not recognized, appear as plain paragraphs, or are assigned to the wrong level (e.g., h1 instead of h2).

**Steps:**

1. Check whether the PDF uses real headings or simulated headings. Real headings are marked semantically in the PDF (large font, bold, specific style). Simulated headings are visually similar but have no semantic markup — they are just bold text at a larger font size, indistinguishable from the tool's perspective.

   - To check: open the PDF in a reader that exposes the tag tree (Adobe Acrobat > Accessibility > Reading Order, or use a preflight tool). If there is no tag tree, the headings are visual only.

2. If the PDF is tagged and headings are still missed, try `--use-struct-tree`. This reads semantic structure directly from the PDF's tag tree.

   ```bash
   opendataloader-pdf input.pdf --use-struct-tree
   ```

3. If the PDF is untagged and headings are simulated with bold text, the heading structure cannot be recovered reliably from layout alone. Consider whether hybrid mode improves detection for your specific document class.

---

### Low Table Detection F1 — Table Region Problems

**Symptoms:** Tables are missed entirely (low recall) or non-table regions such as dense text blocks are incorrectly flagged as tables (low precision).

**Steps:**

1. **Inspect with an annotated PDF** to see which regions are being detected as tables and which real tables are being missed. The `pdf` output format overlays bounding boxes on a copy of the input.

   ```bash
   opendataloader-pdf input.pdf --format json,pdf
   ```

   Combine with `json` so you can correlate each visual box with its element data.

2. **If real tables are being missed (low recall):** enable borderless detection and, if needed, escalate to the hybrid backend. See the Low TEDS steps above — the same escalation path (`--table-method cluster` → `--hybrid docling-fast` → `--hybrid-mode full`) improves region detection as well as internal structure.

3. **If non-table regions are being detected (low precision):** this usually indicates dense multi-column text is being classified as tabular. Check that `--reading-order xycut` is active (it is the default) so column structure is recognized before table detection runs.

---

## Checking Quality

### Quick eval on your own documents

The bundled `scripts/quick-eval.py` gives a **rough normalized text-similarity** check against a reference file — it does **not** measure table structure, cell spans, reading-order (NID), or table-detection precision/recall. Use it as a quick smoke check, not a substitute for NID/TEDS/MHS/Table-F1:

```bash
python scripts/quick-eval.py extracted.md ground-truth.md
```

This script compares an extracted file against a ground truth reference using text similarity (difflib by default, rapidfuzz if available). It reports a similarity score with pass/fail against a configurable threshold (default 0.85). Use `--verbose` for diff snippets.

For full multi-metric benchmarking (NID / TEDS / MHS / Table Detection F1) across a corpus, use the metric definitions above to interpret whatever benchmark harness your project maintains — no such harness is bundled with this skill.
