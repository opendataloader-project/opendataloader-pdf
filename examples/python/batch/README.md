# Batch Processing Example

Demonstrates processing multiple PDFs in a single invocation to avoid repeated Java JVM startup overhead.

## Prerequisites

- Python 3.9+
- Java 11+ (on PATH)

## Example

[`batch_processing.py`](batch_processing.py) shows two methods for batch conversion:

1. **File list** — Pass multiple PDF paths as a list
2. **Directory** — Pass a directory path (recursively finds all PDFs)

Both methods use a single JVM invocation, which is significantly faster than calling the CLI once per file.

**Run:**
```bash
pip install -r requirements.txt
python batch_processing.py
```

## Sample Output

```
Found 4 PDFs in pdf/

==========================================================
Method 1: Batch convert with file list
==========================================================

Document                                  Pages Top-level
----------------------------------------------------------
1901.03003                                   15       241
2408.02509v1                                 14       365
chinese_scan                                  1         1
lorem                                         1         2
----------------------------------------------------------
Total                                        31       609

Processed 4 documents
Time: 7.95s (single JVM invocation)
```
