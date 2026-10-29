# The harness: contract, gates, records

`scripts/fix_gates.py` decides when a fix is done. You write the contract. The user approves it. The tool runs the gates and computes the result. Do not decide "done" yourself.

```bash
FG=.agents/skills/odl-fix/scripts/fix_gates.py
python3 -I "$FG" template --issue "$N" > "$WORK/contract.json"   # a skeleton
python3 -I "$FG" validate --work "$WORK"                          # exit 2: not valid
python3 -I "$FG" gate problem --work "$WORK"                      # Phase 2
python3 -I "$FG" freeze --work "$WORK"                            # after Approval 1
python3 -I "$FG" run --work "$WORK"                               # Phase 3: the required gates
python3 -I "$FG" check --work "$WORK"                             # Phase 4: DONE or NOT DONE (exit 13)
python3 -I "$FG" report --work "$WORK"                            # $WORK/evidence.md
python3 -I "$FG" pr-watch --work "$WORK" --pr <PR>                # Phase 5, after the push
python3 -I "$FG" check --work "$WORK" --final                     # before the final report
```

Exit codes: 0 pass, 2 a usage or contract problem, 3 an environment problem (a tree with uncommitted changes, no base tree, no jar), 13 a gate failed or the work is not done.

The tool reads `SRC` from `$WORK/env.sh` (the fix tree). `--src` sets it. The base tree is `$WORK/base-src`. `--offline` adds `-o` to the Maven commands that the tool starts (not to `scripts/build-java.sh`).

## Contract (`$WORK/contract.json`)

```json
{
  "issue": 623,
  "objective": "\"... Passing `--include-header-footer` makes no difference for these items\"",
  "scope": ["java/opendataloader-pdf-core/src/main/java/org/opendataloader/pdf/hybrid/DoclingSchemaTransformer.java"],
  "cases": [
    {"id": "P1", "kind": "problem", "test": "Issue623HybridHeaderFooterTest#backendHeaderAndFooterAreOutputWithIncludeHeaderFooter",
     "symptom": "lost the page header or footer of the backend"},
    {"id": "G1", "kind": "guard", "test": "Issue623HybridHeaderFooterTest#backendHeaderAndFooterStayOutWithoutIncludeHeaderFooter",
     "why": "without the flag, no header or footer text"},
    {"id": "G2", "kind": "guard", "test": "Issue623HybridHeaderFooterTest#headerItemsAtSeparatePlacesKeepTheBodyColumnOrder",
     "why": "variant: two header items far apart on a two-column page"}
  ],
  "support": [],
  "reporter": {"document": "attachments/a1.pdf", "runs": [
    {"id": "R1", "options": ["--hybrid", "docling-fast", "--hybrid-url", "http://127.0.0.1:5012", "--hybrid-mode", "full",
                             "--include-header-footer", "--pages", "1"],
     "format": "json", "base": {"types": {"header": "=0"}}, "fix": {"types": {"header": ">0", "footer": ">0"}}},
    {"id": "R2", "options": ["--hybrid", "docling-fast", "--hybrid-url", "http://127.0.0.1:5012", "--hybrid-mode", "full", "--pages", "1"],
     "format": "json", "fix_equals_base": ["json:id"]}
  ]},
  "invariants": [
    {"id": "hybrid-default", "corpus": "samples/pdf",
     "options": ["--hybrid", "docling-fast", "--hybrid-url", "http://127.0.0.1:5012", "--hybrid-mode", "full"],
     "ignore": ["json:id", "text:blank-lines"], "allow": []}
  ],
  "amendments": []
}
```

| Field | Content |
|:--|:--|
| `issue`, `objective` | The issue number, and the reporter's pain point, quoted |
| `scope` | The files that the fix can change, other than tests. Globs are allowed (`*` matches `/` too). Generated files and documentation that the fix must update are in the scope too |
| `cases` | Tests. `runner` `junit` (the default): `Class#method`, and `module` is `opendataloader-pdf-core` (the default) or `opendataloader-pdf-cli`. `runner` `pytest`: `tests/<file>.py::[Class::]test` in `python/opendataloader-pdf`, with `extras` (for example `["hybrid"]`). `runner` `vitest`: `test/<file>.test.ts::<test name>` in `node/opendataloader-pdf` |
| `cases[].kind = problem` | Fails on the base with `symptom` in its failure message, passes on the fix. At least one |
| `cases[].kind = guard` | Passes on the base and on the fix. A variant of the input, or an output that must not change. `why` says which |
| `support` | Other test files that the case tests need and that the base does not have. The problem gate copies them too |
| `reporter` | The reporter's document in `$WORK` and the runs on it. Not committed, not quoted |
| `reporter.runs[]` | `options` (a list; the tool adds `-f`, `-o`, and the document), `fix_options` (for fix.jar only, for example the second hybrid server of a `hybrid_server.py` fix), `format` (`json`, `markdown`, `html`, `text`), `base` and `fix` expectations, `fix_equals_base` (normalizers) |
| expectations | `types` (JSON element counts: `=N`, `>N`, `>=N`, `<N`, `<=N`), `contains` and `absent` (text). A failed expectation is reported by its name, never with document text |
| `invariants` | Corpus runs of `side_effects.sh` with base.jar and fix.jar. `corpus` is a folder of the base tree, or `@issue-samples`. `options`, `formats` (default: all), `ignore` (normalizers), `allow` |
| `allow[]` | `file` (a glob of an output path), `reason`, `amendment` (its `n`). A difference that the user accepted |
| `amendments[]` | `n` (1, 2, ...), `what`, `why`. One for each change after the freeze |

For each change of Java main code, the tool adds two invariants that you do not write: `native-samples` (`samples/pdf`, default options) and `native-issue-samples` (`@issue-samples`). To allow a difference in them, write an invariant with that `id`.

Options: `--hybrid-url` must be `http://127.0.0.1:<port>`. `-o`, `--output-dir`, `-f`, and `--format` are refused: the tool sets them. The tool starts each command with an argument list, without a shell.

### Normalizers

| Name | What it removes |
|:--|:--|
| `json:id` | Each `id` key. Element numbers change when an element is added or removed |
| `json:round-2` | Rounds numbers to 2 decimals. A hybrid backend can move a box by 0.001 pt between runs |
| `text:blank-lines` | Empty lines in the text output |
| `tagged-pdf:document-id` | The `/ID` array of a tagged PDF |

A normalizer is a decision that a difference does not matter. Add one only with a reason. After the freeze, an added normalizer is an amendment.

## Gates

`run` runs the required gates of the change in this order. `gate <name>` runs one. Each gate appends one record to `$WORK/records.jsonl` and keeps its logs in `$WORK/gates/<name>/`.

| Gate | Required when | Pass |
|:--|:--|:--|
| `problem` | Always (Phase 2; `run` does not run it). The pytest and vitest cases need the jar of the base tree (`base-build`) | On the base tree, with the case test files copied in: each problem case fails with its symptom; each guard passes. A compile error, a skip, or no test is no result. The base tree is restored |
| `base-build` | Always | `mvn clean package -DskipTests` on the base tree; `$WORK/base.jar` |
| `build` | Always | `scripts/build-java.sh` (all Java tests, Javadoc, enforcer) succeeds with 0 failures and 0 errors; `$WORK/fix.jar` |
| `fix` | Always | Each case passes on the fix tree |
| `scope` | Always | Each changed file that is not a test matches the scope |
| `pinning` | Java main code changes | In a copy of the whole tree, the JUnit cases first pass without a revert (the control run). Then each changed Java main file, reverted alone, makes a JUnit case fail (or the tests do not compile). Without a JUnit case, or with a failing control run, the gate fails. An unpinned file is a change that no case needs: remove it, or add the case |
| `static` | A Java file, or a source file of the Python or Node wrapper (not a generated file), changes | On the changed files in both trees: checkstyle and SpotBugs (Java), ruff and black (Python), eslint and prettier (Node). No count of a tool, file, and rule grows; a new file has none. Each tool ran (for Maven: on each module) |
| `wrappers` | `java/`, `python/`, `node/`, or `options.json` changes | `build-python.sh`, `build-node.sh`, and `verification/ci-verify.py` (in `$WORK/venv`) exit 0 |
| `issue-corpus` | `@issue-samples` is in an invariant | The `Issue*Test` classes of the base tree run; their PDFs (`target/test-samples/`) go to `$WORK/corpus/issue-samples` |
| `invariants` | An invariant applies | For each invariant: no new error; each output file is equal, equal after its normalizers, or allowed. No output at all is a failure (for example, no hybrid server) |
| `reporter` | The contract has `reporter` | Each run: the base expectations hold on base.jar, the fix expectations hold on fix.jar, and the outputs are equal after `fix_equals_base` |
| `bench` | A file in `java/` changes, other than tests | `scripts/bench.sh` for both jars in `$WORK/bench`; `bench.py compare` exits 0; the fix meets the thresholds if the base does; no new `Error converting` |
| `pr-watch` | `check --final` | Each CI check passed. Each critical or major CodeRabbit inline comment has an answer by a person in its thread. Each CodeRabbit review body with a critical or major finding outside the diff has a later answer by a person (a comment or a review). An answer has text: a bot command (`@coderabbitai review`) or an empty review does not count. `waiting` while a check runs |

Sandbox notes:

- The hybrid tests use MockWebServer on `localhost`. A sandbox that blocks local HTTP fails them with "Hybrid server is not available". Run `problem`, `fix`, `pinning`, `build`, and `issue-corpus` outside such a sandbox.
- `wrappers` and `bench` use uv. `bench` clones and pulls from github.com.
- Run `reporter` where you may open the reporter's document.
- The tool keeps the value of `JAVA_TOOL_OPTIONS` (for example `-Djava.net.preferIPv4Stack=true`) and adds `-Djava.awt.headless=true` and `-Djava.io.tmpdir=$TMPDIR`.

## Records and freshness

A record has the gate, the state, the commit and its tree, the base commit, the contract hash, and, for the gates that use them, the jar hashes. `check` counts a record only if:

- its tree is the tree of the current commit. A squash keeps the tree; a rebase or a code change does not. `base-build` and `issue-corpus` read only the base tree, so they do not need this;
- its base is the current base tree;
- for `problem`, `fix`, `pinning`, `scope`, `invariants`, and `reporter`: its contract hash is the current contract;
- for `problem`: the case test files are the same as at the run. A weaker test needs a new problem run;
- for `build`, `base-build`, `invariants`, `reporter`, and `bench`: the jars are the same files;
- the fix tree has no uncommitted changes.

`check` tells you which gate to run again.

## Freeze and amendments

`freeze` keeps a copy of the approved contract in `$WORK/contract.lock`. It needs a passing `problem` record of that contract. After the freeze, the contract can only grow:

- You cannot remove a case, an invariant, a format of an invariant, or a reporter run. You cannot change the kind, the test, the symptom, the module, or the runner of a case, the corpus or the options of an invariant, the reporter document, or a reporter run. Add a new case or run instead.
- Each change needs a new entry in `amendments`. `check` lists each change for Approval 2.
- A new problem case needs a new `problem` run: it must fail on the base too.
- A new guard case or a new scope entry does not need the user first. Show it at Approval 2.
- A new `allow` or a new `ignore` lowers the bar. It is a design decision: ask the user first (see "Stop and ask the user" in SKILL.md).

## Self-check (`$WORK/selfcheck.json`)

The harness cannot read code for these risks. Read each changed line for each item. `template --selfcheck` prints them.

1. PDF input: loops and recursion have a limit; skip a bad object; no allocation sized from a document value
2. Paths: no document or backend text in a file path
3. Secrets: no `--password` value or argument list in logs, messages, exceptions
4. Processes: wrappers start java with an argument list, not a shell
5. Network (hybrid): reads have a limit and a timeout; do not log backend bodies with document text
6. Output: HTML via `escapeHtmlText`/`escapeHtmlAttribute`; Markdown via `getCorrectMarkdownString`; links via `formatMarkdownLinkDestination`
7. Content safety: hidden, off-page, tiny, hidden-layer text stays out of default output
8. Temp files: `Files.createTemp*`, deleted in finally; tests use `@TempDir` or `TestSamples`
9. Logging: no extracted text, alt text, password, backend body at INFO or higher
10. Static state: reset between documents and copied to parallel workers (`StaticLayoutContainers`, `SerializerUtil`)
11. Dependencies: exact versions; license compatible with Apache-2.0
12. CI: no `pull_request_target`, no `secrets: inherit`, minimum permissions

```json
{"head": "<a commit with the current content>", "items": {"1": "ok", "2": "ok", "7": "note: <text for the review notes>"}}
```

Each item is `ok` or `note: <text>`. A note goes to the review notes. Any other text is an open finding: fix it first. A vulnerability is not a note: tell the user only (Rule 5).

## Evals

`python3 -I .agents/skills/odl-fix/evals/run.py` runs the evals of the harness (G) with the evals of the issue state (F). If the harness accepts wrong work or refuses right work, add an eval for that case first. Then change the tool.
