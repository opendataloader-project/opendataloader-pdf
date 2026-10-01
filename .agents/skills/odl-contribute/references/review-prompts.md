# Review prompts

Run each review in a context that has not seen your work: a sub-agent, or a new session.

## Round 0 — blind review

The reviewer sees only the diff and the project conventions, so it judges what the code does rather than what you meant.

```
You are a code reviewer. Review the following diff. You have no prior
context; analyze it purely based on what you see.

## Project conventions

<CONTRIBUTING.md content>

## Diff

<git diff origin/main...HEAD>

## Instructions

Do not assume intent; analyze what the code actually does.

Review criteria:
- Correctness: does the logic do what its structure suggests?
- Edge cases: which inputs could break it?
- Removed code: is anything important deleted?
- Test quality: does the test cover the new behavior, and would it fail without the change?
- Risk: what could go wrong for users?

Tag each finding:
- [critical]: runtime errors, data loss, security vulnerabilities
- [major]: incorrect behavior, missing edge cases, significant quality issues
- [minor]: style, small inefficiencies
- [nit]: cosmetic, optional

End with exactly one line:
  VERDICT: pass
  VERDICT: fail

Use "fail" if any [critical] or [major] finding exists.
```

## Round 1 and 2 — context review

Add the issue and the previous round, so the reviewer can check intent against implementation.

```
You are a code reviewer. Review the following diff in the context of the
issue it fixes.

## Issue

<issue body and the comments that define the problem>

## Intent Spec

<Objective, Approach, Evidence>

## Project conventions

<CONTRIBUTING.md content>

## Diff

<git diff origin/main...HEAD>

## Previous round

<previous findings and how each was addressed>

## Instructions

Review criteria:
- Correctness and edge cases
- Test coverage
- Performance and security
- Root cause: does the change address the cause, or only the symptom?
- Intent: does it resolve the reporter's pain point as quoted in the Objective?
- Evidence: does the run_expect.py table show failure before and success after?

Tag findings [critical], [major], [minor], or [nit] as in round 0. Be
specific: for each finding, state the problem and suggest a fix.

End with exactly one line:
  VERDICT: pass
  VERDICT: fail

Use "fail" if any [critical] or [major] finding exists.
```

## Loop

- Round 0 is blind; rounds 1 and 2 add context.
- Fix only critical and major findings; ignore minor ones and nits.
- Rerun the full test suite after every round of fixes.
- After round 2, list any unresolved findings under "Review notes" in the pull request.
