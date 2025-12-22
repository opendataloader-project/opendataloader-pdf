#!/bin/bash
# Build Stage 3 (Fix) prompt template
# Usage: ./build-stage3-prompt.sh
#
# Outputs a prompt template with placeholders to stdout.
# The caller is responsible for replacing placeholders with actual values.
# Can be used in both GitHub Actions and local testing.

set -euo pipefail

# This script outputs a prompt TEMPLATE with placeholders.
# The caller (workflow) is responsible for replacing placeholders with actual values.
# This design allows the workflow to handle shell injection prevention via env vars.
#
# Placeholders:
#   ISSUE_NUM_PLACEHOLDER       - Issue number
#   ISSUE_TITLE_PLACEHOLDER     - Issue title
#   AI_ANALYSIS_PLACEHOLDER     - AI analysis comment (truncated 2000 chars)
#   FIX_INSTRUCTION_PLACEHOLDER - @ai-issue fix comment (truncated 500 chars)

# Build prompt template (placeholders will be replaced by caller)
cat <<'PROMPT_EOF'
Fix GitHub issue #ISSUE_NUM_PLACEHOLDER: ISSUE_TITLE_PLACEHOLDER

## AI Analysis
AI_ANALYSIS_PLACEHOLDER

## Additional Instructions
FIX_INSTRUCTION_PLACEHOLDER

## Tasks
1. Find affected files and root cause
2. Write a failing test first (skip for docs/config only)
3. Implement minimal fix
4. Run tests and build

## Output
Always output JSON (no markdown code blocks), even if fix fails:
{
  "success": true | false,
  "understanding": "<brief summary of the issue>",
  "failure_reason": "<why fix failed, or null if success>",
  "test_added": "<test file path, or null if not applicable>",
  "changes": ["<file1>", "<file2>", ...],
  "verification": "<test results summary>"
}
PROMPT_EOF
