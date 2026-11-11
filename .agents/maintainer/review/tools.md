# Tools and settings for reviews of this repository

These tools are in this folder. For a review, use the copies of the `main` branch, never the copies of a pull request. A tool's refusal is final.

## Tools

| Tool | Proves or makes | Runs | Refuses | Eval |
|:--|:--|:--|:--|:--|
| `side_effects.sh` | The corpus of main (`samples/pdf` of the main tree, copied into the overlay of the run) converted by both jars to json, markdown, html, text, and tagged-pdf; the error diff and the changed outputs. The folders of the corpus and the PDFs at its top (`_top`) are converted. Run again with options that change the code path (for example `--table-method cluster`) in another output folder | isolated | a corpus inside the run copy (exit 2): a pull request can change its own samples | X6-side-effects-top X25-side-effects-names X26-corpus-refusal |
| `bench.py` | Scores for the Markdown that an isolated run made from the benchmark documents, and the comparison of two scores: a drop of a mean, a drop of more than 0.05 for a document, or more missing predictions fails; so does a missing or different benchmark commit. `score` deletes the prediction folder of the benchmark first: use a copy or a worktree of the benchmark under the work folder | host (the conversion runs isolated first) | different or missing `.commit` files | X22-bench-commit |
| `inspect_pdf.py` | The structure tree of tagged PDFs: element types, `/ID` values, `/IDTree`, dangling marked-content IDs, empty elements, and `Table > TR > TH/TD` with a child in each cell. Needs `qpdf` | isolated (it reads bytes that the PR's code made) | a PDF without a structure tree (exit 1) | X21-inspect-pdf |
| `md_lists.py` | The numbers of ordered lists as a CommonMark renderer shows them, against the written numbers. Run it with `uvx --from markdown-it-py==4.0.0 python md_lists.py FILE.md` | isolated (it reads Markdown that the PR's code made) | a shown number that differs from the written one (exit 1) | X24-md-lists-quote |

## Isolated runs

A run of the code of a pull request is isolated. It needs these settings for this repository:

| Setting | Value |
|:--|:--|
| Image | `maven:3.9-eclipse-temurin-17` (build it only from main) |
| Environment | two lines: `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true -Djava.io.tmpdir=/tmp` and `MAVEN_OPTS=-Duser.home=/tmp -Djava.awt.headless=true` |
| Folders that are not copied | `target/` |
| Cache | a host folder with the Maven repository, mounted read-only at `/cache` |
| More mounts | a wheelhouse for Python runs, if the check needs one |

- Maven runs offline (`-o`) from the cache: add `-Dmaven.repo.local=/cache`.
- Before the first full run of an overlay, run `test-compile` and one test on `main` and on the merge.
- Surefire reports are in `target/surefire-reports/`: copy them to `/work/out` in the same command as the tests. The suite comparison reads them. Tests that need a local port (hybrid tests) need a run environment that allows it.
- Maven logs can contain bytes that are not UTF-8: use `grep -a`.
- An option that names a network address (`--hybrid-url`) gets only `http://127.0.0.1:<port>`.
- `-Djava.awt.headless=true` is set for each Java run.

## Commands

| Name | Command |
|:--|:--|
| build | `./scripts/build-java.sh` (Java), `./scripts/build-python.sh`, `./scripts/build-node.sh` |
| test | `./scripts/test-java.sh`, `./scripts/test-python.sh`, `./scripts/test-node.sh` |
| benchmark run | `./scripts/bench.sh` |

Builds and tests need Java 17 or later and Maven; a change in `python/` or `node/` also needs Node, pnpm, and uv.

For the artifacts of a build, list the members of the jar, the wheel, or the npm package, and check that the expected paths are there and the unwanted ones are not (for example the `py.typed` marker in the wheel).

For the benchmark, convert the benchmark documents in an isolated run with the same arguments as the benchmark: `java -jar <jar> <bench>/pdfs --output-dir /work/out/md --format markdown --table-method cluster --image-output off --quiet`. Then score the output folder on the host with `bench.py score <bench> <md-folder> <evaluation.json>`, for `main` and for the merge, and compare with `bench.py compare base.json head.json`. In the conversion log, count the lines `Error converting` and `Exception` for both.
