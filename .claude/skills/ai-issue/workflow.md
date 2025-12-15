# AI Issue System

## Stage 1: Triage (README-Based)

**Trigger:** `on: issues: opened` or `workflow_dispatch`

**Goal:** Determine "Is this issue worth processing?"

**Checks:**
- Is it a duplicate?
- Is it spam?
- Is it within project scope?
- Is it clear enough?

**Outcomes:**
- Invalid → auto-close, add `wontfix` (GitHub default)
- Duplicate → link original, close, add `duplicate` (GitHub default)
- Unclear → ask clarifying question, add `question` (GitHub default)
- Valid → **directly calls Stage 2** via `workflow_call`

---

## Stage 2: Analyze (Code-Based Analysis)

**Trigger:**
- `workflow_call` from Stage 1 (when triage passes)
- `issue_comment` with `@ai-issue analyze` command (CODEOWNERS only)
- `workflow_dispatch`

**Goal:** Analyze the issue deeply and document findings for humans and AI.

**Checks:**
- Analyze codebase to understand the issue
- Identify affected files and root cause
- Determine implementation complexity
- Decide if auto-fix is appropriate

**Outputs:**
- Detailed analysis comment on the issue
- Labels: `fix/auto-eligible` or `fix/manual-required`
- Assignee recommendation

**Outcomes:**
- If auto-fixable: add `fix/auto-eligible`
- If not auto-fixable: add `fix/manual-required`, assign to team member

---

## Stage 3: Fix

**Trigger:**
- `workflow_dispatch`
- `issue_comment` with `@ai-issue fix` command (CODEOWNERS only)

**Goal:** Attempt to automatically fix issues marked as `fix/auto-eligible`.

**Preconditions:**
- Has `fix/auto-eligible` label (from Stage 2)

**Process:**
1. Read issue details and AI analysis from Stage 2
2. Use analysis to understand affected files and approach
3. Write test (if applicable)
4. Implement fix
5. Verify tests and build pass
6. Create PR

**Outcomes:**
- Success → PR created, remove `fix/auto-eligible` label
- Failure → remove `fix/auto-eligible`, add `fix/manual-required`

---

## Complete Flow Diagram

```
New Issue
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ Stage 1: Triage                                          │
│ Outcomes:                                                │
│   - Valid → directly calls Stage 2                       │
│   - Invalid → close + wontfix                            │
│   - Duplicate → close + duplicate                        │
│   - Unclear → add question label                         │
└──────────────────────────────────────────────────────────┘
    │                                     │
    │ (valid)                             │ (question label)
    │                                     │
    ▼                                     ▼
┌─────────────────────────────┐    ┌─────────────────────────┐
│ Stage 2 called via          │    │ Wait for user comment   │
│ workflow_call               │    │                         │
└─────────────────────────────┘    └───────────┬─────────────┘
    │                                          │
    │                                          │ (@ai-issue analyze)
    │                                          ▼
    │                              ┌───────────────────────────┐
    │                              │ Stage 2 triggered         │
    │                              │ by CODEOWNERS command     │
    └──────────────┬───────────────┴───────────────────────────┘
                   ▼
┌──────────────────────────────────────────────────────────┐
│ Stage 2: Analyze                                         │
│ Labels: fix/auto-eligible OR fix/manual-required         │
└──────────────────────────────────────────────────────────┘
                   │
                   │ (@ai-issue fix or workflow_dispatch)
                   ▼
┌──────────────────────────────────────────────────────────┐
│ Stage 3: Fix                                             │
│ Success: PR created, remove fix/auto-eligible            │
│ Failure: fix/manual-required                             │
└──────────────────────────────────────────────────────────┘
```
