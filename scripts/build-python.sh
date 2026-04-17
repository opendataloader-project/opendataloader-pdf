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

# Copy README.md from repo root *before* build. hatchling validates [project.readme]
# during metadata parsing, which runs BEFORE hatch_build.py's build hook — so we
# cannot rely on the hook to provide it. Clean up on exit so branch switches
# don't leave a stale copy in the package dir.
cp "$ROOT_DIR/README.md" "$PACKAGE_DIR/README.md"
trap 'rm -f "$PACKAGE_DIR/README.md"' EXIT

# Build sdist and wheel packages (hatch_build.py copies JAR/LICENSE/NOTICE/THIRD_PARTY)
uv build

# Verify sdist contains required artifacts (JAR, LICENSE, NOTICE, THIRD_PARTY)
"$SCRIPT_DIR/verify-python-sdist.sh"

# Install and run tests (include hybrid extras for full test coverage)
uv sync --extra hybrid
uv run pytest tests -v -s

echo "Build completed successfully."
