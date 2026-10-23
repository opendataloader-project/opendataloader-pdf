# Samples and acceptance tests

An input for a review is a JUnit test that builds the sample and asserts the expected output. The same test has three uses:

1. It writes the input and the output to `TestSamples.emptyDirectory("<name>")` (`target/test-samples/<name>/`). The demo folder takes the files from there.
2. It is the proof. A run on `main` and a run on the merge give the states of an expectation.
3. It is the request to the author. Put it in the review, in a `<details>` block. The author can add it to the tests as it is.

## Sources, in this order

1. **The issue's document and command.** Use them in `$WORK` only. Do not commit, attach, upload, or quote the original. This applies also to public documents. Download attachments only with `scripts/fetch_attachment.sh`. In the demo, mark such a case "local only", and give the author a synthetic case for the same expectation.
2. **The samples of the repository**: `samples/pdf/`, and `java/opendataloader-pdf-core/src/test/resources/issues/` (small PDFs of earlier fixes) if it exists. Use the ones of `main` for the side-effect corpus (catalog: Q2-corpus): a pull request can change its own samples. `scripts/side_effects.sh` refuses a corpus inside the run copy.
3. **Synthetic samples**, in the simplest form that shows the behavior: objects built in the test (`TextChunk`, `BoundingBox`, `TableBorder`), or a PDF built with PDFBox in the test. These two forms fit almost all cases. PDFBox can make nearly any PDF structure, and a test can write raw bytes for a damaged file. For hybrid mode, use a backend response JSON.

## Examples in the repository, by structure

Start from the closest example.

| Structure | Form | Example |
|:--|:--|:--|
| Table borders, cells, text chunks across a border | objects | `processors/TableBorderProcessorTest` |
| Reading order, layout | objects | `processors/readingorder/XYCutPlusPlusSorterTest` |
| Bordered tables with text | PDFBox | `issues/Issue336UndersegmentedTableTest` |
| Fonts without a Unicode mapping (CID font, no ToUnicode; LiberationSans from PDFBox) | PDFBox | `processors/CidFontDetectionTest` |
| Tagged structure: `L`, `LI`, `Lbl`, `LBody`, marked content, ParentTree | PDFBox | `processors/TaggedPdfMarkedContentTest`, `processors/ParentTreeOrderTest` |
| Scanned pages | PDFBox | `hybrid/ScannedPagePdf` |
| Damaged files (raw bytes) | PDFBox | `processors/HybridReferenceTrailerTest` |
| Hybrid backend responses | JSON | `hybrid/DoclingSchemaTransformerTest` |
| Text on each page | helper | `testsupport/TestSamples.writeTextPdf` |

Paths are under `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/`.

## Rules for each sample

- **Find the trigger.** A behavior usually comes from the structure, not from the content. If the case comes from an issue document, open it locally (`qpdf --qdf --object-streams=disable`), and remove pages and objects until the symptom stops. Make that structure again with neutral content. Do not redact the original: metadata, font subsets, alt text, ActualText, and attachments can keep its content.
- **Measurements.** Copy the measurements of the trigger: spacing, font size, gaps, counts, and sizes. If the code has a threshold, make the sample cross it. A sample that has only the kind of structure can miss regressions. For example, a sample with a 25 pt row pitch did not catch changes to row tolerances, but the reporter's document had 12 pt.
- **Fonts.** Use the standard 14 fonts, or an OFL font with its license. PDFBox ships LiberationSans (SIL OFL) at `/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf`. Do not use a system font.
- **Content.** Use generated text and images. If a script is the trigger (Korean, Chinese, right-to-left), use short neutral text in that script.
- **Repeated text.** Text near the top or the bottom of the page becomes a header or a footer if it is the same on each page, or if only a number in it changes. The default output leaves out headers and footers (`--include-header-footer` adds them). A tagged PDF marks them as artifacts, without an `/MCID`. If the test needs body text, give each page different text.
- **Symptom.** If the case comes from an issue, `main` must show the same symptom on the sample as on the issue's document. If it does not, add structure until it does.

## Rules for a review test

- **Expected value from the specification.** Take it from the issue, the format specification, or the sample design. Do not take it from the output of `main` or of the pull request.
- **One case, one test method.** The method name says the expected behavior, for example `textBeforeAnEmptyGridAppearsOnce`.
- **One row of the case table, one test.** Name the test in the "Test" column of the review table. A test without a row, or a row without a test, is a gap.
- **Accept every correct output.** Assert the ideal, not one implementation. For example, for a nested ordered list assert the numbers that a CommonMark viewer shows, not the indent or the separator that you would use. If you can, run the test on a hand-made correct output that differs from the obvious fix.
- **Results, not mechanisms.** If the expected value is about a log, an exception, or internal state, assert what a user or a caller sees (the failure is reported, the output is complete), not the form (a log record with a throwable, an exception type). The form can conflict with a decision of the project (#811: e72622f0 removed stack traces from the logs).
- **A model of a specification needs its own test.** If the test checks the output with a small model (for example, how CommonMark numbers lists), add a test of the model with cases that you checked with a real tool (for example, markdown-it-py). Say so in a comment.
- **Assert.** A test that only prints the output is a probe, not a proof. Do not give a probe to the author.
- **A pass on `main` and a fail on the merge prove a regression.** A fail on `main` and a pass on the merge prove a fix.
- **A compile error, a missing sample, or a skip is not a fail.** Some tests skip without a message (`assumeTrue`). Check that the test is not in "Skipped".
- **Build the jar without the test sources** (`-Dmaven.test.skip=true`) when an overlay test does not compile on `main` (new API). Then compile and run the tests in a second step.
- **New API.** If the test must call a method that only the pull request has, call it through an interface that `main` has, or by reflection. If you use reflection, fail with a message that names the symptom in the `catch` block for `NoSuchMethodException`. Put the assertions after the call block, not in it: an assertion in the block does not run on `main`, so the test passes there and checks nothing. Assert the effect of the call. Then the test compiles and fails on `main` with the symptom.
- **Check the element that the expectation is about.** Text can become a header or a footer ("Repeated text"). Assert on the element's type in the JSON, or on an `/MCID` on its text in the tagged PDF. An `/MCID` somewhere on the page does not prove it.
- **Same bytes at each run.** No dates, no random values. PDFBox writes a trailer `/ID` from the time; set a fixed one before `save` if the bytes must match.
- **Probes record their settings.** A measurement probe writes its JVM input arguments (`RuntimeMXBean.getInputArguments()`) and the maximum heap into its report. `-DargLine` on the Maven command line does not reach the test JVM when the pom sets `<argLine>`; run the probe with a plain `java` command, or check the recorded arguments.
- **Name.** `Review<PR>_<Topic>Test` in `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/issues/` of the run copy (in the overlay: `extra/<label>/java/opendataloader-pdf-core/...`). Give the author a name that fits their pull request, for example `Issue<N><Description>Test`.

Run the test in the isolated environment on `main` and on the merge. Put the test file in the overlay of the run (`extra/<label>/` for the reference implementation). If the same run gives the reports for the suite comparison, give the overlays to `suite_compare.py` (`--overlay extra/<label> --tree trees/main`), so that the review's tests do not count as existing tests. An overlay file must be new: one that replaces a file of `main` still counts.

## The tests of the pull request

Check the sample fidelity of the tests that the pull request adds:

- Does the sample copy the measurements of the trigger: spacing, sizes, counts, thresholds? Would a small change to a tolerance break the issue's document but not the sample?
- Does the test assert the expected result, or the current output?
- Does the test check the element that it names (see "Repeated text" and "Check the element that the expectation is about" above)?

A finding here is a finding of the check Q3-tests. A test that the pull request deleted or weakened is in the output of `scripts/suite_compare.py` (Q2-suite).

## Files for the author

If PDFBox cannot build the structure, give the PDF files instead. `gh pr review` cannot attach files. At the post gate, plan that the maintainer attaches them in the browser, in a comment after the review.
