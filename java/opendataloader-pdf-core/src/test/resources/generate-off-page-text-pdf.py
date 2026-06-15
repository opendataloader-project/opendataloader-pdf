#!/usr/bin/env python3
"""
Generate a minimal PDF with one on-page text run and one off-page text run.

The page MediaBox is [0 0 612 792]. One text string is drawn at y=700 (inside
the page) and another at y=900 (above the page top, i.e. outside the crop box and
therefore not visible to a viewer). A correct extractor must drop the off-page run.

NOTE: "off-page" here means positioned outside the crop box. This is unrelated to
the contrast-based hidden-text filter (HiddenTextProcessor / --content-safety-off
hidden-text); removal here is the job of the off-page filter only.

This is the fixture for OffPageTextFilterIntegrationTest, which guards the
ForkJoinPool ThreadLocal regression where filterOutOfPageContents silently
no-opped on worker threads (page bounds were unavailable off the main thread).

Uses the standard /Helvetica font, so no font embedding / external deps.

Usage:
    python3 generate-off-page-text-pdf.py [output.pdf]
Output defaults to off-page-text.pdf in the same directory.
"""
import os
import sys

ON_PAGE_TEXT = "ON_PAGE_TEXT"
OFF_PAGE_TEXT = "OFF_PAGE_TEXT"


def build_pdf(output_path):
    """Write a single-page PDF with one on-page and one off-page text run.

    Returns the size in bytes of the written file.
    """
    content_stream = (
        "BT\n"
        "/F1 14 Tf\n"
        f"72 700 Td\n({ON_PAGE_TEXT}) Tj\n"
        "ET\n"
        "BT\n"
        "/F1 14 Tf\n"
        f"72 900 Td\n({OFF_PAGE_TEXT}) Tj\n"
        "ET\n"
    ).encode()

    catalog_id, pages_id, page_id, contents_id, font_id = 1, 2, 3, 4, 5
    objects = {}
    objects[catalog_id] = f"<< /Type /Catalog /Pages {pages_id} 0 R >>".encode()
    objects[pages_id] = f"<< /Type /Pages /Kids [{page_id} 0 R] /Count 1 >>".encode()
    objects[page_id] = (
        f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 612 792] "
        f"/Contents {contents_id} 0 R "
        f"/Resources << /Font << /F1 {font_id} 0 R >> >> >>"
    ).encode()
    objects[font_id] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

    pdf = b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
    offsets = {}
    for obj_id in sorted(objects.keys()):
        offsets[obj_id] = len(pdf)
        pdf += f"{obj_id} 0 obj\n".encode() + objects[obj_id] + b"\nendobj\n"

    offsets[contents_id] = len(pdf)
    pdf += f"{contents_id} 0 obj\n".encode()
    pdf += f"<< /Length {len(content_stream)} >>\n".encode()
    pdf += b"stream\n" + content_stream + b"\nendstream\nendobj\n"

    xref_offset = len(pdf)
    max_obj = max(offsets.keys())
    pdf += b"xref\n" + f"0 {max_obj + 1}\n".encode() + b"0000000000 65535 f \n"
    for i in range(1, max_obj + 1):
        pdf += f"{offsets[i]:010d} 00000 n \n".encode()
    pdf += b"trailer\n"
    pdf += f"<< /Size {max_obj + 1} /Root {catalog_id} 0 R >>\n".encode()
    pdf += b"startxref\n" + f"{xref_offset}\n".encode() + b"%%EOF\n"

    with open(output_path, "wb") as f:
        f.write(pdf)
    return len(pdf)


def main():
    """Generate the fixture PDF at the default (or given) path and print a summary."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_output = os.path.join(script_dir, "off-page-text.pdf")
    output_path = sys.argv[1] if len(sys.argv) > 1 else default_output
    size = build_pdf(output_path)
    print(f"Generated: {output_path} ({size} bytes)")
    print(f"  on-page  text @ y=700: {ON_PAGE_TEXT!r}")
    print(f"  off-page text @ y=900: {OFF_PAGE_TEXT!r} (MediaBox top = 792)")


if __name__ == "__main__":
    main()
