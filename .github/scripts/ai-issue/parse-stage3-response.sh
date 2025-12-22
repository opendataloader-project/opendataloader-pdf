#!/bin/bash
# Parse Stage 3 (Fix) response from Claude Code CLI
# Usage: ./parse-stage3-response.sh [output_dir]
#
# Reads Claude Code CLI output from stdin and extracts JSON fields
# Outputs key=value pairs for GitHub Actions to stdout
# Also writes summary fields to files in output_dir (default: /tmp)
#
# Outputs to stdout:
#   has_summary=true|false
#   success=true|false
#
# Outputs to files (in output_dir):
#   fix_result.txt        - Full CLI output
#   fix_understanding.txt - understanding field
#   fix_failure_reason.txt - failure_reason field
#   fix_test_added.txt    - test_added field
#   fix_changes.txt       - changes field (comma-separated)
#   fix_verification.txt  - verification field

set -euo pipefail

OUTPUT_DIR="${1:-/tmp}"

# Read from stdin
RESPONSE_TEXT=$(cat)

# Save full output for reference
echo "$RESPONSE_TEXT" > "$OUTPUT_DIR/fix_result.txt"

# Extract JSON using same strategy as Stage 2
extract_json() {
  local text="$1"
  local json_block
  local json_obj

  # First try: extract from ```json ... ``` block
  json_block=$(echo "$text" | awk '
    /```json/ { in_block=1; next }
    /```/ && in_block { in_block=0; exit }
    in_block { print }
  ')

  if [ -n "$json_block" ] && echo "$json_block" | jq -e . >/dev/null 2>&1; then
    echo "$json_block"
    return
  fi

  # Second try: find JSON object by matching braces
  json_obj=$(echo "$text" | awk '
    /^{/ {
      depth=1;
      line=$0;
      while (depth > 0 && (getline) > 0) {
        line = line "\n" $0
        gsub(/[^{}]/, "", $0)
        for (i=1; i<=length($0); i++) {
          c = substr($0, i, 1)
          if (c == "{") depth++
          else if (c == "}") depth--
        }
      }
      print line
      exit
    }
  ')

  if [ -n "$json_obj" ] && echo "$json_obj" | jq -e . >/dev/null 2>&1; then
    echo "$json_obj"
    return
  fi

  echo '{}'
}

PARSED_JSON=$(extract_json "$RESPONSE_TEXT")

# Check if we got valid JSON with expected fields
if echo "$PARSED_JSON" | jq -e 'has("success")' >/dev/null 2>&1; then
  echo "has_summary=true"

  # Extract fields
  SUCCESS=$(echo "$PARSED_JSON" | jq -r '.success // false')
  UNDERSTANDING=$(echo "$PARSED_JSON" | jq -r '.understanding // ""')
  FAILURE_REASON=$(echo "$PARSED_JSON" | jq -r '.failure_reason // ""')
  TEST_ADDED=$(echo "$PARSED_JSON" | jq -r '.test_added // ""')
  CHANGES=$(echo "$PARSED_JSON" | jq -r '.changes // [] | join(", ")')
  VERIFICATION=$(echo "$PARSED_JSON" | jq -r '.verification // ""')

  # Output success status
  if [ "$SUCCESS" = "true" ]; then
    echo "success=true"
  else
    echo "success=false"
  fi

  # Write fields to files
  echo "$UNDERSTANDING" > "$OUTPUT_DIR/fix_understanding.txt"
  echo "$FAILURE_REASON" > "$OUTPUT_DIR/fix_failure_reason.txt"
  echo "$TEST_ADDED" > "$OUTPUT_DIR/fix_test_added.txt"
  echo "$CHANGES" > "$OUTPUT_DIR/fix_changes.txt"
  echo "$VERIFICATION" > "$OUTPUT_DIR/fix_verification.txt"
else
  echo "has_summary=false"
  echo "success=false"

  # Create empty files
  echo "" > "$OUTPUT_DIR/fix_understanding.txt"
  echo "" > "$OUTPUT_DIR/fix_failure_reason.txt"
  echo "" > "$OUTPUT_DIR/fix_test_added.txt"
  echo "" > "$OUTPUT_DIR/fix_changes.txt"
  echo "" > "$OUTPUT_DIR/fix_verification.txt"
fi
