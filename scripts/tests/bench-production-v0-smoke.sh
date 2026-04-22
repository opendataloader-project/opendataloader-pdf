#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

output="$("${REPO_ROOT}/scripts/bench-production-v0.sh" --dry-run)"

if echo "$output" | grep -q "production-v0"; then
    echo "PASS: dry-run output contains 'production-v0'"
    exit 0
else
    echo "FAIL: dry-run output missing 'production-v0'"
    echo "Got: $output"
    exit 1
fi
