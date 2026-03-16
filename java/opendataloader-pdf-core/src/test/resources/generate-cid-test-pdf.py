#!/usr/bin/env python3
"""
Generate a minimal PDF with a Type0 (CID) font that has NO ToUnicode CMap.

When parsed by veraPDF, this PDF will emit U+FFFD (replacement character) for
the majority of text characters because there is no ToUnicode mapping to convert
CID values back to Unicode code points.

Strategy:
  Use reportlab to create a valid PDF with an embedded TTF font (as CID/Type0),
  then post-process the raw PDF bytes to remove /ToUnicode references.
  This ensures the font has proper metrics (widths, bounding boxes) so veraPDF
  produces TextChunks with valid geometry, while still triggering U+FFFD output.

Usage:
    python3 generate-cid-test-pdf.py [output.pdf]

Output defaults to cid-font-no-tounicode.pdf in the same directory.

Requirements:
    pip install reportlab
"""
import os
import re
import sys


def find_system_cjk_font():
    """Find a CJK TrueType font on the system."""
    candidates = [
        # macOS
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/Library/Fonts/Arial Unicode.ttf",
        "/System/Library/Fonts/STHeiti Light.ttc",
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/Hiragino Sans GB.ttc",
        # Linux
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return path
    return None


def find_any_ttf_font():
    """Find any TrueType font on the system."""
    candidates = [
        # macOS
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/Times.ttc",
        "/System/Library/Fonts/Supplemental/Times New Roman.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        # Linux
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return path
    return None


def generate_with_ttf(tmp_path, font_path):
    """Generate a PDF with embedded TTF as CID/Type0 font."""
    from reportlab.lib.pagesizes import letter
    from reportlab.pdfgen import canvas
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont

    font_name = 'TestCIDFont'
    pdfmetrics.registerFont(TTFont(font_name, font_path))

    c = canvas.Canvas(tmp_path, pagesize=letter)
    c.setFont(font_name, 14)

    # Write text that will become unmappable once ToUnicode is stripped
    # Use a mix of ASCII and extended chars to have enough character volume
    text = "The quick brown fox jumps over the lazy dog 0123456789"
    c.drawString(72, 700, text)
    c.drawString(72, 680, text)
    c.save()


def strip_tounicode(input_path, output_path):
    """Remove /ToUnicode references from a PDF file.

    This makes the CID font's character codes unmappable to Unicode,
    causing veraPDF to emit U+FFFD for each character.

    We replace the /ToUnicode key+value with spaces to preserve byte offsets,
    avoiding the need to rewrite the xref table.
    """
    with open(input_path, 'rb') as f:
        data = f.read()

    original_count = data.count(b'/ToUnicode')

    # Remove /ToUnicode references: /ToUnicode N 0 R
    modified = re.sub(
        rb'/ToUnicode\s+\d+\s+\d+\s+R',
        lambda m: b' ' * len(m.group()),
        data
    )

    remaining_count = modified.count(b'/ToUnicode')
    removed = original_count - remaining_count

    with open(output_path, 'wb') as f:
        f.write(modified)

    return removed


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_output = os.path.join(script_dir, "cid-font-no-tounicode.pdf")
    output_path = sys.argv[1] if len(sys.argv) > 1 else default_output
    tmp_path = output_path + ".tmp"

    try:
        # Find a suitable font
        font_path = find_any_ttf_font()
        if font_path is None:
            font_path = find_system_cjk_font()
        if font_path is None:
            print("ERROR: No suitable TrueType font found on system", file=sys.stderr)
            sys.exit(1)

        print(f"Using font: {font_path}")
        print("Step 1: Generating PDF with embedded TTF as CID/Type0 font...")
        generate_with_ttf(tmp_path, font_path)
        print(f"  Created temp PDF: {tmp_path} ({os.path.getsize(tmp_path)} bytes)")

        print("Step 2: Stripping /ToUnicode references...")
        removed = strip_tounicode(tmp_path, output_path)
        print(f"  Removed {removed} /ToUnicode reference(s)")

        if removed == 0:
            print("WARNING: No /ToUnicode references were found.", file=sys.stderr)
            print("The generated PDF may not trigger U+FFFD in veraPDF.", file=sys.stderr)

        size = os.path.getsize(output_path)
        print(f"Generated: {output_path} ({size} bytes)")
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


if __name__ == "__main__":
    main()
