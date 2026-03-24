#!/usr/bin/env bash
# Benchmark script for opendataloader-pdf
#
# Clones opendataloader-bench and runs benchmark against locally built JAR.
#
# Usage:
#   ./scripts/bench.sh                       # Run full benchmark
#   ./scripts/bench.sh --doc-id 01030...     # Run for specific document
#   ./scripts/bench.sh --check-regression    # Run with regression check (CI)
#   ./scripts/bench.sh --skip-build          # Skip Java build step
#
# Environment:
#   BENCH_DIR   Override bench repo clone location (default: /tmp/opendataloader-bench)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BENCH_REPO="https://github.com/opendataloader-project/opendataloader-bench.git"
BENCH_DIR="${BENCH_DIR:-/tmp/opendataloader-bench}"

# Parse --skip-build flag (pass everything else through)
SKIP_BUILD=false
ARGS=()
for arg in "$@"; do
    if [[ "$arg" == "--skip-build" ]]; then
        SKIP_BUILD=true
    else
        ARGS+=("$arg")
    fi
done

# Find CLI JAR (shaded first, then regular, excluding sources/javadoc/original)
find_jar() {
    local target_dir="$PROJECT_ROOT/java/opendataloader-pdf-cli/target"
    local jar
    jar=$(find "$target_dir" -name "opendataloader-pdf-cli-*-shaded.jar" 2>/dev/null | head -1)
    if [[ -z "$jar" ]]; then
        jar=$(find "$target_dir" -name "opendataloader-pdf-cli-*.jar" \
            ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "original-*" \
            2>/dev/null | head -1)
    fi
    echo "$jar"
}

# Step 1: Build Java if needed
if [[ "$SKIP_BUILD" == "false" ]]; then
    JAR_PATH=$(find_jar)
    if [[ -z "$JAR_PATH" ]]; then
        echo "Building Java..."
        "$SCRIPT_DIR/build-java.sh"
    else
        echo "Using existing JAR: $JAR_PATH"
    fi
fi

# Step 2: Clone or update bench repo
if [[ -d "$BENCH_DIR/.git" ]]; then
    echo "Updating bench repo..."
    git -C "$BENCH_DIR" pull --ff-only --quiet 2>/dev/null || true
else
    echo "Cloning bench repo..."
    git clone --depth 1 "$BENCH_REPO" "$BENCH_DIR"
fi

# Step 3: Find JAR path
JAR_PATH=$(find_jar)
if [[ -z "$JAR_PATH" ]]; then
    echo "Error: No JAR found. Run ./scripts/build-java.sh first."
    exit 1
fi

# Step 4: Run benchmark with JAR
echo "Running benchmark with JAR: $JAR_PATH"
cd "$BENCH_DIR"

if ! command -v uv &> /dev/null; then
    echo "Error: uv is not installed."
    exit 1
fi

uv sync --quiet
OPENDATALOADER_JAR="$JAR_PATH" uv run python src/run.py \
    --engine opendataloader \
    "${ARGS[@]}"
