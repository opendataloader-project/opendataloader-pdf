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
  raw="$(java -version 2>&1 | head -1)"
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
  raw="$("${cmd}" --version 2>&1 | head -1)"
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

  # Try the CLI entry-point first
  local cli_ver=""
  if command -v opendataloader-pdf &>/dev/null; then
    cli_ver="$(opendataloader-pdf --version 2>/dev/null || true)"
  fi

  if [[ -n "${cli_ver}" ]]; then
    installed="true"
    version="$(printf '%s' "${cli_ver}" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)"
    version="${version:-none}"
  elif [[ -n "${pycmd}" ]]; then
    # Try python -m opendataloader_pdf --version
    local mod_ver
    mod_ver="$("${pycmd}" -m opendataloader_pdf --version 2>/dev/null || true)"
    if [[ -n "${mod_ver}" ]]; then
      installed="true"
      version="$(printf '%s' "${mod_ver}" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)"
      version="${version:-none}"
    else
      # Last resort: importlib.metadata
      local meta_ver
      meta_ver="$("${pycmd}" -c "import importlib.metadata; print(importlib.metadata.version('opendataloader-pdf'))" 2>/dev/null || true)"
      if [[ -n "${meta_ver}" ]]; then
        installed="true"
        version="${meta_ver}"
      fi
    fi
  fi

  printf '%s\n' "ODL_INSTALLED=${installed}"
  printf '%s\n' "ODL_VERSION=${version}"
}

# ---------------------------------------------------------------------------
# Hybrid extras — check for docling (primary indicator)
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
  result="$("${pycmd}" -c "import docling; print('ok')" 2>/dev/null || true)"
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
