---
name: odl-contribute
description: >-
  Procedure for contributing to opendataloader-pdf with evidence anyone can
  rerun: report a bug on a public test document, fix an issue and prove it with
  before/after runs, or add a PDF to opendataloader-testdocs. Use when the user
  wants to report a bug, crash, or wrong output from opendataloader-pdf, reproduce
  or fix an opendataloader-pdf issue, open a pull request to this repository, or
  contribute a test document. Every issue, comment, and pull request is drafted
  for the user's approval before it is posted.
license: Apache-2.0
compatibility: >-
  Requires git, the GitHub CLI (gh) signed in, a POSIX shell, Java 11+, Maven,
  Python 3.10+, and uv. Needs network access to github.com and pypi.org.
metadata:
  project: opendataloader-pdf
---

# Contributing to opendataloader-pdf

This skill is the procedure; [CONTRIBUTING.md](../../../CONTRIBUTING.md) holds the rules. Read CONTRIBUTING.md before you start — every step below applies one of its rules.

The person you work for is called the **operator** here. You gather information, reproduce, write code, and draft. The operator decides what gets posted.

## Principles

1. **Draft, then post only what the operator approves.** Every write to GitHub — issue, comment, label, push, pull request — is shown to the operator as a complete draft first. Post it only after an explicit yes. Maintainers see a wrong comment in their notifications even after it is deleted.
2. **Evidence is execution.** A unit test proves the code does what you wrote. Evidence proves the reporter's problem is gone: the command the reporter would run, on the document the reporter had, producing the output the reporter expected.
3. **Reproduce before you fix.** A fix for a failure you never saw is a guess. If you cannot reproduce, stop and report what you tried.
4. **Public documents only.** Never upload, attach, or quote a document the operator cannot publish. Recreate the structure that triggers the problem in a new document instead.
5. **Read the whole conversation before acting.** Know who spoke last, what they are waiting for, and whether a merged pull request already solved it.
6. **Run what you claim.** Execute every command and code example before it appears in a draft, and paste real output.
7. **One issue, smallest change.** A pull request resolves one issue and changes nothing it does not need to.

## Setup

Work from a checkout of opendataloader-pdf; the target repository is `opendataloader-project/opendataloader-pdf`. Find or clone the test documents next to it:

```bash
TESTDOCS=../opendataloader-testdocs
[ -d "$TESTDOCS" ] || git clone https://github.com/opendataloader-project/opendataloader-testdocs "$TESTDOCS"
git -C "$TESTDOCS" pull --ff-only
```

Confirm `gh auth status` succeeds. Outside contributors do not have push access; work from a fork (`gh repo fork --remote`).

## Choose the procedure

| The operator wants to | Follow |
|:----------------------|:-------|
| Report a bug, a crash, or wrong output | [references/report.md](references/report.md) |
| Fix an issue or open a pull request for one | [references/fix.md](references/fix.md) |
| Add a PDF to opendataloader-testdocs | [references/add-document.md](references/add-document.md) |

Decide from the request; ask the operator when it is unclear. Report and fix both hand off to add-document when no public document shows the problem, then return.

## Drafts

Present every draft the same way:

```
Draft — <issue | comment | pull request> on <repository>#<number or "new">

<the complete text exactly as it will be posted>

Checks: <commands you ran and what they showed; anything you could not verify and why>
Post this?
```

After posting, give the operator the link. Everything posted to GitHub is in English; talk with the operator in their language.

## Disclosure

- Every commit carries `Assisted-by: <agent> (<model>)` and a `Signed-off-by` line for the operator, from `git commit -s` with the operator's Git identity.
- Issue and pull request forms have an **AI assistance** field. Choose the agent option.

## Stop and ask the operator

| Situation | Do this |
|:----------|:--------|
| A merged pull request already addresses the issue | Compare its change with the reporter's pain point; draft a comment that links it, and do not write a new fix |
| An open pull request or a recent comment claims the issue | Tell the operator and stop |
| The problem does not reproduce | Draft a comment listing exactly what you ran and on which version; do not submit a fix |
| The fix needs a design decision, such as a new option or a changed default | Present the options with their trade-offs and wait |
| One issue describes several problems | Propose splitting it, with a draft issue per problem |
| The root cause is in a dependency, such as veraPDF or docling | Show where it is and ask whether to work around it here, report it upstream, or both |
| The only document that shows the problem is private | Follow add-document to synthesize one; never upload the original |
