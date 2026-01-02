# Schema Mapping: Docling to IObject

---
name: schema-mapping
description: Maps Docling output schema to OpenDataLoader IObject hierarchy for hybrid mode conversion
---

## Overview

This skill documents the mapping between Docling's `DoclingDocument` output schema and OpenDataLoader's `IObject` hierarchy. Use this when implementing hybrid mode conversion logic.

## Type Mapping Table

| Docling Type | Docling Label | IObject Type | Notes |
|--------------|---------------|--------------|-------|
| `texts` | `text` | `paragraph` | Body text content |
| `texts` | `section_header` | `heading` | With level (1-6) |
| `texts` | `page_header` | `paragraph` | Filter as header if needed |
| `texts` | `page_footer` | `paragraph` | Filter as footer if needed |
| `texts` | `caption` | `paragraph` | Associated with table/figure |
| `texts` | `footnote` | `paragraph` | Associated with table/figure |
| `tables` | `table` | `table` | Complex structure mapping |
| `pictures` | `picture` | `image` | Figure/image element |

## Element Structure Details

### Text Elements (Docling -> IObject)

**Docling Text Structure:**
```json
{
  "self_ref": "#/texts/0",
  "parent": { "$ref": "#/body" },
  "children": [],
  "content_layer": "body",
  "label": "text",
  "prov": [{
    "page_no": 1,
    "bbox": { "l": 54.0, "t": 537.04, "r": 375.14, "b": 499.90, "coord_origin": "BOTTOMLEFT" },
    "charspan": [0, 157]
  }],
  "orig": "Original text...",
  "text": "Processed text..."
}
```

**IObject Paragraph Structure:**
```json
{
  "type": "paragraph",
  "id": 17,
  "page number": 1,
  "bounding box": [54.0, 499.92, 372.73, 539.28],
  "font": "Georgia",
  "font size": 10.0,
  "text color": "[0.0, 0.0, 0.0, 1.0]",
  "content": "Text content..."
}
```

**Mapping Rules:**
| Docling Field | IObject Field | Transformation |
|---------------|---------------|----------------|
| `prov[0].page_no` | `page number` | Direct copy |
| `prov[0].bbox` | `bounding box` | See coordinate conversion |
| `text` or `orig` | `content` | Use `text` if available, fallback to `orig` |
| N/A | `font` | Not available from Docling |
| N/A | `font size` | Not available from Docling |
| N/A | `text color` | Not available from Docling |
| N/A | `id` | Generate sequential ID |

### Heading Elements

**Docling Section Header:**
```json
{
  "label": "section_header",
  "prov": [{
    "page_no": 1,
    "bbox": { "l": 54.0, "t": 478.79, "r": 353.53, "b": 454.91, "coord_origin": "BOTTOMLEFT" }
  }],
  "text": "Heading text",
  "level": 1
}
```

**IObject Heading:**
```json
{
  "type": "heading",
  "id": 20,
  "level": "Doctitle",
  "page number": 1,
  "bounding box": [54.0, 455.38, 350.48, 480.87],
  "heading level": 1,
  "font": "Arial-BoldMT",
  "font size": 11.0,
  "content": "Heading text"
}
```

**Mapping Rules:**
| Docling Field | IObject Field | Transformation |
|---------------|---------------|----------------|
| `level` | `heading level` | Direct copy (1-6) |
| `level` | `level` | Map: 1->"Doctitle", 2->"1", 3->"2", etc. |
| `text` | `content` | Direct copy |

### Table Elements

**Docling Table Structure:**
```json
{
  "self_ref": "#/tables/0",
  "label": "table",
  "prov": [{
    "page_no": 1,
    "bbox": { "l": 53.22, "t": 439.98, "r": 373.94, "b": 234.74, "coord_origin": "BOTTOMLEFT" }
  }],
  "captions": [{ "$ref": "#/texts/2" }],
  "footnotes": [{ "$ref": "#/texts/3" }],
  "data": {
    "table_cells": [...],
    "num_rows": 9,
    "num_cols": 3,
    "grid": [[...], ...]
  }
}
```

**IObject Table Structure:**
```json
{
  "type": "table",
  "id": 21,
  "level": "1",
  "page number": 1,
  "bounding box": [54.0, 234.44, 372.73, 440.21],
  "number of rows": 3,
  "number of columns": 3,
  "rows": [...]
}
```

**Table Mapping Rules:**
| Docling Field | IObject Field | Transformation |
|---------------|---------------|----------------|
| `data.num_rows` | `number of rows` | Direct copy |
| `data.num_cols` | `number of columns` | Direct copy |
| `data.grid` | `rows` | Transform grid to row/cell structure |

### Table Cell Mapping

**Docling Cell (from `data.table_cells` or `data.grid`):**
```json
{
  "bbox": { "l": 61.76, "t": 159.67, "r": 75.76, "b": 168.13, "coord_origin": "TOPLEFT" },
  "row_span": 1,
  "col_span": 1,
  "start_row_offset_idx": 0,
  "end_row_offset_idx": 1,
  "start_col_offset_idx": 0,
  "end_col_offset_idx": 1,
  "text": "No.",
  "column_header": true,
  "row_header": false
}
```

**IObject Cell:**
```json
{
  "type": "table cell",
  "page number": 1,
  "bounding box": [54.38, 413.86, 83.52, 440.09],
  "row number": 1,
  "column number": 1,
  "row span": 1,
  "column span": 1,
  "kids": [{
    "type": "paragraph",
    "content": "No."
  }]
}
```

**Cell Mapping Rules:**
| Docling Field | IObject Field | Transformation |
|---------------|---------------|----------------|
| `start_row_offset_idx + 1` | `row number` | Add 1 (0-indexed to 1-indexed) |
| `start_col_offset_idx + 1` | `column number` | Add 1 (0-indexed to 1-indexed) |
| `row_span` | `row span` | Direct copy |
| `col_span` | `column span` | Direct copy |
| `text` | `kids[0].content` | Wrap in paragraph element |
| `bbox` | `bounding box` | See coordinate conversion |

### Image/Picture Elements

**Docling Picture:**
```json
{
  "self_ref": "#/pictures/0",
  "label": "picture",
  "prov": [{
    "page_no": 1,
    "bbox": { "l": 54.0, "t": 68.53, "r": 126.0, "b": 68.28, "coord_origin": "BOTTOMLEFT" }
  }]
}
```

**IObject Image:**
```json
{
  "type": "image",
  "id": 22,
  "page number": 1,
  "bounding box": [54.0, 68.28, 126.0, 68.53]
}
```

## Bounding Box Coordinate Conversion

### Coordinate Systems

Both Docling and IObject use PDF coordinate systems, but with different field ordering:

| System | Format | Origin | Field Order |
|--------|--------|--------|-------------|
| Docling | Object `{l, t, r, b}` | BOTTOMLEFT or TOPLEFT | left, top, right, bottom |
| IObject | Array `[left, bottom, right, top]` | BOTTOMLEFT | left, bottom, right, top |

### Conversion Formula

**Docling BOTTOMLEFT -> IObject:**
```
iobject_bbox = [docling.l, docling.b, docling.r, docling.t]
```

Note: Docling uses `t` for top and `b` for bottom. IObject array is `[left, bottom, right, top]`.

**Docling TOPLEFT -> IObject (requires page height):**
```
page_height = pages[page_no].size.height
iobject_bbox = [
  docling.l,                    // left
  page_height - docling.t,      // bottom (convert from top origin)
  docling.r,                    // right
  page_height - docling.b       // top (convert from top origin)
]
```

### Important Notes

1. **Check `coord_origin`**: Always check the `coord_origin` field in Docling's bbox
2. **Page dimensions**: Available in `pages[page_no].size.width` and `pages[page_no].size.height`
3. **Table cells may differ**: Table cell bboxes sometimes use TOPLEFT even when element bbox uses BOTTOMLEFT

## Page Number Handling

- Docling: `prov[0].page_no` (1-indexed)
- IObject: `page number` (1-indexed)
- Direct copy, no transformation needed

## Text Content Extraction

**Priority order for text content:**
1. Use `text` field if present (processed/cleaned text)
2. Fall back to `orig` field (original OCR/extraction text)

## Element References

Docling uses JSON references for relationships:
- `parent`: `{ "$ref": "#/body" }` - parent element
- `children`: `[{ "$ref": "#/texts/2" }]` - child elements
- `captions`: References to caption text elements
- `footnotes`: References to footnote text elements

When converting, resolve these references to build the IObject hierarchy.

## Missing Fields

Fields in IObject not available from Docling:
- `font`: Font name (requires PDF text extraction)
- `font size`: Font size in points
- `text color`: CMYK color array

These fields should be omitted or populated from PDF extraction if available.

## List Elements

Docling does not have a dedicated list type in the standard schema. Lists are typically:
- Detected as individual text elements with bullet/number patterns
- May need post-processing to group into list structure

**IObject List Structure:**
```json
{
  "type": "list",
  "id": 4,
  "level": "1",
  "page number": 1,
  "bounding box": [...],
  "numbering style": "arabic numbers",
  "number of list items": 7,
  "list items": [
    {
      "type": "list item",
      "page number": 1,
      "bounding box": [...],
      "content": "1",
      "kids": []
    }
  ]
}
```

Consider implementing list detection as a post-processing step if Docling output lacks explicit list structures.
