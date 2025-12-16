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
#   ISSUE_NUM_PLACEHOLDER     - Issue number
#   ISSUE_TITLE_PLACEHOLDER   - Issue title
#   ISSUE_BODY_PLACEHOLDER    - Issue body content
#   ALL_COMMENTS_PLACEHOLDER  - All comments from the issue

# Build prompt template (placeholders will be replaced by caller)
cat <<'PROMPT_EOF'
You are fixing GitHub issue #ISSUE_NUM_PLACEHOLDER.

## Issue Title
ISSUE_TITLE_PLACEHOLDER

## Issue Body
ISSUE_BODY_PLACEHOLDER

## Discussion Thread (All Comments)
The following is the complete discussion thread including AI analysis and any follow-up questions/answers from humans:

ALL_COMMENTS_PLACEHOLDER

## Instructions
Based on the issue description and the ENTIRE discussion thread above:
1. First, understand the issue by reading the original description and ALL comments (including any clarifications or additional context provided after the AI analysis)
2. Locate the affected files
3. Understand the root cause and decide on the approach
4. **Write a test that reproduces the issue BEFORE implementing the fix** - The test should fail before the fix and pass after. This is required for code changes (bug fixes, new features, logic changes). Skip only for non-code changes (documentation, config files, typos, comments).
5. Implement the fix
6. Verify all tests pass (including any new tests you added)
7. Ensure the build succeeds
8. Only make minimal, focused changes

## Output Format
After completing the fix, output a summary in the following format:

---FIX_SUMMARY_START---
UNDERSTANDING: <Your understanding of the issue based on the description and all comments. Summarize what the problem is, what was discussed, and what approach you decided to take.>
SUCCESS: true/false
TEST_ADDED: <name and location of the test file(s) added, or "N/A - non-code change" if not applicable>
CHANGES: <list of files changed and why>
VERIFICATION: <how you verified the fix works - must include test results>
---FIX_SUMMARY_END---

Do NOT create a PR - just make the code changes and verify they work.
PROMPT_EOF
