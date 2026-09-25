#!/usr/bin/env bash
set -e

# Change to script directory
cd "$(dirname "$0")"

# Export OpenJDK
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"

# Activate Virtual Environment
if [ -d "venv" ]; then
    source venv/bin/activate
else
    echo "[!] Error: Virtual environment 'venv' not found."
    exit 1
fi

# Run the Python Pipeline
python run_pipeline.py "$@"
