#!/bin/bash

# Test script for Node management endpoints

echo "Testing Node Management Endpoints"
echo "================================="

# Test 1: List all nodes
echo -e "\n1. Testing GET /api/v1/nodes"
curl -X GET http://localhost:8080/api/v1/nodes \
  -H "Accept: application/json" | jq .

# Test 2: Get node status (replace 'pve' with actual node name from list above)
echo -e "\n2. Testing GET /api/v1/nodes/{nodeName}/status"
curl -X GET http://localhost:8080/api/v1/nodes/pve/status \
  -H "Accept: application/json" | jq .

# Test 3: Get node resources (replace 'pve' with actual node name from list above)
echo -e "\n3. Testing GET /api/v1/nodes/{nodeName}/resources"
curl -X GET http://localhost:8080/api/v1/nodes/pve/resources \
  -H "Accept: application/json" | jq .

# Test OpenAPI documentation
echo -e "\n4. Checking OpenAPI documentation"
echo "Visit: http://localhost:8080/swagger-ui/"