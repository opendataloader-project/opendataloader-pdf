# Fix an issue

Goal: a pull request whose evidence shows the reporter's document failing before the change and passing after it, with no benchmark regression.

## 1. Read everything

```bash
gh issue view <N> --repo opendataloader-project/opendataloader-pdf --json title,body,comments,labels,state,url
gh pr list --repo opendataloader-project/opendataloader-pdf --state all --search "<N>" \
  --json number,title,state,url,mergedAt --limit 10
```

Open every image in the issue. Read the comments in order and answer three questions before anything else:

- Is it already solved? A merged pull request may address it; compare its change with the pain point.
- Is someone on it? An open pull request, or a recent comment claiming the issue.
- Who is waiting for whom? The reporter may have been asked for information and not answered yet.

Any of these stops the fix; see Stop and ask the operator in [SKILL.md](../SKILL.md).

## 2. Claim the issue

Draft a one-line comment that you are working on it, and post it after approval. This keeps two agents from fixing the same issue.

## 3. Build

```bash
npm run build-java
JAR=$(ls java/opendataloader-pdf-cli/target/opendataloader-pdf-cli-*.jar | grep -v -e sources -e javadoc | head -1)
```

Documents tagged `hybrid` need a running backend; start it as the README's hybrid mode section describes.

## 4. Reproduce — the gate

Look for a document in this order:

1. a testdocs document linked to the issue: `python "$TESTDOCS/scripts/find.py" --issue opendataloader-project/opendataloader-pdf#<N>`
2. the reporter's own steps and document, when public
3. a testdocs document with matching tags
4. a new document, through [add-document.md](add-document.md)

Continue only when one of these holds:

- you ran it and saw the failure the issue describes, or
- you can show, by running both versions, a difference in behavior that the change will produce.

Otherwise stop. Draft a comment listing what you ran, on which version, and what you saw.

## 5. Agree on an Intent Spec

Before writing code, write three parts and get the operator's agreement. They become the commit message body and the pull request description.

| Part | Question | How to write it |
|:-----|:---------|:----------------|
| **Objective** | What problem does the reporter have? | Quote the reporter's pain point |
| **Approach** | How will it be solved? | In terms a user understands, not class names |
| **Evidence** | What will show that it is solved? | The document, the expectations, and the command; results come later |

```
Objective: "Tables after the first figure lose their cells" (#1000)
Approach:  Keep assigning text to a table's cells when a figure sits between the
           table and the previous column, instead of closing the table at the figure.
Evidence:  Run testdocs 1000-table-before-figures on the latest release and on this
           build; table_shape and table_cell expectations fail before and pass after.
```

## 6. Test first, then fix

1. Write a test that fails because of the bug.
2. Run it and confirm it fails for the expected reason.
3. Make the smallest change that fixes it.
4. Run the test again; it passes.
5. Run the whole suite: `./scripts/test-java.sh`, plus `./scripts/test-python.sh` or `./scripts/test-node.sh` when you touched a wrapper.
6. If you changed CLI options, run `npm run sync`.
7. If the testdocs document has no expectation for this behavior, add one through [add-document.md](add-document.md). It must fail on the release and pass on your build.

## 7. Produce the evidence

```bash
python "$TESTDOCS/scripts/run_expect.py" <id> \
  --cli before="uvx opendataloader-pdf@latest" \
  --cli after="java -jar $JAR"
./scripts/bench.sh --skip-build --check-regression
```

The evidence holds only if at least one expectation fails under `before` and all pass under `after`. When the document comes from DP-Bench, also compare `./scripts/bench.sh --doc-id <id>` scores before and after.

## 8. Review

Run a blind review in a fresh context — a sub-agent if you can start one, otherwise a new session — with the prompts in [review-prompts.md](review-prompts.md). Fix every critical and major finding, ignore minor ones and nits, and rerun the tests after each fix. Stop after round 2.

## 9. Update documentation

Only when user-facing behavior changes: update `README.md` or `content/docs/`. Run every code example you add.

## 10. Commit and open the pull request

```bash
git fetch origin
git switch -c fix/<N>-<short-description> origin/main
git commit -s
```

The commit message is a Conventional Commit whose body is the Intent Spec, ending with the trailers:

```
Assisted-by: <agent> (<model>)
Signed-off-by: <operator name> <operator email>
```

The pull request body follows `.github/PULL_REQUEST_TEMPLATE.md`: Objective, Approach, Evidence with the `run_expect.py` table and the benchmark result, and `Fixes https://github.com/opendataloader-project/opendataloader-pdf/issues/<N>`. Show the draft; after approval, push to your fork and run `gh pr create --repo opendataloader-project/opendataloader-pdf --body-file <draft file>`.
