#!/bin/bash
# AI Issue Test Runner
# Usage: ./run-tests.sh [--stage=1|2|all] [--case=<name>] [--dry-run] [--verbose]
#
# This script can be used both locally and in GitHub Actions
# It uses the shared prompt builder scripts to ensure consistency

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AI_ISSUE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FIXTURES_DIR="$SCRIPT_DIR/fixtures"
CASES_DIR="$SCRIPT_DIR/cases"

# Parse arguments
STAGE_FILTER="all"
CASE_FILTER=""
DRY_RUN=false
VERBOSE=false

for arg in "$@"; do
  case $arg in
    --stage=*)
      STAGE_FILTER="${arg#*=}"
      ;;
    --case=*)
      CASE_FILTER="${arg#*=}"
      ;;
    --dry-run)
      DRY_RUN=true
      ;;
    --verbose)
      VERBOSE=true
      ;;
  esac
done

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
TOTAL=0
PASSED=0
FAILED=0

# Log functions
log_info() {
  echo -e "${NC}$1${NC}"
}

log_pass() {
  echo -e "${GREEN}✓ $1${NC}"
}

log_fail() {
  echo -e "${RED}✗ $1${NC}"
}

log_warn() {
  echo -e "${YELLOW}⚠ $1${NC}"
}

# Test a Stage 1 case
run_stage1_test() {
  local case_file="$1"
  local case_name=$(jq -r '.name' "$case_file")
  local expected_decision=$(jq -r '.expected.decision' "$case_file")
  local expected_duplicate=$(jq -r '.expected.duplicate_of // "null"' "$case_file")

  local title=$(jq -r '.input.title' "$case_file")
  local body=$(jq -r '.input.body' "$case_file")

  TOTAL=$((TOTAL + 1))

  if $VERBOSE; then
    log_info "  Input: $title"
  fi

  if $DRY_RUN; then
    # Just show the prompt
    log_info "  [DRY-RUN] Would test: $case_name"
    "$AI_ISSUE_DIR/build-stage1-prompt.sh" "999" "$title" "$body" \
      "$FIXTURES_DIR/readme-excerpt.txt" "$FIXTURES_DIR/existing-issues.txt" | head -20
    echo "..."
    return 0
  fi

  # Start timer
  local start_time=$(date +%s)

  # Build and run
  local prompt_file=$(mktemp)
  "$AI_ISSUE_DIR/build-stage1-prompt.sh" "999" "$title" "$body" \
    "$FIXTURES_DIR/readme-excerpt.txt" "$FIXTURES_DIR/existing-issues.txt" > "$prompt_file"

  local response=$("$AI_ISSUE_DIR/call-claude-api.sh" "$prompt_file")
  rm -f "$prompt_file"

  # Calculate elapsed time
  local end_time=$(date +%s)
  local elapsed=$((end_time - start_time))

  if $VERBOSE; then
    log_info "  Response: $response"
  fi

  # Parse response
  local parsed=$(echo "$response" | "$AI_ISSUE_DIR/parse-stage1-response.sh")
  local actual_decision=$(echo "$parsed" | grep "^decision=" | cut -d= -f2)
  local actual_duplicate=$(echo "$parsed" | grep "^duplicate_of=" | cut -d= -f2)

  # Check result
  local test_passed=true
  local errors=""

  if [ "$actual_decision" != "$expected_decision" ]; then
    test_passed=false
    errors="Expected decision '$expected_decision', got '$actual_decision'"
  fi

  if [ "$expected_duplicate" != "null" ] && [ "$actual_duplicate" != "$expected_duplicate" ]; then
    test_passed=false
    errors="${errors:+$errors; }Expected duplicate_of '$expected_duplicate', got '$actual_duplicate'"
  fi

  if $test_passed; then
    log_pass "$case_name (decision: $actual_decision) [${elapsed}s]"
    PASSED=$((PASSED + 1))
  else
    log_fail "$case_name [${elapsed}s]"
    log_info "    → $errors"
    FAILED=$((FAILED + 1))
  fi
}

# Test a Stage 2 case
run_stage2_test() {
  local case_file="$1"
  local case_name=$(jq -r '.name' "$case_file")
  local expected_action=$(jq -r '.expected.action' "$case_file")

  # Use mock codebase for testing (prevents dependency on real codebase)
  local mock_codebase="$FIXTURES_DIR/mock-codebase"

  TOTAL=$((TOTAL + 1))

  if $DRY_RUN; then
    log_info "  [DRY-RUN] Would test: $case_name"
    log_info "  [DRY-RUN] Using mock codebase: $mock_codebase"
    # Show prompt preview
    local issue_json_file=$(mktemp)
    jq '{
      title: .input.title,
      body: .input.body,
      labels: [.input.labels[] | {name: .}],
      comments: [.input.comments[] | {author: {login: .author}, body: .body}]
    }' "$case_file" > "$issue_json_file"
    "$AI_ISSUE_DIR/build-stage2-prompt.sh" "999" "$issue_json_file" "$mock_codebase" | head -30
    echo "..."
    rm -f "$issue_json_file"
    return 0
  fi

  # Start timer
  local start_time=$(date +%s)

  # Create issue JSON file
  local issue_json_file=$(mktemp)
  jq '{
    title: .input.title,
    body: .input.body,
    labels: [.input.labels[] | {name: .}],
    comments: [.input.comments[] | {author: {login: .author}, body: .body}]
  }' "$case_file" > "$issue_json_file"

  # Build and run using Claude Code CLI (same as workflow)
  local prompt_file=$(mktemp)
  "$AI_ISSUE_DIR/build-stage2-prompt.sh" "999" "$issue_json_file" "$mock_codebase" > "$prompt_file"

  local response=$("$AI_ISSUE_DIR/call-claude-code.sh" "$prompt_file" "Read,Glob,Grep")
  rm -f "$prompt_file" "$issue_json_file"

  # Calculate elapsed time
  local end_time=$(date +%s)
  local elapsed=$((end_time - start_time))

  if $VERBOSE; then
    log_info "  Response: $response"
  fi

  # Parse response using shared script
  local output_dir=$(mktemp -d)
  local parsed=$(echo "$response" | "$AI_ISSUE_DIR/parse-stage2-response.sh" "$output_dir")
  local actual_action=$(echo "$parsed" | grep "^action=" | cut -d= -f2)
  rm -rf "$output_dir"

  # Check result
  if [ "$actual_action" = "$expected_action" ]; then
    log_pass "$case_name (action: $actual_action) [${elapsed}s]"
    PASSED=$((PASSED + 1))
  else
    log_fail "$case_name [${elapsed}s]"
    log_info "    → Expected action '$expected_action', got '$actual_action'"
    FAILED=$((FAILED + 1))
  fi
}

# Main
echo "========================================"
echo "AI Issue Test Runner"
echo "========================================"
echo ""

# Run Stage 1 tests
if [ "$STAGE_FILTER" = "all" ] || [ "$STAGE_FILTER" = "1" ]; then
  echo "Stage 1: Triage"
  echo "----------------------------------------"

  for case_file in "$CASES_DIR/stage1"/*.json; do
    if [ -n "$CASE_FILTER" ] && [[ ! "$(basename "$case_file")" =~ $CASE_FILTER ]]; then
      continue
    fi
    run_stage1_test "$case_file"
  done
  echo ""
fi

# Run Stage 2 tests
if [ "$STAGE_FILTER" = "all" ] || [ "$STAGE_FILTER" = "2" ]; then
  echo "Stage 2: Analyze"
  echo "----------------------------------------"

  for case_file in "$CASES_DIR/stage2"/*.json; do
    if [ -n "$CASE_FILTER" ] && [[ ! "$(basename "$case_file")" =~ $CASE_FILTER ]]; then
      continue
    fi
    run_stage2_test "$case_file"
  done
  echo ""
fi

# Summary
echo "========================================"
if [ $FAILED -eq 0 ]; then
  log_pass "All tests passed: $PASSED/$TOTAL"
else
  log_fail "Tests failed: $FAILED/$TOTAL"
fi
echo "========================================"

exit $FAILED
