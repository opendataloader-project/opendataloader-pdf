# Rules for issue work in opendataloader-pdf

These rules are for an agent that fixes one GitHub issue of this repository and opens a pull request. The harness (`fix_gates.py`) and its documentation (`harness.md`) are in this folder. The harness decides when the work is done. These rules give the facts that the procedure needs: tools, commands, tests, samples, files to update, and the troubleshooting list.

## 1. Tools

```bash
java -version          # 17 or later (code targets Java 11)
mvn -v
python3 --version      # 3.11 or later
uv --version
gh auth status
curl --version         # 8.4.0 or later, for attachments and hybrid
node -v; pnpm -v       # only for changes in java/, python/, node/, or CLI options (Node.js 22.13 or later)
qpdf --version         # to inspect an original or a tagged PDF
```

Network hosts: github.com, api.github.com, *.githubusercontent.com, pypi.org, files.pythonhosted.org, repo.maven.apache.org, artifactory.openpreservation.org (veraPDF), registry.npmjs.org. For hybrid: huggingface.co, *.hf.co.

## 2. Downloads

These commands download packages that are not in a local cache. Show this list to the user before you run the first of them. Get one approval for the full list.

- `uvx`: the latest release and the release that the reporter used. `ruff` and `black` for changes in `python/` (the `static` gate).
- `python3 -m pip download`: an old release, only if `uvx` cannot run it (see "Troubleshooting").
- `uv run` and `scripts/build-python.sh`: Python packages. `build-python.sh` installs the `hybrid` extra (docling, several GB).
- `pnpm install`, `scripts/build-node.sh`, and `scripts/test-node.sh`: Node packages, for changes in `java/`, `python/`, `node/`, or CLI options. The `wrappers` gate builds the Python and the Node wrapper for each of these changes.
- Maven: dependencies and plugins.
- `scripts/bench.sh`: the bench repository and its Python packages, for changes in `java/`.
- If the reporter used `hybrid`: the `hybrid` extra in `$WORK/hybrid-venv`, and the models from huggingface.co.

## 3. Base branch, remote, branch name

- The base branch is `main`. Make the work branch from `$UPSTREAM/main`, also in a fork. The `main` branch of a fork can be old.
- `<type>` of the branch name: the Conventional Commit type (`fix`, `feat`, `perf`, `docs`).
- Branch name: `<type>/<issue>-<slug>`, `<slug>` is two to five lowercase words with hyphens. Example: `fix/1000-table-before-figure`.
- A project branch with the issue number (`$UPSTREAM/<issue>-*` or `$UPSTREAM/*/<issue>-*`) means that another person claims the issue.

## 4. Commands for each build

| Name | Command |
|:--|:--|
| release | `uvx opendataloader-pdf@latest` |
| release, Java API | the failing test in `$WORK/release-src` (see "Reproduce") |
| base | `java -Djava.awt.headless=true -jar "$WORK/base.jar"` |
| fix | `java -Djava.awt.headless=true -jar "$WORK/fix.jar"` |

- The `base-build` gate writes `$WORK/base.jar`. It refuses a base tree with changes, and it never selects `original-*.jar`.
- Write commands in full. Do not put a command line in a variable. zsh does not split the variable into words.
- The CLI has no `--version` option. To get the release version, run `uvx --from opendataloader-pdf@latest python -c "import importlib.metadata as m; print(m.version('opendataloader-pdf'))"`. In each GitHub post and each commit message, write the release version in each command. Do not write `@latest`.
- Use the reporter's version only if it has the form `<digits>.<digits>.<digits>`, for example `2.5.12`. If it does not, ask the user.

## 5. Reproduce

Use the reporter's document from `$WORK`. If there is no document, make the sample first (see "Reproduction samples").

```bash
uvx opendataloader-pdf@latest <options> -o "$WORK/release" <document>.pdf
java -Djava.awt.headless=true -jar "$WORK/base.jar" <options> -o "$WORK/main" <document>.pdf
uvx opendataloader-pdf@<reporter's version> <options> -o "$WORK/reported" <document>.pdf   # if different
```

If the reporter used the Python API or the Node API, reproduce with that API too.

If the reporter used the Java API, the CLI cannot call it. The failing test is then the reproduction. Write it first. Then run it on the source of the release, and use this result as the release column:

```bash
git worktree add --detach "$WORK/release-src" "v<release version>"
cp <test file> "$WORK/release-src/<test file>"     # at the same path
(cd "$WORK/release-src" && ./scripts/test-java.sh -pl opendataloader-pdf-core "-Dtest=<Class>")
```

Copy each new test file. Also copy each test file that the test uses and that the release does not have (for example, the test class of an earlier issue whose methods it calls). For a test in `opendataloader-pdf-cli`, add `-am` and `-Dsurefire.failIfNoSpecifiedTests=false`. The test must compile on the release (see "New API"). A compile error is not a reproduction. If the reporter's version is different, do the same with its tag. At the end of the work, remove this worktree with `git worktree remove --force "$WORK/release-src"`: the copied test files are untracked.

### Hybrid

If the reporter used `hybrid`, install the `hybrid` extra in `$WORK/hybrid-venv`. Do not install it in the Python of the user. The `hybrid` extra installs docling (several GB). The server downloads models from huggingface.co. Start the server with `--host 127.0.0.1`. Without this option, the server listens on all network interfaces.

```bash
[ -x "$WORK/hybrid-venv/bin/python" ] || uv venv "${WORK:?}/hybrid-venv"
uv pip install --python "$WORK/hybrid-venv/bin/python" "opendataloader-pdf[hybrid]==<release version>"
"$WORK/hybrid-venv/bin/opendataloader-pdf-hybrid" --host 127.0.0.1 --port 5002 > "$WORK/hybrid.log" 2>&1 &
echo $! > "$WORK/hybrid.pid"
```

- The server runs in the background. Wait until `curl -sS http://127.0.0.1:5002/health` answers.
- Add the server options that the reporter used. Put each value from the issue in single quotes.
- For `--hybrid-url`, use only `http://127.0.0.1:<port>`.
- If the fix changes `python/opendataloader-pdf/src/opendataloader_pdf/hybrid_server.py`, start a second server from the repository for the fix runs, in the background: `(cd python/opendataloader-pdf && uv run --extra hybrid opendataloader-pdf-hybrid --host 127.0.0.1 --port 5003 > "$WORK/hybrid-fix.log" 2>&1 & echo $! > "$WORK/hybrid-fix.pid")`. Wait until `curl -sS http://127.0.0.1:5003/health` answers. For the fix runs, use `--hybrid-url http://127.0.0.1:5003`: in the contract, give each reporter run `fix_options`. `fix_options` replaces `options` for the fix runs: copy the complete list of `options` into it, and change only the URL.
- Before the final report, stop each server that you started: `kill "$(cat "$WORK/hybrid.pid")"`, and `kill "$(cat "$WORK/hybrid-fix.pid")"` for the second server. Then make sure that no server answers on port 5002 or 5003.

## 6. Tests

- **Location**: Put a unit test in the package of the defective code, under `src/test/java/org/opendataloader/pdf/` in `opendataloader-pdf-core` (or `-cli`). Put a test that processes one sample from input to output in `src/test/java/org/opendataloader/pdf/issues/`. Name it `Issue<N><Description>Test` (example: `Issue336UndersegmentedTableTest`). For a fix in the Python or the Node wrapper (also `hybrid_server.py`), put the test in `python/opendataloader-pdf/tests/` or `node/opendataloader-pdf/test/`, and give its case the `runner` `pytest` or `vitest`. A change of Java main code needs at least one JUnit case: the pinning gate reverts Java files and runs the JUnit cases.
- **Sample**: Use the first rung in "Reproduction samples" that shows the symptom. Write the sample and the output to `TestSamples.emptyDirectory("issue-<N>")`. The files stay in `target/test-samples/issue-<N>/`, so you can open them. If the symptom occurs with all PDFs, use `TestSamples.writeTextPdf`. It puts the same text on each page. With more than one page, this text becomes a header (see "Repeated text").
- **New API**: If the fix adds a method, a test that calls it does not compile on `before`. Write the call so that the test compiles on both builds:
  - If the new method implements an interface that `before` has, call it through that interface: `Object result = extraction; if (result instanceof AutoCloseable) { ((AutoCloseable) result).close(); }`
  - If no interface fits, call the method by reflection. In the `catch` block for `NoSuchMethodException`, fail with a message that names the symptom.
  - Put the assertions after the `if` block or the `try` block, not in it. An assertion in the block does not run on `before`, so the test passes there and checks nothing.
  - Assert the effect of the call (the symptom), for example that the document is closed. Then the test fails on `before`, where the call does not occur, with the symptom of the reporter.
- **Guards, example**: The first fix of #623 put the two header items of a page in one element. Its box covered both body columns, and the default output mixed the columns. A guard for "two header items far apart on a two-column page", written before the fix, finds this in the first run. A guard for an unchanged output: without `--include-header-footer`, the output has no header text.
- **Mutation case, example**: In #706, the loop reads the default chunk size, not the configured one. The mutation patch puts this defect into the base tree. Example of an issue that asks for tests of several things in one pull request: #854 (four issues that ask for tests of one feature).
- **Input variants for guards**: counts (one, two, many), positions (top, margin, between columns), pages (one, two), layouts (one column, two columns), and each related option on and off.
- **Misses**: for `misses`, name JUnit classes or methods, pytest files or node ids, or vitest files.

## 7. Files to update with a change

| Change | Update |
|:--|:--|
| CLI option | Change `CLIOptions.OPTION_DEFINITIONS`. For a comma-separated list, also add it to `LIST_OPTIONS` in `scripts/generate-options.mjs` before the sync. Run `npm run sync`. Commit `options.json` and the four generated files. Add the option and a check to `COVERED_OPTIONS` (or `HYBRID_OPTIONS`) in `verification/ci-verify.py`. Update `CLIOptionsTest` and `python/opendataloader-pdf/tests/test_cli_options.py`. If users see the change, update the options section of `README.md` |
| JSON structure | Update `schema.json` by hand. If `scripts/generate-schema.mjs` contains the element, update it. Update the JSON example and the field table in `README.md`. Update `samples/json/lorem.json` |
| Silent-failure behavior (added, removed, changed) | Update `skills/odl-pdf-maintenance/MAINTAINING.md` |
| `skills/odl-pdf/` | Run `python3 skills/odl-pdf-maintenance/sync-skill-refs.py`. Fix the text, not the allowlist |
| Dependency | Change the properties in `java/pom.xml`, or `pyproject.toml` or `package.json` and the lock file. Use exact versions only. Give the dependency and its license in the pull request |

- Do not change version numbers. Do not change generated files by hand: the commands above make them again.
- Do not commit `content/docs/`. The release makes it. Do not edit `CHANGELOG.md`. The release notes come from pull request titles.
- If the change touches CLI options or the JSON structure, also run these commands after the `build` gate. Pass: both commands exit with 0 and show no diff lines. The npm banners and the `Generated:` lines are normal.

```bash
java -Djava.awt.headless=true -jar "$WORK/fix.jar" --export-options > "$WORK/options.export.json" && diff "$WORK/options.export.json" options.json
npm run generate-options && npm run generate-schema && git diff --exit-code -- \
  options.json node/opendataloader-pdf/src/*.generated.ts python/opendataloader-pdf/src/opendataloader_pdf/*_generated.py
```

## 8. Clean-code checklist

- New code agrees with the code around it and with `.editorconfig`.
- Each new Java file starts with the header in `LICENSE_TEMPLATE/license.txt`.

## 9. Commit and pull request

- This repository merges by rebase and by squash. Make one commit for each logical change. Usually this is one commit. Generated files can have a separate commit.
- Commit with `git commit -s`. Do not write `Signed-off-by` yourself.
- Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary): `fix(<scope>): <change for the user, lowercase, no period>`. `<scope>` comes from `git log --format=%s -100` (examples: `hybrid`, `cli`, `core`, `tagging`, `filter`).
- The commit link line is `Fixes https://github.com/opendataloader-project/opendataloader-pdf/issues/<N>`.
- Agent trailer: keep the co-author trailer that your agent adds. If it adds none, write `Assisted-by: <agent> (<model>)`.
- Pull request body: keep the headings of the pull request template. The sections are Objective, Approach, Evidence, Review notes, AI assistance, then the resolved issue and the checklist:

```markdown
## AI assistance

Drafted by <agent> (<model>). Reviewed by <user's GitHub handle>, who signed off the commits.

**Issue resolved by this Pull Request:**
Resolves #<N>

**Checklist:**

- [ ] Documentation has been updated, if necessary.
- [ ] Examples have been added, if necessary.
- [ ] Tests have been added, if necessary.
```

- If a checklist item is not necessary, tick it. Give the reason in the description.
- A sub-issue draft uses the sections of `.github/ISSUE_TEMPLATE/bug_report.md`.
- A fork clone: `--head` of `gh pr create` does not accept an organization as owner. For a fork that an organization owns, use `gh api "repos/$REPO/pulls" -f base=main -f head="<org>:<branch>" -f title='<title>' -F body=@"$WORK/pr.md"`.
- **Test & Benchmark** runs for changes in `java/`, `python/`, `node/`, `scripts/`, `verification/`, `samples/`, `.github/workflows/`. **Skill Lint** and **Skill Smoke Test** run for `skills/odl-pdf/` and `skills/odl-pdf-maintenance/`. Other changes have no checks.
- The CI benchmark does not run your jar (the `bench` gate does). It is not evidence.

## 10. Reproduction samples

The original stays in `$WORK`. The repository gets only what you make: the code that makes the sample, not the PDF.

| What | Where | Committed |
|:--|:--|:--|
| The code that makes the sample | The test class (`src/test/java/.../issues/Issue<N>...Test.java`) | Yes |
| The PDF and the outputs that the test makes | `java/<module>/target/test-samples/issue-<N>-<name>/` (Git ignores it) | No |
| A PDF that a test cannot make, and its generator (rung 3) | `java/opendataloader-pdf-core/src/test/resources/issues/` | Yes |
| A backend response (rung 4) | In the test, or `src/test/resources/issues/issue-<N>-<slug>.json` | Yes |
| The reporter's document | `$WORK/attachments/` | Never |

The `issue-corpus` gate runs the `Issue*Test` classes of the base tree and collects their PDFs. The `native-issue-samples` invariant converts them with both jars. Thus the samples of earlier fixes are a regression corpus for each later fix, without PDFs in the repository.

Use the first rung that shows the symptom:

| Rung | Form | Use for | Example in the code |
|:--|:--|:--|:--|
| 1 | Objects built in the test (`TextChunk`, `BoundingBox`, `TableBorder`) | Reading order, tables, layout, text spacing | `TableBorderProcessorTest`, `XYCutPlusPlusSorterTest` |
| 2 | PDF built in the test with PDFBox | Tables, fonts, damaged or encrypted files, images, scans, structure trees | `Issue336UndersegmentedTableTest`, `CidFontDetectionTest`, `ScannedPagePdf`, `HybridReferenceTrailerTest` |
| 3 | Small PDF and its generator, both committed | Structures that the test cannot build | — |
| 4 | Backend response JSON | Hybrid mode | `DoclingSchemaTransformerTest` |
| 5 | Large PDF made during the test | Size, memory, time | — |
| 6 | No sample | No rung above shows the symptom | — |

Rungs 1 and 2 fit almost all bugs. PDFBox can make nearly any PDF structure. A test can also write raw bytes for a damaged file (see `HybridReferenceTrailerTest`).

**Find the trigger.** A bug usually comes from the structure, not from the content. Open the original locally: `qpdf --qdf --object-streams=disable original.pdf "$WORK/qdf.pdf"`. Remove pages and objects until the symptom stops. Make that structure again with neutral content. Do not redact the original. Metadata, font subsets, alt text, ActualText, and attachments can keep its content.

**Rules for each sample:**

- Measurements: Copy the measurements of the trigger from the original: spacing, font size, gaps, counts, and sizes. If the code has a threshold, make the sample cross it. For example, veraPDF uses a temporary file only for a stream larger than 10240 bytes. A sample that has only the kind of structure can miss regressions. The first #336 sample had a 25 pt row pitch, but the reporter had 12 pt. It did not catch changes to the row tolerances.
- Fonts: Use the standard 14 fonts, or an OFL font with its license. PDFBox ships LiberationSans (SIL OFL) at `/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf` (see `CidFontDetectionTest`). Do not use a system font.
- Content: Use generated text and images. If a script is the trigger (Korean, Chinese, right-to-left), use short neutral text in that script.
- Repeated text: Text near the top or the bottom of the page becomes a header or footer if it is the same on each page, or if only a number in it changes. The default output leaves out headers and footers (`--include-header-footer` adds them). A tagged PDF marks them as artifacts, without an `/MCID`. If the test needs body text, give each page different text. Check the element that the test is about: its type in the JSON, or an `/MCID` on its text in the tagged PDF. An `/MCID` somewhere on the page does not prove it.
- Symptom: Run `before` on the sample. It must show the same symptom as the original. If it does not, add structure until it does.
- Output: Write the sample and the output to `TestSamples.emptyDirectory("<name>")`. The name uses lowercase letters, digits, and hyphens. Git ignores `target/test-samples/`.

**Extra rules for committed files (rungs 3 and 4):**

- Location: Put the files in `java/opendataloader-pdf-core/src/test/resources/issues/`. Name them `issue-<N>-<slug>.pdf` and the generator `issue-<N>-<slug>.*`, or `.json` for rung 4.
- Header: In the generator header, write the issue link, the trigger, the expected result, and the word "synthetic".
- Same bytes: The generator must give the same bytes at each run. Pin library versions. Fix dates and IDs. Do not use random values.
- Size: Each committed file is 100 KB or smaller. If a file is larger, use rung 5.

**Rung 5.** Make the document in the test, not in the repository. For a memory problem, use a smaller heap and a smaller document in a separate JVM (`-Xmx64m`). For a time problem, compare ratios (2N pages against N pages), not seconds. Keep slow tests out of the default run. In the pull request, give the command that runs them.

**Rung 6.** Stop and ask the user. If the user agrees, write in the pull request: "Reproduced locally on the reporter's document (not shared). No sample reproduces it: <reason>." Add the lowest test that you can (rung 1 or 4). In the final report, include a comment draft that asks the reporter to check the fix.

A sample cannot show how often a threshold is wrong on real documents. For threshold changes, the benchmark (the `bench` gate) is the gate.

## 11. Sandbox and dependencies of the root cause

- The root cause can be in a dependency (veraPDF, docling). Then show where the cause is, and ask the user: work around it, report it upstream, or both.
- Signs of a sandbox block that are specific to the tools here: a `system-configuration` panic of uv, and `Unknown host` for a host in the network list of section 1.

## 12. Troubleshooting

| Symptom | Fix |
|:--|:--|
| JVM exit 3 "Cannot use the temporary directory", or Maven tests hang after `com.apple.hiservices-xpcservice` | `export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.io.tmpdir=$TMPDIR -Djava.awt.headless=true"` |
| `-pl opendataloader-pdf-cli` cannot resolve dependencies | Add `-am` |
| `SocketException: Operation not permitted` | The environment blocks local ports. Run the same tests in `$WORK/base-src`. List the classes that fail on both. A failed `build-java.sh` leaves no jar. Build one with `-DskipTests`. In Claude Code, `sandbox.network.allowLocalBinding: true` allows local ports |
| pip: `SSLCertVerificationError('OSStatus -26276')` | The sandbox blocks the macOS certificate store. Add `--use-deprecated=legacy-certs`. Behind a company proxy, set `PIP_CERT` to the company certificate bundle |
| `uvx` cannot run an old release | If the approval for downloads (section 2) does not include this download, ask the user first. Run `python3 -m pip download "opendataloader-pdf==<version>" --no-deps --only-binary=:all: -d "$WORK/old-release"`. Extract `opendataloader_pdf/jar/opendataloader-pdf-cli.jar` from the wheel into `$WORK/old-release`. Run it with `java -Djava.awt.headless=true -jar` |
| uv: `Failed to initialize cache` | Run `export UV_CACHE_DIR="$TMPDIR/uv-cache"`. If uv then panics (`system-configuration`), report the step as blocked |
| Bench: `Pipeline failed: 'brand_raw'` | The sandbox blocks `sysctl`. No new `evaluation.json` exists. Report the step as blocked |
| Two jars match the glob | Build again with `clean` |
| A gate that runs the hybrid tests fails with "Hybrid server is not available at http://localhost:<port>" | The sandbox blocks local HTTP for MockWebServer. Run the gate outside the sandbox |
