# Docling vs OpenDataLoader Output Comparison

## Test Document
- File: `01030000000045.pdf` (1 page with table)

## Element Count Comparison

| Category | Docling | OpenDataLoader |
|----------|---------|----------------|
| Tables | 1 | 1 |
| Text elements | 5 | 4 paragraphs |
| Images | 0 | 1 |
| Headings | (N/A - uses labels) | 1 |

## Text Element Labels (Docling)

| Label | Count |
|-------|-------|
| caption | 1 |
| footnote | 1 |
| page_footer | 1 |
| page_header | 1 |
| text | 1 |

## Table Structure Comparison

| Property | Docling | OpenDataLoader |
|----------|---------|----------------|
| Rows | 9 | 3 |
| Columns | 3 | 3 |
| Total cells | 26 | 9 |

**Note**: Docling detects more rows in the table structure. This may be due to:
- Different table detection algorithms
- OpenDataLoader may have merged some rows
- Different handling of header rows

## Bounding Box Comparison (Table)

| System | l/left | t/top | r/right | b/bottom | Origin |
|--------|--------|-------|---------|----------|--------|
| Docling | 53.22 | 439.98 | 373.94 | 234.74 | BOTTOMLEFT |
| OpenDataLoader | 54.0 | 234.44 | 372.73 | 440.21 | BOTTOMLEFT |

**Coordinate mapping**: Both use BOTTOMLEFT origin.
- Docling: `{l, t, r, b}` where t=top, b=bottom
- OpenDataLoader: `[left, bottom, right, top]`

So the actual coordinates match closely:
- Left: 53.22 ≈ 54.0
- Bottom: 234.74 ≈ 234.44
- Right: 373.94 ≈ 372.73
- Top: 439.98 ≈ 440.21

## Schema Mapping Summary

| Docling Type | OpenDataLoader Type |
|--------------|---------------------|
| texts (label: text) | paragraph |
| texts (label: section_header) | heading |
| tables | table |
| pictures | image |
| texts (label: page_header) | paragraph (filtered as header) |
| texts (label: page_footer) | paragraph (filtered as footer) |
| texts (label: caption) | paragraph |
| texts (label: footnote) | paragraph |

## Key Differences

1. **Type naming**: Docling uses `label` field for text types, OpenDataLoader uses `type`
2. **Table structure**: Docling detects more detailed row structure
3. **Coordinate format**: Same origin but different field order
4. **Heading detection**: Docling uses `SectionHeaderItem` with `level`, OpenDataLoader uses `heading` type with `level`
