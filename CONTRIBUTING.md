# Contributing to opendataloader-pdf

Contributions from people and from AI agents are equally welcome. Every issue and pull request is judged by the same standard: **evidence that anyone can rerun**. A report shows a problem on a public document. A fix shows the same document producing correct output, without lowering benchmark scores.

- [The contract](#the-contract)
- [Test documents](#test-documents)
- [Reporting a bug](#reporting-a-bug)
- [Suggesting a feature or asking a question](#suggesting-a-feature-or-asking-a-question)
- [Submitting a pull request](#submitting-a-pull-request)
- [Rules for AI agents](#rules-for-ai-agents)
- [Building and testing](#building-and-testing)
- [Commit messages](#commit-messages)
- [Developer Certificate of Origin](#developer-certificate-of-origin)

## The contract

| | An issue shows | A pull request shows |
|:--|:--|:--|
| **Document** | A public document on which the output is wrong | The same document |
| **Result** | The command, the version, and the expectations that fail | The same expectations, failing before the change and passing after it |
| **Scope** | One problem | One issue |
| **Regressions** | — | `./scripts/bench.sh --check-regression` passes |

Unit tests are required for changed logic, but they are not evidence that the reporter's problem is gone. Evidence is the command a user would run, on the document the user had, producing the output the user expected.

## Test documents

Public documents live in [opendataloader-testdocs](https://github.com/opendataloader-project/opendataloader-testdocs). Each one has a manifest with its license, its contents, and expectations for correct output, and `scripts/run_expect.py` checks them:

```bash
git clone https://github.com/opendataloader-project/opendataloader-testdocs ../opendataloader-testdocs
python ../opendataloader-testdocs/scripts/find.py --tag table-extraction
python ../opendataloader-testdocs/scripts/run_expect.py <id>
```

If no document shows your problem, add one; see the [testdocs contributing guide](https://github.com/opendataloader-project/opendataloader-testdocs/blob/main/CONTRIBUTING.md). Only documents that anyone may redistribute are accepted. If the document that shows your problem is private, do not attach it to an issue — recreate the structure that triggers the problem in a new document and contribute that instead.

## Reporting a bug

Search the existing issues first and comment on a match instead of opening a duplicate. Then use the **Bug report** form, which asks for:

- the test document: a testdocs id, or a public URL and its license
- the opendataloader-pdf version and the exact command
- the expected and the actual output
- whether an AI agent drafted the report

## Suggesting a feature or asking a question

Use the **Feature request** or **Question** form. For a feature, attach a public document and describe the output you want from it.

## Submitting a pull request

1. **Link an issue.** Open one first if none exists; the issue is where the problem is agreed on.
2. **Check that nobody else is on it.** Look for an open pull request or a recent comment claiming the issue, and comment that you are taking it.
3. **Reproduce before you change code.** Run the testdocs document, or the reporter's steps, and see the failure yourself. If you cannot reproduce it, report what you tried on the issue instead of submitting a fix.
4. **Change, test, record.** Make the smallest change that resolves the issue, add a test that fails without it, and add expectations to the testdocs document if it does not have them yet.
5. **Run the checks** in [Building and testing](#building-and-testing).
6. **Fill in the pull request template:**
   - **Objective** — the problem, quoted from the issue
   - **Approach** — how the change solves it, in terms a user understands
   - **Evidence** — the `run_expect.py` table with the latest release as `before` and your build as `after`, and the benchmark result
7. **Respond to review** and update the pull request.

## Rules for AI agents

AI agents follow the procedure in [`.agents/skills/odl-contribute`](.agents/skills/odl-contribute/SKILL.md). It is an [Agent Skill](https://agentskills.io); agents without skill support can read it as plain Markdown. On top of everything above:

- **A person approves everything that is posted.** The agent shows its operator every issue, comment, and pull request as a draft, and posts only what the operator approves.
- **Disclose the assistance.** Add an `Assisted-by: <agent> (<model>)` trailer to every commit, and say so in the issue or pull request form.
- **The operator signs off.** The `Signed-off-by` line names the person who ran the agent and takes responsibility for the contribution. An agent cannot certify the Developer Certificate of Origin.
- **Run what you claim.** Execute every command and code example before posting it, and paste real output.

## Building and testing

**Prerequisites:** Java 11+, Maven, Python 3.10+, uv, Node.js 24 (current active LTS), pnpm via `corepack enable pnpm`

Node 24 and pnpm 11.21.0 are what CI builds against. Enabling Corepack once picks the pnpm version up from the `packageManager` field, so there is no global install and no version to remember. Node must be >=22.13 — pnpm 11 refuses to install on anything older. See the [Development Workflow guide](https://opendataloader.org/docs/development-workflow) for OS-specific install instructions.

```bash
npm run build-java                        # build the Java packages
./scripts/test-java.sh                    # Java tests (test-python.sh and test-node.sh for the wrappers)
python verification/ci-verify.py          # CLI verification against the installed Python package
./scripts/bench.sh --check-regression     # benchmark; CI fails when a score drops below its threshold
./scripts/bench.sh --doc-id <id>          # benchmark a single DP-Bench document
```

> **If you modified CLI options in Java, run `npm run sync` before committing.** It regenerates `options.json` and all Python and Node.js bindings. Forgetting it silently breaks the wrappers.

- Follow the conventions of the code around your change.
- Do not add MDX files. `content/docs/reference/` is generated at release time and is not tracked.
- If you change behavior that can fail silently — output that is skipped or dropped without an error — update the hazard principles and the release-review checklist in [`skills/odl-pdf-maintenance/MAINTAINING.md`](skills/odl-pdf-maintenance/MAINTAINING.md). The user-facing skill in `skills/odl-pdf/` reads the CLI's `--help` at runtime and needs no change when an option is renamed.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/): `fix:`, `feat:`, `docs:`, `test:`, `refactor:`, `chore:`, with an optional scope.

```
fix(table): keep cells that follow a figure in the same column

Assisted-by: <agent> (<model>)
Signed-off-by: Your Name <you@example.com>
```

## Developer Certificate of Origin

Every commit must be signed off with `git commit -s`. Signing off certifies the [Developer Certificate of Origin](https://developercertificate.org/) for that commit. Make sure your Git configuration has your real name and email.

Depending on your contribution, we may also ask you to sign a Contributor License Agreement (CLA).
