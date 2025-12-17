#!/bin/bash

# CI/CD build script for Node.js package
# For local development, use test-node.sh instead

set -e

# Prerequisites
command -v node >/dev/null || { echo "Error: node not found"; exit 1; }
command -v pnpm >/dev/null || { echo "Error: pnpm not found"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/node/opendataloader-pdf"
cd "$PACKAGE_DIR"

# Install dependencies
pnpm install --frozen-lockfile

# Build
pnpm run build

# Run tests
pnpm test
