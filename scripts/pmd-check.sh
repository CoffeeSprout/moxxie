#!/bin/bash

# PMD Code Analysis Script for Moxxie Project
# This script runs PMD analysis and generates reports

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "🔍 Running PMD Code Analysis..."
echo "================================"

# Clean previous PMD cache and reports
rm -rf target/pmd target/pmd.xml target/pmd.html 2>/dev/null || true

# Run PMD check (will fail if violations exceed threshold)
echo "Running PMD check..."
./mvnw pmd:check || EXIT_CODE=$?

# Generate reports (PMD 7.16+ generates XML by default)
echo ""
echo "📊 Generating PMD reports..."
./mvnw pmd:pmd

echo ""
echo "📈 PMD Analysis Complete!"
echo "========================="

# Display summary if violations found
if [ -f "target/pmd.xml" ]; then
    VIOLATIONS=$(grep -c "<violation" target/pmd.xml 2>/dev/null || echo "0")
    echo "Total violations found: $VIOLATIONS"
    
    if [ "$VIOLATIONS" -gt "0" ]; then
        echo ""
        echo "⚠️  Code quality issues detected!"
        echo "View detailed report: target/pmd.xml"
        echo ""
        echo "Top issues:"
        grep "<violation" target/pmd.xml | head -10 | sed 's/.*rule="\([^"]*\)".*file="\([^"]*\)".*line="\([^"]*\)".*/  - \1 at \2:\3/'
    else
        echo "✅ No PMD violations found!"
    fi
fi

exit ${EXIT_CODE:-0}