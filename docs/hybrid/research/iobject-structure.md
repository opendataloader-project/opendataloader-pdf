# IObject Class Structure

## Overview
IObject is imported from `org.verapdf.wcag.algorithms.entities.IObject` (external verapdf-wcag-algs library).

## JSON Output Types

Based on sample response analysis, OpenDataLoader produces the following element types:

### Element Types

| Type | JSON `type` field | Description |
|------|-------------------|-------------|
| Paragraph | `paragraph` | Text paragraph with font info |
| Heading | `heading` | Section heading with level |
| Table | `table` | Table with rows and cells |
| Image | `image` | Image/figure element |
| List | `list` | Bulleted or numbered list |

### Common Fields (all types)

```json
{
  "type": "paragraph",
  "id": 17,
  "page number": 1,
  "bounding box": [left, bottom, right, top]  // PDF points, origin at bottom-left
}
```

### Paragraph Fields

```json
{
  "type": "paragraph",
  "font": "ArialMT",
  "font size": 8.0,
  "text color": "[0.0, 0.0, 0.0, 0.7]",
  "content": "Text content here"
}
```

### Heading Fields

```json
{
  "type": "heading",
  "level": "1",
  "content": "Heading text"
}
```

### Table Structure

```json
{
  "type": "table",
  "level": "1",
  "number of rows": 3,
  "number of columns": 3,
  "rows": [
    {
      "type": "table row",
      "row number": 1,
      "cells": [
        {
          "type": "table cell",
          "page number": 1,
          "bounding box": [left, bottom, right, top],
          "row number": 1,
          "column number": 1,
          "row span": 1,
          "column span": 1,
          "kids": [
            {
              "type": "paragraph",
              "content": "Cell text"
            }
          ]
        }
      ]
    }
  ]
}
```

## Bounding Box Coordinate System

- **OpenDataLoader**: `[left, bottom, right, top]` in PDF points, origin at BOTTOMLEFT
- **Docling**: `{l, t, r, b}` with `coord_origin: "BOTTOMLEFT"` or `"TOPLEFT"`

### Conversion Notes

- If docling uses TOPLEFT origin: `bottom = page_height - docling_t`, `top = page_height - docling_b`
- If docling uses BOTTOMLEFT origin: direct mapping `[l, b, r, t]` → `[left, bottom, right, top]`

## Key Java Classes

From the codebase:

- `TableBorder` - Table with border-based detection
- `TableBorderRow` - Table row
- `TableBorderCell` - Table cell with contents, rowSpan, colSpan
- `BoundingBox` - PDF coordinates (page, left, bottom, right, top)
- Processors: `TextLineProcessor`, `TableBorderProcessor`, `HeadingProcessor`, `ListProcessor`
