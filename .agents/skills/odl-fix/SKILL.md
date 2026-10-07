---
name: odl-fix
description: >-
  Procedure to fix an existing opendataloader-pdf GitHub issue and open a pull
  request. It analyzes the issue and makes sub-issues for extra problems. It
  reproduces the failure and pins it with a small synthetic sample and a failing
  test. It fixes the problem, gets an independent verification, and submits
  before/after evidence that anyone can run again. Use it when the user asks to
  work on, fix, or send a pull request for an opendataloader-pdf issue ("fix
  #123", "take issue 456 and open a PR"). Do not use it for new bug reports,
  triage, reviews of other pull requests, or replies to review comments. The
  user approves each GitHub post.
license: Apache-2.0
compatibility: >-
  Requires git, a signed-in GitHub CLI (gh), a POSIX shell, JDK 17+ (tests need
  it; code targets Java 11), Maven, Python 3.10+, and uv. Node.js 22.13+ with pnpm
  when the change touches java/, the Node wrapper, or CLI options; qpdf only to
  inspect an original locally. Network: github.com, api.github.com, pypi.org,
  files.pythonhosted.org, repo.maven.apache.org, artifactory.openpreservation.org
  (veraPDF), registry.npmjs.org.
metadata:
  project: opendataloader-pdf
---

# Fix an opendataloader-pdf issue

Use this skill to fix one GitHub issue of opendataloader-pdf and send a pull request. If the issue has more than one problem, make a sub-issue for each extra problem. Fix one problem in each pull request. The user approves each post to GitHub.

## Rules

1. **Get approval before you post.** Each GitHub write needs a yes from the user. The user gives it at Approval 1, at Approval 2, at a stop, or in the final report. Two exceptions: the fixed claim comment, and pushes that fix CI on the approved pull request. Local commits are not GitHub writes.
2. **Evidence is a run.** Run the reporter's command. It must fail on `before` and pass on the fix. Run it on the committed sample and on the reporter's document (locally). `before` is the release. For a regression that is not released, `before` is the base build.
3. **Reproduce first.** If the failure does not occur, stop. Report the commands that you ran.
4. **Do not commit an original.** Use the reporter's document only on your computer. Commit only samples that you make (see "Reproduction samples"). Do not upload, attach, or quote the original. This rule applies also to public documents.
5. **One problem, smallest change.** Write problems outside the diff in the pull request. Exception: tell a security vulnerability only to the user.
6. **Show real output.** Each command, number, and example in a draft must come from a run on the final commit. If you cannot show it, the step is not complete.
7. **An independent verifier checks the fix.** The agent that writes the fix does not judge it (Phase 4).
8. **Do not change this skill during a fix.** Record each problem with the skill in the skill log (see "Skill feedback").
9. **Write clearly.** Use the ASD-STE100 rules for all text that you write: approval requests, analyses, reports, and GitHub posts (see "Writing").

## Procedure

| Phase | Steps |
|:--|:--|
| 1. Prepare | Tools → clean, current `main` → read the issue and attachments → sub-issue drafts → claim → branch |
| 2. Reproduce | Base build → reproduce → failing test → root cause and Intent Spec → **Approval 1: plan** |
| 3. Fix and check | Fix → build and static analysis → unit tests → end-to-end → side effects → benchmark → related files |
| 4. Independent verification | Clean-code pass → verifier rounds |
| 5. Submit | Rebase → commit → pull request draft → **Approval 2: submit** → push and open → CI → final report |

After you write the failing test, commit after each change. The checks compare only commits.

The work is complete when one of these conditions is true:

- The pull request is open, and CI is finished or waits for workflow approval.
- A stop condition applies.

Then report to the user: what you posted, what passed, and what waits for other persons.

## Approvals

Stop for the user only at these two points. At each point, show one approval request (see "Drafts"). Wait for the answer.

| Approval | When | Show | After yes |
|:--|:--|:--|:--|
| 1. Plan | Before you write the fix (end of Phase 2) | Issue analysis, reproduction results (release and `main`), root cause, Intent Spec, sub-issue drafts, sample rung | Post the sub-issues. Start Phase 3 |
| 2. Submit | Before you push (Phase 5 step 3) | Commit message, pull request draft, evidence summary, verifier verdict, review notes | Push and open the pull request |

These are the last points where a change is cheap. At Approval 1, no work builds on the plan yet. At Approval 2, the work is not public yet. Do not stop at other points. Exception: a condition in "Stop and ask the user".

## Phase 1: Prepare

Result: The tools work. You have a clean branch from the current project `main`. You read the issue, and it has one problem. You claimed the issue.

```bash
REPO=opendataloader-project/opendataloader-pdf
N=<issue number>
WORK="${TMPDIR:-/tmp}/odl-$N"; mkdir -p "$WORK"
```

If your shell does not keep variables between commands, write them to `$WORK/env.sh`. Write `WORK` as an absolute path. Start each command with `. <path>/env.sh`.

### 1. Tools

```bash
java -version          # 17 or later
mvn -v
python3 --version      # 3.10 or later
uv --version
gh auth status
node -v; pnpm -v       # only for changes in java/, node/, or CLI options
```

If a tool is missing, stop and tell the user. Do not install a tool without approval.

### 2. Clean, current `main`

```bash
git status --porcelain       # must print nothing
git remote -v
```

`UPSTREAM` is the remote for `github.com/opendataloader-project/opendataloader-pdf`. In a project clone, it is usually `origin`. In a fork clone, it is usually `upstream`. If there is no such remote, add it: `git remote add upstream https://github.com/opendataloader-project/opendataloader-pdf.git`.

```bash
git fetch "$UPSTREAM"
```

Always make the branch from `$UPSTREAM/main`, also in a fork. The `main` branch of a fork can be old.

If `git status` shows changes, do not stash, commit, or discard them. The changes belong to the user. Ask the user, or use a worktree (step 6).

### 3. Read the issue

```bash
gh issue view "$N" --repo "$REPO" \
  --json title,body,comments,labels,state,url,assignees,closedByPullRequestsReferences
gh pr list --repo "$REPO" --state all --search "$N" \
  --json number,title,state,url,mergedAt,author --limit 10
git branch -r --list "$UPSTREAM/$N-*" "$UPSTREAM/*/$N-*"
```

If the issue is solved, another person claims it, or the reporter did not answer a question, stop (see "Stop and ask the user").

### 4. Attachments

Download each image and each document that the issue or its comments link to.

```bash
mkdir -p "$WORK/attachments"
curl -fsSL -o "$WORK/attachments/<name>" "<url>"
```

| Attachment | Use |
|:--|:--|
| Image | Look at it to understand the problem. Do not upload it again |
| PDF or other document | Use it only to reproduce the problem locally. Keep it in `$WORK` |

Write a short analysis for the user:

- **Pain point**: the reporter's words, quoted
- **Expected and actual** results
- **Environment**: version, command or API call, options
- **Documents**: each attachment and what it shows
- **Problems**: each expected result that the issue asks for

### 5. Sub-issue drafts

If the issue asks for two or more results, and independent changes fix them, make sub-issues. Signs: different output formats, features, or code paths. One root cause with many symptoms is one problem. If you are not sure, wait. Examine the problem again after you find the root cause (Phase 2 step 4).

Draft one sub-issue for each extra problem. Use the sections of [bug_report.md](../../../.github/ISSUE_TEMPLATE/bug_report.md). Show the drafts at Approval 1. After the yes, post them:

```bash
gh issue create --repo "$REPO" --parent "$N" --title "<title>" --body-file "$WORK/sub-<k>.md"
```

If `--parent` fails, create the issue without it. (`--parent` can need triage permission.) Then start the body with `Part of #<N>.`

Do not edit or close the original issue.

### 6. Claim and branch

Post the claim immediately. The request to fix the issue is the approval for this fixed comment.

```bash
gh issue comment "$N" --repo "$REPO" --body "I'm working on this and will link a pull request here."
git switch -c <type>/$N-<slug> "$UPSTREAM/main"
```

- `<type>`: the Conventional Commit type (`fix`, `feat`, `perf`, `docs`).
- `<slug>`: two to five lowercase words with hyphens. Example: `fix/1000-table-before-figure`.
- If the checkout is not clean, use a worktree: `git worktree add -b <type>/$N-<slug> "$WORK/src" "$UPSTREAM/main"`. Then run all later commands in `$WORK/src`.

## Phase 2: Reproduce

Result: You saw the failure. A synthetic sample and a failing test pin it. The user accepts the Intent Spec.

### 1. Base build

Build `$UPSTREAM/main` in a separate worktree. Later steps compare the fix with this jar.

```bash
git worktree add --detach "$WORK/base-src" "$UPSTREAM/main"
(cd "$WORK/base-src/java" && mvn -B -q clean package -DskipTests)
ls "$WORK"/base-src/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar   # exactly one file
cp "$WORK"/base-src/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar "$WORK/base.jar"
```

Do not run `original-*.jar`. Do not select a jar with `head -1`.

| Name | Command |
|:--|:--|
| release | `uvx opendataloader-pdf@latest` |
| base | `java -Djava.awt.headless=true -jar "$WORK/base.jar"` |
| fix | `java -Djava.awt.headless=true -jar "$WORK/fix.jar"` (Phase 3) |

Write commands in full. Do not put a command line in a variable. zsh does not split the variable into words.

The CLI has no `--version` option. To get the release version, run `uvx --from opendataloader-pdf@latest python -c "import importlib.metadata as m; print(m.version('opendataloader-pdf'))"`.

### 2. Reproduce

Use the reporter's document from `$WORK`. If there is no document, make the sample first (see "Reproduction samples").

```bash
uvx opendataloader-pdf@latest <options> -o "$WORK/release" <document>.pdf
java -Djava.awt.headless=true -jar "$WORK/base.jar" <options> -o "$WORK/main" <document>.pdf
uvx opendataloader-pdf@<reporter's version> <options> -o "$WORK/reported" <document>.pdf   # if different
```

If the reporter used the Python API or the Node API, reproduce with that API too. For `hybrid`, start the server as the README tells.

| Release | `main` | Action | `before` |
|:--|:--|:--|:--|
| fails | fails | Continue | release |
| fails | passes | Already fixed. Stop | — |
| passes | fails | Regression. Continue | base |
| passes | passes | Not reproduced. Stop | — |

Record each run: the command, the version, the document, and the output lines that show the symptom.

### 3. Failing test

- **Location**: Put the test in the package of the defective code, under `src/test/java/org/opendataloader/pdf/` in `opendataloader-pdf-core` (or `-cli`). For a test of one document from input to output, use `Issue<N>…IntegrationTest` (example: `Issue336IntegrationTest`).
- **Sample**: Use the first rung in "Reproduction samples" that shows the symptom. If the symptom occurs with all PDFs, use `samples/pdf/lorem.pdf`.
- **Assertion**: Assert the reporter's expected result (text, order, table shape, exit code). Get the expected value from the sample design or from the specification. Do not get it from the current output. The current output contains the bug.

```bash
./scripts/test-java.sh -pl opendataloader-pdf-core "-Dtest=<Class>[#method]"
./scripts/test-java.sh -pl opendataloader-pdf-cli -am "-Dtest=<Class>[#method]" -Dsurefire.failIfNoSpecifiedTests=false
```

Wrapper tests copy the jar from `java/opendataloader-pdf-cli/target/`. The Python build also needs `README.md` in the package directory:

```bash
(cd java && mvn -B -q clean package -DskipTests)
rm -rf python/opendataloader-pdf/src/opendataloader_pdf/jar && cp README.md python/opendataloader-pdf/README.md
(cd python/opendataloader-pdf && uv run --reinstall-package opendataloader-pdf pytest tests/<file>.py::<Class>::<test> -v)
(cd node/opendataloader-pdf && pnpm install --frozen-lockfile && pnpm run build) && ./scripts/test-node.sh test/<file>.test.ts -t "<name>"
```

**Pass: the test fails, and the message shows the reporter's symptom.** A compile error, a missing sample, or a skip is not a pass. Some tests skip without a message (`assumeTrue`). Make sure that your test is not in "Skipped".

```bash
git add <test> <sample files> && git commit -m "wip: failing test for #$N"
```

### 4. Root cause, Intent Spec, and Approval 1

Write the root cause in one to three sentences. Give the class, the method, and the reason for the wrong result.

- If the causes are independent, draft sub-issues (Phase 1 step 5).
- If the fix needs a design decision, stop (see "Stop and ask the user").

Write the Intent Spec. It becomes the commit body and the pull request description.

| Part | Content |
|:--|:--|
| **Objective** | The reporter's pain point, quoted, with the issue link |
| **Approach** | The root cause and the change, in user terms |
| **Evidence** | Document, expected results, `before` build, commands, test |

```
Objective: "Tables after the first figure lose their cells" (#1000)
Approach:  A figure between the table and the previous column closes the table.
           Later rows become paragraphs. Keep the table open across the figure.
Evidence:  Issue1000IntegrationTest on issue-1000-table-before-figure.pdf (built:
           2 tables, 4x3 each); release fails, this build passes.
```

Ask for Approval 1. Do not write the fix before the answer. After the yes:

- Post the sub-issues.
- Continue with one problem: the problem that the user selects, or the first problem that the reporter gave.
- If that problem is in a sub-issue, set `N` to its number. Keep `WORK`. Rename the branch (`git branch -m`). Post the claim on the sub-issue.

## Phase 3: Fix and check

Result: The smallest change removes the root cause. All gates pass on the final commit.

After each later code change, do steps 2 to 7 again. Later changes come from verifier findings, a rebase, or CI. Step 6 applies only to changes in `java/`.

### 1. Fix

- Change only the code that the root cause needs.
- Do not change version numbers. Do not change generated files. Step 7 makes them again.
- Commit after each change. Before each gate, `git status --porcelain` must show nothing.

### 2. Build and static analysis

```bash
./scripts/build-java.sh                     # clean, compile, all Java tests, Javadoc lint, enforcer
cp java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar "$WORK/fix.jar"
git status --porcelain
git diff --check "$UPSTREAM/main"...HEAD
```

The Java tests write `samples/json/lorem.json`. If your change must change that output, commit the file. If not, run `git restore samples/json/lorem.json`.

**Pass: `BUILD SUCCESS`, and the two git commands show nothing.**

Static analysis: `main` already has findings, and CI does not run these tools. **Pass: no new findings in the files that you changed.** Run each tool in the repository and in `$WORK/base-src`. Then compare the results.

```bash
git diff --name-only --diff-filter=AM "$UPSTREAM/main"...HEAD
git diff --name-only --diff-filter=AM --relative=python/opendataloader-pdf/ "$UPSTREAM/main"...HEAD
git diff --name-only --diff-filter=AM --relative=node/opendataloader-pdf/ "$UPSTREAM/main"...HEAD
```

```bash
(cd java && mvn -B -fn -pl opendataloader-pdf-core,opendataloader-pdf-cli checkstyle:check \
  -Dcheckstyle.includes='**/A.java,**/B.java') 2>&1 | grep -E '\.java:[0-9]+:' \
  | sed -E 's|^.*/(opendataloader-pdf-[a-z]+/src/)|\1|; s|:[0-9]+(:[0-9]+)?:|:|' | sort | uniq -c
(cd java && mvn -B -q -fn -DskipTests compile spotbugs:check \
  -Dspotbugs.onlyAnalyze=org.opendataloader.pdf.pkg.A,org.opendataloader.pdf.pkg.B) 2>&1 \
  | grep -E '^\[ERROR\] (High|Medium|Low):' | sed -E 's/\[line [0-9]+\]//' | sort
(cd python/opendataloader-pdf && uvx ruff check <files> && uvx black --diff <files>)
(cd node/opendataloader-pdf && pnpm install --frozen-lockfile && pnpm exec eslint <files>)
(cd node/opendataloader-pdf && pnpm exec prettier <file> | diff -u <file> -)
```

- `-fn`: Maven does not skip the CLI module when core has findings. The exit code is then 0. Read the list.
- Do not use `-q` with checkstyle. It hides the findings.
- If a count increases for a file and a rule, it is a new finding.
- Formatters: a new file must have no diff. In a changed file, no hunk can touch your lines.
- Skip generated files (`*.generated.ts`, `*_generated.py`).

### 3. Unit tests

```bash
./scripts/build-java.sh
rm -rf python/opendataloader-pdf/src/opendataloader_pdf/jar && ./scripts/build-python.sh
./scripts/build-node.sh                     # when java/, node/, or CLI options change
```

Remove the bundled jar first. If you do not remove it, the Python build keeps the old jar.

**Pass: 0 failures and 0 errors. The Phase 2 test passes and is not skipped.**

### 4. End to end

```bash
uv venv --clear "$WORK/venv" && uv pip install --python "$WORK/venv/bin/python" python/opendataloader-pdf/dist/*.whl
"$WORK/venv/bin/python" verification/ci-verify.py
```

**Pass: exit 0.** The script fails if an option in `options.json` is not in `COVERED_OPTIONS` (step 7).

Run the reporter's command again on `before` and on the fix. Use the sample and the original in `$WORK`. If the reporter used an API, run that call too.

Make the evidence table from the sample runs. Use one row for each expected result. Use one column for `before` and one column for the fix. In each cell, put the output lines that decide it. For the original, report only pass or fail. Do not paste its output.

**Pass: at least one row fails on `before`, and all rows pass on the fix.**

### 5. Side effects

```bash
rm -rf "$WORK/side" && mkdir -p "$WORK/side"
for b in base fix; do
  for d in samples/pdf java/opendataloader-pdf-core/src/test/resources/issues; do
    [ -d "$d" ] || continue
    java -Djava.awt.headless=true -jar "$WORK/$b.jar" -f json,markdown,html,text \
      -o "$WORK/side/$b/${d##*/}" "$d" >> "$WORK/side/$b.log" 2>&1
    echo "$b $d: exit $?"
  done
  grep -E '^Error|Exception during processing file' "$WORK/side/$b.log" > "$WORK/side/$b.errors"
done
diff "$WORK/side/base.errors" "$WORK/side/fix.errors"
diff -r "$WORK/side/base" "$WORK/side/fix"
```

- The output is deterministic. Thus each difference comes from your change.
- `issues/` contains the samples of earlier fixes. Each fix adds samples, so this check becomes larger with each fix.
- Clear the directory first. The CLI does not clear `-o`.
- If the reporter used options that change the code path (`--table-method cluster`, `--hybrid`, `--reading-order`), run again with these options. Use a separate, clean directory.

**Pass:**

- The `.errors` diff is empty. Both builds exit with 1 because of `password-protected.pdf`. Thus the exit code does not show a new failure.
- Each `diff -r` line is part of the intended change. If a difference is not related, it is a regression: fix it. If an intended difference occurs in documents that the issue does not mention, it is a design decision: stop.

### 6. Benchmark

Do this step for changes in `java/`. The benchmark uses 200 DP-Bench documents, Markdown output, and `--table-method cluster`. The scores are NID (reading order), TEDS (tables), and MHS (headings).

```bash
export BENCH_DIR="$WORK/bench"
[ -d "$BENCH_DIR/.git" ] || git clone --depth 1 https://github.com/opendataloader-project/opendataloader-bench.git "$BENCH_DIR"
for b in base fix; do
  rm -rf "$BENCH_DIR/prediction/opendataloader/markdown" "$BENCH_DIR/prediction/opendataloader/evaluation.json"
  ./scripts/bench.sh --skip-build --check-regression --force --jar-path "$WORK/$b.jar" > "$WORK/bench-$b.log" 2>&1
  echo "$b: exit $?"
  cp "$BENCH_DIR/prediction/opendataloader/evaluation.json" "$WORK/bench-$b.json"
done
python3 - "$WORK/bench-base.json" "$WORK/bench-fix.json" <<'EOF'
import json, sys
b, f = (json.load(open(p)) for p in sys.argv[1:3])
bad = []
for m in ("overall", "nid", "teds", "mhs"):
    x, y = b["metrics"]["score"][f"{m}_mean"], f["metrics"]["score"][f"{m}_mean"]
    print(f"{m}: {x:.4f} -> {y:.4f} ({y - x:+.4f})")
    if y < x - 1e-9: bad.append(f"mean {m} dropped")
fix = {d["document_id"]: d["scores"].get("overall") for d in f["documents"]}
for d in b["documents"]:
    x, y = d["scores"].get("overall"), fix.get(d["document_id"])
    if x is not None and (y is None or x - y > 0.05): bad.append(f"{d['document_id']}: {x} -> {y}")
if f["metrics"].get("missing_predictions", 0) > b["metrics"].get("missing_predictions", 0):
    bad.append("more missing predictions than base")
print("\n".join(bad) or "no drop")
sys.exit(1 if bad else 0)
EOF
```

- `--force`: Without it, the bench scores its committed predictions and does not run your jar. CI does not use `--force`. Thus a green CI benchmark is not evidence.
- `rm` and clone first: The bench repository contains old predictions. Without the `rm`, a failed document gets an old score.
- The log line `Running benchmark with JAR:` shows the wrong jar. The jar in `--jar-path` is the jar that runs.

**Pass:**

- `bench-fix.log` contains `All thresholds met.`
- `grep -c 'Error converting'` in `bench-fix.log` is not more than in `bench-base.log`.
- The comparison exits with 0 (no mean drop, no document drop > 0.05, no new missing prediction).

If the base also misses a threshold, report it. Then use the comparison. If the comparison shows a drop, try to remove the drop. If you cannot remove it, stop (see "Stop and ask the user").

### 7. Related files

| Change | Update |
|:--|:--|
| CLI option | Change `CLIOptions.OPTION_DEFINITIONS`. For a comma-separated list, also add it to `LIST_OPTIONS` in `scripts/generate-options.mjs` before the sync. Run `npm run sync`. Commit `options.json` and the four generated files. Add the option and a check to `COVERED_OPTIONS` (or `HYBRID_OPTIONS`) in `verification/ci-verify.py`. Update `CLIOptionsTest` and `python/opendataloader-pdf/tests/test_cli_options.py`. If users see the change, update the options section of `README.md` |
| JSON structure | Update `schema.json` by hand. If `scripts/generate-schema.mjs` contains the element, update it. Update the JSON example and the field table in `README.md`. Update `samples/json/lorem.json` |
| Silent-failure behavior (added, removed, changed) | Update `skills/odl-pdf-maintenance/MAINTAINING.md` |
| `skills/odl-pdf/` | Run `python3 skills/odl-pdf-maintenance/sync-skill-refs.py`. Fix the text, not the allowlist |
| Dependency | Change the properties in `java/pom.xml`, or `pyproject.toml` or `package.json` and the lock file. Use exact versions only. Give the dependency and its license in the pull request |

These updates are code changes. Commit them. Build `fix.jar` again (step 2). Do steps 3 to 6 again. Then run:

```bash
java -Djava.awt.headless=true -jar "$WORK/fix.jar" --export-options > "$WORK/options.export.json" && diff "$WORK/options.export.json" options.json
npm run generate-options && npm run generate-schema && git diff --exit-code -- \
  options.json node/opendataloader-pdf/src/*.generated.ts python/opendataloader-pdf/src/opendataloader_pdf/*_generated.py
```

**Pass: both commands exit with 0 and show no diff lines.** The npm banners and the `Generated:` lines are normal.

Do not commit `content/docs/`. The release makes it. Do not edit `CHANGELOG.md`. The release notes come from pull request titles.

## Phase 4: Independent verification

Result: A verifier that did not write the fix ran the evidence again. The verifier returned `VERDICT: pass`, or each open finding is in "Review notes".

### 1. Clean-code pass

```bash
git diff --stat "$UPSTREAM/main"...HEAD
```

- [ ] Each file in `--stat` has a reason in one sentence. Revert the other files.
- [ ] New code agrees with the code around it and with `.editorconfig`.
- [ ] Each new Java file starts with the header in `LICENSE_TEMPLATE/license.txt`.

If a check conflicts with the smallest change, use the smallest change.

### 2. The verifier

The verifier gets only the package below. It does not get your reasoning or your output. It runs the commands itself.

| Package item | Content |
|:--|:--|
| Diff | `git diff "$UPSTREAM/main"...HEAD` |
| Issue | The body and the comments that define the problem |
| Intent Spec | Objective, Approach, Evidence |
| Commands | The reproduction commands, the failing test, Phase 3 steps 2 to 6 |
| Prompt | Below |

Select the method in this order:

1. **Sub-agent**: Use it if your agent can start one (for example, the Agent tool of Claude Code). Give it the package. Start a new sub-agent for each round.
2. **New session**: Use it if there is no sub-agent. Give the user the complete package. Ask the user to run it in a new session and to paste the full reply.
3. **Neither method is possible**: Tell the user. In "Review notes", write "Verified by the authoring agent only".

### 3. Rounds

| Round | Package | When |
|:--|:--|:--|
| 1 (blind) | Diff and Commands only | Always |
| 2 (context) | Full package and round 1 findings | Always |
| 3 (context) | Full package and round 2 findings | Only if round 2 is `fail` |

After each round, fix all `[critical]` and `[major]` findings. Commit, and do Phase 3 steps 2 to 7 again. Do not fix `[minor]` or `[nit]` findings. After round 3, put the open critical or major findings in "Review notes". Give the reason.

Verifier prompt. Send it without changes. In round 1, remove the Issue part and the Intent Spec part:

```
You are an independent verifier. You did not write this change. Do not trust
the results in this message. Run the commands yourself. Judge only from what you see.

## Issue
<issue body and the comments that define the problem>

## Intent Spec
<Objective, Approach, Evidence>

## Diff
<git diff>

## Commands
<reproduction commands, failing test, build, tests, end-to-end, side effects, benchmark>

## Previous findings
<findings and what was done for each>

## Check
- Evidence: run the reproduction on before and on the fix. Does before fail and the fix pass?
- Test: does the new test fail without the change?
- Correctness and edge cases
- Root cause: does the change fix the cause or only the symptom?
- Intent: does it solve the pain point in the Objective?
- Scope: does the diff change something that the issue does not need?
- Side effects: is each output difference explained?
- Security, on each changed line:
  1 PDF input: loops and recursion have a limit; skip a bad object; no allocation sized from a document value
  2 Paths: no document or backend text in a file path
  3 Secrets: no --password value or argument list in logs, messages, exceptions
  4 Processes: wrappers start java with an argument list, not a shell
  5 Network (hybrid): reads have a limit and a timeout; do not log backend bodies with document text
  6 Output: HTML via escapeHtmlText/escapeHtmlAttribute; Markdown via getCorrectMarkdownString; links via formatMarkdownLinkDestination
  7 Content safety: hidden, off-page, tiny, hidden-layer text stays out of default output
  8 Temp files: Files.createTemp*, deleted in finally; tests use @TempDir
  9 Logging: no extracted text, alt text, password, backend body at INFO or higher
  10 Static state: reset between documents and copied to parallel workers (StaticLayoutContainers, SerializerUtil)
  11 Dependencies: exact versions; license compatible with Apache-2.0
  12 CI: no pull_request_target, no secrets: inherit, minimum permissions

Tag each finding [critical] (runtime error, data loss, vulnerability),
[major] (wrong behavior, missing edge case, evidence does not hold), [minor], or [nit].
For each finding, give the command that you ran and its output.
End with one line: VERDICT: pass, or VERDICT: fail if any [critical] or [major].
```

A vulnerability in existing code is not a review note. Do not write it in a public place. Tell the user the file, the line, and the effect. [SECURITY.md](../../../.github/SECURITY.md) gives no address. Thus the user decides how to report it.

## Phase 5: Submit

Result: An open pull request. The commits, the description, and the evidence agree with the final code. CI is finished.

### 1. Rebase

```bash
git status --porcelain       # must print nothing
git fetch "$UPSTREAM"
git rebase "$UPSTREAM/main"
```

If the rebase added new commits, build the base again. Then do Phase 3 steps 2 to 7 again:

```bash
git -C "$WORK/base-src" checkout --detach "$UPSTREAM/main"
(cd "$WORK/base-src/java" && mvn -B -q clean package -DskipTests)
cp "$WORK"/base-src/java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar "$WORK/base.jar"
```

### 2. Commit

This repository merges by rebase and by squash. Make one commit for each logical change. Usually this is one commit. Generated files can have a separate commit.

Commit with the Git identity of the user (`git config user.name`, `git config user.email`). `-s` adds the sign-off line.

```bash
git reset --soft "$UPSTREAM/main"
git commit -s -F "$WORK/commit-msg.txt"
git status --porcelain
git show --stat HEAD
```

**Pass: `git status` shows nothing. `git show --stat` lists each file of the change, including the test and the sample files.**

Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary). The pull request template asks for them:

```
fix(<scope>): <change for the user, lowercase, no period>

Objective: "<the reporter's pain point>" (#<N>)
Approach: <root cause and change, in user terms>
Evidence: <document, expected results, before -> after, test>

Fixes https://github.com/opendataloader-project/opendataloader-pdf/issues/<N>

<agent trailer>
```

- `<scope>`: a scope from `git log --format=%s -100` (examples: `hybrid`, `cli`, `core`, `tagging`, `filter`).
- `<agent trailer>`: Keep the co-author trailer that your agent adds. If it adds none, write `Assisted-by: <agent> (<model>)`.
- Do not write `Signed-off-by`. `-s` adds it.

Before the draft, read the issue again, with the new comments. If they change the problem, stop and tell the user.

### 3. Pull request draft and Approval 2

Title: the commit subject. A squash merge uses it, and the release notes come from titles.

```markdown
## Objective

"<the reporter's pain point>" (#<N>)

## Approach

<root cause and change, in user terms>

## Evidence

<before/fix table and commands>

- Sample: <rung and file or test>; synthetic, the original is not included
- Unit tests: <suites and counts>; `<new test>` fails before, passes after
- End to end: `verification/ci-verify.py`, exit 0
- Side effects: <count> sample PDFs in json, markdown, html, text: <"no differences", or each difference and why>
- Benchmark: <benchmark comparison output>, or "not run: no change in java/"
- Verification: <sub-agent | new session | authoring agent only>, last verdict <pass | fail>

## Review notes

<open findings, problems outside the diff, accepted benchmark drops; or "None">

## AI assistance

Drafted by <agent> (<model>). Reviewed by <user's GitHub handle>, who signed off the commits.

**Issue resolved by this Pull Request:**
Resolves #<N>

**Checklist:**

- [ ] Documentation has been updated, if necessary.
- [ ] Examples have been added, if necessary.
- [ ] Tests have been added, if necessary.
```

- If an item is not necessary, tick it. Give the reason in the description.
- Replace local paths with `base.jar` and `fix.jar`. Local paths contain the user name.
- Ask for Approval 2. Show this draft, the commit message, the evidence summary, and the verifier verdict. Do not push before the answer.

### 4. Push and open

```bash
git push -u origin HEAD
gh pr create --repo "$REPO" --base main --head "<branch>" --title "<title>" --body-file "$WORK/pr.md"               # branch in the project
gh pr create --repo "$REPO" --base main --head "<your login>:<branch>" --title "<title>" --body-file "$WORK/pr.md"  # branch in your fork
```

In a project clone, `origin` is the project. In a fork clone, `origin` is your fork. `--head` does not accept an organization as owner. For a fork that an organization owns, use `gh api "repos/$REPO/pulls" -f base=main -f head="<org>:<branch>" -f title="<title>" -F body=@"$WORK/pr.md"`.

### 5. CI

```bash
gh pr checks <PR> --repo "$REPO" --watch
```

- **Test & Benchmark** runs for changes in `java/`, `python/`, `node/`, `scripts/`, `verification/`, `samples/`, `.github/workflows/`. **Skill Lint** and **Skill Smoke Test** run for `skills/odl-pdf/` and `skills/odl-pdf-maintenance/`. Other changes have no checks.
- If the paths match but no checks show, run `gh run list --repo "$REPO" --branch <branch> --limit 5`. A run that waits for approval needs a maintainer. Report it and go to step 6.
- If a check fails, read `gh run view <run-id> --repo "$REPO" --log-failed`. Reproduce the failure. Fix it. Commit. Do Phase 3 steps 2 to 7 again. Push the fix without a stop. If the evidence changed, show the new description to the user. Update the pull request only after the yes. Before a review starts, use `git commit --amend` and `git push --force-with-lease`. After a review starts, add a new commit with `git commit -s`.
- The CI benchmark does not run your jar (Phase 3 step 6). It is not evidence.
- If a CLA bot asks for a signature, give the user its link.

### 6. Final report

```bash
git worktree remove "$WORK/base-src"     # and "$WORK/src" if you used it
```

Report to the user:

- the pull request link and the status of each check
- what waits for other persons (CLA, workflow approval, review)
- follow-up work (problems outside the diff)
- the skill feedback draft, if the skill log has entries (see "Skill feedback")

## Reproduction samples

The original stays in `$WORK`. The repository gets only what you make. Use the first rung that shows the symptom:

| Rung | Form | Use for | Example in the code |
|:--|:--|:--|:--|
| 1 | Objects built in the test (`TextChunk`, `BoundingBox`, `TableBorder`) | Reading order, tables, layout, text spacing | `TableBorderProcessorTest`, `XYCutPlusPlusSorterTest` |
| 2 | PDF built in the test with PDFBox | Damaged or encrypted files, images, scans, structure trees | `ScannedPagePdf`, `HybridReferenceTrailerTest`, `ParentTreeOrderTest` |
| 3 | Small PDF and its generator, both committed | Structures that rung 2 cannot express | — |
| 4 | Backend response JSON | Hybrid mode | `DoclingSchemaTransformerTest` |
| 5 | Large PDF made during the test | Size, memory, time | — |
| 6 | No sample | No rung above shows the symptom | — |

**Find the trigger.** A bug usually comes from the structure, not from the content. Open the original locally: `qpdf --qdf --object-streams=disable original.pdf "$WORK/qdf.pdf"`. Remove pages and objects until the symptom stops. Make that structure again with neutral content. Do not redact the original. Metadata, font subsets, alt text, ActualText, and attachments can keep its content.

**Rules for files that you commit (rungs 3 to 5):**

- Location: Put the files in `java/opendataloader-pdf-core/src/test/resources/issues/`. Name them `issue-<N>-<slug>.pdf` and `issue-<N>-<slug>.py` (the generator), or `.json` for rung 4.
- Header: In the generator header, write the issue link, the trigger, the expected result, and the word "synthetic".
- Fonts: Use the standard 14 fonts, or an OFL font in the repository with its license. Do not use a system font. Do not copy `generate-cid-test-pdf.py`. It embeds the font of the computer that runs it.
- Content: Use generated text and images. Keep the coordinates and operators of the trigger. If a script is the trigger (Korean, Chinese, right-to-left), use short neutral text in that script.
- Same bytes: The generator must give the same bytes at each run. Pin library versions in the script header (`uv run generate.py`). Fix dates and IDs (ReportLab: `invariant=1`). Do not use random values.
- Size: Each committed file is 100 KB or smaller. If a file is larger, use rung 5.
- Symptom: Run `before` on the sample. It must show the same symptom as the original. If it does not, add structure until it does.

**Rung 5.** Make the document in the test, not in the repository. For a memory problem, use a smaller heap and a smaller document in a separate JVM (`-Xmx64m`). For a time problem, compare ratios (2N pages against N pages), not seconds. Keep slow tests out of the default run. In the pull request, give the command that runs them.

**Rung 6.** Stop (see "Stop and ask the user"). If the user agrees, write in the pull request: "Reproduced locally on the reporter's document (not shared). No sample reproduces it: <reason>." Add the lowest test that you can (rung 1 or 4). In the final report, include a comment draft that asks the reporter to check the fix.

A sample cannot show how often a threshold is wrong on real documents. For threshold changes, the benchmark (Phase 3 step 6) is the gate.

## Skill feedback

This skill can contain errors. Record them. Do not correct the skill during a fix.

**During the work**, add one line to `$WORK/skill-log.md` for each problem with the skill:

```
<type> | <phase and step> | <evidence: command and output> | <suggested change>
```

Types: wrong fact, missing step, unclear text, unnecessary step, missed defect.

**At the final report**, if the log has entries, draft one comment for the tracking issue. Use a table with the same four columns. Put the draft in the final report. Post it only after the user says yes. If the log is empty, post nothing.

```bash
gh issue list --repo "$REPO" --state open --search 'in:title "odl-fix skill feedback"' --json number,url
gh issue comment <tracking issue> --repo "$REPO" --body-file "$WORK/skill-feedback.md"
```

- If there is no tracking issue, do not create one. Give the draft to the user.
- Do not write the content of the original document, local paths, or user names.
- If an earlier comment has the same problem, add only the new evidence and a link to that comment.

## Troubleshooting

| Symptom | Fix |
|:--|:--|
| JVM exit 3 "Cannot use the temporary directory", or Maven tests hang after `com.apple.hiservices-xpcservice` | `export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.io.tmpdir=$TMPDIR -Djava.awt.headless=true"` |
| `-pl opendataloader-pdf-cli` cannot resolve dependencies | Add `-am` |
| `SocketException: Operation not permitted` | The environment blocks local ports. Run the same tests in `$WORK/base-src`. List the classes that fail on both. A failed `build-java.sh` leaves no jar. Build one with `-DskipTests` |
| uv: `Failed to initialize cache` | Run `export UV_CACHE_DIR="$TMPDIR/uv-cache"`. If uv then panics (`system-configuration`), report the step as blocked |
| Bench: `Pipeline failed: 'brand_raw'` | The sandbox blocks `sysctl`. No new `evaluation.json` exists. Report the step as blocked |
| Two jars match the glob | Build again with `clean` |

## Drafts

Use one format for Approval 1, Approval 2, each stop, and the final report:

```
<Approval 1: plan | Approval 2: submit | Stop: condition | Final report> — <repository>#<issue>

<the result or the decision, first>
<what you found, and the full text of each post>

Checks: <commands and results; what you could not verify, and why>
Recommendation: <what you recommend, and why>
Proceed?
```

Write all GitHub text in English. Use the user's language for the user.

## Writing

Use the ASD-STE100 rules for all text that you write. Apply them in the language of the text.

- Put the result or the decision first. Put the details after it.
- Write one instruction or one fact in each sentence.
- Keep sentences short. In English, use 20 words or fewer for an instruction and 25 for a description.
- Use the active voice. Write instructions as commands.
- Put a condition before the instruction: "If X, do Y."
- Use one term for one thing. Do not change the term in the same text.
- Explain an abbreviation or a technical term at its first use, or do not use it.
- Use a list or a table for steps and comparisons.

## Stop and ask the user

| Condition | Action |
|:--|:--|
| A tool is missing, or the checkout has changes of the user | Tell the user. Do not install, stash, or discard |
| The issue is closed, or a merged pull request solves it | Draft a comment that links the change. Do not fix |
| Another person claims it (open pull request, other assignee, project branch with the issue number, comment) | Tell the user who claims it and when. Stop |
| The reporter did not answer a question | Stop |
| No failure on the release or on `main` | Draft a comment with the commands that you ran. Do not fix |
| Failure on the release, no failure on `main` | Find the commit that fixed it. Draft a comment that gives the commit. Stop |
| The fix needs a design decision (new option, changed default, changed output schema, change for other documents, new dependency) | Draft an issue comment with the options. Wait for the decision in the issue |
| The root cause is in a dependency (veraPDF, docling) | Show where the cause is. Ask the user: work around it, report it upstream, or both |
| No sample shows the symptom (rung 6) | Tell the user the reason. Continue only if the user agrees |
| A benchmark drop that you cannot remove | Show the numbers. The user decides. The pull request explains the drop |
| A tool or network access is not available | Report the step, the command, and the error. Do not try other methods to continue |

Out of scope: new issues other than sub-issues, triage, reviews of other pull requests, replies to reviews, merge, and release.
