# Report a problem

Goal: an issue that a maintainer can reproduce with one command on a public document.

## 1. Capture the problem in the operator's words

Write down what the operator is trying to do and what goes wrong, in their words. This becomes the pain point that the issue quotes and that a fix will answer.

Collect:

- the opendataloader-pdf version: `pip show opendataloader-pdf`, the npm package version, or the JAR file name
- the exact command or code, including every option, and whether a hybrid backend is involved
- the full error message, or the part of the output that is wrong

## 2. Look for an existing report

```bash
gh issue list --repo opendataloader-project/opendataloader-pdf --state all --search "<keywords>" \
  --json number,title,state,url --limit 20
gh pr list --repo opendataloader-project/opendataloader-pdf --state all --search "<keywords>" \
  --json number,title,state,url --limit 10
```

If an open issue matches, draft a comment that adds your document and evidence instead of a new issue. If a merged pull request looks like the fix, check whether the latest release still shows the problem before going further.

## 3. Get a public document that shows it

| Situation | Action |
|:----------|:-------|
| The operator's document is published under a license in the testdocs [LICENSES.md](https://github.com/opendataloader-project/opendataloader-testdocs/blob/main/LICENSES.md) | Use it; follow [add-document.md](add-document.md) to add the smallest part that shows the problem |
| A testdocs document already shows the same behavior | Use it: `python "$TESTDOCS/scripts/find.py" --tag <tag>` |
| The document is private, unlicensed, or contains personal data | Follow [add-document.md](add-document.md) to synthesize one; never attach the original |

## 4. Reproduce on that document

Run the operator's command on the public document with the latest release:

```bash
uvx opendataloader-pdf@latest <options> -o /tmp/odl-report <document>.pdf
```

For a testdocs document with expectations, run `python "$TESTDOCS/scripts/run_expect.py" <id> --cli release="uvx opendataloader-pdf@latest"`.

If the problem does not appear, tell the operator what differs from their setup and stop. An issue that does not reproduce costs the maintainers more than no issue.

## 5. Describe correct output precisely

Quote the wrong lines of actual output. Describe correct output in the terms of testdocs expectations — a table's shape, a cell's text, which text comes first, what must not appear — so the fix can be checked mechanically. See the [expectation types](https://github.com/opendataloader-project/opendataloader-testdocs#expectations).

## 6. Draft the issue

Mirror the Bug report form so the issue reads the same as one filed on the web:

```markdown
### Test document

<testdocs id, or public URL and license>

### Version

<version>

### Command

<exact command>

### Expected output

<what correct output looks like, as expectations where possible>

### Actual output

<the wrong output, quoted>

### AI assistance

AI agent (drafted by an agent, reviewed by its operator)
```

Title: the wrong behavior in one line, such as `Table cells after a figure are missing from JSON output`.

Show the draft (see Drafts in [SKILL.md](../SKILL.md)). After approval:

```bash
gh issue create --repo opendataloader-project/opendataloader-pdf --label bug \
  --title "<title>" --body-file <draft file>
```

If you added a testdocs document, set its manifest `issues` field to the new issue and link the testdocs pull request from the issue.
