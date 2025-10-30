#!/bin/bash
# Ansible Integration Examples for Moxxie
# These examples demonstrate various ways to use Moxxie's Ansible integration features

set -e

MOXXIE_URL="${MOXXIE_URL:-http://localhost:8080}"

echo "=== Moxxie Ansible Integration Examples ==="
echo "Moxxie URL: $MOXXIE_URL"
echo ""

# =============================================================================
# EXAMPLE 1: Get Dynamic Inventory (JSON format)
# =============================================================================
echo "Example 1: Get dynamic inventory in JSON format"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?format=json" | jq .
echo ""

# =============================================================================
# EXAMPLE 2: Get Dynamic Inventory (INI format)
# =============================================================================
echo "Example 2: Get dynamic inventory in INI format"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?format=ini"
echo ""

# =============================================================================
# EXAMPLE 3: Filter by Client
# =============================================================================
echo "Example 3: Get inventory for specific client"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?client=acme&format=json" | jq .
echo ""

# =============================================================================
# EXAMPLE 4: Filter by Environment
# =============================================================================
echo "Example 4: Get production environment inventory"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?environment=prod&format=ini"
echo ""

# =============================================================================
# EXAMPLE 5: Filter by Multiple Tags
# =============================================================================
echo "Example 5: Get inventory with multiple tag filters"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?tags=env-prod,client-acme&format=json" | jq .
echo ""

# =============================================================================
# EXAMPLE 6: Filter by Node and Status
# =============================================================================
echo "Example 6: Get running VMs on specific node"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?node=hv7&status=running&format=ini"
echo ""

# =============================================================================
# EXAMPLE 7: Include Non-Moxxie VMs
# =============================================================================
echo "Example 7: Get all VMs including non-Moxxie managed"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?moxxieOnly=false&format=json" | jq '.groups.all'
echo ""

# =============================================================================
# EXAMPLE 8: Create VM with Ansible Callback (Webhook)
# =============================================================================
echo "Example 8: Create VM that triggers Ansible webhook"
echo "Prerequisites: Set MOXXIE_ANSIBLE_ENABLED=true and MOXXIE_ANSIBLE_WEBHOOK_URL"

# This assumes Ansible callbacks are configured
curl -X POST "${MOXXIE_URL}/api/v1/vms/cloud-init" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-vm-ansible",
    "node": "hv7",
    "imageSource": "local-zfs:base-9001-disk-0",
    "targetStorage": "local-zfs",
    "cores": 2,
    "memoryMB": 4096,
    "diskSizeGB": 50,
    "tags": "test,moxxie,ansible-test",
    "start": false,
    "cloudInitUser": "admin",
    "sshKeys": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIExample test@example.com",
    "ipConfigs": ["ip=dhcp"]
  }' | jq .
echo ""

# =============================================================================
# EXAMPLE 9: Use Inventory with Ansible Ping
# =============================================================================
echo "Example 9: Use dynamic inventory with Ansible ping"
echo "Creating temporary inventory script..."

cat > /tmp/moxxie-inventory.sh << 'EOF'
#!/bin/bash
curl -s "http://localhost:8080/api/v1/ansible/inventory?format=json&client=acme"
EOF

chmod +x /tmp/moxxie-inventory.sh

echo "Testing inventory with Ansible:"
# Uncomment to run actual Ansible command
# ansible all -i /tmp/moxxie-inventory.sh -m ping
echo "Command: ansible all -i /tmp/moxxie-inventory.sh -m ping"
echo ""

# =============================================================================
# EXAMPLE 10: Export Inventory to File
# =============================================================================
echo "Example 10: Export inventory to file"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?format=ini&client=acme" > /tmp/acme-inventory.ini
echo "Inventory saved to /tmp/acme-inventory.ini"
cat /tmp/acme-inventory.ini
echo ""

# =============================================================================
# EXAMPLE 11: Generate Group-Specific Inventory
# =============================================================================
echo "Example 11: Get inventory for Kubernetes workers only"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?tags=k8s-worker&format=json" | \
  jq '.groups["k8s-worker"]'
echo ""

# =============================================================================
# EXAMPLE 12: Check Host Variables
# =============================================================================
echo "Example 12: Inspect host variables for a specific VM"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?format=json" | \
  jq '._meta.hostvars | to_entries | .[0]'
echo ""

# =============================================================================
# EXAMPLE 13: List All Groups
# =============================================================================
echo "Example 13: List all available inventory groups"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?format=json" | \
  jq '.groups | keys'
echo ""

# =============================================================================
# EXAMPLE 14: Count VMs per Group
# =============================================================================
echo "Example 14: Count VMs per group"
curl -s "${MOXXIE_URL}/api/v1/ansible/inventory?format=json" | \
  jq '.groups | to_entries | map({group: .key, count: (.value.hosts | length)}) | sort_by(.count) | reverse'
echo ""

# =============================================================================
# EXAMPLE 15: Webhook Callback Test
# =============================================================================
echo "Example 15: Test webhook callback handler"
echo "This requires a webhook listener to be running"

cat > /tmp/webhook-test.py << 'EOF'
#!/usr/bin/env python3
from http.server import HTTPServer, BaseHTTPRequestHandler
import json

class WebhookHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        body = self.rfile.read(content_length)

        print("\n=== Webhook Received ===")
        print(json.dumps(json.loads(body), indent=2))

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"status": "received"}')

print("Starting webhook test server on http://localhost:8000")
print("Configure Moxxie with:")
print("  MOXXIE_ANSIBLE_WEBHOOK_URL=http://localhost:8000/webhook")
HTTPServer(('0.0.0.0', 8000), WebhookHandler).serve_forever()
EOF

chmod +x /tmp/webhook-test.py
echo "Webhook test handler created at /tmp/webhook-test.py"
echo "Run: python3 /tmp/webhook-test.py"
echo ""

# =============================================================================
# EXAMPLE 16: GitOps Workflow - VM Definitions
# =============================================================================
echo "Example 16: GitOps workflow with VM definitions"

cat > /tmp/vm-definitions.yml << 'EOF'
# VM Definitions for Client ACME - Production Environment
client: acme
environment: prod

vms:
  - name: acme-web-01
    node: hv7
    image: local-zfs:base-9001-disk-0
    storage: local-zfs
    cores: 4
    memory: 8192
    disk_size: 100
    tags: client-acme,env-prod,webserver,moxxie
    ssh_keys: ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIExample admin@acme.com
    ip_config: ip=10.0.0.100/24,gw=10.0.0.1
    start: true

  - name: acme-db-01
    node: hv8
    image: local-zfs:base-9001-disk-0
    storage: local-zfs
    cores: 8
    memory: 16384
    disk_size: 500
    tags: client-acme,env-prod,database,moxxie
    ssh_keys: ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIExample admin@acme.com
    ip_config: ip=10.0.0.101/24,gw=10.0.0.1
    start: true
EOF

echo "VM definitions created at /tmp/vm-definitions.yml"
cat /tmp/vm-definitions.yml
echo ""

# =============================================================================
# EXAMPLE 17: Provision VMs from Definitions
# =============================================================================
echo "Example 17: Provision VMs from YAML definitions"

cat > /tmp/provision-vms.sh << 'EOF'
#!/bin/bash
set -e

MOXXIE_URL="${MOXXIE_URL:-http://localhost:8080}"
VM_DEFS="${1:-vm-definitions.yml}"

echo "Provisioning VMs from $VM_DEFS..."

# Parse YAML and create VMs (requires yq)
if ! command -v yq &> /dev/null; then
    echo "Error: yq is required. Install with: brew install yq"
    exit 1
fi

yq '.vms[]' "$VM_DEFS" -o json | while IFS= read -r vm; do
    name=$(echo "$vm" | jq -r '.name')
    echo "Creating VM: $name"

    # Convert YAML format to Moxxie API format
    payload=$(echo "$vm" | jq '{
        name: .name,
        node: .node,
        imageSource: .image,
        targetStorage: .storage,
        cores: .cores,
        memoryMB: .memory,
        diskSizeGB: .disk_size,
        tags: .tags,
        sshKeys: .ssh_keys,
        ipConfigs: [.ip_config],
        start: .start
    }')

    # Create VM via Moxxie API
    response=$(curl -s -X POST "$MOXXIE_URL/api/v1/vms/cloud-init" \
        -H "Content-Type: application/json" \
        -d "$payload")

    echo "Response: $response" | jq .
    echo "---"
done

echo "All VMs provisioned. Waiting for Ansible callbacks..."
sleep 5

# Verify VMs are in inventory
echo "Checking inventory:"
curl -s "$MOXXIE_URL/api/v1/ansible/inventory?client=acme&format=ini"
EOF

chmod +x /tmp/provision-vms.sh
echo "Provisioning script created at /tmp/provision-vms.sh"
echo "Usage: /tmp/provision-vms.sh /tmp/vm-definitions.yml"
echo ""

# =============================================================================
# EXAMPLE 18: Complete GitOps Workflow
# =============================================================================
echo "Example 18: Complete GitOps workflow"

cat > /tmp/gitops-workflow.sh << 'EOF'
#!/bin/bash
# Complete GitOps workflow: Git -> Moxxie -> Ansible
set -e

MOXXIE_URL="${MOXXIE_URL:-http://localhost:8080}"
GIT_REPO="${GIT_REPO:-https://github.com/yourorg/vm-configs.git}"
BRANCH="${BRANCH:-main}"

echo "=== GitOps Workflow ==="
echo "1. Clone VM configurations from Git"
echo "2. Provision VMs via Moxxie"
echo "3. Moxxie triggers Ansible callbacks"
echo "4. Verify configuration with Ansible"
echo ""

# Step 1: Clone or pull repo
if [ ! -d "/tmp/vm-configs" ]; then
    git clone "$GIT_REPO" /tmp/vm-configs
else
    cd /tmp/vm-configs && git pull
fi

cd /tmp/vm-configs

# Step 2: Provision VMs
for vm_file in vms/**/*.yml; do
    echo "Processing: $vm_file"
    /tmp/provision-vms.sh "$vm_file"
done

# Step 3: Wait for Ansible callbacks to complete
echo "Waiting 60 seconds for Ansible configuration..."
sleep 60

# Step 4: Verify all VMs are configured
echo "Verifying VMs with Ansible..."
ansible all -i <(curl -s "$MOXXIE_URL/api/v1/ansible/inventory?format=json") -m ping

echo "GitOps workflow complete!"
EOF

chmod +x /tmp/gitops-workflow.sh
echo "GitOps workflow script created at /tmp/gitops-workflow.sh"
echo ""

# =============================================================================
# Summary
# =============================================================================
echo "=== Examples Summary ==="
echo "All example scripts have been created in /tmp/"
echo ""
echo "Files created:"
echo "  - /tmp/moxxie-inventory.sh        - Dynamic inventory fetcher"
echo "  - /tmp/acme-inventory.ini         - Sample exported inventory"
echo "  - /tmp/webhook-test.py            - Webhook test server"
echo "  - /tmp/vm-definitions.yml         - Sample VM definitions"
echo "  - /tmp/provision-vms.sh           - VM provisioning script"
echo "  - /tmp/gitops-workflow.sh         - Complete GitOps workflow"
echo ""
echo "Next steps:"
echo "1. Configure Ansible callbacks in Moxxie"
echo "2. Test dynamic inventory: ./tmp/moxxie-inventory.sh | jq ."
echo "3. Create VMs and verify callbacks work"
echo "4. Set up GitOps repo with VM definitions"
echo ""
echo "For more information, see: ANSIBLE_INTEGRATION.md"
