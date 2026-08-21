#!/usr/bin/env bash
# Scan a corpus of PDFs with Hancom AI DLA and record which visual/formula
# labels each document contains.
#
# Why this exists: the hybrid pipeline's label handling (Figure 10 / Chart 18 /
# Image 19, Equation 12) is driven by what the DLA model actually emits, not by
# the published spec. Label 18/19 were absent from our transformer until we
# measured a real corpus. Re-run this after a DLA model update to see whether
# the label distribution — and therefore our captioning coverage — moved.
#
# The output feeds two consumers:
#   1. label-report.md      — human-readable distribution + overlap findings
#   2. raw/<doc>.json       — candidates for promotion to unit-test fixtures
#
# Requests are sequential on purpose: concurrent uploads made the staging
# server return 502/503 and restart (observed repeatedly, ~45-60s outages).
#
# Usage:
#   DLA_BASE_URL=http://<host>:18008/api/v1 ./scan.sh <pdf-dir> [out-dir]
set -uo pipefail

# The DLA server is internal-only, so there is no default worth shipping in a
# public repository — name it explicitly, including the /api/v1 suffix.
BASE_URL="${DLA_BASE_URL:?set DLA_BASE_URL to the DLA server, e.g. http://host:18008/api/v1}"
PDF_DIR="${1:?usage: scan.sh <pdf-dir> [out-dir]}"
OUT_DIR="${2:-$(dirname "$0")/out}"
RAW_DIR="$OUT_DIR/raw"
# DLA on a single page took 3-7s; allow generous headroom for slow pages.
TIMEOUT="${DLA_TIMEOUT:-300}"
MAX_RETRY="${DLA_MAX_RETRY:-3}"
# Staging restarts take 45-60s to come back, so wait longer than that.
RETRY_WAIT="${DLA_RETRY_WAIT:-45}"
SLEEP_BETWEEN="${DLA_SLEEP:-1}"

mkdir -p "$RAW_DIR"
LOG="$OUT_DIR/scan.log"
: > "$LOG"

log() { printf '%s\n' "$*" | tee -a "$LOG"; }

# A response counts as usable only when the envelope succeeded and every page
# in it reports success — see response_complete.py.
CHECKER="$(dirname "$0")/response_complete.py"
response_complete() { python3 "$CHECKER" "$1"; }

wait_for_server() {
    for _ in $(seq 1 20); do
        if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/ping" --max-time 30)" = "200" ]; then
            return 0
        fi
        sleep 15
    done
    return 1
}

total=0 ok=0 fail=0
for pdf in "$PDF_DIR"/*.pdf; do
    [ -e "$pdf" ] || continue
    total=$((total + 1))
    name=$(basename "$pdf" .pdf)
    out="$RAW_DIR/$name.json"

    # Resume support: a previous run's successful results are kept as-is so an
    # interrupted scan can continue without re-billing the GPU for every doc.
    if [ -s "$out" ] && response_complete "$out"; then
        log "$name: cached"
        ok=$((ok + 1))
        continue
    fi

    got=""
    # Download to a temp file and promote on success: curl truncates its -o
    # target before the response arrives, so writing straight to $out would let
    # a failed retry destroy a previously cached good result — defeating the
    # resume check above.
    tmp="$out.part"
    for attempt in $(seq 1 "$MAX_RETRY"); do
        code=$(curl -s -X POST "$BASE_URL/hocr/sdk" \
            -F "REQUEST_ID=scan-$name" \
            -F "OPEN_API_NAME=DOCUMENT_LAYOUT_ANALYSIS" \
            -F "DATA_FORMAT=pdf" \
            -F "FILE=@$pdf;type=application/pdf" \
            -o "$tmp" -w "%{http_code}" --max-time "$TIMEOUT")
        if [ "$code" = "200" ] && response_complete "$tmp"; then
            mv -f "$tmp" "$out"
            got="yes"
            break
        fi
        if [ "$code" = "200" ]; then
            log "$name: http=200 but a page did not report success (attempt $attempt/$MAX_RETRY)"
        else
            log "$name: http=$code (attempt $attempt/$MAX_RETRY)"
        fi
        # Nothing to wait for after the final attempt.
        if [ "$attempt" -eq "$MAX_RETRY" ]; then
            break
        fi
        # 502/503 mean the server is restarting rather than rejecting the file.
        if [ "$code" = "502" ] || [ "$code" = "503" ]; then
            wait_for_server || log "$name: server still down"
        else
            sleep "$RETRY_WAIT"
        fi
    done
    rm -f "$tmp"

    if [ -n "$got" ]; then
        ok=$((ok + 1))
        log "$name: ok"
    else
        fail=$((fail + 1))
        log "$name: FAILED"
    fi
    sleep "$SLEEP_BETWEEN"
done

log "---"
log "total=$total ok=$ok failed=$fail"
log "raw responses: $RAW_DIR"
