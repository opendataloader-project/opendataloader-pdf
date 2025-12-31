#!/bin/bash

# Local development test script for Python package using uv
# For CI/CD builds, use build-python.sh instead

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/python/opendataloader-pdf"
cd "$PACKAGE_DIR"

# Check uv is available
command -v uv >/dev/null || { echo "Error: uv not found. Install with: curl -LsSf https://astral.sh/uv/install.sh | sh"; exit 1; }

# Sync dependencies and run tests
uv sync
uv run pytest tests -v -s "$@"
