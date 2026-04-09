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

HEALTH_ENDPOINT="${HYBRID_URL}/health"

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

# Interpret result
if [[ -z "${HTTP_STATUS}" || "${HTTP_STATUS}" == "000" || "${HTTP_STATUS}" == "none" ]]; then
  echo "HYBRID_SERVER=stopped"
  echo "HYBRID_URL=${HYBRID_URL}"
  echo "HYBRID_STATUS=none"
  echo ""
  echo "Hybrid server is not running. Start it with: opendataloader-pdf-hybrid --port 5002"
  exit 0
fi

# Any 2xx response is considered running; other codes are an error state
if [[ "${HTTP_STATUS}" =~ ^2 ]]; then
  echo "HYBRID_SERVER=running"
else
  echo "HYBRID_SERVER=error"
fi

echo "HYBRID_URL=${HYBRID_URL}"
echo "HYBRID_STATUS=${HTTP_STATUS}"
