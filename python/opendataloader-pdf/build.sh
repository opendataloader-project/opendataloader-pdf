#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# Change to the directory where this script is located.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Install dependencies
pip install -r ../requirements.txt

# Copy README
cp ../../README.md .

# Build wheel package
python -m build --wheel

# Force install the built package
pip install ./dist/opendataloader_pdf-*.whl --force-reinstall

# Run tests
python -m unittest discover -s tests
