#!/bin/bash

# Manual integration test script for Safe Mode feature
# This script tests Safe Mode against a running Moxxie instance

set -e

echo "=== Safe Mode Manual Testing Script ==="
echo "This script requires a running Moxxie instance with Safe Mode enabled"
echo ""

# Configuration
BASE_URL="${MOXXIE_URL:-http://localhost:8080}"
API_URL="${BASE_URL}/api/v1"
API_KEY="${MOXXIE_API_KEY:-}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test tracking
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
print_test() {
    echo -e "${BLUE}TEST:${NC} $1"
}

print_pass() {
    echo -e "${GREEN}✓ PASS:${NC} $1"
    ((TESTS_PASSED++))
}

print_fail() {
    echo -e "${RED}✗ FAIL:${NC} $1"
    ((TESTS_FAILED++))
}

print_info() {
    echo -e "${YELLOW}INFO:${NC} $1"
}

# API call helper
api_call() {
    local method=$1
    local endpoint=$2
    local expected_status=$3
    local data=$4
    local query_params=$5
    
    local url="${API_URL}${endpoint}"
    if [ -n "$query_params" ]; then
        url="${url}?${query_params}"
    fi
    
    local headers="-H 'Content-Type: application/json'"
    if [ -n "$API_KEY" ]; then
        headers="$headers -H 'X-API-Key: $API_KEY'"
    fi
    
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" $headers -d "$data" "$url" 2>/dev/null || true)
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" $headers "$url" 2>/dev/null || true)
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)
    
    if [ "$http_code" = "$expected_status" ]; then
        echo "$body"
        return 0
    else
        echo "Expected: $expected_status, Got: $http_code" >&2
        echo "Response: $body" >&2
        return 1
    fi
}

# Test 1: Check Safety Status
test_safety_status() {
    print_test "Checking safety status endpoint"
    
    if response=$(api_call GET "/safety/status" 200); then
        enabled=$(echo "$response" | jq -r '.enabled')
        mode=$(echo "$response" | jq -r '.mode')
        print_pass "Safety status endpoint works"
        print_info "Safe Mode enabled: $enabled, Mode: $mode"
        
        if [ "$enabled" != "true" ]; then
            print_fail "Safe Mode is not enabled! Please enable it in application.properties"
            return 1
        fi
    else
        print_fail "Failed to get safety status"
        return 1
    fi
}

# Test 2: Check Safety Configuration
test_safety_config() {
    print_test "Checking safety configuration endpoint"
    
    if response=$(api_call GET "/safety/config" 200); then
        print_pass "Safety config endpoint works"
        echo "$response" | jq '.'
    else
        print_fail "Failed to get safety config"
    fi
}

# Test 3: Create a Moxxie-managed VM
test_create_moxxie_vm() {
    print_test "Creating a Moxxie-managed VM"
    
    local vm_data='{
        "name": "moxxie-safe-test-vm",
        "node": "node1",
        "cores": 1,
        "memoryMB": 512,
        "network": {
            "bridge": "vmbr0"
        }
    }'
    
    if response=$(api_call POST "/vms" 201 "$vm_data"); then
        MOXXIE_VM_ID=$(echo "$response" | jq -r '.vmid // empty')
        if [ -z "$MOXXIE_VM_ID" ]; then
            # Try to extract from Location header
            print_info "VM created, but ID not in response. Check manually."
            MOXXIE_VM_ID=9999
        fi
        print_pass "Created Moxxie VM with ID: $MOXXIE_VM_ID"
    else
        print_fail "Failed to create Moxxie VM"
        MOXXIE_VM_ID=9999
    fi
}

# Test 4: Check VM tags
test_vm_tags() {
    print_test "Checking tags on Moxxie VM"
    
    if response=$(api_call GET "/vms/$MOXXIE_VM_ID/tags" 200); then
        tags=$(echo "$response" | jq -r '.tags[]' 2>/dev/null || echo "none")
        print_pass "Retrieved VM tags: $tags"
        
        if echo "$tags" | grep -q "moxxie"; then
            print_pass "VM is properly tagged as 'moxxie'"
        else
            print_fail "VM is not tagged as 'moxxie'"
        fi
    else
        print_fail "Failed to get VM tags"
    fi
}

# Test 5: Test operations on Moxxie VM (should work)
test_moxxie_vm_operations() {
    print_test "Testing operations on Moxxie-managed VM"
    
    # Start VM (should work)
    if api_call POST "/vms/$MOXXIE_VM_ID/start" 202 >/dev/null 2>&1; then
        print_pass "Start operation allowed on Moxxie VM"
    else
        print_info "Start operation failed (VM might already be running)"
    fi
    
    # Stop VM (should work)
    if api_call POST "/vms/$MOXXIE_VM_ID/stop" 202 >/dev/null 2>&1; then
        print_pass "Stop operation allowed on Moxxie VM"
    else
        print_info "Stop operation failed (VM might already be stopped)"
    fi
}

# Test 6: Test operations on non-Moxxie VM (should be blocked in strict mode)
test_non_moxxie_vm_operations() {
    print_test "Testing operations on non-Moxxie VM (should be blocked)"
    
    # Find a non-Moxxie VM
    if vms=$(api_call GET "/vms" 200); then
        NON_MOXXIE_VM_ID=$(echo "$vms" | jq -r '.[] | select(.name != null and (.name | contains("moxxie") | not)) | .vmId' | head -1)
        
        if [ -z "$NON_MOXXIE_VM_ID" ]; then
            print_info "No non-Moxxie VMs found, skipping test"
            return
        fi
        
        print_info "Testing with non-Moxxie VM ID: $NON_MOXXIE_VM_ID"
        
        # Try to stop (should be blocked)
        if api_call POST "/vms/$NON_MOXXIE_VM_ID/stop" 403 >/dev/null 2>&1; then
            print_pass "Stop operation correctly blocked on non-Moxxie VM"
        else
            print_fail "Stop operation was not blocked on non-Moxxie VM"
        fi
        
        # Try to delete (should be blocked)
        if api_call DELETE "/vms/$NON_MOXXIE_VM_ID" 403 >/dev/null 2>&1; then
            print_pass "Delete operation correctly blocked on non-Moxxie VM"
        else
            print_fail "Delete operation was not blocked on non-Moxxie VM"
        fi
    fi
}

# Test 7: Test force flag override
test_force_override() {
    print_test "Testing force flag override"
    
    if [ -z "$NON_MOXXIE_VM_ID" ]; then
        print_info "No non-Moxxie VM available, skipping force flag test"
        return
    fi
    
    # Try to add tag with force flag (should work)
    local tag_data='{"tag": "test-forced"}'
    if api_call POST "/vms/$NON_MOXXIE_VM_ID/tags" 200 "$tag_data" "force=true" >/dev/null 2>&1; then
        print_pass "Force flag correctly overrides safe mode"
        
        # Clean up - remove the tag
        api_call DELETE "/vms/$NON_MOXXIE_VM_ID/tags/test-forced" 204 "" "force=true" >/dev/null 2>&1
    else
        print_fail "Force flag did not override safe mode"
    fi
}

# Test 8: Check audit log
test_audit_log() {
    print_test "Checking audit log"
    
    if response=$(api_call GET "/safety/audit" 200); then
        entry_count=$(echo "$response" | jq '.entries | length')
        print_pass "Audit log endpoint works, found $entry_count entries"
        
        if [ "$entry_count" -gt 0 ]; then
            print_info "Recent audit entries:"
            echo "$response" | jq '.entries[:3]'
        fi
    else
        print_fail "Failed to get audit log"
    fi
}

# Test 9: Test different safety modes
test_safety_modes() {
    print_test "Current safety mode configuration"
    
    if response=$(api_call GET "/safety/config" 200); then
        mode=$(echo "$response" | jq -r '.mode')
        
        case $mode in
            "STRICT")
                print_info "Mode: STRICT - Only Moxxie VMs can be modified"
                ;;
            "PERMISSIVE")
                print_info "Mode: PERMISSIVE - Destructive operations blocked on non-Moxxie VMs"
                ;;
            "AUDIT")
                print_info "Mode: AUDIT - All operations allowed but logged"
                ;;
            *)
                print_fail "Unknown mode: $mode"
                ;;
        esac
    fi
}

# Test 10: Cleanup
cleanup() {
    print_test "Cleaning up test VM"
    
    if [ -n "$MOXXIE_VM_ID" ] && [ "$MOXXIE_VM_ID" != "9999" ]; then
        if api_call DELETE "/vms/$MOXXIE_VM_ID" 204 >/dev/null 2>&1; then
            print_pass "Cleaned up test VM"
        else
            print_info "Failed to clean up test VM (might already be deleted)"
        fi
    fi
}

# Main execution
main() {
    echo "Starting Safe Mode integration tests..."
    echo "API URL: $API_URL"
    echo ""
    
    # Check if service is running
    if ! curl -s "$API_URL/info" >/dev/null 2>&1; then
        echo -e "${RED}ERROR: Moxxie service is not running at $API_URL${NC}"
        echo "Please start the service with: ./mvnw quarkus:dev"
        exit 1
    fi
    
    # Run tests
    test_safety_status || true
    test_safety_config || true
    test_create_moxxie_vm || true
    test_vm_tags || true
    test_moxxie_vm_operations || true
    test_non_moxxie_vm_operations || true
    test_force_override || true
    test_audit_log || true
    test_safety_modes || true
    
    # Cleanup
    cleanup || true
    
    # Summary
    echo ""
    echo "=== Test Summary ==="
    echo -e "Tests passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "Tests failed: ${RED}$TESTS_FAILED${NC}"
    
    if [ $TESTS_FAILED -gt 0 ]; then
        echo -e "${RED}Some tests failed!${NC}"
        exit 1
    else
        echo -e "${GREEN}All tests passed!${NC}"
    fi
}

# Run main
main "$@"