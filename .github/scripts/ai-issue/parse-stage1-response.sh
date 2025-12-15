#!/bin/bash
# Parse Stage 1 response and output GitHub Actions outputs
# Usage: ./parse-stage1-response.sh <response_text>
#
# Outputs (for GitHub Actions):
#   decision=invalid|duplicate|needs-info|valid
#   duplicate_of=<number or empty>
#   reason=<string>
#   questions=<json array>

set -euo pipefail

RESPONSE_TEXT="${1:-}"

if [ -z "$RESPONSE_TEXT" ]; then
  # Read from stdin if no argument
  RESPONSE_TEXT=$(cat)
fi

# Parse JSON from text (handle multiline JSON)
RESULT=$(echo "$RESPONSE_TEXT" | jq -c '.' 2>/dev/null || \
         echo "$RESPONSE_TEXT" | sed -n '/{/,/}/p' | jq -c '.' 2>/dev/null || \
         echo '{}')

# Extract fields (expected: invalid|duplicate|needs-info|valid)
DECISION=$(echo "$RESULT" | jq -r '.decision // "valid"')
DUPLICATE_OF=$(echo "$RESULT" | jq -r '.duplicate_of // empty')
REASON=$(echo "$RESULT" | jq -r '.reason // "Could not parse response"')
QUESTIONS=$(echo "$RESULT" | jq -c '.questions // []')

# Output for GitHub Actions
echo "decision=$DECISION"
echo "duplicate_of=$DUPLICATE_OF"
echo "reason=$REASON"
echo "questions=$QUESTIONS"
