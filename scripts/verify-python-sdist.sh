#!/bin/bash

# Verify the Python sdist contains all required files (JAR, LICENSE, etc).
# The files listed below are gitignored in the package dir and only exist in
# the dist because [tool.hatch.build] artifacts force-includes them. This
# script guards against silent regressions if that config ever drifts.
#
# Usage: ./scripts/verify-python-sdist.sh
# Prerequisite: run 'uv build' (or scripts/build-python.sh) first.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
DIST_DIR="$ROOT_DIR/python/opendataloader-pdf/dist"

shopt -s nullglob
SDIST_CANDIDATES=("$DIST_DIR"/*.tar.gz)
shopt -u nullglob

if [ ${#SDIST_CANDIDATES[@]} -eq 0 ]; then
  echo "Error: no sdist found in $DIST_DIR. Run 'uv build' first." >&2
  exit 1
fi
if [ ${#SDIST_CANDIDATES[@]} -gt 1 ]; then
  echo "Error: multiple sdists found in $DIST_DIR. Remove stale ones first:" >&2
  printf '  - %s\n' "${SDIST_CANDIDATES[@]}" >&2
  exit 1
fi
SDIST="${SDIST_CANDIDATES[0]}"

echo "Verifying sdist: $(basename "$SDIST")"

REQUIRED=(
  "jar/opendataloader-pdf-cli.jar"
  "LICENSE"
  "NOTICE"
  "THIRD_PARTY/"
)

CONTENTS=$(tar -tzf "$SDIST")
MISSING=()
for path in "${REQUIRED[@]}"; do
  # Directory prefixes (trailing '/') match any entry under that prefix.
  # File paths are anchored to end-of-line so "LICENSE" does not match "LICENSE.bak".
  # (^|/) prefix keeps the check layout-tolerant if hatchling ever emits
  # a differently-rooted sdist (e.g., without the top-level pkgname-version/ dir).
  if [[ "$path" == */ ]]; then
    pattern="(^|/)src/opendataloader_pdf/${path}"
  else
    pattern="(^|/)src/opendataloader_pdf/${path}\$"
  fi
  if ! echo "$CONTENTS" | grep -qE "$pattern"; then
    MISSING+=("$path")
  fi
done

if [ ${#MISSING[@]} -gt 0 ]; then
  echo "Error: sdist is missing required files:" >&2
  printf '  - src/opendataloader_pdf/%s\n' "${MISSING[@]}" >&2
  echo "" >&2
  echo "Fix: ensure [tool.hatch.build] in pyproject.toml lists these under 'artifacts'." >&2
  echo "(They are gitignored, so hatch drops them unless force-included.)" >&2
  exit 1
fi

echo "OK: sdist contains all required files."
