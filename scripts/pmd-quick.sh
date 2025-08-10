#!/bin/bash

# Quick PMD Analysis for Changed Files Only
# Useful for pre-commit checks on modified files

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "🚀 Quick PMD Analysis (Changed Files Only)"
echo "=========================================="

# Get list of changed Java files
CHANGED_FILES=$(git diff --name-only --cached --diff-filter=ACM | grep "\.java$" || true)

if [ -z "$CHANGED_FILES" ]; then
    echo "No Java files changed. Skipping PMD analysis."
    exit 0
fi

echo "Analyzing changed files:"
echo "$CHANGED_FILES"
echo ""

# Create temporary filelist
FILELIST_TEMP=$(mktemp)
echo "$CHANGED_FILES" > "$FILELIST_TEMP"

# Run PMD on changed files only
./mvnw pmd:pmd -DanalysisCache=false -DincludeFiles="$(echo $CHANGED_FILES | tr '\n' ',')"

rm -f "$FILELIST_TEMP"

echo ""
echo "✅ Quick PMD analysis complete!"