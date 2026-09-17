#!/bin/bash

# Build and test all packages: Java, Python, Node.js
# Usage: ./scripts/build-all.sh [VERSION]
#
# Without a VERSION each package keeps the version its own manifest declares.

set -e

# =================================================================
# Configuration
# =================================================================
VERSION="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Only -SNAPSHOT is translated. A prerelease version passes through, and PyPI
# then normalizes e.g. 2.5.10-rc1 to 2.5.10rc1 — a coordinate that differs
# textually from the Java and npm ones.
to_pep440() { echo "${1/-SNAPSHOT/.dev0}"; }
to_semver() { echo "${1/-SNAPSHOT/-dev.0}"; }

# =================================================================
# Prerequisites Check
# =================================================================
echo "Checking prerequisites..."

command -v java >/dev/null || { echo "Error: java not found"; exit 1; }
command -v mvn >/dev/null || { echo "Error: mvn not found"; exit 1; }
command -v uv >/dev/null || { echo "Error: uv not found. Install with: curl -LsSf https://astral.sh/uv/install.sh | sh"; exit 1; }
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
if [ -n "$VERSION" ]; then
  mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
fi
"$SCRIPT_DIR/build-java.sh"

echo "[1/3] Java: Done"

# =================================================================
# Python Build & Test
# =================================================================
echo ""
echo "[2/3] Python: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/python/opendataloader-pdf"
if [ -n "$VERSION" ]; then
  py_version="$(to_pep440 "$VERSION")"
  sed -i.bak "s/^version = \"[^\"]*\"/version = \"$py_version\"/" pyproject.toml && rm -f pyproject.toml.bak
fi
"$SCRIPT_DIR/build-python.sh"

echo "[2/3] Python: Done"

# =================================================================
# Node.js Build & Test
# =================================================================
echo ""
echo "[3/3] Node.js: Building and testing..."
echo "----------------------------------------"

cd "$ROOT_DIR/node/opendataloader-pdf"
if [ -n "$VERSION" ]; then
  pnpm version "$(to_semver "$VERSION")" --no-git-tag-version --allow-same-version --no-git-checks
fi
"$SCRIPT_DIR/build-node.sh"

echo "[3/3] Node.js: Done"

# =================================================================
# Summary
# =================================================================
echo ""
echo "========================================"
echo "All builds completed successfully!"
if [ -n "$VERSION" ]; then
  echo "Version: $VERSION"
fi
echo "========================================"
