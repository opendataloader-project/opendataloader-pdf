#!/bin/bash

# Local development test script for Python package
# For CI/CD builds, use build-python.sh instead

set -e

# Python executable (can be overridden: PYTHON=python3.11 ./scripts/test-python.sh)
PYTHON="${PYTHON:-python3}"
command -v "$PYTHON" >/dev/null || PYTHON="python"
command -v "$PYTHON" >/dev/null || { echo "Error: python not found"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/python/opendataloader-pdf"
cd "$PACKAGE_DIR"

# Install in editable mode (if not already)
$PYTHON -m pip install -e . --quiet

# Run tests
$PYTHON -m pytest tests -v -s "$@"
