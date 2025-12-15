---
name: ai-issue
description: Automatically process GitHub issues using AI analysis. Validates issues, determines priority, assigns labels, routes to assignees, and decides auto-fix eligibility. Use when processing, categorizing, or routing GitHub issues.
---

# AI Issue Skill

Three-stage AI system for automatic GitHub issue classification and processing.

## Purpose

- **Stage 1 (Triage)**: Validate issues (duplicate, spam, scope check)
- **Stage 2 (Analyze)**: Analyze code and decide action (fix/auto-eligible, fix/manual-required, fix/comment-only)
- **Stage 3 (Fix)**: Automatically fix eligible issues and create PRs

## When to Use

- Processing new GitHub issues
- Determining issue priority and complexity
- Deciding AI fix eligibility
- Assigning to appropriate team members

## Reference

| File | Description |
|------|-------------|
| [workflow.md](workflow.md) | Detailed workflow (Stage 1/2/3 process, flowchart) |
| [testing.md](testing.md) | Test framework for validating decisions |
| [issue-policy.yml](issue-policy.yml) | Labels, priority (P0-P2), story points policy |
| [ai-fix-criteria.yml](ai-fix-criteria.yml) | Criteria for AI auto-fix eligibility |
| [members.yml](members.yml) | Team member list and availability |
| [labels.yml](labels.yml) | Label definitions (data source) |
| [setup-labels.sh](setup-labels.sh) | Script to sync labels to GitHub |
| [../../scripts/build-all.sh](../../../scripts/build-all.sh) | Build & test all packages (Java, Python, Node.js) |

## Key Decisions

### Actions

| Action | Condition |
|--------|-----------|
| `fix/auto-eligible` | Meets criteria in ai-fix-criteria.yml, creates PR |
| `fix/manual-required` | Expert review required (see members.yml) |
| `fix/comment-only` | No code change needed, respond with comment (existing feature guidance, docs reference, roadmap review needed, external dependency, needs more info, duplicate, won't fix) |

## Build & Test

Before creating a PR (Stage 3), **MUST** run the build script to verify all packages build and test successfully.

```bash
# Build and test all packages (Java → Python → Node.js)
./scripts/build-all.sh

# With specific version
./scripts/build-all.sh 1.0.0
```

**Requirements:**
- All three builds (Java, Python, Node.js) must pass
- All tests must pass
- Script exits immediately on first failure (`set -e`)
