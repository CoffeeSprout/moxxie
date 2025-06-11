#!/bin/bash

echo "=== Safe Production Testing Script for Moxxie ==="
echo
echo "This script will help you safely test Moxxie with your production Proxmox cluster"
echo "using READ-ONLY credentials."
echo

# Check if the user has created read-only credentials
echo "Prerequisites:"
echo "1. You've created a read-only Proxmox user: moxxie-readonly@pve"
echo "2. You have the password for this user"
echo
read -p "Have you completed these steps? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    echo "Please create the read-only user first:"
    echo "  pveum user add moxxie-readonly@pve"
    echo "  pveum passwd moxxie-readonly@pve"
    echo "  pveum aclmod / -user moxxie-readonly@pve -role PVEAuditor"
    exit 1
fi

# Get Proxmox server details
echo
read -p "Enter your Proxmox server URL (e.g., https://192.168.1.100:8006): " PROXMOX_URL
export PROXMOX_URL="${PROXMOX_URL}/api2/json"

# Build the application
echo
echo "Building Moxxie..."
./mvnw clean package

# Run with safe-test profile
echo
echo "Starting Moxxie in SAFE TEST mode..."
echo "This will:"
echo "  - Run on localhost:8080"
echo "  - Enable Swagger UI at http://localhost:8080/swagger-ui/"
echo "  - Use READ-ONLY mode"
echo "  - Connect to: $PROXMOX_URL"
echo

java -Dquarkus.profile=safe-test \
     -DPROXMOX_URL="$PROXMOX_URL" \
     -jar target/moxxie-1.0.0-SNAPSHOT-runner.jar