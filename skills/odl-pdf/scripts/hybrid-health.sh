#!/usr/bin/env bash
# hybrid-health.sh
# Checks the health of a running opendataloader-pdf hybrid server.
# Works on Windows (Git Bash), macOS, and Linux.
# Outputs key=value pairs for machine readability.

set -euo pipefail

DEFAULT_URL="http://localhost:5002"
HYBRID_URL="${DEFAULT_URL}"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --url)
      if [[ $# -lt 2 ]]; then
        echo "Error: --url requires a value" >&2
        exit 1
      fi
      HYBRID_URL="$2"
      shift 2
      ;;
    --url=*)
      HYBRID_URL="${1#--url=}"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: $0 [--url <url>]" >&2
      exit 1
      ;;
  esac
done

# Validate the URL before use: reject empty or malformed values.
# Require the form http(s)://host[:port] (optional trailing slash; no path).
if [[ -z "${HYBRID_URL}" ]]; then
  echo "Error: --url must not be empty" >&2
  echo "Usage: $0 [--url <http(s)://host[:port]>]" >&2
  exit 1
fi
# Note the excluded '@': a URL with userinfo (https://user:pass@host) is rejected
# so credentials are never echoed back to stdout.
if [[ ! "${HYBRID_URL}" =~ ^https?://[^[:space:]/@]+(:[0-9]+)?/?$ ]]; then
  if [[ "${HYBRID_URL}" == *@* ]]; then
    echo "Error: --url must not contain embedded credentials (userinfo '@'); pass a plain host[:port]" >&2
  else
    echo "Error: --url must be of the form http(s)://host[:port] (got: '${HYBRID_URL}')" >&2
  fi
  echo "Usage: $0 [--url <http(s)://host[:port]>]" >&2
  exit 1
fi

HEALTH_ENDPOINT="${HYBRID_URL%/}/health"

# Detect available HTTP client
_http_get_status() {
  local url="$1"
  if command -v curl &>/dev/null; then
    curl --silent --output /dev/null --write-out "%{http_code}" \
         --max-time 5 --connect-timeout 3 "$url" 2>/dev/null
  elif command -v wget &>/dev/null; then
    wget --quiet --server-response --spider --timeout=5 "$url" 2>&1 \
      | awk '/HTTP\//{print $2}' | tail -1
  else
    echo "none"
  fi
}

HTTP_STATUS=$(_http_get_status "${HEALTH_ENDPOINT}" || true)

# No HTTP client available to probe — this is NOT "server stopped"; the check
# could not run at all. Report a distinct state so callers don't misread it.
if [[ "${HTTP_STATUS}" == "none" ]]; then
  echo "HYBRID_SERVER=error"
  echo "HYBRID_URL=${HYBRID_URL}"
  echo "HYBRID_STATUS=client-missing"
  echo ""
  echo "Cannot probe the hybrid server: no HTTP client (curl or wget) is available. Install one, or check the server manually."
  exit 0
fi

# Interpret result
if [[ -z "${HTTP_STATUS}" || "${HTTP_STATUS}" == "000" ]]; then
  echo "HYBRID_SERVER=stopped"
  echo "HYBRID_URL=${HYBRID_URL}"
  echo "HYBRID_STATUS=none"
  echo ""
  echo "Hybrid server is not running at ${HYBRID_URL}. Start it with: opendataloader-pdf-hybrid"
  exit 0
fi

# The script always exits 0 (a completed health probe is not itself a failure).
# The result is on stdout: HYBRID_SERVER=running means reachable; stopped/error
# mean not usable. Callers must branch on that value, NOT on the exit code.
if [[ "${HTTP_STATUS}" =~ ^2 ]]; then
  echo "HYBRID_SERVER=running"
else
  echo "HYBRID_SERVER=error"
fi

echo "HYBRID_URL=${HYBRID_URL}"
echo "HYBRID_STATUS=${HTTP_STATUS}"
