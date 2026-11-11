# Samples and acceptance tests of this repository

An input for a review is a JUnit test that builds the sample and asserts the expected output. The same test has three uses:

1. It writes the input and the output to `TestSamples.emptyDirectory("<name>")` (`target/test-samples/<name>/`). The demo folder takes the files from there.
2. It is the proof. A run on `main` and a run on the merge give the states of an expectation.
3. It is the request to the author. Put it in the review, in a `<details>` block. The author can add it to the tests as it is.

## Sources, in this order

1. **The issue's document and command.** Use them in `$WORK` only. Do not commit, attach, upload, or quote the original, also if it is public. Download attachments only with the attachment tool of the review. In the demo, mark such a case "local only", and give the author a synthetic case for the same expectation.
2. **The samples of the repository**: `samples/pdf/`, and `java/opendataloader-pdf-core/src/test/resources/issues/` (small PDFs of earlier fixes) if it exists. Use the ones of `main` for the side-effect corpus (catalog: Q2-corpus): a pull request can change its own samples. `side_effects.sh` refuses a corpus inside the run copy.
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

- **Find the trigger.** If the case comes from an issue document, open it locally (`qpdf --qdf --object-streams=disable`), and remove pages and objects until the symptom stops. Make that structure again with neutral content. Do not redact the original: metadata, font subsets, alternative text, ActualText, and attachments can keep its content.
- **Fonts.** Use the standard 14 fonts, or an OFL font with its license. PDFBox ships LiberationSans (SIL OFL) at `/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf`. Do not use a system font.
- **Content.** Use generated text and images. If a script is the trigger (Korean, Chinese, right-to-left), use short neutral text in that script.
- **Repeated text.** Text near the top or the bottom of the page becomes a header or a footer if it is the same on each page, or if only a number in it changes. The default output leaves out headers and footers (`--include-header-footer` adds them). A tagged PDF marks them as artifacts, without an `/MCID`. If the test needs body text, give each page different text.

## Rules for a review test

- **Check the element that the expectation is about.** Text can become a header or a footer ("Repeated text"). Assert on the element's type in the JSON, or on an `/MCID` on its text in the tagged PDF. An `/MCID` somewhere on the page does not prove it.
- **Same bytes at each run.** PDFBox writes a trailer `/ID` from the time; set a fixed one before `save` if the bytes must match.
- **A test can skip without a message** (`assumeTrue`). Check that the test is not in "Skipped".
- **Build the jar without the test sources** (`-Dmaven.test.skip=true`) when an overlay test does not compile on `main` (new API). Then compile and run the tests in a second step.
- **Probes record their JVM settings.** A measurement probe writes its JVM input arguments (`RuntimeMXBean.getInputArguments()`) and the maximum heap into its report. `-DargLine` on the Maven command line does not reach the test JVM when the pom sets `<argLine>`; run the probe with a plain `java` command, or check the recorded arguments.
- **Name.** `Review<PR>_<Topic>Test` in `java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/issues/` of the run copy (in the overlay: `extra/<label>/java/opendataloader-pdf-core/...`). Give the author a name that fits their pull request, for example `Issue<N><Description>Test`. Surefire runs only classes whose names match its patterns: a class name that surefire skips runs no test.
- **A posted test uses only what the author's branch has.** A helper that only `main` has (`TestSamples`) does not compile on the head: use `@TempDir` in a test that you give to the author.

## Files for the author

If PDFBox cannot build the structure, give the PDF files instead. `gh pr review` cannot attach files. When the review is ready to post, plan that the maintainer attaches them in the browser, in a comment after the review.
