#!/bin/bash

# Local development test script for Java package
# For CI/CD builds, use build-java.sh instead

set -e

# Prerequisites
command -v java >/dev/null || { echo "Error: java not found"; exit 1; }
command -v mvn >/dev/null || { echo "Error: mvn not found"; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
PACKAGE_DIR="$ROOT_DIR/java"
cd "$PACKAGE_DIR"

# Run tests
mvn test "$@"
