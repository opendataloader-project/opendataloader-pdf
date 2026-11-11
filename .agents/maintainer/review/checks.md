# Checks of this repository

The commands run in an isolated run: the run copy is at `/work/tree`, and the outputs go to `/work/out` (the paths of the reference run tool). `$M`, `$H`, and `$C` are `main`, `head`, and `merge` of `pr.json`.

## Safety list of this repository

The generic safety checklist applies. These items are the ones that only this repository has. Read each one against each changed line.

1. Input: a PDF is the attacker input. Loops and recursion over PDF objects have a limit; a bad object is skipped; no allocation is sized from a document value.
2. Paths: no document or backend text in a file path.
3. Processes: the Python and Node wrappers start `java` with an argument list, not a shell string.
4. Network (hybrid mode): reads of the backend have a limit and a timeout; backend bodies with document text are not logged. The hybrid server response is an attacker input.
5. Output: HTML text goes through `escapeHtmlText` or `escapeHtmlAttribute`; Markdown text goes through `getCorrectMarkdownString`; links go through `formatMarkdownLinkDestination`.
6. Content safety: hidden, off-page, tiny, and hidden-layer text stays out of the default output.
7. Temporary files: `Files.createTemp*`, deleted in `finally`; tests use `@TempDir`.
8. Logging: no extracted text, alternative text, password, or backend body at INFO or higher. The exception stack traces were removed from the logs on purpose (commit e72622f0, 2026-08-07, no recorded reason): a change or a request that puts a stack trace back into a log is a precedent conflict until the maintainers decide the log policy.
9. Static state: reset between documents and copied to parallel workers (`StaticLayoutContainers`, `SerializerUtil`).
10. A `--password` value or an argument list does not appear in logs, messages, or exceptions.

## Regression inputs of this repository

- Each output format that the changed code reaches: JSON, Markdown (as a CommonMark renderer shows it), HTML, text, and tagged PDF.
- The other modes that reach the code: `--use-struct-tree`, `--hybrid`, other `--table-method` values.
- Documents without the problem: their output must stay the same, or stay valid.

## Linters (Q3-lint)

`main` already has findings, and CI does not run these tools. Compare the findings on `main` and on the merge, for the same list of changed files: a new finding is a count that increases for a file and a rule. List the changed files on the host with `git diff --name-only --diff-filter=AMR "$M" "$C"`. Run these in each run copy. Give the Python and Node files relative to their package folder:

```bash
(cd java && mvn -B -o -fn -pl opendataloader-pdf-core,opendataloader-pdf-cli checkstyle:check \
  -Dcheckstyle.includes='**/A.java,**/B.java') > /work/out/checkstyle.log 2>&1
grep -a -E '\.java:[0-9]+:' /work/out/checkstyle.log \
  | sed -E 's|^.*/(opendataloader-pdf-[a-z]+/src/)|\1|; s|:[0-9]+(:[0-9]+)?:|:|' | sort | uniq -c
(cd java && mvn -B -o -fn -DskipTests compile spotbugs:check \
  -Dspotbugs.onlyAnalyze=org.opendataloader.pdf.pkg.A,org.opendataloader.pdf.pkg.B) > /work/out/spotbugs.log 2>&1
grep -a -E '^\[ERROR\] (High|Medium|Low):' /work/out/spotbugs.log | sed -E 's/\[line [0-9]+\]//' | sort
cd python/opendataloader-pdf
ruff check <files> > /work/out/ruff.log 2>&1; echo "ruff exit $?" >> /work/out/ruff.log
black --diff <files> > /work/out/black.log 2>&1; echo "black exit $?" >> /work/out/black.log
cd /work/tree/node/opendataloader-pdf
pnpm exec eslint <files> > /work/out/eslint.log 2>&1; echo "eslint exit $?" >> /work/out/eslint.log
for f in <files>; do pnpm exec prettier "$f" > "/work/out/prettier.out" 2>&1; echo "prettier $f exit $?"; diff -u "$f" /work/out/prettier.out; done > /work/out/prettier.log 2>&1
```

- `-fn`: Maven does not skip the CLI module when core has findings. The exit code is then 0. Read the list. Do not use `-q` with checkstyle: it hides the findings.
- A tool counts only if its log shows that it ran. Each Maven log must contain the goal line (`--- checkstyle:` or `--- spotbugs:`) for `opendataloader-pdf-core` and for `opendataloader-pdf-cli`. If a goal line is missing, or the log contains `COMPILATION ERROR` or `Could not resolve`, the tool did not run. For the other tools, an exit line without a "not found" error shows that it ran, if the exit code is 0 or 1 (prettier: 0). Any other code is an error of the tool, for example a configuration error (ruff, eslint, prettier: 2; black: 123): the tool did not check the files.
- Skip generated files (`*.generated.ts`, `*_generated.py`).

## Related files (Q3-related)

| Change | The merge also changes |
|:--|:--|
| CLI option | `CLIOptions.OPTION_DEFINITIONS`; `LIST_OPTIONS` in `scripts/generate-options.mjs` for a comma-separated list; `options.json` and the generated files; `COVERED_OPTIONS` (or `HYBRID_OPTIONS`) in `verification/ci-verify.py`; `CLIOptionsTest` and `python/opendataloader-pdf/tests/test_cli_options.py`; the options section of `README.md` if users see the change |
| JSON structure | `schema.json`; `scripts/generate-schema.mjs` if it contains the element; the JSON example and the field table in `README.md`; `samples/json/lorem.json` |
| Silent-failure behavior: work that is skipped without an error (`CONTRIBUTING.md`, "Agent Skill Maintenance") | `skills/odl-pdf-maintenance/MAINTAINING.md` |
| `skills/odl-pdf/` | Nothing. The lint `skills/odl-pdf-maintenance/sync-skill-refs.py` of `main` exits 0 on the merge, in isolation |
| Dependency | The properties in `java/pom.xml`, or `pyproject.toml` or `package.json` and the lock file. Exact versions only. The pull request gives the dependency and its license |

A pull request does not change `content/docs/` (the release makes it) or `CHANGELOG.md` (the release notes come from pull request titles).

The generated files must agree with the merge. If the merge does not change `scripts/generate-options.mjs` or `scripts/utils.mjs`, copy them from `main` into the run copy of the merge. If it changes them (for example `LIST_OPTIONS` for a new list option), keep the merge's copies, and review their diff as code of the PR. Then run:

```bash
java -Djava.awt.headless=true -jar <merge jar> --export-options > /work/out/options.export.json \
  && diff /work/out/options.export.json options.json
G="node/opendataloader-pdf/src/cli-options.generated.ts node/opendataloader-pdf/src/convert-options.generated.ts python/opendataloader-pdf/src/opendataloader_pdf/cli_options_generated.py python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py"
mkdir -p /work/out/gen && for f in $G; do cp "$f" "/work/out/gen/${f##*/}"; done
node scripts/generate-options.mjs
for f in $G; do diff "/work/out/gen/${f##*/}" "$f"; done
```

Pass: each command exits with 0 and shows no diff lines.

## Fork CI

The repository settings make the CI of a fork pull request wait for a maintainer for all external contributors (`action_required`). The pr-reminders workflow (`scripts/pr-reminders.py`) sends the CLA and review-bot reminders.
