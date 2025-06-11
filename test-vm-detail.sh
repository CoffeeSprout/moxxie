#!/bin/bash

# Configuration
BASE_URL="http://localhost:8080/api/v1"
VM_ID="${1:-8200}"

echo "=== Testing VM Detail Endpoint ==="
echo "VM ID: $VM_ID"
echo ""

# Test basic VM info
echo "1. Basic VM Info:"
curl -s "${BASE_URL}/vms/${VM_ID}" | jq '.' || echo "Failed to get basic VM info"
echo ""

# Test detailed VM info with network
echo "2. Detailed VM Info with Network:"
curl -s "${BASE_URL}/vms/${VM_ID}/detail" | jq '.' || echo "Failed to get detailed VM info"
echo ""

# If we got network info, check VLAN assignments
echo "3. Checking VLAN assignments (100-200):"
curl -s "${BASE_URL}/sdn/vlan-assignments?rangeStart=100&rangeEnd=200&allocatedOnly=true" | jq '.' || echo "Failed to get VLAN assignments"