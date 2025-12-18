#!/bin/bash

# Run the Java CLI directly
# Usage: ./scripts/run-cli.sh [options] [input...]
#
# If no arguments: uses DEFAULT_ARGS
# If arguments given: uses only the provided arguments
#
# Examples:
#   ./scripts/run-cli.sh                              # Use defaults
#   ./scripts/run-cli.sh -f markdown my.pdf           # Custom args only

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
JAR_DIR="$ROOT_DIR/java/opendataloader-pdf-cli/target"

# Defaults (used only when no arguments provided)
DEFAULT_ARGS=("-f" "json,markdown,html,pdf" "-o" "$ROOT_DIR/samples/temp" "$ROOT_DIR/samples/pdf")

# Check if Java is installed
command -v java >/dev/null || { echo "Error: java not found"; exit 1; }

# Find the shaded JAR (excludes original-* and *-sources.jar)
find_jar() {
    find "$JAR_DIR" -maxdepth 1 -name "opendataloader-pdf-cli-*.jar" \
        ! -name "original-*" \
        ! -name "*-sources.jar" \
        ! -name "*-javadoc.jar" \
        2>/dev/null | head -1
}

JAR_PATH=$(find_jar)

# Build JAR if it doesn't exist
if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    echo "JAR not found. Building..."
    cd "$ROOT_DIR/java"
    mvn -B package -DskipTests -q
    cd "$ROOT_DIR"
    JAR_PATH=$(find_jar)
fi

if [ -z "$JAR_PATH" ]; then
    echo "Error: Could not find CLI JAR file"
    exit 1
fi

# Use defaults if no arguments, otherwise use provided arguments only
if [ $# -eq 0 ]; then
    ARGS=("${DEFAULT_ARGS[@]}")
else
    ARGS=("$@")
fi

# Run the CLI
java -jar "$JAR_PATH" "${ARGS[@]}"
