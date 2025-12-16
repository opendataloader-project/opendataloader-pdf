#!/bin/bash
# Parse Stage 3 (Fix) response from Claude Code CLI
# Usage: ./parse-stage3-response.sh [output_dir]
#
# Reads Claude Code CLI output from stdin and extracts the fix summary
# Outputs key=value pairs for GitHub Actions to stdout
# Also writes summary fields to files in output_dir (default: /tmp)
#
# Outputs to stdout:
#   has_summary=true|false
#   success=true|false
#
# Outputs to files (in output_dir):
#   fix_summary.txt       - Full summary section
#   fix_understanding.txt - UNDERSTANDING field
#   fix_test_added.txt    - TEST_ADDED field
#   fix_changes.txt       - CHANGES field
#   fix_verification.txt  - VERIFICATION field

set -euo pipefail

OUTPUT_DIR="${1:-/tmp}"

# Read from stdin
RESPONSE_TEXT=$(cat)

# Save full output for reference
echo "$RESPONSE_TEXT" > "$OUTPUT_DIR/fix_result.txt"

# Extract summary section between markers
SUMMARY_SECTION=$(echo "$RESPONSE_TEXT" | sed -n '/---FIX_SUMMARY_START---/,/---FIX_SUMMARY_END---/p' | sed '1d;$d' || echo "")

if [ -n "$SUMMARY_SECTION" ]; then
  echo "has_summary=true"

  # Save full summary
  echo "$SUMMARY_SECTION" > "$OUTPUT_DIR/fix_summary.txt"

  # Extract individual fields
  UNDERSTANDING=$(echo "$SUMMARY_SECTION" | sed -n 's/^UNDERSTANDING: //p' | head -1)
  SUCCESS=$(echo "$SUMMARY_SECTION" | sed -n 's/^SUCCESS: //p' | head -1)
  TEST_ADDED=$(echo "$SUMMARY_SECTION" | sed -n 's/^TEST_ADDED: //p' | head -1)
  CHANGES=$(echo "$SUMMARY_SECTION" | sed -n 's/^CHANGES: //p' | head -1)
  VERIFICATION=$(echo "$SUMMARY_SECTION" | sed -n 's/^VERIFICATION: //p' | head -1)

  # Output success status
  if [ "$SUCCESS" = "true" ]; then
    echo "success=true"
  else
    echo "success=false"
  fi

  # Write fields to files
  echo "${UNDERSTANDING:-}" > "$OUTPUT_DIR/fix_understanding.txt"
  echo "${TEST_ADDED:-}" > "$OUTPUT_DIR/fix_test_added.txt"
  echo "${CHANGES:-}" > "$OUTPUT_DIR/fix_changes.txt"
  echo "${VERIFICATION:-}" > "$OUTPUT_DIR/fix_verification.txt"
else
  echo "has_summary=false"
  echo "success=false"

  # Create empty files
  echo "" > "$OUTPUT_DIR/fix_summary.txt"
  echo "" > "$OUTPUT_DIR/fix_understanding.txt"
  echo "" > "$OUTPUT_DIR/fix_test_added.txt"
  echo "" > "$OUTPUT_DIR/fix_changes.txt"
  echo "" > "$OUTPUT_DIR/fix_verification.txt"
fi
