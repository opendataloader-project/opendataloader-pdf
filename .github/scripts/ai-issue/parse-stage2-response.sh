#!/bin/bash
# Parse Stage 2 (Deep Triage) response from Claude Code CLI
# Usage: ./parse-stage2-response.sh [output_dir]
#
# Reads Claude Code CLI output from stdin and extracts JSON fields
# Outputs key=value pairs for GitHub Actions to stdout
# Also writes analysis fields to files in output_dir (default: /tmp)
#
# Outputs to stdout:
#   action=fix/auto-eligible|fix/manual-required|respond/comment-only
#   labels=["label1", "label2"]
#   priority=P0|P1|P2
#   estimated=1|2|3|5|8
#   assignee=github_id
#   score_total=<number>
#   score_threshold=<number>
#
# Outputs to files (in output_dir):
#   analysis_summary.txt
#   expected_behavior.txt
#   current_behavior.txt
#   affected_files.txt
#   root_cause.txt
#   suggested_approach.txt
#   score_breakdown.txt

set -euo pipefail

OUTPUT_DIR="${1:-/tmp}"

# Read from stdin
RESPONSE_TEXT=$(cat)

# Extract JSON from result
# Strategy: find the outermost JSON object that starts with { and ends with }
# Handle nested code blocks by finding balanced braces

extract_json() {
  local text="$1"
  local json_block
  local json_obj

  # First try: extract from ```json ... ``` block (first occurrence only)
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

# Parse JSON fields
ACTION=$(echo "$PARSED_JSON" | jq -r '.action // "fix/manual-required"')
LABELS=$(echo "$PARSED_JSON" | jq -c '.labels // []')
PRIORITY=$(echo "$PARSED_JSON" | jq -r '.priority // "P2"')
ESTIMATED=$(echo "$PARSED_JSON" | jq -r '.estimated // 3')
ASSIGNEE=$(echo "$PARSED_JSON" | jq -r '.assignee // ""' | tr -d '@')

# Parse score fields
SCORE_TOTAL=$(echo "$PARSED_JSON" | jq -r '.score.total // 0')
SCORE_THRESHOLD=$(echo "$PARSED_JSON" | jq -r '.score.threshold // 70')

# Output for GitHub Actions (single-line values)
echo "action=$ACTION"
echo "labels=$LABELS"
echo "priority=$PRIORITY"
echo "estimated=$ESTIMATED"
echo "assignee=$ASSIGNEE"
echo "score_total=$SCORE_TOTAL"
echo "score_threshold=$SCORE_THRESHOLD"

# Write analysis fields to files (for multi-line content)
echo "$PARSED_JSON" | jq -r '.analysis.summary // "Unable to analyze"' > "$OUTPUT_DIR/analysis_summary.txt"
echo "$PARSED_JSON" | jq -r '.analysis.expected_behavior // ""' > "$OUTPUT_DIR/expected_behavior.txt"
echo "$PARSED_JSON" | jq -r '.analysis.current_behavior // ""' > "$OUTPUT_DIR/current_behavior.txt"
echo "$PARSED_JSON" | jq -r '.analysis.affected_files // [] | join(", ")' > "$OUTPUT_DIR/affected_files.txt"
echo "$PARSED_JSON" | jq -r '.analysis.root_cause // ""' > "$OUTPUT_DIR/root_cause.txt"
echo "$PARSED_JSON" | jq -r '.analysis.suggested_approach // ""' > "$OUTPUT_DIR/suggested_approach.txt"

# Write score breakdown to file (markdown table format)
{
  echo "| Criteria | Max | Score | Reason |"
  echo "|----------|-----|-------|--------|"

  SCOPE_SCORE=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.scope.score // 0')
  SCOPE_REASON=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.scope.reason // ""')
  echo "| Scope | 30 | $SCOPE_SCORE | $SCOPE_REASON |"

  RISK_SCORE=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.risk.score // 0')
  RISK_REASON=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.risk.reason // ""')
  echo "| Risk | 30 | $RISK_SCORE | $RISK_REASON |"

  VERIFY_SCORE=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.verifiability.score // 0')
  VERIFY_REASON=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.verifiability.reason // ""')
  echo "| Verifiability | 25 | $VERIFY_SCORE | $VERIFY_REASON |"

  CLARITY_SCORE=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.clarity.score // 0')
  CLARITY_REASON=$(echo "$PARSED_JSON" | jq -r '.score.breakdown.clarity.reason // ""')
  echo "| Clarity | 15 | $CLARITY_SCORE | $CLARITY_REASON |"
} > "$OUTPUT_DIR/score_breakdown.txt"
