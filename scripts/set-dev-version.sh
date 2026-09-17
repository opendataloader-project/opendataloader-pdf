#!/bin/bash

# Write one logical version into every package as its own ecosystem's
# development version. Takes the plain N.N.N; each suffix is added here.
#
# PEP 440 rejects `-SNAPSHOT`, so no single literal serves all three.

set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <N.N.N>" >&2
  exit 1
fi

VERSION="$1"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: '$VERSION' is not N.N.N" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR/java"
mvn -B -q versions:set -DnewVersion="${VERSION}-SNAPSHOT" -DgenerateBackupPoms=false

for project in opendataloader-pdf opendataloader-pdf-mcp; do
  cd "$ROOT_DIR/python/$project"
  sed -i.bak "s/^version = \"[^\"]*\"/version = \"${VERSION}.dev0\"/" pyproject.toml
  # uv.lock records the project's own version, under the entry whose name is
  # this package. Edited in place rather than relocked: `uv lock` also rewrites
  # dependency markers, which a version bump has no business touching.
  sed -i.bak -e "/^name = \"${project}\"\$/,/^version = / s/^version = \".*\"\$/version = \"${VERSION}.dev0\"/" uv.lock
  rm -f pyproject.toml.bak uv.lock.bak
done

cd "$ROOT_DIR/node/opendataloader-pdf"
# No git flags: the caller owns the commit.
pnpm version "${VERSION}-dev.0" --no-git-tag-version --allow-same-version --no-git-checks >/dev/null

echo "set development version ${VERSION} (Java ${VERSION}-SNAPSHOT, Python ${VERSION}.dev0, Node ${VERSION}-dev.0)"
