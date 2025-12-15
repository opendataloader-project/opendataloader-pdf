#!/bin/bash
# Call Claude Code CLI with a prompt
# Usage: ./call-claude-code.sh <prompt_file> [allowed_tools] [timeout_minutes]
#
# Environment variables:
#   ANTHROPIC_API_KEY - Required API key
#
# Arguments:
#   prompt_file     - Path to file containing the prompt
#   allowed_tools   - Comma-separated list of tools (default: "Read,Glob,Grep")
#   timeout_minutes - Timeout in minutes (default: 15, used for display only)
#
# Outputs the Claude Code response to stdout

set -euo pipefail

PROMPT_FILE="${1:-}"
ALLOWED_TOOLS="${2:-Read,Glob,Grep}"
TIMEOUT_MINUTES="${3:-15}"

if [ -z "$PROMPT_FILE" ] || [ ! -f "$PROMPT_FILE" ]; then
  echo "Error: Prompt file required" >&2
  exit 1
fi

# Run Claude Code CLI
RESULT=$(cat "$PROMPT_FILE" | npx -y @anthropic-ai/claude-code \
  --print \
  --verbose \
  --allowedTools "$ALLOWED_TOOLS" \
  2>&1 || echo '{}')

echo "$RESULT"
