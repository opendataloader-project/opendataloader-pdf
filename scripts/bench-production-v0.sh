#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOC_IDS_FILE="${SCRIPT_DIR}/../benchmarks/suites/production-v0-doc-ids.txt"

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Run the production-v0 benchmark suite against a defined list of document IDs.

Options:
  --dry-run           Print the documents that would be run, then exit 0
  --check-regression  Exit non-zero if any document benchmark failed
  --help              Print this help message and exit 0
EOF
}

dry_run=false
check_regression=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            dry_run=true
            shift
            ;;
        --check-regression)
            check_regression=true
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

mapfile -t doc_ids < <(grep -v '^\s*$' "$DOC_IDS_FILE")
count="${#doc_ids[@]}"

if $dry_run; then
    echo "production-v0: would run ${count} documents: ${doc_ids[*]}"
    exit 0
fi

passed=0
failed=0

for id in "${doc_ids[@]}"; do
    echo "production-v0: running doc-id=${id}"
    if "${SCRIPT_DIR}/bench.sh" --doc-id "$id"; then
        ((passed++)) || true
    else
        echo "production-v0: FAILED doc-id=${id}" >&2
        ((failed++)) || true
    fi
done

total=$((passed + failed))
echo "production-v0 summary: ${passed}/${total} passed"

if $check_regression && [[ $failed -gt 0 ]]; then
    exit 1
fi
