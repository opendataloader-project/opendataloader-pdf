# Dependency upgrades

These rules are for an upgrade of the dependencies of this repository.

The question is not "does the build pass". The question is "is the output the same". The tests cover code paths. They do not measure how complete a conversion is. In one upgrade, a change of font metrics passed all tests, but the JSON output of a document became four times smaller and lost its paragraphs and headings. Only a byte comparison of the output found the problem.

## Package managers

| Manager | Location | Lockfile |
|:--|:--|:--|
| Maven | `java/pom.xml` and the modules `opendataloader-pdf-core` and `opendataloader-pdf-cli` | None |
| pnpm | `node/opendataloader-pdf/` | `pnpm-lock.yaml` |
| npm | `package.json` at the root | None. This file has scripts only (`npm run sync` makes `options.json`, the schema, and the bindings again). It has no dependency to upgrade |
| uv | `python/opendataloader-pdf/`, `python/opendataloader-pdf-mcp/` | `uv.lock` in each directory |
| pip | `examples/python/batch/`, `examples/python/rag/` | `requirements.txt` |

Scan the repository again before you make a plan. This table can be old.

## Notes for each manager

**Maven**

- Read the comments next to the version properties in `java/pom.xml` before a bump. They tell you why the versions of veraPDF, wcag-algorithms, PDFBox, and Jackson are pinned, and which versions must change together.
- The enforcer rule `ban-dynamic-versions` stops the build when a version range is resolved.
- `opendataloader-pdf-cli` makes a shaded jar. If you declare a transitive compile dependency again with the `test` scope, the shaded jar loses the classes of that dependency. The unit tests pass, and the jar fails when it runs. After a change in that module, run the jar.
- After a bump, make sure that one group has one version:

```bash
mvn -f java/pom.xml dependency:tree -Dverbose -Dincludes=org.verapdf
```

**pip (examples)**

The two `requirements.txt` files are for example code. An import smoke test is sufficient. Say in the report that the check was only an import.

## Tiers

| Tier | Packages | Gate |
|:--|:--|:--|
| 1. Development tools | eslint, prettier, pytest, ruff, `@types/*` | Build and tests |
| 2. Runtime | commander, fastapi, mcp | Tier 1, and the sample comparison |
| 3. Output | Pillow, torchvision, docling, veraPDF, wcag-algorithms, PDFBox, scikit-image, easyocr, rapidocr, python-pptx, and each other package that touches fonts, images, or parsing | Tier 2, and the benchmark corpus |
| Major version | Each package | The tier 3 gate |

If you are not sure about a tier, ask: can the output of the package reach a rendered page or an extracted string? If yes, use tier 3.

Build and test commands:

```bash
./scripts/build-java.sh && ./scripts/test-java.sh
./scripts/build-python.sh && ./scripts/test-python.sh
./scripts/build-node.sh && ./scripts/test-node.sh
```

## Corpora

| Corpus | Size | Location | Use |
|:--|:--|:--|:--|
| Samples | 22 PDF files | `samples/pdf/` | Tier 2 |
| Benchmark | 200 PDF files with ground truth | The repository that `scripts/bench.sh` clones (`pdfs/`, `ground-truth/`) | Tier 3 and major versions |

## Baseline

Make the baseline on `main` before you make the upgrade branch. Keep it out of the repository: it is scratch data for one comparison.

```bash
./scripts/build-java.sh
JAR=$(find java/opendataloader-pdf-cli/target -maxdepth 1 -name 'opendataloader-pdf-cli-*.jar' \
  ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar')
BASE="$TMPDIR/deps/baseline"; mkdir -p "$BASE" "$TMPDIR/deps/jtmp"
java -Djava.io.tmpdir="$TMPDIR/deps/jtmp" -Djava.awt.headless=true -jar "$JAR" samples/pdf -o "$BASE" -f json -q
```

- Set `java.io.tmpdir` to a directory that you can write. The conversion writes font metrics and page content to temporary files. If it cannot write them, the JSON looks correct, but most of the text is missing.
- For a bump of veraPDF, wcag-algorithms, or PDFBox, add `pdf` to `-f`, and compare the PDF files too. The comment in `java/pom.xml` tells you that the tests pass with a PDFBox version that does not agree with wcag-algorithms.
- Make the output of the upgrade branch with the same command in a second directory (`AFTER`).

## Comparison

Do the byte comparison first. It needs no interpretation.

```bash
for f in "$BASE"/*.json; do
  cmp -s "$f" "$AFTER/$(basename "$f")" || echo "DIFFERS: $(basename "$f")"
done
```

Identical output is the strongest result. If a file differs, count the element types before you decide. The file size does not show what changed.

```bash
python3 -c "
import json, sys
def counts(p):
    acc = {}
    def walk(o):
        if isinstance(o, dict):
            if o.get('type'): acc[o['type']] = acc.get(o['type'], 0) + 1
            for v in o.values(): walk(v)
        elif isinstance(o, list):
            for v in o: walk(v)
    walk(json.load(open(p))); return acc
a, b = counts(sys.argv[1]), counts(sys.argv[2])
for k in sorted(set(a) | set(b)):
    if a.get(k, 0) != b.get(k, 0): print(f'{k}: {a.get(k, 0)} -> {b.get(k, 0)}')
" "$BASE/doc.json" "$AFTER/doc.json"
```

- A smaller count of `paragraph`, `heading`, or `list item` is a regression, whatever the tests say.
- A small change in the count of images or table cells can be correct. Examine it. Do not decide it automatically.

## Benchmark

For tier 3 and for major versions, run the benchmark after the comparison.

```bash
./scripts/bench.sh --check-regression
```

It reports NID (reading order), TEDS (table structure), MHS (heading structure), the F1 of table detection, and the speed.

The numbers change between runs. A small difference is noise. A decrease in the same direction in each run is a finding. Give the conditions of the run with each number.
