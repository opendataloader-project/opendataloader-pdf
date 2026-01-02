# IObject Type Reference

This document summarizes the IObject class hierarchy used in OpenDataLoader for PDF element representation.

## Overview

IObject is the base interface from `org.verapdf.wcag.algorithms.entities.IObject` (external verapdf-wcag-algs library). OpenDataLoader extends this to represent structured PDF content.

## Element Types

### Paragraph

Represents text paragraphs with font information.

**JSON Type:** `"paragraph"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"paragraph"` |
| `id` | number | Unique element identifier |
| `page number` | number | 1-indexed page number |
| `bounding box` | array | `[left, bottom, right, top]` in PDF points |
| `font` | string | Font name (e.g., `"ArialMT"`) |
| `font size` | number | Font size in points |
| `text color` | string | CMYK color as string `"[C, M, Y, K]"` |
| `content` | string | Text content |

**Example:**
```json
{
  "type": "paragraph",
  "id": 17,
  "page number": 1,
  "bounding box": [281.571, 551.756, 372.715, 560.694],
  "font": "ArialMT",
  "font size": 8.0,
  "text color": "[0.0, 0.0, 0.0, 0.7]",
  "content": "Civil Society Engagement"
}
```

### Heading

Represents section headings with level information.

**JSON Type:** `"heading"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"heading"` |
| `id` | number | Unique element identifier |
| `level` | string | Semantic level: `"Doctitle"`, `"1"`, `"2"`, etc. |
| `heading level` | number | Numeric level (1-6) |
| `page number` | number | 1-indexed page number |
| `bounding box` | array | `[left, bottom, right, top]` in PDF points |
| `font` | string | Font name |
| `font size` | number | Font size in points |
| `text color` | string | CMYK color as string |
| `content` | string | Heading text |

**Example:**
```json
{
  "type": "heading",
  "id": 20,
  "level": "Doctitle",
  "page number": 1,
  "bounding box": [54.0, 455.381, 350.475, 480.87],
  "heading level": 1,
  "font": "Arial-BoldMT",
  "font size": 11.0,
  "text color": "[0.0, 0.0, 0.0, 1.0]",
  "content": "Table: The number of accredited observers..."
}
```

### Table

Represents tables with row/cell structure.

**JSON Type:** `"table"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"table"` |
| `id` | number | Unique element identifier |
| `level` | string | Nesting level (usually `"1"`) |
| `page number` | number | 1-indexed page number |
| `bounding box` | array | `[left, bottom, right, top]` in PDF points |
| `number of rows` | number | Total row count |
| `number of columns` | number | Total column count |
| `rows` | array | Array of table row objects |

**Example:**
```json
{
  "type": "table",
  "id": 21,
  "level": "1",
  "page number": 1,
  "bounding box": [54.0, 234.441, 372.727, 440.212],
  "number of rows": 3,
  "number of columns": 3,
  "rows": [...]
}
```

### Table Row

**JSON Type:** `"table row"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"table row"` |
| `row number` | number | 1-indexed row number |
| `cells` | array | Array of table cell objects |

### Table Cell

**JSON Type:** `"table cell"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"table cell"` |
| `page number` | number | 1-indexed page number |
| `bounding box` | array | `[left, bottom, right, top]` in PDF points |
| `row number` | number | 1-indexed row position |
| `column number` | number | 1-indexed column position |
| `row span` | number | Number of rows spanned |
| `column span` | number | Number of columns spanned |
| `kids` | array | Child elements (usually paragraphs) |

**Example:**
```json
{
  "type": "table cell",
  "page number": 1,
  "bounding box": [54.375, 413.86, 83.52, 440.087],
  "row number": 1,
  "column number": 1,
  "row span": 1,
  "column span": 1,
  "kids": [{
    "type": "paragraph",
    "id": 1,
    "page number": 1,
    "bounding box": [61.757, 427.253, 75.761, 437.307],
    "font": "ArialMT",
    "font size": 9.0,
    "text color": "[0.0, 0.0, 0.0, 1.0]",
    "content": "No."
  }]
}
```

### Image

Represents image/figure elements.

**JSON Type:** `"image"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"image"` |
| `id` | number | Unique element identifier |
| `page number` | number | 1-indexed page number |
| `bounding box` | array | `[left, bottom, right, top]` in PDF points |

**Example:**
```json
{
  "type": "image",
  "id": 22,
  "page number": 1,
  "bounding box": [54.0, 68.275, 126.0, 68.525]
}
```

### List

Represents bulleted or numbered lists.

**JSON Type:** `"list"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"list"` |
| `id` | number | Unique element identifier |
| `level` | string | Nesting level |
| `page number` | number | 1-indexed page number |
| `bounding box` | array | `[left, bottom, right, top]` in PDF points |
| `numbering style` | string | `"arabic numbers"`, `"bullets"`, etc. |
| `number of list items` | number | Count of list items |
| `list items` | array | Array of list item objects |

### List Item

**JSON Type:** `"list item"`

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Always `"list item"` |
| `page number` | number | 1-indexed page number |
| `bounding box` | array | `[left, bottom, right, top]` in PDF points |
| `font` | string | Font name |
| `font size` | number | Font size in points |
| `text color` | string | CMYK color as string |
| `content` | string | List item text (may include marker) |
| `kids` | array | Child elements |

## Coordinate System

**Origin:** Bottom-left corner of page
**Units:** PDF points (1/72 inch)
**Format:** `[left, bottom, right, top]`

```
(0, page_height) -------- (page_width, page_height)
       |                           |
       |          PAGE             |
       |                           |
     (0, 0) ------------ (page_width, 0)
```

## Java Class Reference

Key classes in the codebase:

| Class | Purpose |
|-------|---------|
| `TableBorder` | Table with border-based detection |
| `TableBorderRow` | Table row container |
| `TableBorderCell` | Table cell with contents, spans |
| `BoundingBox` | PDF coordinates (page, left, bottom, right, top) |
| `TextLineProcessor` | Text line extraction |
| `TableBorderProcessor` | Table structure detection |
| `HeadingProcessor` | Heading level detection |
| `ListProcessor` | List structure detection |

## Document Root

The document root contains metadata and child elements:

```json
{
  "file name": "example.pdf",
  "number of pages": 1,
  "author": null,
  "title": null,
  "creation date": null,
  "modification date": null,
  "kids": [...]
}
```

The `kids` array contains all top-level elements in reading order.
