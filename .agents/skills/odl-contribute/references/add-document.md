# Add a test document

Goal: a document in opendataloader-testdocs that anyone may redistribute, that shows the problem on its own, and whose expectations describe correct output.

The rules are in the testdocs [CONTRIBUTING.md](https://github.com/opendataloader-project/opendataloader-testdocs/blob/main/CONTRIBUTING.md), [LICENSES.md](https://github.com/opendataloader-project/opendataloader-testdocs/blob/main/LICENSES.md), and the [manifest reference](https://github.com/opendataloader-project/opendataloader-testdocs#manifest). Read them in `$TESTDOCS`.

## 1. Decide whether the document can be published

Find the license at the source — a license page, a footer, the publisher's terms — not in your memory of the publisher. Keep the URL where the license is stated; the pull request cites it.

| What you find | Method |
|:--------------|:-------|
| A license listed in LICENSES.md, and no personal data | `original` or `extract:<pages>` |
| Anything else: no license, a non-commercial or no-derivatives license, a private file, personal data | `synthetic` |

For `synthetic`, the original never leaves the operator's machine: not in the repository, not attached to an issue, not quoted beyond what the operator approves.

## 2. Make the smallest document that still fails

- **extract** — keep only the pages that show the problem: `qpdf --empty --pages original.pdf 3-4 -- <id>.pdf`
- **synthetic** — write `generate.py` that rebuilds the trigger with neutral text: the same layout, font kind, drawing operators, or encoding, with none of the original content. Start from `documents/bordered-table-basic/generate.py`, which writes a PDF with the standard library alone.

Choose the id: `<issue>-<short-description>` for an issue, otherwise `<short-description>`.

## 3. Confirm the problem survived

Extraction can drop the structure tree, font subsets, or other objects, and synthesis can miss the real trigger. Run the affected version on `<id>.pdf` and confirm the symptom. If it is gone, add back pages or structure until it returns. Record that version in `symptom_verified`.

## 4. Write MANIFEST.json

Fill every field in the manifest reference. `contains` comes from looking at the pages — render them to images if you cannot see them otherwise — never from opendataloader-pdf output, because counting from buggy output makes the bug the expected result.

## 5. Write expectations

Describe correct output for the behavior in question:

| Symptom | Expectation |
|:--------|:------------|
| Crash or non-zero exit | `exit_code` |
| Missing or extra elements | `count` |
| Text missing, garbled, or leaked | `text_contains`, `text_absent` |
| Text out of order | `order` |
| Table merged, split, or misshapen | `table_shape`, `table_cell` |

Leave out expectations that merely restate unrelated current output; they break on unrelated improvements. Then run:

```bash
python "$TESTDOCS/scripts/run_expect.py" <id> --cli release="uvx opendataloader-pdf@latest"
```

At least one expectation must fail on the affected version, for the reason given in `symptom`.

## 6. Validate and draft the pull request

```bash
python "$TESTDOCS/scripts/build_index.py"
python "$TESTDOCS/scripts/check.py"
```

Draft a pull request to `opendataloader-project/opendataloader-testdocs` containing: what the document shows, the license and the URL where it is stated (or "synthetic"), the `run_expect.py` table, and the linked issue. Show the draft; after approval, push to your fork and open it.
