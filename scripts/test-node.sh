#!/bin/bash

# Local development test script for Node.js package
# For CI/CD builds, use build-node.sh instead

set -e

# Prerequisites
command -v node >/dev/null || { echo "Error: node not found"; exit 1; }
command -v pnpm >/dev/null || { echo "Error: pnpm not found"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/node/opendataloader-pdf"
cd "$PACKAGE_DIR"

# Install dependencies (if needed)
pnpm install

# Run tests
pnpm test "$@"
