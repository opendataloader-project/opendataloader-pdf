#!/bin/bash

# Move main to the next development version, through a pull request.
#
# main requires one, and nothing a workflow run can authenticate as is exempt:
# `github-actions` is a runtime actor, so it never appears in a ruleset bypass
# list, which holds only the organization-admin and repository-admin roles. The
# ruleset asks for zero approving reviews and declares no required status
# checks, so the pull request opened here can be merged in the same step.
#
# PATCH by default — raise MINOR or MAJOR by hand before tagging.
#
# Reads VERSION (the released version, no leading v) and GH_TOKEN.

set -euo pipefail

: "${VERSION:?}"
: "${GH_TOKEN:?}"
: "${GITHUB_REPOSITORY:?}"

# gh runs from the worktree below, where deriving the repo from the remote
# would work but says less than naming it.
export GH_REPO="$GITHUB_REPOSITORY"

if [[ "$VERSION" == *-* ]]; then
  next="${VERSION%%-*}"
else
  IFS=. read -r major minor patch <<< "$VERSION"
  next="${major}.${minor}.$((patch + 1))"
fi

branch="chore/begin-${next}-snapshot"
# A worktree, not a checkout in place: the build rewrote every manifest in the
# tag's tree, and the steps around this one read options.json and schema.json
# from it. Nothing here touches that tree.
work="${RUNNER_TEMP:-/tmp}/version-bump"

MANIFESTS=(
  java/pom.xml
  java/opendataloader-pdf-core/pom.xml
  java/opendataloader-pdf-cli/pom.xml
  python/opendataloader-pdf/pyproject.toml
  python/opendataloader-pdf/uv.lock
  python/opendataloader-pdf-mcp/pyproject.toml
  python/opendataloader-pdf-mcp/uv.lock
  node/opendataloader-pdf/package.json
)

git worktree remove --force "$work" 2>/dev/null || true
git fetch --no-tags origin main
git worktree add --detach "$work" FETCH_HEAD

cleanup() {
  cd "${GITHUB_WORKSPACE:-/}" || return 0
  git worktree remove --force "$work" 2>/dev/null || true
}
trap cleanup EXIT

cd "$work"
git config user.name  'github-actions[bot]'
git config user.email 'github-actions[bot]@users.noreply.github.com'

# Each attempt restarts from main as it is now. A release that finishes while
# main moves on is the normal case, and rebase merge only resolves a moved base
# when the bump still applies to it.
for attempt in 1 2 3; do
  git fetch --no-tags origin main
  git checkout --detach --force FETCH_HEAD

  ./scripts/set-dev-version.sh "$next"
  if git diff --quiet; then
    echo "main already declares $next"
    exit 0
  fi

  git add "${MANIFESTS[@]}"
  git commit -m "chore: begin ${next}-SNAPSHOT"

  # --force: a previous attempt may have left the branch behind, and its commit
  # is never the one to keep.
  if ! git push --force origin "HEAD:refs/heads/${branch}"; then
    echo "branch push failed, retrying (${attempt}/3)" >&2
    continue
  fi

  url="$(gh pr list --head "$branch" --base main --state open --json url --jq '.[0].url // empty')"
  if [[ -z "$url" ]]; then
    url="$(gh pr create --base main --head "$branch" \
      --title "chore: begin ${next}-SNAPSHOT" \
      --body "Opened by the v${VERSION} release run. Merging it keeps main off a coordinate that is now released, so the next push to main publishes a snapshot of ${next} rather than of ${VERSION}.

Produced by \`./scripts/set-dev-version.sh ${next}\`.")"
  fi
  echo "pull request: $url"

  # mergeable is UNKNOWN until GitHub has computed it, and `gh pr merge` reads
  # it rather than waiting.
  for _ in $(seq 1 20); do
    state="$(gh pr view "$url" --json mergeable --jq .mergeable)"
    [[ "$state" == "UNKNOWN" ]] || break
    sleep 3
  done

  if gh pr merge "$url" --rebase; then
    echo "merged $url"
    # A merge performed with GITHUB_TOKEN triggers no workflow, so the first
    # snapshot of the new development version has to be asked for.
    gh workflow run snapshot.yml --ref main || echo "::warning::could not dispatch snapshot.yml" >&2
    exit 0
  fi

  echo "merge refused, retrying (${attempt}/3)" >&2
  gh pr view "$url" --json mergeable,mergeStateStatus,reviewDecision >&2 || true
done

echo "::error::could not land the version bump — main has not moved to ${next}" >&2
exit 1
