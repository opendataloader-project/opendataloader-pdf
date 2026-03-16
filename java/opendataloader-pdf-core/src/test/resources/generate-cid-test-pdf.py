#!/usr/bin/env python3
"""
Generate a minimal PDF with a Type0 (CID) font that has NO ToUnicode CMap.

When parsed by veraPDF, this PDF will emit U+FFFD (replacement character) for
text characters because there is no ToUnicode mapping and the encoding is
Identity-H (raw CID values with no inherent Unicode mapping).

Strategy:
  Use reportlab to generate a PDF with an embedded TTF font as a Type0/CID font,
  then post-process the raw bytes to:
  1. Change the encoding to /Identity-H (removing any Unicode-based CMap)
  2. Strip any /ToUnicode references
  This ensures proper font metrics (widths, bounding boxes) while preventing
  Unicode mapping.

Usage:
    python3 generate-cid-test-pdf.py [output.pdf]

Output defaults to cid-font-no-tounicode.pdf in the same directory.

Requirements:
    pip install reportlab
"""
import os
import re
import struct
import sys


def find_ttf_font():
    """Find a TrueType font on the system."""
    candidates = [
        "/System/Library/Fonts/Supplemental/Times New Roman.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return path
    return None


def read_ttf_tables(font_path):
    """Read TrueType font and extract key tables for embedding."""
    with open(font_path, 'rb') as f:
        data = f.read()
    return data


def build_pdf_with_real_font(output_path, font_path):
    """Build a PDF with Type0/CID font using a real TTF font program.

    The font is embedded as CIDFontType2 with Identity-H encoding and
    no ToUnicode CMap, so veraPDF cannot map CID values to Unicode.
    """
    font_data = read_ttf_tables(font_path)

    # Parse the TTF to get unitsPerEm and some metrics
    # Read offset table
    sfVersion, numTables = struct.unpack('>IH', font_data[0:6])

    tables = {}
    for i in range(numTables):
        offset = 12 + i * 16
        tag = font_data[offset:offset+4].decode('ascii', errors='replace')
        checksum, tbl_offset, tbl_length = struct.unpack('>III', font_data[offset+4:offset+16])
        tables[tag] = (tbl_offset, tbl_length)

    # Read head table for unitsPerEm
    if 'head' in tables:
        ho, hl = tables['head']
        head_data = font_data[ho:ho+hl]
        units_per_em = struct.unpack('>H', head_data[18:20])[0]
        x_min, y_min, x_max, y_max = struct.unpack('>hhhh', head_data[36:44])
    else:
        units_per_em = 1000
        x_min, y_min, x_max, y_max = 0, -200, 1000, 800

    # Read hhea for ascent/descent
    if 'hhea' in tables:
        ho, hl = tables['hhea']
        hhea_data = font_data[ho:ho+hl]
        ascent = struct.unpack('>h', hhea_data[4:6])[0]
        descent = struct.unpack('>h', hhea_data[6:8])[0]
        num_hmtx = struct.unpack('>H', hhea_data[34:36])[0]
    else:
        ascent, descent, num_hmtx = 800, -200, 0

    # Read hmtx for glyph widths
    widths = []
    if 'hmtx' in tables:
        ho, hl = tables['hmtx']
        hmtx_data = font_data[ho:ho+hl]
        for i in range(min(num_hmtx, 256)):
            aw = struct.unpack('>H', hmtx_data[i*4:i*4+2])[0]
            widths.append(aw)

    # Scale factor to convert from font units to 1000-unit space
    scale = 1000.0 / units_per_em

    # Default width (most common)
    default_width = int(widths[0] * scale) if widths else 600

    # Build width array for CIDs we'll use (32-127, ASCII printable range)
    # CID values = glyph IDs in Identity-H encoding
    # We'll use glyph IDs for common ASCII characters
    # In most fonts, glyph IDs for ASCII chars are in a predictable range

    # Read cmap to find glyph IDs for ASCII characters
    glyph_ids = {}
    if 'cmap' in tables:
        co, cl = tables['cmap']
        cmap_data = font_data[co:co+cl]
        num_subtables = struct.unpack('>H', cmap_data[2:4])[0]

        for i in range(num_subtables):
            so = 4 + i * 8
            platform_id, encoding_id, subtable_offset = struct.unpack('>HHI', cmap_data[so:so+8])

            # Prefer Windows Unicode BMP (3,1) or Unicode (0,3)
            if (platform_id == 3 and encoding_id == 1) or (platform_id == 0):
                st_data = cmap_data[subtable_offset:]
                fmt = struct.unpack('>H', st_data[0:2])[0]

                if fmt == 4:
                    seg_count = struct.unpack('>H', st_data[6:8])[0] // 2
                    end_codes = []
                    for j in range(seg_count):
                        end_codes.append(struct.unpack('>H', st_data[14 + j*2:16 + j*2])[0])

                    start_offset = 14 + seg_count * 2 + 2
                    start_codes = []
                    for j in range(seg_count):
                        start_codes.append(struct.unpack('>H', st_data[start_offset + j*2:start_offset + 2 + j*2])[0])

                    delta_offset = start_offset + seg_count * 2
                    deltas = []
                    for j in range(seg_count):
                        deltas.append(struct.unpack('>h', st_data[delta_offset + j*2:delta_offset + 2 + j*2])[0])

                    range_offset_start = delta_offset + seg_count * 2
                    range_offsets = []
                    for j in range(seg_count):
                        range_offsets.append(struct.unpack('>H', st_data[range_offset_start + j*2:range_offset_start + 2 + j*2])[0])

                    for j in range(seg_count):
                        for code in range(start_codes[j], end_codes[j] + 1):
                            if 32 <= code <= 126:
                                if range_offsets[j] == 0:
                                    gid = (code + deltas[j]) & 0xFFFF
                                else:
                                    idx = range_offset_start + j * 2 + range_offsets[j] + (code - start_codes[j]) * 2
                                    gid = struct.unpack('>H', st_data[idx:idx+2])[0]
                                    if gid != 0:
                                        gid = (gid + deltas[j]) & 0xFFFF
                                glyph_ids[code] = gid
                    break

    # Build the text content using glyph IDs (CID values)
    text1 = "The quick brown fox jumps over the lazy dog 0123456789"
    text2 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz"

    def text_to_hex_cids(text):
        hex_str = ""
        for ch in text:
            code = ord(ch)
            gid = glyph_ids.get(code, 0)
            hex_str += f"{gid:04X}"
        return hex_str

    hex1 = text_to_hex_cids(text1)
    hex2 = text_to_hex_cids(text2)

    # Build W array for the glyph IDs we use
    used_gids = set()
    for ch in text1 + text2:
        gid = glyph_ids.get(ord(ch), 0)
        used_gids.add(gid)

    w_entries = []
    for gid in sorted(used_gids):
        if gid < len(widths):
            w = int(widths[gid] * scale)
        else:
            w = default_width
        w_entries.append(f"{gid} [{w}]")
    w_array = " ".join(w_entries)

    # Scale font metrics
    s_ascent = int(ascent * scale)
    s_descent = int(descent * scale)
    s_xmin = int(x_min * scale)
    s_ymin = int(y_min * scale)
    s_xmax = int(x_max * scale)
    s_ymax = int(y_max * scale)

    # Build PDF content stream
    content_stream = f"""BT
/F1 14 Tf
72 700 Td
<{hex1}> Tj
0 -20 Td
<{hex2}> Tj
0 -20 Td
<{hex1}> Tj
0 -20 Td
<{hex2}> Tj
0 -20 Td
<{hex1}> Tj
ET""".encode()

    # Build PDF objects
    catalog_id = 1
    pages_id = 2
    page_id = 3
    contents_id = 4
    type0_font_id = 5
    cid_font_id = 6
    font_descriptor_id = 7
    font_file_id = 8

    objects = {}
    objects[catalog_id] = f"<< /Type /Catalog /Pages {pages_id} 0 R >>".encode()
    objects[pages_id] = f"<< /Type /Pages /Kids [{page_id} 0 R] /Count 1 >>".encode()
    objects[page_id] = (
        f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 612 792] "
        f"/Contents {contents_id} 0 R "
        f"/Resources << /Font << /F1 {type0_font_id} 0 R >> >> >>"
    ).encode()

    # No /ToUnicode reference - this is the key
    objects[type0_font_id] = (
        f"<< /Type /Font /Subtype /Type0 /BaseFont /AAAAAA+TestCIDFont "
        f"/Encoding /Identity-H "
        f"/DescendantFonts [{cid_font_id} 0 R] >>"
    ).encode()

    objects[cid_font_id] = (
        f"<< /Type /Font /Subtype /CIDFontType2 /BaseFont /AAAAAA+TestCIDFont "
        f"/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> "
        f"/FontDescriptor {font_descriptor_id} 0 R "
        f"/DW {default_width} "
        f"/W [{w_array}] >>"
    ).encode()

    objects[font_descriptor_id] = (
        f"<< /Type /FontDescriptor /FontName /AAAAAA+TestCIDFont /Flags 4 "
        f"/FontBBox [{s_xmin} {s_ymin} {s_xmax} {s_ymax}] "
        f"/ItalicAngle 0 /Ascent {s_ascent} "
        f"/Descent {s_descent} /CapHeight 700 /StemV 80 "
        f"/FontFile2 {font_file_id} 0 R >>"
    ).encode()

    # Build PDF bytes
    pdf = b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
    offsets = {}

    # Write non-stream objects first
    for obj_id in sorted(objects.keys()):
        offsets[obj_id] = len(pdf)
        data = objects[obj_id]
        pdf += f"{obj_id} 0 obj\n".encode()
        pdf += data
        pdf += b"\nendobj\n"

    # Write content stream
    offsets[contents_id] = len(pdf)
    pdf += f"{contents_id} 0 obj\n".encode()
    pdf += f"<< /Length {len(content_stream)} >>\n".encode()
    pdf += b"stream\n"
    pdf += content_stream
    pdf += b"\nendstream\n"
    pdf += b"endobj\n"

    # Write font file stream
    offsets[font_file_id] = len(pdf)
    pdf += f"{font_file_id} 0 obj\n".encode()
    pdf += f"<< /Length {len(font_data)} /Length1 {len(font_data)} >>\n".encode()
    pdf += b"stream\n"
    pdf += font_data
    pdf += b"\nendstream\n"
    pdf += b"endobj\n"

    # xref table
    xref_offset = len(pdf)
    max_obj = max(offsets.keys())
    pdf += b"xref\n"
    pdf += f"0 {max_obj + 1}\n".encode()
    pdf += b"0000000000 65535 f \n"
    for i in range(1, max_obj + 1):
        if i in offsets:
            pdf += f"{offsets[i]:010d} 00000 n \n".encode()
        else:
            pdf += b"0000000000 00000 f \n"

    # trailer
    pdf += b"trailer\n"
    pdf += f"<< /Size {max_obj + 1} /Root {catalog_id} 0 R >>\n".encode()
    pdf += b"startxref\n"
    pdf += f"{xref_offset}\n".encode()
    pdf += b"%%EOF\n"

    with open(output_path, 'wb') as f:
        f.write(pdf)

    return len(pdf), len(used_gids)


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_output = os.path.join(script_dir, "cid-font-no-tounicode.pdf")
    output_path = sys.argv[1] if len(sys.argv) > 1 else default_output

    font_path = find_ttf_font()
    if font_path is None:
        print("ERROR: No suitable TrueType font found on system", file=sys.stderr)
        sys.exit(1)

    print(f"Using font: {font_path}")
    print("Generating PDF with Type0/CID font (no ToUnicode)...")
    size, num_glyphs = build_pdf_with_real_font(output_path, font_path)
    print(f"Generated: {output_path} ({size} bytes)")
    print()
    print("Key properties:")
    print("  - Type0 font with Identity-H encoding")
    print("  - CIDFontType2 descendant with real TrueType font program")
    print("  - No /ToUnicode CMap")
    print(f"  - {num_glyphs} unique glyphs used")
    print("  - 5 lines of text, all characters should map to U+FFFD in veraPDF")


if __name__ == "__main__":
    main()
