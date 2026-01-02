#!/bin/bash
# Benchmark script for opendataloader-pdf
#
# Usage:
#   ./scripts/bench.sh                      # Run full benchmark (Java only)
#   ./scripts/bench.sh --doc-id 01030...    # Run for specific document
#   ./scripts/bench.sh --check-regression   # Run with regression check (CI)
#   ./scripts/bench.sh --hybrid docling     # Run with hybrid mode (requires docling-serve)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BENCHMARK_DIR="$PROJECT_ROOT/tests/benchmark"

# Build Java if needed
echo "Building Java..."
"$SCRIPT_DIR/build-java.sh"

# Install Python dependencies and run benchmark
echo "Running benchmark..."
cd "$BENCHMARK_DIR"

# Check if uv is available
if ! command -v uv &> /dev/null; then
    echo "Error: uv is not installed. Please install it first."
    exit 1
fi

# Sync dependencies
uv sync --quiet

# Run benchmark with all passed arguments
uv run python run.py "$@"
