#!/bin/bash

# Test script for Safe Mode functionality
# This script tests all three safety modes: strict, permissive, and audit

set -e

echo "=== Safe Mode Testing Script ==="
echo "This script will test the Safe Mode feature in different configurations"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Base URL
BASE_URL="${MOXXIE_URL:-http://localhost:8080}"
API_URL="${BASE_URL}/api/v1"

# Test VM IDs
MOXXIE_VM_ID=9001
NON_MOXXIE_VM_ID=9002

echo "Using API URL: $API_URL"
echo ""

# Function to print test results
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

# Function to test an operation
test_operation() {
    local method=$1
    local path=$2
    local expected_status=$3
    local description=$4
    local data=$5
    
    echo -n "Testing: $description... "
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method -H "Content-Type: application/json" -d "$data" "$API_URL$path")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method "$API_URL$path")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)
    
    if [ "$http_code" = "$expected_status" ]; then
        print_result 0 "Expected: $expected_status, Got: $http_code"
        return 0
    else
        print_result 1 "Expected: $expected_status, Got: $http_code"
        echo "Response body: $body"
        return 1
    fi
}

# Setup: Create test VMs if they don't exist
setup_test_vms() {
    echo "=== Setting up test VMs ==="
    
    # Check if Moxxie VM exists
    if curl -s "$API_URL/vms/$MOXXIE_VM_ID" | grep -q "\"vmId\":$MOXXIE_VM_ID"; then
        echo "Moxxie test VM already exists"
    else
        echo "Creating Moxxie test VM..."
        curl -s -X POST -H "Content-Type: application/json" \
            -d "{\"vmId\":$MOXXIE_VM_ID,\"name\":\"moxxie-test-vm\",\"node\":\"node1\",\"cores\":1,\"memoryMB\":512}" \
            "$API_URL/vms"
    fi
    
    # Check if non-Moxxie VM exists (simulated)
    echo "Note: Non-Moxxie VM ($NON_MOXXIE_VM_ID) should be created manually in Proxmox"
    echo ""
}

# Test 1: Safety Status and Configuration
test_safety_endpoints() {
    echo "=== Testing Safety Endpoints ==="
    
    test_operation "GET" "/safety/status" "200" "Get safety status"
    test_operation "GET" "/safety/config" "200" "Get safety configuration"
    test_operation "GET" "/safety/audit" "200" "Get audit log"
    echo ""
}

# Test 2: Strict Mode
test_strict_mode() {
    echo "=== Testing STRICT Mode ==="
    echo "Configuration: moxxie.safety.mode=strict"
    echo ""
    
    # Read operations should always work
    test_operation "GET" "/vms" "200" "List all VMs (always allowed)"
    test_operation "GET" "/vms/$MOXXIE_VM_ID" "200" "Get Moxxie VM details (always allowed)"
    test_operation "GET" "/vms/$NON_MOXXIE_VM_ID" "200" "Get non-Moxxie VM details (always allowed)"
    
    # Write operations on Moxxie VMs should work
    test_operation "POST" "/vms/$MOXXIE_VM_ID/start" "202" "Start Moxxie VM (allowed)"
    
    # Write operations on non-Moxxie VMs should be blocked
    test_operation "POST" "/vms/$NON_MOXXIE_VM_ID/stop" "403" "Stop non-Moxxie VM (blocked)"
    test_operation "DELETE" "/vms/$NON_MOXXIE_VM_ID" "403" "Delete non-Moxxie VM (blocked)"
    
    # Force flag should override
    test_operation "DELETE" "/vms/$NON_MOXXIE_VM_ID?force=true" "204" "Delete non-Moxxie VM with force (allowed)"
    echo ""
}

# Test 3: Permissive Mode
test_permissive_mode() {
    echo "=== Testing PERMISSIVE Mode ==="
    echo "Configuration: moxxie.safety.mode=permissive"
    echo "Note: You need to manually change the configuration and restart the service"
    echo ""
    
    # Non-destructive operations should work
    test_operation "POST" "/vms/$NON_MOXXIE_VM_ID/start" "202" "Start non-Moxxie VM (allowed)"
    
    # Destructive operations should be blocked
    test_operation "DELETE" "/vms/$NON_MOXXIE_VM_ID" "403" "Delete non-Moxxie VM (blocked)"
    test_operation "POST" "/vms/$NON_MOXXIE_VM_ID/stop" "403" "Stop non-Moxxie VM (blocked)"
    echo ""
}

# Test 4: Audit Mode
test_audit_mode() {
    echo "=== Testing AUDIT Mode ==="
    echo "Configuration: moxxie.safety.mode=audit"
    echo "Note: You need to manually change the configuration and restart the service"
    echo ""
    
    # All operations should work but be logged
    test_operation "POST" "/vms/$NON_MOXXIE_VM_ID/stop" "202" "Stop non-Moxxie VM (allowed with warning)"
    test_operation "DELETE" "/vms/$NON_MOXXIE_VM_ID" "204" "Delete non-Moxxie VM (allowed with warning)"
    
    # Check audit log
    echo "Checking audit log for warnings..."
    curl -s "$API_URL/safety/audit" | jq '.entries[] | select(.vmId == '$NON_MOXXIE_VM_ID')'
    echo ""
}

# Test 5: Tag Operations
test_tag_operations() {
    echo "=== Testing Tag Operations ==="
    
    # Get tags
    test_operation "GET" "/vms/$MOXXIE_VM_ID/tags" "200" "Get Moxxie VM tags"
    
    # Add tag to Moxxie VM (should work)
    test_operation "POST" "/vms/$MOXXIE_VM_ID/tags" "200" "Add tag to Moxxie VM" '{"tag":"test-tag"}'
    
    # Add tag to non-Moxxie VM (should be blocked in strict mode)
    test_operation "POST" "/vms/$NON_MOXXIE_VM_ID/tags" "403" "Add tag to non-Moxxie VM (blocked)" '{"tag":"test-tag"}'
    
    # Force override
    test_operation "POST" "/vms/$NON_MOXXIE_VM_ID/tags?force=true" "200" "Add tag with force" '{"tag":"test-tag"}'
    echo ""
}

# Main execution
main() {
    echo "Starting Safe Mode tests..."
    echo "Make sure the Moxxie service is running with Safe Mode enabled"
    echo ""
    
    # Run tests
    test_safety_endpoints
    
    echo "=== Mode-specific tests ==="
    echo "To test different modes, you need to:"
    echo "1. Stop the service"
    echo "2. Change moxxie.safety.mode in application.properties"
    echo "3. Restart the service"
    echo "4. Run the specific test"
    echo ""
    
    read -p "Which mode is currently configured? (strict/permissive/audit): " current_mode
    
    case $current_mode in
        strict)
            test_strict_mode
            test_tag_operations
            ;;
        permissive)
            test_permissive_mode
            ;;
        audit)
            test_audit_mode
            ;;
        *)
            echo "Invalid mode. Please configure one of: strict, permissive, audit"
            exit 1
            ;;
    esac
    
    echo ""
    echo "=== Test Summary ==="
    echo "Check the audit log for all safety decisions:"
    echo "curl -s $API_URL/safety/audit | jq ."
}

# Run main function
main