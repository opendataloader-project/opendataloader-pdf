#!/bin/bash

# Move main to the next development version, through a pull request.
#
# main requires one, and nothing a workflow run can authenticate as is exempt:
# `github-actions` is a runtime actor, so it never appears in a ruleset bypass
# list, which holds only the organization-admin and repository-admin roles. The
# ruleset asks for zero approving reviews and declares no required status
# checks, so the pull request opened here can be merged as soon as it is open.
#
# PATCH by default — raise MINOR or MAJOR by hand before tagging.

set -euo pipefail

: "${RELEASED_TAG:?}"
: "${GH_TOKEN:?}"
: "${GITHUB_REPOSITORY:?}"

export GH_REPO="$GITHUB_REPOSITORY"

version="${RELEASED_TAG#v}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] \
  || { echo "::error::tag '${RELEASED_TAG}' is not vN.N.N[-suffix]" >&2; exit 1; }

if [[ "$version" == *-* ]]; then
  next="${version%%-*}"
else
  IFS=. read -r major minor patch <<< "$version"
  next="${major}.${minor}.$((patch + 1))"
fi

branch="chore/begin-${next}-snapshot"
url=""

# Called as a condition, which suspends `set -e` for the whole body, so every
# command carries its own guard. 0 landed the bump, 2 found main already there,
# 1 asks for another attempt — a release finishing while main moves on is the
# normal case, and each attempt starts over from main as it is now.
attempt_bump() {
  git fetch --no-tags origin main || return 1
  git checkout --detach --force FETCH_HEAD || return 1

  ./scripts/set-dev-version.sh "$next" || return 1
  git diff --quiet && return 2

  # --update, so what gets staged is what the check above looked at. A manifest
  # belonging to a module added later reaches both or neither.
  git add --update || return 1
  git commit -m "chore: begin ${next}-SNAPSHOT" || return 1

  local head_sha
  head_sha="$(git rev-parse HEAD)" || return 1

  # --force: the branch name belongs to this script, and a commit an earlier
  # attempt left under it is never the one to keep.
  git push --force origin "HEAD:refs/heads/${branch}" || return 1

  # `gh pr create` fails once a pull request for this head is open, so ask
  # first. But --head filters on the ref name alone, and the name follows from
  # the version main declares, so a fork's branch of that name matches just as
  # well: only a pull request from this repository, at the commit just pushed,
  # is this run's own.
  local found
  found="$(gh pr list --head "$branch" --base main --state open \
             --json url,isCrossRepository,headRefOid \
           | jq -r --arg sha "$head_sha" \
               'map(select(.isCrossRepository == false and .headRefOid == $sha))
                | .[0].url // empty')" || return 1

  if [[ -n "$found" ]]; then
    url="$found"
  else
    url="$(gh pr create --base main --head "$branch" \
      --title "chore: begin ${next}-SNAPSHOT" \
      --body "Opened by the v${version} release run, which published ${version} and left main declaring the coordinate it was cut from. Merging this keeps the next push to main on a snapshot of ${next}.

Produced by \`./scripts/set-dev-version.sh ${next}\`.")" || {
      echo "::warning::could not open the pull request — Actions may not be permitted to create one here" >&2
      return 1
    }
  fi
  echo "pull request: $url"

  # Mergeability is computed asynchronously, and a refusal read off a stale
  # answer is worth asking again about. --match-head-commit is what makes the
  # gap between that answer and the merge acting on it safe to leave open.
  local _
  for _ in $(seq 1 10); do
    gh pr merge "$url" --rebase --match-head-commit "$head_sha" && return 0
    sleep 6
  done
  return 1
}

discard_branch() {
  if [[ -n "$url" ]]; then
    gh pr close "$url" --delete-branch \
      --comment "Superseded — the v${version} release run did not land this bump." || true
  else
    git push --delete origin "$branch" || true
  fi
}

landed=0
already=0
for attempt in 1 2 3; do
  attempt_bump && { landed=1; break; }
  rc=$?
  if [[ $rc -eq 2 ]]; then already=1; break; fi
  echo "attempt ${attempt}/3 did not land the bump" >&2
  [[ -n "$url" ]] && gh pr view "$url" --json mergeable,mergeStateStatus >&2 || true
done

if [[ $already -eq 1 ]]; then
  echo "main already declares $next"
  discard_branch
fi

if [[ $landed -eq 1 || $already -eq 1 ]]; then
  # A merge performed with GITHUB_TOKEN triggers no workflow, so the first
  # snapshot of the new development version has to be asked for.
  gh workflow run snapshot.yml --ref main \
    || echo "::warning::could not dispatch snapshot.yml" >&2
  exit 0
fi

# The branch and its pull request exist only to carry a bump that did not land.
discard_branch

echo "::error::could not land the version bump — main has not moved to ${next}" >&2
exit 1
