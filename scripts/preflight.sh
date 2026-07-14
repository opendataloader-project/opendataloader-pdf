#!/bin/bash

# Preflight: verify that every deploy credential actually authenticates BEFORE
# the ~30-40 min build runs. release.yml only touches these credentials in its
# final steps, so an expired token or missing scope would otherwise fail late
# (and risk a partial publish). This gates the release job via `needs`.
#
# Checks (auth-only — no publish, no dry-run):
#   npm     : `npm whoami` with NODE_AUTH_TOKEN
#   maven   : Sonatype Central Portal /published (200 vs 401)
#   gpg     : import key + sign+verify a dummy file with the passphrase
#   github  : repo read + push permission for the homepage-sync PAT
#   pypi    : mint a GitHub Actions OIDC token for audience=pypi
#
# Secrets are read from the environment ONLY (never script args — they leak via
# `ps`). curl auth is fed via `--config /dev/stdin` (a here-doc), NOT `-H` on the
# command line, so tokens never appear in curl's argv / /proc/<pid>/cmdline.
# Nothing secret is ever printed; only PASS/FAIL labels and HTTP status codes.
#
# Usage: ./scripts/preflight.sh   (env vars injected by the workflow)

set -euo pipefail

# --- coordinates (kept as named vars to avoid drift) ------------------------
MAVEN_NS="org.opendataloader"
MAVEN_NAME="opendataloader-pdf-core"
MAVEN_PROBE_VERSION="0.0.0"          # any value; we only read 200-vs-401
NPM_REGISTRY="https://registry.npmjs.org"
GH_REPO="opendataloader-project/opendataloader.org"
PYPI_AUDIENCE="pypi"

# --- result accumulator -----------------------------------------------------
RESULTS=()
FAILED=0

pass() { RESULTS+=("PASS | $1"); echo "PASS | $1"; }
fail() { RESULTS+=("FAIL | $1"); echo "FAIL | $1" >&2; FAILED=1; }

# Assert an env var is set and non-empty without ever printing its value.
require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Error: required secret \$$name is unset or empty." >&2
    return 1
  fi
}

# --- 1. npm -----------------------------------------------------------------
check_npm() {
  local label="npm (NPM_TOKEN)"
  require_env NODE_AUTH_TOKEN || { fail "$label"; return; }
  # npm reads NODE_AUTH_TOKEN from the env via the registry auth config that
  # setup-node writes; whoami is a pure authenticated call, no publish.
  if npm whoami --registry="$NPM_REGISTRY" >/dev/null 2>&1; then
    pass "$label"
  else
    fail "$label"
  fi
}

# --- 2. Maven Central (Sonatype Central Portal) -----------------------------
check_maven() {
  local label="Maven Central (MAVEN_CENTRAL_USERNAME/PASSWORD)"
  require_env MAVEN_CENTRAL_USERNAME || { fail "$label"; return; }
  require_env MAVEN_CENTRAL_PASSWORD || { fail "$label"; return; }

  local url="https://central.sonatype.com/api/v1/publisher/published?namespace=${MAVEN_NS}&name=${MAVEN_NAME}&version=${MAVEN_PROBE_VERSION}"

  # Build the Basic credential in the shell (base64 handles ANY bytes safely),
  # then pass it as an Authorization header via a curl config read from stdin so
  # the token never lands in argv. We do NOT put user:pass in curl's `user =`:
  # curl's config quoting mangles values containing " \ or whitespace, which
  # would send the wrong credential and misreport a valid one as a 401. The
  # base64 blob is [A-Za-z0-9+/=] only, so it is safe inside the quoted header.
  local basic
  basic="$(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')"
  echo "::add-mask::$basic"

  # On transport failure curl still prints "000" to stdout AND exits non-zero,
  # so use the exit status (not a "|| echo 000" that would append a 2nd "000").
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' --config /dev/stdin "$url" <<EOF || true
header = "Authorization: Basic ${basic}"
EOF
)"
  [ -z "$code" ] && code="000"

  # 200 => authenticated (published true/false is irrelevant).
  # 401/403 => bad creds. 5xx/000 => Sonatype outage or network blip, NOT a
  # credential problem — label it so an infra hiccup isn't misread as expiry.
  if [ "$code" = "200" ]; then
    pass "$label"
  elif [ "$code" = "401" ] || [ "$code" = "403" ]; then
    fail "$label (HTTP $code — bad credentials)"
  else
    fail "$label (HTTP $code — Sonatype unreachable? not necessarily a credential fault)"
  fi
}

# --- 3. GPG -----------------------------------------------------------------
check_gpg() {
  local label="GPG signing (MAVEN_GPG_KEY/PASSPHRASE)"
  require_env MAVEN_GPG_KEY || { fail "$label"; return; }
  require_env MAVEN_GPG_PASSPHRASE || { fail "$label"; return; }

  # Isolated ephemeral keyring so we never touch the runner's default homedir.
  local gpg_home="$TMP/gnupg"
  mkdir -p "$gpg_home"
  chmod 700 "$gpg_home"

  if ! printf '%s' "$MAVEN_GPG_KEY" | gpg --homedir "$gpg_home" --batch --import >/dev/null 2>&1; then
    fail "$label (import)"
    return
  fi

  local dummy="$TMP/preflight-sign.txt"
  echo "opendataloader-pdf preflight" > "$dummy"

  # Passphrase via stdin (--passphrase-fd 0), never on the command line.
  if ! printf '%s' "$MAVEN_GPG_PASSPHRASE" \
      | gpg --homedir "$gpg_home" --batch --yes --pinentry-mode loopback \
            --passphrase-fd 0 --detach-sign --armor -o "$dummy.asc" "$dummy" >/dev/null 2>&1; then
    fail "$label (sign)"
    return
  fi

  if gpg --homedir "$gpg_home" --batch --verify "$dummy.asc" "$dummy" >/dev/null 2>&1; then
    pass "$label"
  else
    fail "$label (verify)"
  fi
}

# --- 4. GitHub PAT (homepage sync) ------------------------------------------
check_github() {
  local label="GitHub PAT (HOMEPAGE_SYNC_TOKEN)"
  require_env HOMEPAGE_SYNC_TOKEN || { fail "$label"; return; }

  # Token via --header on a stdin config (out of argv). Body carries no secret
  # (repo metadata); safe to capture. The `permissions` object only appears on
  # authenticated requests, and .permissions.push is the write-access signal the
  # docs-sync step needs.
  local body
  body="$(curl -sS --config /dev/stdin \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${GH_REPO}" 2>/dev/null <<EOF || echo '{}'
header = "Authorization: Bearer ${HOMEPAGE_SYNC_TOKEN}"
EOF
)"

  if echo "$body" | jq -e '.permissions.push == true' >/dev/null 2>&1; then
    pass "$label"
  else
    fail "$label (no push access to ${GH_REPO})"
  fi
}

# --- 5. PyPI (OIDC issuance) ------------------------------------------------
check_pypi() {
  local label="PyPI OIDC (id-token: write)"
  require_env ACTIONS_ID_TOKEN_REQUEST_URL || { fail "$label (no id-token permission)"; return; }
  require_env ACTIONS_ID_TOKEN_REQUEST_TOKEN || { fail "$label (no id-token permission)"; return; }

  # Mint an OIDC token for audience=pypi. Proves id-token: write works and the
  # runner can issue the exact token the PyPI publish step relies on. Request
  # token via stdin config (out of argv); the minted value is piped straight
  # into jq, masked, and discarded — never printed.
  local value
  value="$(curl -sS --config /dev/stdin \
    "${ACTIONS_ID_TOKEN_REQUEST_URL}&audience=${PYPI_AUDIENCE}" 2>/dev/null <<EOF | jq -r '.value // empty' 2>/dev/null || echo ""
header = "Authorization: bearer ${ACTIONS_ID_TOKEN_REQUEST_TOKEN}"
EOF
)"

  if [ -n "$value" ]; then
    echo "::add-mask::$value"
    pass "$label"
  else
    fail "$label"
  fi
}

# --- summary ----------------------------------------------------------------
write_summary() {
  local summary="${GITHUB_STEP_SUMMARY:-}"
  [ -z "$summary" ] && return 0
  {
    echo "## Release preflight — credential checks"
    echo ""
    echo "| Status | Check |"
    echo "|--------|-------|"
    local line status rest
    for line in "${RESULTS[@]}"; do
      status="${line%% | *}"
      rest="${line#* | }"
      if [ "$status" = "PASS" ]; then
        echo "| ✅ | $rest |"
      else
        echo "| ❌ | $rest |"
      fi
    done
    echo ""
    if [ "$FAILED" -eq 0 ]; then
      echo "**All credentials authenticated. Release may proceed.**"
    else
      echo "**One or more credentials failed. Release is blocked.**"
    fi
  } >> "$summary"
}

# --- run all (collect-then-fail: never short-circuit on first failure) ------
main() {
  # $TMP holds the ephemeral GPG keyring + dummy files; cleaned on exit.
  # In CI, require RUNNER_TEMP so the imported private signing key never lands
  # under world-writable /tmp; the /tmp fallback is for local testing only.
  local temp_base="${RUNNER_TEMP:-}"
  if [ -z "$temp_base" ]; then
    if [ "${CI:-}" = "true" ]; then
      echo "Error: RUNNER_TEMP is unset in CI; refusing to write the GPG key under /tmp." >&2
      exit 1
    fi
    temp_base="/tmp"
  fi
  if ! TMP="$(mktemp -d "${temp_base}/preflight.XXXXXX")"; then
    echo "Error: could not create a temp dir for preflight (${temp_base} unwritable?)." >&2
    exit 1
  fi
  # Kill any gpg-agent spawned under $TMP (holds the passphrase in memory) before
  # wiping the dir, so no keyring/agent state outlives the run.
  trap 'gpgconf --homedir "$TMP/gnupg" --kill all >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT

  echo "Running release preflight credential checks..."
  echo "------------------------------------------------"

  check_npm    || true
  check_maven  || true
  check_gpg    || true
  check_github || true
  check_pypi   || true

  echo "------------------------------------------------"
  write_summary

  if [ "$FAILED" -eq 0 ]; then
    echo "Preflight OK: all deploy credentials authenticated."
  else
    echo "Preflight FAILED: fix the credentials above before releasing." >&2
  fi
  exit "$FAILED"
}

main
