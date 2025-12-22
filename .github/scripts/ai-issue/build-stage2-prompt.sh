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
1. Read skill files in .claude/skills/ai-issue/ for policies
2. Analyze codebase to identify affected files and root cause
3. Score: Scope(0-30), Risk(0-30), Verifiability(0-25), Clarity(0-15)
4. Action: score >= threshold → "fix/auto-eligible", else → "fix/manual-required", no code → "fix/comment-only"
5. Select labels, priority, estimate, assignee per policies

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
    "total": <0-100>,
    "threshold": <from ai-fix-criteria.yml>,
    "breakdown": { "scope": <0-30>, "risk": <0-30>, "verifiability": <0-25>, "clarity": <0-15> }
  },
  "labels": ["bug" | "enhancement" | "documentation"],
  "priority": "P0" | "P1" | "P2",
  "estimated": 1 | 2 | 3 | 5 | 8,
  "assignee": "github_id",
  "analysis": {
    "summary": "2-3 sentences: what the issue is, why it occurs, and how to fix",
    "files": ["path/to/file1.java", ...]
  },
  "comment_draft": "(Only for fix/comment-only) Response to post"
}
PROMPT_EOF
