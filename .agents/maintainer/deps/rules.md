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
T="${TMPDIR:-/tmp}/deps"; BASE="$T/baseline"; rm -rf "$BASE"; mkdir -p "$BASE" "$T/jtmp"
cp "$(find java/opendataloader-pdf-cli/target -maxdepth 1 -name 'opendataloader-pdf-cli-*.jar' \
  ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar')" "$T/base.jar"
JAR="$T/base.jar"
java -Djava.io.tmpdir="$T/jtmp" -Djava.awt.headless=true -jar "$JAR" samples/pdf -o "$BASE" -f json -q \
  || echo "the conversion failed: do not compare this output"
```

- Set `java.io.tmpdir` to a directory that you can write. The conversion writes font metrics and page content to temporary files. If it cannot write them, the JSON looks correct, but most of the text is missing.
- For a bump of veraPDF, wcag-algorithms, or PDFBox, add `pdf` to `-f`, and compare the PDF files too. The comment in `java/pom.xml` tells you that the tests pass with a PDFBox version that does not agree with wcag-algorithms.
- Each output directory must be empty before its run, and each run must end without an error. Do not compare an output of a run that failed.
- On the upgrade branch, build again, copy the jar to `$T/upgrade.jar`, set `JAR="$T/upgrade.jar"`, and make the output with the same command in a second, empty directory (`AFTER`). The copies keep the two jars apart: a build replaces the jar in `target/`.

### Packages of the hybrid backend

The command above does not use the hybrid backend. An upgrade of a Python package that the `hybrid` extra installs (docling, easyocr, torchvision, Pillow, and the other packages in `python/opendataloader-pdf/uv.lock`) cannot change that output. For such a package, also compare the output of hybrid mode.

```bash
curl -sS -m 5 -o /dev/null http://127.0.0.1:5002/health && echo "a server answers on port 5002 already: stop it first"
(cd python/opendataloader-pdf && uv run --extra hybrid opendataloader-pdf-hybrid --host 127.0.0.1 --port 5002 > "$T/hybrid.log" 2>&1 & echo $! > "$T/hybrid.pid")
ready=no
for i in $(seq 120); do
  kill -0 "$(cat "$T/hybrid.pid")" 2>/dev/null || break
  curl -sS -m 5 -o /dev/null http://127.0.0.1:5002/health && { ready=yes; break; }
  sleep 5
done
if [ "$ready" = yes ]; then
  rm -rf "$T/hybrid-baseline"
  java -Djava.io.tmpdir="$T/jtmp" -Djava.awt.headless=true -jar "$JAR" samples/pdf -o "$T/hybrid-baseline" -f json -q \
    --hybrid docling-fast --hybrid-mode full --hybrid-url http://127.0.0.1:5002 \
    || echo "the conversion failed: do not compare this output"
else
  echo "the server did not start:"; tail -20 "$T/hybrid.log"
fi
kill "$(cat "$T/hybrid.pid")" 2>/dev/null
```

- The port must be free before the start. If another server answers on it, the health check passes for that server, and the output does not come from this checkout.
- The conversion runs only if this server is alive and answers in 10 minutes. If it does not, the end of `$T/hybrid.log` is printed. Do not go on.
- `--hybrid-mode full` sends each page to the backend. With the default mode, the backend gets only some pages.
- Do not add `--hybrid-fallback`. A backend error must stop the run, or the output is the Java output again.
- Make the hybrid baseline two times, in two directories. If the two outputs differ, that difference comes from the backend and not from an upgrade. Compare the upgrade only where the two baselines agree.
- For an OCR package, start the server with `--force-ocr` in each run.
- Start the server of the upgrade branch from that branch, so that it uses the upgraded lockfile. After each run, make sure that no server answers on the port.

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

For tier 3 and for major versions, run the benchmark after the comparison. Run it for the jar of `main` and for the jar of the upgrade, and compare the two scores. `--check-regression` alone compares with fixed thresholds: a score can drop and stay above them.

```bash
BENCH="$T/bench"    # a clone of the benchmark for this comparison only; bench.sh makes it
bench_one() {       # bench_one <name> <absolute path of the jar>
  rm -rf "$BENCH/prediction/opendataloader/markdown" "$BENCH/prediction/opendataloader/evaluation.json"
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.awt.headless=true -Djava.io.tmpdir=$T/jtmp" \
    BENCH_DIR="$BENCH" ./scripts/bench.sh --skip-build --check-regression --force --jar-path "$2"
  cp "$BENCH/prediction/opendataloader/evaluation.json" "$T/bench-$1.json"
  git -C "$BENCH" rev-parse HEAD > "$T/bench-$1.json.commit"
}
bench_one base "$T/base.jar"
bench_one upgrade "$T/upgrade.jar"
python3 -I .agents/maintainer/review/bench.py compare "$T/bench-base.json" "$T/bench-upgrade.json"
python3 -c "import json, sys; print('missing predictions:', [json.load(open(p))['metrics'].get('missing_predictions') for p in sys.argv[1:]])" "$T/bench-base.json" "$T/bench-upgrade.json"
```

- Give `--jar-path` an absolute path. `bench.sh` changes to the folder of the benchmark before the path is read.
- `JAVA_TOOL_OPTIONS` gives the Java of the benchmark the same temporary directory as the sample runs.
- `--force` makes the benchmark convert the documents again. Without it, the benchmark skips the conversion when an `evaluation.json` exists, and it scores old predictions.
- `bench.py compare` exits 1 if the two runs used different revisions of the benchmark, if a mean drops, if a document drops more than 0.05, or if the upgrade misses more predictions than `main`.
- The two counts of missing predictions must be 0. If both runs miss the same documents, `bench.py compare` does not fail.
- The benchmark runs the jar only. It does not show a change of a package of the hybrid backend.

The benchmark reports NID (reading order), TEDS (table structure), MHS (heading structure), and the speed. For this converter, its log prints `F1: N/A` for table detection, so there is no F1 to compare.

The numbers change between runs. A small difference is noise. A decrease in the same direction in each run is a finding. Give the conditions of the run with each number.
