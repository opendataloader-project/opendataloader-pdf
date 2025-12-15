#!/bin/bash
# Build Stage 2 (Deep Triage) prompt
# Usage: ./build-stage2-prompt.sh <issue_num> <issue_json_file> [codebase_path]
#
# Outputs the complete prompt to stdout
# Can be used in both GitHub Actions and local testing

set -euo pipefail

# Arguments
ISSUE_NUM="${1:-999}"
ISSUE_JSON_FILE="${2:-}"
CODEBASE_PATH="${3:-}"  # Optional: restrict codebase search to this path

# Find script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
SKILL_DIR="$ROOT_DIR/.claude/skills/ai-issue"

# Read issue JSON
if [ -n "$ISSUE_JSON_FILE" ] && [ -f "$ISSUE_JSON_FILE" ]; then
  ISSUE_JSON=$(cat "$ISSUE_JSON_FILE")
else
  ISSUE_JSON="{}"
fi

# Read skill files (optional)
AI_FIX_CRITERIA=""
ISSUE_POLICY=""
MEMBERS=""

if [ -f "$SKILL_DIR/ai-fix-criteria.yml" ]; then
  AI_FIX_CRITERIA=$(cat "$SKILL_DIR/ai-fix-criteria.yml")
fi

if [ -f "$SKILL_DIR/issue-policy.yml" ]; then
  ISSUE_POLICY=$(cat "$SKILL_DIR/issue-policy.yml")
fi

if [ -f "$SKILL_DIR/members.yml" ]; then
  MEMBERS=$(cat "$SKILL_DIR/members.yml")
fi

# Build codebase instruction
if [ -n "$CODEBASE_PATH" ]; then
  CODEBASE_INSTRUCTION="IMPORTANT: Only search within this directory: $CODEBASE_PATH
Do NOT search outside this path. This is a test codebase for evaluation."
else
  CODEBASE_INSTRUCTION="Search the entire repository for relevant files."
fi

# Build prompt
cat <<PROMPT_EOF
Perform deep analysis for GitHub issue #$ISSUE_NUM using the ai-issue skill.

## Issue Details
$ISSUE_JSON

## Codebase Scope
$CODEBASE_INSTRUCTION

## Instructions
Use the ai-issue skill to:
1. Read the skill files in .claude/skills/ai-issue/ for policies and criteria
2. **Analyze the codebase** (within the specified scope) to understand:
   - What the issue is about
   - Which files/components are involved
   - How the current implementation works
3. **Score the issue** using the 4-axis scoring system in ai-fix-criteria.yml:
   - Scope (0-30): Change scope and complexity
   - Risk (0-30): Failure risk level
   - Verifiability (0-25): Verification capability
   - Clarity (0-15): Requirement clarity
4. Decide action based on total score vs threshold:
   - score >= threshold → "fix/auto-eligible"
   - score < threshold → "fix/manual-required"
   - no code change needed → "fix/comment-only"
5. For "fix/comment-only", use when:
   - User didn't find existing feature (guide them to it)
   - Documentation question (point to docs)
   - Feature request requiring roadmap review
   - External dependency issue (not our problem)
   - Need more information to reproduce
   - Duplicate of existing issue
   - Working as designed (won't fix)
6. Select appropriate labels, priority, and estimate based on issue-policy.yml
7. Recommend the best available team member from members.yml (skip if fix/comment-only)

## AI Fix Criteria (Scoring System)
${AI_FIX_CRITERIA:-Use standard criteria with threshold 70.}

## Issue Policy
${ISSUE_POLICY:-Priority: P0 (critical), P1 (important), P2 (normal). Story points: 1, 2, 3, 5, 8.}

## Team Members
${MEMBERS:-Available: benedict (available)}

## Required Output
Respond with JSON only (no markdown code blocks):
{
  "action": "fix/auto-eligible" | "fix/manual-required" | "fix/comment-only",
  "score": {
    "total": <number 0-100>,
    "threshold": <number from ai-fix-criteria.yml>,
    "breakdown": {
      "scope": { "score": <0-30>, "reason": "explanation" },
      "risk": { "score": <0-30>, "reason": "explanation" },
      "verifiability": { "score": <0-25>, "reason": "explanation" },
      "clarity": { "score": <0-15>, "reason": "explanation" }
    }
  },
  "labels": ["bug", "enhancement", "documentation", ...],
  "priority": "P0" | "P1" | "P2",
  "estimated": 1 | 2 | 3 | 5 | 8,
  "assignee": "github_id",
  "analysis": {
    "summary": "One paragraph summary of what this issue is about",
    "expected_behavior": "What the user expects to happen",
    "current_behavior": "What currently happens (if applicable)",
    "affected_files": ["path/to/file1.ts", "path/to/file2.ts"],
    "root_cause": "Technical explanation of why the issue occurs (if identifiable)",
    "suggested_approach": "How to fix or implement this"
  },
  "comment_draft": "(Only for fix/comment-only) Draft response to post on the issue"
}
PROMPT_EOF
