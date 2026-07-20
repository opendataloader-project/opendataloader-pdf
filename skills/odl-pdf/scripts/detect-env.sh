#!/usr/bin/env bash
# detect-env.sh — Cross-platform environment detection for the odl-pdf agent skill.
# Outputs key=value pairs (one per line) to stdout. No other output.
# Make this file executable: chmod +x detect-env.sh

set -euo pipefail

# ---------------------------------------------------------------------------
# OS detection
# ---------------------------------------------------------------------------
detect_os() {
  local raw
  raw="$(uname -s 2>/dev/null || echo "unknown")"
  case "${raw}" in
    Darwin*)          echo "macos"   ;;
    Linux*)           echo "linux"   ;;
    MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
    *)                echo "unknown" ;;
  esac
}

# ---------------------------------------------------------------------------
# Java version  (java outputs version to stderr)
# ---------------------------------------------------------------------------
detect_java() {
  if ! command -v java &>/dev/null; then
    echo "none"
    return
  fi
  local raw
  # `head -1` closes the pipe early; under `set -o pipefail` java's SIGPIPE
  # would otherwise abort the script. Guard with `|| true`.
  raw="$(java -version 2>&1 | head -1 || true)"
  # Handles formats:
  #   openjdk version "21.0.3" ...
  #   java version "1.8.0_401" ...
  #   openjdk version "11.0.22" ...
  local ver
  ver="$(printf '%s' "${raw}" | grep -oE '"[^"]+"' | tr -d '"' | head -1 || true)"
  if [[ -z "${ver}" ]]; then
    echo "none"
    return
  fi
  # Normalise legacy 1.x format → major only; otherwise keep major
  if [[ "${ver}" =~ ^1\.([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    # Extract leading integer(s) before the first dot
    local major
    major="$(printf '%s' "${ver}" | grep -oE '^[0-9]+' || true)"
    echo "${major:-none}"
  fi
}

# ---------------------------------------------------------------------------
# Python version  (try python3 first, then python)
# ---------------------------------------------------------------------------
detect_python() {
  local cmd=""
  if command -v python3 &>/dev/null; then
    cmd="python3"
  elif command -v python &>/dev/null; then
    cmd="python"
  else
    echo "none"
    return
  fi
  local raw
  # Guard the pipe against SIGPIPE aborting the script under `set -o pipefail`.
  raw="$("${cmd}" --version 2>&1 | head -1 || true)"
  # e.g. "Python 3.12.4"
  local ver
  ver="$(printf '%s' "${raw}" | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -1 || true)"
  echo "${ver:-none}"
}

# ---------------------------------------------------------------------------
# Node version
# ---------------------------------------------------------------------------
detect_node() {
  if ! command -v node &>/dev/null; then
    echo "none"
    return
  fi
  local raw
  raw="$(node --version 2>/dev/null)"
  # e.g. "v20.19.0"  → strip leading 'v'
  local ver
  ver="$(printf '%s' "${raw}" | sed 's/^v//')"
  echo "${ver:-none}"
}

# ---------------------------------------------------------------------------
# ODL installed + version
# Tries CLI first, then Python module.
# ---------------------------------------------------------------------------
detect_odl() {
  local installed="false"
  local version="none"

  # Determine python binary
  local pycmd=""
  if command -v python3 &>/dev/null; then
    pycmd="python3"
  elif command -v python &>/dev/null; then
    pycmd="python"
  fi

  # Detect install + version WITHOUT a --version flag (the CLI has none;
  # see SKILL.md). CLI presence => installed; version from package metadata.
  if command -v opendataloader-pdf &>/dev/null; then
    installed="true"
  fi

  # Python package version — from importlib.metadata
  local version_source="none"
  if [[ -n "${pycmd}" ]]; then
    local meta_ver
    meta_ver="$("${pycmd}" -c "import importlib.metadata as m; print(m.version('opendataloader-pdf'))" 2>/dev/null || true)"
    if [[ -n "${meta_ver}" ]]; then
      installed="true"
      version="${meta_ver}"
      version_source="python"
    fi
  fi

  # Node package version via npm ls
  local npm_ver=""
  if command -v npm &>/dev/null; then
    npm_ver="$(npm ls @opendataloader/pdf --depth=0 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)"
  fi
  if [[ "${version}" == "none" && -n "${npm_ver}" ]]; then
    installed="true"
    version="${npm_ver}"
    version_source="node"
  elif [[ "${version_source}" == "python" && -n "${npm_ver}" && "${npm_ver}" != "${version}" ]]; then
    # Both a Python and a Node package are present with different versions — the
    # version reported here may not be the one the PATH `opendataloader-pdf` runs.
    version_source="ambiguous"
  fi

  printf '%s\n' "ODL_INSTALLED=${installed}"
  printf '%s\n' "ODL_VERSION=${version}"
  # Which package the version came from; 'ambiguous' => trust `--help`, not this label.
  printf '%s\n' "ODL_VERSION_SOURCE=${version_source}"
}

# ---------------------------------------------------------------------------
# Hybrid extras — the [hybrid] extra pulls docling, fastapi, uvicorn AND
# python-multipart. Check all four so a partially-installed env is not reported
# as ready. (python-multipart's import name varies by version, so verify the
# installed distribution by metadata rather than importing it.)
# ---------------------------------------------------------------------------
detect_hybrid_extras() {
  local pycmd=""
  if command -v python3 &>/dev/null; then
    pycmd="python3"
  elif command -v python &>/dev/null; then
    pycmd="python"
  fi

  if [[ -z "${pycmd}" ]]; then
    echo "HYBRID_EXTRAS=false"
    return
  fi

  local result
  result="$("${pycmd}" -c "import importlib.metadata as m; import docling, fastapi, uvicorn; m.version('python-multipart'); print('ok')" 2>/dev/null || true)"
  if [[ "${result}" == "ok" ]]; then
    echo "HYBRID_EXTRAS=true"
  else
    echo "HYBRID_EXTRAS=false"
  fi
}

# ---------------------------------------------------------------------------
# Main — emit all key=value pairs
# ---------------------------------------------------------------------------
printf '%s\n' "OS=$(detect_os)"
printf '%s\n' "JAVA=$(detect_java)"
printf '%s\n' "PYTHON=$(detect_python)"
printf '%s\n' "NODE=$(detect_node)"
detect_odl
detect_hybrid_extras
