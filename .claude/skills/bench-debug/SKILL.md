---
name: bench-debug
description: Debug specific document parsing failures
---

# /bench-debug <doc_id>

Compares parsing output with ground-truth for a specific document and analyzes failure causes.

## Usage

```
/bench-debug 01030000000189
```

## Execution Steps

1. Run benchmark for the specific document
   ```bash
   ./scripts/bench.sh --doc-id <doc_id>
   ```

2. Compare files
   - Ground-truth: `tests/benchmark/ground-truth/markdown/<doc_id>.md`
   - Prediction: `tests/benchmark/prediction/opendataloader/markdown/<doc_id>.md`
   - Original PDF: `tests/benchmark/pdfs/<doc_id>.pdf`

3. Analyze differences
   - Missing/extra text locations
   - Table structure differences (TEDS score causes)
   - Heading level mismatches (MHS score causes)
   - Reading order errors (NID score causes)

4. Identify root causes
   - Which PDF elements caused the issue
   - Which Java core components are involved

5. Suggest improvements
   - Java classes/methods that need modification
   - Expected impact scope

## Reference Files

- `ground-truth/reference.json`: Per-document element info (categories, coordinates, etc.)
- `java/opendataloader-pdf-core/`: Core parsing logic

## Example Output

```
Document 01030000000189 Analysis:

Overall: 0.2763 (one of the worst performing documents)

Issues:
1. 2 of 3 tables not detected (TEDS: 0.15)
   - Table boundary detection failed
   - Related code: TableDetector.java

2. Reading order errors (NID: 0.45)
   - Multi-column layout handling failed
   - Related code: ColumnDetector.java

Recommended Actions:
- Adjust clustering threshold in TableDetector
- Improve multi-column detection logic
```
