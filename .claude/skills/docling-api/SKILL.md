# Docling Serve API Specification

This skill provides API specification for docling-serve, the HTTP API for the Docling PDF/document parsing library.

## API Overview

- **Base URL**: `http://localhost:5000` (default)
- **API Version**: 1.9.0
- **Authentication**: Optional API key via `APIKeyAuth` header

## Endpoints

### Convert File (Synchronous)

```
POST /v1/convert/file
Content-Type: multipart/form-data
```

Converts a PDF or document file and returns structured output.

**Request Parameters** (multipart/form-data):

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `files` | binary[] | required | File(s) to convert |
| `to_formats` | string[] | `["md"]` | Output formats: `md`, `json`, `html`, `text`, `doctags` |
| `do_ocr` | boolean | `true` | Enable OCR for bitmap content |
| `do_table_structure` | boolean | `true` | Extract table structure |
| `table_mode` | string | `"accurate"` | Table mode: `fast` or `accurate` |
| `pipeline` | string | `"standard"` | Processing pipeline: `standard` or `vlm` |
| `page_range` | int[2] | `[1, MAX]` | Page range to convert (1-indexed) |

### Convert File (Async)

```
POST /v1/convert/file/async
Content-Type: multipart/form-data
```

Same parameters as synchronous, returns a task ID for polling.

### Poll Task Status

```
GET /v1/status/poll/{task_id}?wait=0
```

Poll for async task completion. Set `wait` to number of seconds for long-polling.

### Get Task Result

```
GET /v1/result/{task_id}
```

Retrieve the result of a completed async task.

### Health Check

```
GET /health
```

Returns server health status.

## Request Example (Java HttpClient)

```java
// Build multipart request
HttpRequest.Builder builder = HttpRequest.newBuilder()
    .uri(URI.create(baseUrl + "/v1/convert/file"))
    .header("Content-Type", "multipart/form-data; boundary=" + boundary);

// Multipart body parts:
// 1. File part: name="files", filename="document.pdf"
// 2. Form fields: name="to_formats", value="json"
//                 name="do_table_structure", value="true"
```

## Response Structure

### ConvertDocumentResponse

```json
{
  "document": {
    "filename": "document.pdf",
    "md_content": "# Title\n\nParagraph text...",
    "json_content": { ... },  // DoclingDocument
    "html_content": "<html>...</html>",
    "text_content": "Plain text...",
    "doctags_content": null
  },
  "status": "success",
  "errors": [],
  "processing_time": 2.897,
  "timings": { ... }
}
```

### DoclingDocument (json_content)

The main structured document format with all parsed elements:

```json
{
  "schema_name": "DoclingDocument",
  "version": "1.8.0",
  "name": "document",
  "origin": {
    "mimetype": "application/pdf",
    "filename": "document.pdf"
  },
  "body": {
    "self_ref": "#/body",
    "children": [
      {"$ref": "#/texts/0"},
      {"$ref": "#/tables/0"}
    ]
  },
  "texts": [ ... ],
  "tables": [ ... ],
  "pictures": [ ... ],
  "pages": { ... }
}
```

## Element Types

All document content is stored in typed arrays (`texts`, `tables`, `pictures`) and referenced via JSON pointers in the `body.children` array for reading order.

### Text Elements (in `texts` array)

| Label | Description | Extra Fields |
|-------|-------------|--------------|
| `title` | Document title | - |
| `section_header` | Section/chapter heading | `level` (1-100) |
| `text` | Regular paragraph | - |
| `paragraph` | Paragraph text | - |
| `caption` | Table/figure caption | - |
| `footnote` | Footnote text | - |
| `list_item` | List item | `enumerated`, `marker` |
| `page_header` | Page header (furniture) | - |
| `page_footer` | Page footer (furniture) | - |
| `reference` | Reference/citation | - |

**Common Text Fields**:
```json
{
  "self_ref": "#/texts/0",
  "parent": {"$ref": "#/body"},
  "label": "text",
  "text": "The processed text content",
  "orig": "Original text from PDF",
  "prov": [{
    "page_no": 1,
    "bbox": {"l": 54.0, "t": 537.0, "r": 375.0, "b": 499.0, "coord_origin": "BOTTOMLEFT"},
    "charspan": [0, 157]
  }]
}
```

### Table Elements (in `tables` array)

```json
{
  "self_ref": "#/tables/0",
  "label": "table",
  "prov": [{ "page_no": 1, "bbox": {...} }],
  "captions": [{"$ref": "#/texts/2"}],
  "footnotes": [{"$ref": "#/texts/3"}],
  "data": {
    "num_rows": 9,
    "num_cols": 3,
    "table_cells": [...],
    "grid": [[...], [...]]
  }
}
```

**TableCell Structure**:
```json
{
  "text": "Cell content",
  "bbox": {"l": 61.7, "t": 159.6, "r": 75.7, "b": 168.1, "coord_origin": "TOPLEFT"},
  "row_span": 1,
  "col_span": 1,
  "start_row_offset_idx": 0,
  "end_row_offset_idx": 1,
  "start_col_offset_idx": 0,
  "end_col_offset_idx": 1,
  "column_header": true,
  "row_header": false
}
```

### Picture Elements (in `pictures` array)

```json
{
  "self_ref": "#/pictures/0",
  "label": "picture",  // or "chart"
  "prov": [{ "page_no": 1, "bbox": {...} }],
  "captions": [{"$ref": "#/texts/5"}],
  "image": {
    "mimetype": "image/png",
    "uri": "data:image/png;base64,..."
  }
}
```

## Bounding Box (bbox)

Coordinates for element position on page:

```json
{
  "l": 54.0,      // left
  "t": 537.0,     // top
  "r": 375.0,     // right
  "b": 499.0,     // bottom
  "coord_origin": "BOTTOMLEFT"  // or "TOPLEFT"
}
```

**Coordinate Origins**:
- `BOTTOMLEFT`: Origin at bottom-left corner (PDF default). Y increases upward.
- `TOPLEFT`: Origin at top-left corner. Y increases downward.

**Important**: Table cell bboxes often use `TOPLEFT` origin, while text element bboxes use `BOTTOMLEFT`. Always check `coord_origin` when processing coordinates.

## Page Information

```json
{
  "pages": {
    "1": {
      "size": {"width": 419.5, "height": 595.3},
      "image": {
        "mimetype": "image/png",
        "dpi": 144,
        "size": {"width": 839, "height": 1191},
        "uri": "data:image/png;base64,..."
      }
    }
  }
}
```

## Java Implementation Notes

1. **Multipart Request**: Use boundary-based multipart/form-data encoding
2. **Timeouts**: Set appropriate timeouts (default document timeout is 604800s)
3. **JSON Parsing**: Use Jackson or Gson with proper type handling for polymorphic text types
4. **Coordinate Handling**: Normalize coordinates to a single origin (recommend TOPLEFT)
5. **Reference Resolution**: Elements use JSON pointer refs (`#/texts/0`) - build a lookup map
6. **Reading Order**: Iterate `body.children` refs in order for correct reading sequence

## Error Handling

```json
{
  "status": "failure",
  "errors": [{
    "error_type": "...",
    "message": "..."
  }],
  "processing_time": 0.5
}
```

Check `status` field: `success` or `failure`. Handle errors in the `errors` array.
