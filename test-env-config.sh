#!/bin/bash

# Test environment variable configuration
echo "Testing Moxxie with environment variables..."

# Set environment variables
export MOXXIE_INSTANCE_ID="moxxie-prod-1"
export MOXXIE_LOCATION="datacenter-west"
export MOXXIE_ENVIRONMENT="production"
export MOXXIE_PROXMOX_URL="https://proxmox.example.com:8006/api2/json"
export MOXXIE_PROXMOX_USERNAME="admin@pve"
export MOXXIE_PROXMOX_PASSWORD="${MOXXIE_PROXMOX_PASSWORD:-changeme}"
export MOXXIE_PROXMOX_VERIFY_SSL="true"
export MOXXIE_API_AUTH_ENABLED="true"
export MOXXIE_API_KEY="${MOXXIE_API_KEY:-test-api-key}"
export MOXXIE_API_READ_ONLY="true"

# Run the application in dev mode
echo "Starting Moxxie with the following configuration:"
echo "- Instance ID: $MOXXIE_INSTANCE_ID"
echo "- Location: $MOXXIE_LOCATION"
echo "- Environment: $MOXXIE_ENVIRONMENT"
echo "- Proxmox URL: $MOXXIE_PROXMOX_URL"
echo "- Proxmox Username: $MOXXIE_PROXMOX_USERNAME"
echo "- API Auth Enabled: $MOXXIE_API_AUTH_ENABLED"
echo "- Read-only Mode: $MOXXIE_API_READ_ONLY"

# Start in dev mode
./mvnw quarkus:dev