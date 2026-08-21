#!/usr/bin/env python3
"""Summarize a DLA corpus scan: which visual/formula labels the model emits,
and where its supposedly-exclusive visual classes overlap.

Consumes the raw responses written by scan.sh and answers the questions unit
tests cannot: how often labels 18/19 actually appear (the reach of the figure
subdivision work), how often one graphic is reported twice, and how many
regions would go uncaptioned if captioning trusted the classifier.

Usage: report.py [scan-out-dir]
"""
import collections
import json
import pathlib
import sys

from response_complete import validated_pages

# The three classes the model split its original "figure" class into.
VISUAL = {10: "Figure", 18: "Chart", 19: "Image"}
LABEL_EQUATION = 12

# Matches the de-duplication threshold in HancomAISchemaTransformer.
DUPLICATE_IOU = 0.8


def iou(a, b):
    # Response data, so coordinates are not guaranteed numeric.
    try:
        a = [float(v) for v in a[:4]]
        b = [float(v) for v in b[:4]]
    except (TypeError, ValueError):
        return 0.0
    left, top = max(a[0], b[0]), max(a[1], b[1])
    right, bottom = min(a[2], b[2]), min(a[3], b[3])
    if right <= left or bottom <= top:
        return 0.0
    inter = (right - left) * (bottom - top)
    area_a = (a[2] - a[0]) * (a[3] - a[1])
    area_b = (b[2] - b[0]) * (b[3] - b[1])
    union = area_a + area_b - inter
    return inter / union if union > 0 else 0.0


def _num(value, default=-1.0):
    """Confidence as a float; response data is not guaranteed numeric."""
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def load_pages(path):
    """Yields the pages of one scan response, or nothing if it is unusable.

    Shares its usability rule with scan.sh via response_complete, so a
    response the scan refused to cache is not counted here either. A response
    with any failed page yields nothing at all rather than its successful
    pages: counting part of a document as though it were whole would understate
    the label distribution without saying so.
    """
    try:
        raw = path.read_text()
    except OSError:
        return
    if not raw.lstrip().startswith("{"):
        return  # an HTML error page from the gateway
    try:
        doc = json.loads(raw)
    except json.JSONDecodeError:
        return
    pages = validated_pages(doc)
    if pages is None:
        return
    yield from pages


def main():
    out_dir = pathlib.Path(sys.argv[1] if len(sys.argv) > 1
                           else pathlib.Path(__file__).parent / "out")
    raw_dir = out_dir / "raw"
    if not raw_dir.is_dir():
        sys.exit(f"no scan output at {raw_dir} — run scan.sh first")

    files = sorted(raw_dir.glob("*.json"))
    label_counts = collections.Counter()
    visual_counts = collections.Counter()
    docs_with_visual = 0
    docs_with_equation = 0
    usable = 0
    unusable = []
    overlaps = []
    combos = collections.Counter()

    for path in files:
        pages = list(load_pages(path))
        if not pages:
            unusable.append(path.stem)
            continue
        usable += 1

        doc_visuals = []
        has_equation = False
        for page in pages:
            objects = page.get("objects") or []
            if not isinstance(objects, list):
                objects = []
            for obj in objects:
                if not isinstance(obj, dict):
                    continue
                label = obj.get("label", -1)
                if not isinstance(label, int):
                    continue
                label_counts[label] += 1
                if label in VISUAL:
                    visual_counts[label] += 1
                    if isinstance(obj.get("bbox"), list) and len(obj["bbox"]) >= 4:
                        doc_visuals.append(obj)
                elif label == LABEL_EQUATION:
                    has_equation = True

            # Overlaps are only meaningful within a single page.
            page_visuals = [o for o in objects
                            if o.get("label") in VISUAL
                            and isinstance(o.get("bbox"), list) and len(o["bbox"]) >= 4]
            for i in range(len(page_visuals)):
                for j in range(i + 1, len(page_visuals)):
                    a, b = page_visuals[i], page_visuals[j]
                    score = iou(a["bbox"], b["bbox"])
                    if score >= DUPLICATE_IOU:
                        # Page number included so the same duplicate appearing on
                        # several pages does not read as several findings.
                        overlaps.append((path.stem, page.get("page_number", 0),
                                         VISUAL[a["label"]], _num(a.get("confidence")),
                                         VISUAL[b["label"]], _num(b.get("confidence")),
                                         score))

        if doc_visuals:
            docs_with_visual += 1
            combos["+".join(sorted({VISUAL[o["label"]] for o in doc_visuals}))] += 1
        if has_equation:
            docs_with_equation += 1

    lines = []
    add = lines.append
    add("# DLA corpus label report")
    add("")
    add(f"- documents scanned: {len(files)}")
    add(f"- usable responses: {usable}")
    if unusable:
        add(f"- unusable (server error / not scanned): {len(unusable)}")
    add(f"- documents with at least one visual object: {docs_with_visual}")
    add(f"- documents with at least one equation: {docs_with_equation}")
    add("")

    add("## Visual objects by class")
    add("")
    add("| label | class | count |")
    add("|---|---|---|")
    for label in sorted(VISUAL):
        add(f"| {label} | {VISUAL[label]} | {visual_counts[label]} |")
    add("")
    subdivided = visual_counts[18] + visual_counts[19]
    total_visual = sum(visual_counts.values())
    if total_visual:
        share = 100.0 * subdivided / total_visual
        add(f"Chart + Image account for {subdivided} of {total_visual} visual objects "
            f"({share:.0f}%). Those are the regions that were dropped entirely before "
            "the transformer learned labels 18 and 19.")
    add("")

    add("## Class combinations per document")
    add("")
    for combo, count in combos.most_common():
        add(f"- `{combo}`: {count}")
    add("")

    add("## Overlapping visual detections")
    add("")
    if not overlaps:
        add(f"None at IoU >= {DUPLICATE_IOU}.")
    else:
        add(f"One graphic reported under two classes, at IoU >= {DUPLICATE_IOU}. "
            "De-duplication keeps the more confident detection.")
        add("")
        add("| document / page | detection A | detection B | IoU |")
        add("|---|---|---|---|")
        for doc, page_no, la, ca, lb, cb, score in sorted(overlaps, key=lambda r: -r[6]):
            add(f"| {doc} p{page_no} | {la} ({ca:.3f}) | {lb} ({cb:.3f}) | {score:.3f} |")
        add("")
        margins = [abs(r[3] - r[5]) for r in overlaps]
        add(f"Confidence gap within a duplicate pair: "
            f"{min(margins):.3f}-{max(margins):.3f}. A wide, consistent gap is what "
            "makes confidence a usable tie-breaker; the winning *label* varies.")
    add("")

    add("## All labels seen")
    add("")
    add("| label | count |")
    add("|---|---|")
    for label in sorted(label_counts):
        add(f"| {label} | {label_counts[label]} |")
    add("")

    report = out_dir / "label-report.md"
    report.write_text("\n".join(lines) + "\n")
    print("\n".join(lines))
    print(f"\nwritten: {report}", file=sys.stderr)


if __name__ == "__main__":
    main()
