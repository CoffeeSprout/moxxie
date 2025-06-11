#!/bin/bash

echo "Testing automatic authentication with Moxxie..."

# Set environment variables for your Proxmox instance
export MOXXIE_PROXMOX_URL="https://10.0.0.10:8006/api2/json"
export MOXXIE_PROXMOX_USERNAME="moxxie-readonly@pve"
export MOXXIE_PROXMOX_PASSWORD="moxxie-readonly@pve"

# Start the application
echo "Starting Moxxie with automatic authentication..."
./mvnw quarkus:dev -Dquarkus.http.host=127.0.0.1 &
PID=$!

# Wait for startup
echo "Waiting for application to start..."
sleep 10

echo -e "\n=== Testing endpoints without providing authentication ==="

echo -e "\n1. Testing /api/v1/info (no auth required):"
curl -s http://127.0.0.1:8080/api/v1/info | jq .

echo -e "\n2. Testing /api/v1/proxmox/discover (auth handled automatically):"
curl -s http://127.0.0.1:8080/api/v1/proxmox/discover | jq .

echo -e "\n3. Testing /api/v1/proxmox/nodes (auth handled automatically):"
curl -s http://127.0.0.1:8080/api/v1/proxmox/nodes | jq .

echo -e "\n4. Testing /api/v1/proxmox/vms (auth handled automatically):"
curl -s http://127.0.0.1:8080/api/v1/proxmox/vms | jq .

# Clean up
echo -e "\nStopping application..."
kill $PID
wait $PID 2>/dev/null

echo -e "\nTest complete!"