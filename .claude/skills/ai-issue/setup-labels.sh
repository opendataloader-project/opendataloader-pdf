#!/bin/bash
# Setup GitHub labels from labels.yml definition
# Usage: .claude/skills/ai-issue/setup-labels.sh

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Path to labels.yml (same directory as this script)
LABELS_FILE="$SCRIPT_DIR/labels.yml"

if [[ ! -f "$LABELS_FILE" ]]; then
    echo "Error: labels.yml not found at $LABELS_FILE"
    exit 1
fi

echo "Creating/updating GitHub labels from $LABELS_FILE..."

# Check if yq is available
if ! command -v yq &> /dev/null; then
    echo "Error: yq is required but not installed."
    echo "Install with: brew install yq (macOS) or snap install yq (Linux)"
    exit 1
fi

# Parse YAML and create labels
# yq outputs each label as: name|color|description
yq -r '.[][] | "\(.name)|\(.color)|\(.description)"' "$LABELS_FILE" | while IFS='|' read -r name color description; do
    if [[ -n "$name" ]]; then
        echo "  Creating/updating: $name"
        gh label create "$name" --color "$color" --description "$description" --force
    fi
done

echo "Done! All labels created/updated."
