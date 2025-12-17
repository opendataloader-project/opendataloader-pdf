#!/bin/bash

# Build and test all packages: Java, Python, Node.js
# Usage: ./scripts/build-all.sh [VERSION]
# Example: ./scripts/build-all.sh 1.0.0
# If VERSION is not provided, defaults to "0.0.0"

set -e

# =================================================================
# Configuration
# =================================================================
VERSION="${1:-0.0.0}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# =================================================================
# Prerequisites Check
# =================================================================
echo "Checking prerequisites..."

command -v java >/dev/null || { echo "Error: java not found"; exit 1; }
command -v mvn >/dev/null || { echo "Error: mvn not found"; exit 1; }
command -v python3 >/dev/null || { echo "Error: python3 not found"; exit 1; }
command -v pip >/dev/null || { echo "Error: pip not found"; exit 1; }
command -v node >/dev/null || { echo "Error: node not found"; exit 1; }
command -v pnpm >/dev/null || { echo "Error: pnpm not found"; exit 1; }

echo "All prerequisites found."

echo ""
echo "========================================"
echo "Building all packages (version: $VERSION)"
echo "========================================"

# =================================================================
# Java Build & Test
# =================================================================
echo ""
echo "[1/3] Java: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/java"
mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
"$SCRIPT_DIR/build-java.sh"

echo "[1/3] Java: Done"

# =================================================================
# Python Build & Test
# =================================================================
echo ""
echo "[2/3] Python: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/python/opendataloader-pdf"
sed -i.bak "s/version=\"[^\"]*\"/version=\"$VERSION\"/" setup.py && rm -f setup.py.bak
"$SCRIPT_DIR/build-python.sh"

echo "[2/3] Python: Done"

# =================================================================
# Node.js Build & Test
# =================================================================
echo ""
echo "[3/3] Node.js: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/node/opendataloader-pdf"
pnpm version "$VERSION" --no-git-tag-version --allow-same-version
"$SCRIPT_DIR/build-node.sh"

echo "[3/3] Node.js: Done"

# =================================================================
# Summary
# =================================================================
echo ""
echo "========================================"
echo "All builds completed successfully!"
echo "Version: $VERSION"
echo "========================================"
