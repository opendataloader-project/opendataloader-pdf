# Tools and files

Run each tool from the trusted skill folder that the start gate records, never from a tree of a pull request. A tool's refusal is final: do not work around it. Only the user can accept a gap, at a gate, in `decisions.json`.

## Tools

| Tool | Proves or makes | Runs | Refuses | Eval |
|:--|:--|:--|:--|:--|
| `prepare.py triage` | `triage/triage.json`: for each open pull request (or each one of `--prs`) the facts and a class: CLA, CI checks of the workflows and the runs of the head against the pull_request workflows of `main`, conflicts, a branch with more than 100 commits, draft, base, sensitive files, a cut file list, the same closing issue, the round from footers by writers, a push or an author comment after the last counted review, an open CodeRabbit thread without an author answer | host, network (gh, git fetch), read-only | exit 11: the list length is not GitHub's count of open pull requests | X5-triage X5-triage-count |
| `prepare.py triage-report` | `triage/triage.md` from `triage.json` and your `judgment.json`, sorted by priority; outside text in one line, escaped, and cut | host | exit 12: an incomplete triage, a missing or unknown judgment, a class weaker than the facts, a changed class without a reason, or `review` for a concern | X5-triage-report |
| `prepare.py fetch` | `state.json`: PR state, main and head SHAs, CLA and CI checks, the workflow runs of the head (`gh run list --commit`, with `createdAt`), `fetched_at`, `head_committed_at` (the committer date of the head), the permission of the PR's author, reviews with a review-state footer and the permission of each author | host, network (gh, git fetch) | the head moved during the fetch | — |
| `prepare.py trees` | the round folder: trees written from git objects, without agent instruction files (of all common agents, matched after Unicode and case folding) and links (their text goes to `quoted/`); `pr.json` (with failing and pending CI checks; a pull_request workflow of main without a finished run for the head is pending, unless it has a path filter; a run that failed before any job started is pending, or failing as "GitHub cannot rerun it" when it is 30 days or more older than the fetch; `author_permission`; `cla` is `PENDING` when the status is missing and the head is less than 15 minutes older than the fetch, with `head_age_minutes`), `<tree>.manifest` (each folder and file of a tree, with SHA-256), `kind.json` (kind from the changed files), `files.json`, `diff.patch`, `head.txt` (the head of the files in the round folder, also after a run that stopped); in a later round, `earlier-review.md`, `pr.json` `last_review` (commit, `in_history`) and `new.diff` (since the reviewed commit, if it is in the history) | host, no network | not open or draft (exit 3); base is not main (exit 4); an instruction file or link left in a tree, or two paths that collide on a case-insensitive file system (exit 5); a file that cannot be written (exit 6) ; if `pr.json` of the round names another head (a push before a post), the files that `trees` does not make move to `old-<sha8>/` first, and no `new.diff` of the old head stays; a git error of `merge-tree` (an exit code other than 0 and 1) stops it, and is not a conflict | E1 X15-old-head X14-cla-fresh X-prepare X-ci X7-later-round X4-ci-old-run X18-trees-round-files |
| `prepare.py fresh` | `fresh.json` before a post: the new head and `main` against `pr.json`; the files that changed on `main`; the lines of the drafts (`review.md`, `close.md`, `thanks.md`, `new-issue-*.md`, `comment*.md`, outside code) that promise maintainer work, a schedule, or credit, or say "(from the maintainers)" before `decided_checks` has `Q0-direction` (`promises`); with `--approve`, the CI of the head | host, no network (after `fetch`) | exit 7: no fetch after the round, not open, a draft, another base, or a new head; a card older than `verdict.json`; a promise that `commitments_ok` does not hold; a gain case (`main` fail, PR pass) and no ✅ in the "This PR" column of the Checks table of `review.md`; with `--approve`, CI not finished or not green; exit 8: `main` moved | X3-fresh X4-ciapproval X8-card-fresh X11-promises X12-gain |
| `prepare.py ci-approval` | the approve command of each fork CI run of the head that waits for a maintainer, and the rerun command of each run that failed before any job started (no job: a workflow or GitHub problem; `fetch` counts the jobs of each failed run); for such a run that is 30 days or older, no command but the choices (a push by the author, or a close and reopen) | host, no network (after `fetch`) | exit 9: the head changed, the PR changes workflow, build, tooling, or agent files, an unexplained scan hit, no passing Q2-security record, or no `ci_approve` from the user | X4-ciapproval X4-ci-old-run |
| `prepare.py cleanup` | after the post: removes `refs/odl-review/<n>` | host, local git | exit 10: no `retro.md` with the retrospective table | X4-retro |
| `prepare.py blind` | `$WORK/<n>/r<k>-blind/` for the blind correctness reviewer: the diff, the demo input files, the tree paths; no plan, cases, precedents, evidence, or outputs | host | — | X3-blind |
| `prepare.py strip-outputs` | removes links, special files, and agent instruction files from the outputs of a run; the run tool calls it | host | — | X-strip |
| `prepare.py check-tree` | the tree is what `trees` wrote: no path outside the git objects, none missing, no changed file (`<tree>.manifest`); the run tool calls it | host | exit 13: an extra, missing, or changed path (for example a `.claude` folder that an agent made in the tree), or no manifest | X10-tree-clean X18-trees-round-files |
| `diff_scan.py diff` | `scan.json`: hidden and invisible Unicode, encoded blobs, long lines, sensitive paths, risky APIs, workflow and build changes, test configuration, removed assertions, active markup, added PDFs, fonts (license fields), images, executables, personal-data patterns. Source and text files are always scanned as text, whatever their first bytes | host (reads git objects only; bounded patterns, lines cut at 4,000 characters) | — (lists hits; `verdict.py` holds until each is explained) | E1 E3 E7 X-scan X16-scan-headers |
| `diff_scan.py pii` | personal-data hits in text that main's CLI extracted from an added PDF in an isolated run | host | — | E7 |
| `precedents.py` | `precedents.json`, `precedents.txt`, `precedents/<id>.md`: earlier decisions from GitHub (closed, not planned, merged, open) and from git (reverts, line history of all hunks of a file, decision comments). The author of this PR is never a decider. A close without a comment counts for the maintainer who closed it. A merged PR's text is its body; its decider is the person who merged it. A line-history commit gets `reverts` when the PR puts back a line that the commit removed or removes a line that it added; `verdict.py` then needs its assessment, and in a later round it needs the assessment of each hit that the earlier round did not have (of each hit, if the folder of the earlier round has no `precedents.json`). Full text, quote structure kept | host, network (gh) | — (a failed query, a search that reached `--limit`, or a line history that reached `--max-line-commits` makes the result partial; `verdict.py` holds) | E2 E8 X-precedents X3-precedents X9-revert X9-new-hits X16-scan-headers |
| `suite_compare.py` | tests that ran on main but are missing, skipped, or failing on the merge, and tests that only the merge has and that fail (`failing-new`); changed or deleted test files and removed assertions. The review's own tests (`--overlay extra/<label> --tree trees/main`) are left out. With `--round-dir`, it writes the Q2-suite evidence record itself | host if the PR changes no build or tooling file, else isolated (`--isolated`) | no reports for main; on the host, a PR with build or tooling changes (exit 3); a report with a DOCTYPE, a link, or over 50 MB does not count; a `git diff` that fails stops it | E3 X3-suite X17-suite-new-failing |
| `panel_brief.py` | the brief of a second panel round: the verdict now, each correction, confirmation, and refutation with its record, and the known open items | host | — | X3-panel |
| `side_effects.sh` | main's corpus converted by both jars to json, markdown, html, text, and tagged-pdf; error diff and changed outputs | isolated | a corpus inside the run copy | E3 X6-side-effects-top X25-side-effects-names |
| `verdict.py` | `verdict.json`, `verdict.md`: the verdict and its reasons (also a Hold when Q1-solves passes and no case shows the PR's gain), from the files below and `references/catalog.json`; a `PENDING` CLA holds, a missing or failed CLA blocks. `verdict.md` lists each user decision that it used | host | — (a missing or malformed input gives Hold; if the tool fails, it writes Hold) | E2 E3 E6 E7 E8 R703 R805 R806 R705 U1 X-verdict X4-reviewers X12-gain X14-cla-fresh |
| `isolate.reference.sh` | isolated runs (I1-I6), in a copy of the run folder on a new Docker volume (`/scratch/tree`; a host bind mount can break file deletes); outputs without links, special files, or agent instruction files; `manifest.jsonl` | Docker | no passing isolation check for its settings; a bad label; a tree that `check-tree` refuses, also a folder of `trees/` without its manifest (exit 5); a copy of the tree or of `extra/<label>/` that fails (exit 4) | X4-runtool X10-tree-clean X20-runtool-copies |
| `demo.py` | the demo folder | host | a symbolic link as input | X23-demo-count |
| `save_report.py` | `rev-<role>/report.md` or `panel-<round>-<member>.md` from the transcript of the subagent: the last SubagentHandback text (or `--index N`), else the last assistant text; prints only the sizes and the first line | host | exit 1: no report in the transcript | X13-save-report |
| `bench.py`, `inspect_pdf.py`, `md_lists.py`, `artifacts.py`, `fetch_attachment.sh` | benchmark scores, tagged-PDF structure, CommonMark list numbers, artifact contents, attachments | see each file | see each file | X21-inspect-pdf X22-bench-commit X24-md-lists-quote |

Commands for checks that have no tool yet (linters, related files, fork CI, skill feedback): `checks.md`.

Run the evals after each change to a tool: `python3 -I evals/run.py`. A new weakness becomes a new eval first. Then change the tool until the eval passes.

## The round folder

`prepare.py trees` makes `$WORK/<n>/r<k>/`, one folder for each round. Put all files of the round there.

| File | Made by | Content |
|:--|:--|:--|
| `pr.json`, `kind.json`, `files.json`, `diff.patch`, `trees/`, `quoted/` | `prepare.py` | see above. `kind.json` "auto" is one of `dependency`, `docs`, `test`, `tooling`, `code`, `mixed` |
| `scan.json` | `diff_scan.py diff --old <pr.json diff_range[0]> --new <diff_range[1]>` | hits `S1`… |
| `precedents.json`, `precedents.txt`, `precedents/` | `precedents.py` | hits `P1`… |
| `plan.json` | you | below |
| `evidence.jsonl` | you and the tools | `references/evidence.md`. Only records with the current head and main SHAs count |
| `cases.json` | you | below. Keep `cases.md` too, for people |
| `objections.json` | you, from the plan critic and the panel | below; `[]` if there were none |
| `decisions.json` | you, with the user's answers at a gate | below |
| `verdict.json`, `verdict.md` | `verdict.py` | the verdict, the reasons, the check table, the post command |
| `fresh.json` | `prepare.py fresh` | before a post: the new head and `main`, the files that changed on `main`, and the draft lines that promise (`promises`) |

**`plan.json`:**

```json
{
  "kind": "bugfix",
  "kind_reason": "the issue reports wrong Markdown output; the change is in the list code",
  "risks": [{"id": "R1", "text": "labels that are not bullets are removed", "checks": ["Q2-counterexamples"]}],
  "checks": [
    {"id": "Q2-corpus", "status": "done"},
    {"id": "Q2-benchmark", "status": "skipped", "reason": "no call path from the changed code to Markdown", "reason_evidence": "E9"},
    {"id": "Q2-hybrid-path", "status": "cannot", "reason": "no docling on this computer"},
    {"id": "X-md-render", "question": "Q2", "status": "done"}
  ],
  "scan_explanations": {"S3": {"text": "test data for the sanitizer", "evidence": "E4"}, "rule:time-api": {"text": "test timing only"}},
  "precedents": [{"id": "P2", "covers": true, "valid": true, "effect": "conflict", "evidence": "E2"}],
  "q1_reproduce": {"release": "fail", "main": "fail", "release_evidence": "E5", "main_evidence": "E6",
                   "same_environment": true, "issue_environment_available": true},
  "partial_fix": false,
  "safety_weakened": false,
  "decide_reasons": [{"id": "T1", "text": "a new default for all users, or an option"}],
  "ci_explanations": {"build (windows)": "E8"}
}
```

- `kind`: one of the kinds of `catalog.json`. It must fit `kind.json` "auto".
- `checks`: each needed check of the catalog, and each check that you invent (an id that the catalog does not have; no `:` in it). Status: `planned`, `done`, `skipped` (with a reason; it counts only if a record `{"condition": "skip:<id>", "state": "pass", "verified_by": "panel"}` exists), `cannot` (a capability is missing; the user decides). A security check cannot be skipped or waived. A skip or a gap never hides a record that fails.
- `q1_reproduce`: `release` and `main` are `fail` (the problem shows) or `pass`; each must equal the state of its run record.
- The state of a check comes from its evidence records: any `fail` is a fail, else any `pass` is a pass. A record with `n/a` does not make a needed check pass.
- A record that an explanation, an assessment, or a CI explanation points to must have the state `pass`. A record that a case or a reproduction points to must be a run (`pass` or `fail`).
- An invented check has the impact "block". To lower it, set `"impact"` and `"impact_shown_at_gate": true`, and show it at the post gate.
- `scan_explanations`: each high and medium hit by id, with evidence. Info hits can share one explanation by rule (`rule:<rule>`).
- `decide_reasons`: questions for the maintainers that no case carries. Give each an id, so that an answer in `decisions.json` closes it; a plain string stays a Decide.
- `precedents`: each hit that `verdict.py` lists as "not assessed" (closed PRs, issues not planned, reverts, decision comments, line history with decision words). `effect`: `reject` (the same contribution was rejected), `conflict` (the change undoes a decision), `unknown-reason` (for example a revert without a reason), `support` (the decision agrees with the change), `none`.

**`cases.json`:** `[{"id": 1, "input": "...", "ideal": "...", "source": "issue|spec|reader|safety|precedent|failure-cost", "main": "pass|fail|n/a", "pr": "pass|fail", "type": "request|main-defect|info", "tradeoff": "T1", "test": "Review805_...", "evidence": "E12"}]`. `evidence` is the run of the merge; its state must equal `pr`. A request or safety case needs that run (`pr` cannot be `n/a`). Several cases can carry one trade-off; it is one question.

**`objections.json`:** `[{"id": "O1", "panel": "critic|evidence|ideal|new-risk", "claim": "...", "input": "...", "severity": "blocker|major|minor|nit", "status": "confirmed-pr|fixed|refuted|open", "security": false, "evidence": "E14", "accepted_by": "panel round 2"}]`

- `confirmed-pr`: the pull request is wrong. `fixed`: our own work was wrong, and you corrected it; it needs a passing record of the correction.
- A `security` value that is missing, `null`, `false`, `0`, `"false"`, or `"no"` means not a security objection. Any other value counts as a security objection. An open security objection holds at any severity.
- `refuted` counts only with `accepted_by` (the objector or a new panel member accepted the refutation) and evidence that ran the objection's input on the right SHAs. Else it is disputed. The tools cannot tell who accepted it, so `verdict.md` lists each refuted objection, and the post gate shows the list to the user.

**`decisions.json`:** `{"tradeoffs": {"T1": "B"}, "decided_checks": {"Q0-direction": "the proposed behavior of the review"}, "maintainer_followup": ["Q3-lint"], "accept_gaps": ["Q2-hybrid-path"], "sensitive_ok": ["S2"], "approve_ok": false, "cla_not_required": false, "post_while_deciding": false, "ci_approve": false, "commitments_ok": ["We will review the next push in this pull request."]}`. Write only what the user said, as lists, strings, and `true`/`false`. `tradeoffs` takes the ids of trade-offs, of decide reasons, and of precedents whose effect is `conflict` or `unknown-reason` (the user keeps the change against that decision; `verdict.md` lists it). `decided_checks` takes failed checks with the impact "decide" only; an answer never closes a check with another impact. `maintainer_followup` takes failed checks with the impact "block" and failing request cases that the maintainer fixes after the merge; the verdict is then "Approve (follow-up by the maintainer: …)" and the card lists them. Security, hold, and decide items cannot be followed up. `sensitive_ok` takes scan hit ids or rule names. `post_while_deciding` gives a post command while Decide questions are open (for example, to post a comment that asks them). `commitments_ok` takes draft lines (the text, or `file:line`) that promise something and that the user accepted as written; `prepare.py fresh` then lets them pass.

- The tools cannot tell who wrote this file. Thus `verdict.md` lists each decision that it used, and the post gate shows that list to the user.
- A security check cannot be accepted as a gap.
- With an accepted gap, the post command is `--comment`, not `--approve`.
