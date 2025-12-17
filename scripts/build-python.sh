#!/bin/bash

# CI/CD build script for Python package
# Uses a temporary virtual environment to avoid affecting local installation
# For local development, use test-python.sh instead

set -e

# Python executable (can be overridden: PYTHON=python3.11 ./scripts/build-python.sh)
PYTHON="${PYTHON:-python3}"
command -v "$PYTHON" >/dev/null || PYTHON="python"
command -v "$PYTHON" >/dev/null || { echo "Error: python not found"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/python/opendataloader-pdf"
VENV_DIR="$PACKAGE_DIR/.venv-build"
cd "$PACKAGE_DIR"

# Create temporary virtual environment
echo "Creating virtual environment..."
rm -rf "$VENV_DIR"
$PYTHON -m venv "$VENV_DIR"
source "$VENV_DIR/bin/activate"

# Install build dependencies
pip install -r ../requirements.txt

# Copy README
cp "$ROOT_DIR/README.md" .

# Build wheel package
python -m build --wheel

# Install the built package
pip install ./dist/opendataloader_pdf-*.whl

# Run tests
python -m pytest tests -v -s

# Cleanup
deactivate
rm -rf "$VENV_DIR"

echo "Build completed successfully."
