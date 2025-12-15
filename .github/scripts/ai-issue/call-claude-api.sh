#!/bin/bash
# Call Claude API with a prompt
# Usage: ./call-claude-api.sh <prompt_file> [model] [max_tokens]
#
# Environment variables:
#   ANTHROPIC_API_KEY - Required API key
#
# Outputs the response JSON to stdout

set -euo pipefail

PROMPT_FILE="${1:-}"
MODEL="${2:-claude-haiku-4-5}"
MAX_TOKENS="${3:-512}"

if [ -z "$PROMPT_FILE" ] || [ ! -f "$PROMPT_FILE" ]; then
  echo "Error: Prompt file required" >&2
  exit 1
fi

if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  echo "Error: ANTHROPIC_API_KEY environment variable required" >&2
  exit 1
fi

PROMPT=$(cat "$PROMPT_FILE")

RESPONSE=$(curl -s -X POST https://api.anthropic.com/v1/messages \
  -H 'Content-Type: application/json' \
  -H "X-Api-Key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -d "$(jq -n --arg p "$PROMPT" --arg m "$MODEL" --argjson t "$MAX_TOKENS" '{
    model: $m,
    max_tokens: $t,
    messages: [{role: "user", content: $p}]
  }')")

# Check for API error
if echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
  echo "API Error: $(echo "$RESPONSE" | jq -r '.error.message')" >&2
  exit 1
fi

# Extract text content
TEXT=$(echo "$RESPONSE" | jq -r '.content[0].text // empty')

echo "$TEXT"
