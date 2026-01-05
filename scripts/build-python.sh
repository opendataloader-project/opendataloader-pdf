#!/bin/bash

# CI/CD build script for Python package using uv
# For local development, use test-python.sh instead

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/python/opendataloader-pdf"
cd "$PACKAGE_DIR"

# Check uv is available
command -v uv >/dev/null || { echo "Error: uv not found. Install with: curl -LsSf https://astral.sh/uv/install.sh | sh"; exit 1; }

# Clean previous build
rm -rf dist/

# Copy README.md from root (gitignored in package dir)
cp "$ROOT_DIR/README.md" "$PACKAGE_DIR/README.md"

# Build wheel package
uv build --wheel

# Install and run tests
uv sync
uv run pytest tests -v -s

echo "Build completed successfully."
