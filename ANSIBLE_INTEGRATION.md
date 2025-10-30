# Ansible Integration Guide

This guide covers Moxxie's Ansible integration features for CaffeineStacks managed VM deployments.

## Table of Contents

- [Overview](#overview)
- [Dynamic Inventory](#dynamic-inventory)
- [Post-Creation Callbacks](#post-creation-callbacks)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [Troubleshooting](#troubleshooting)

## Overview

Moxxie provides two main Ansible integration features:

1. **Dynamic Inventory Export** - Generate Ansible inventory from Moxxie-managed VMs
2. **Post-Creation Callbacks** - Automatically trigger Ansible playbooks after VM creation

These features enable GitOps workflows where:
- VMs are provisioned via Moxxie API
- Ansible automatically configures new VMs
- Inventory is always up-to-date with VM state

## Dynamic Inventory

### API Endpoint

```
GET /api/v1/ansible/inventory
```

### Features

- **Multiple Formats**: JSON (default) or INI format
- **Advanced Filtering**: By tags, client, environment, node, status
- **Automatic Grouping**: VMs grouped by tags, node, and status
- **Host Variables**: Includes VM metadata (vmid, node, cores, memory, tags)
- **Moxxie-Only Mode**: Filter to only Moxxie-managed VMs (default: true)

### JSON Format (Default)

Returns Ansible dynamic inventory JSON with `_meta` section for optimal performance:

```bash
curl http://localhost:8080/api/v1/ansible/inventory?format=json | jq .
```

**Response Structure:**

```json
{
  "_meta": {
    "hostvars": {
      "web-server-01": {
        "ansible_host": null,
        "vmid": 8200,
        "node": "hv7",
        "status": "running",
        "tags": ["client-acme", "env-prod", "moxxie"],
        "cores": 4,
        "memory": 8192,
        "name": "web-server-01",
        "moxxie_managed": true,
        "custom": {}
      }
    }
  },
  "groups": {
    "client-acme": {
      "hosts": ["web-server-01", "db-server-01"],
      "children": null,
      "vars": {}
    },
    "env-prod": {
      "hosts": ["web-server-01", "db-server-01"],
      "children": null,
      "vars": {}
    },
    "node_hv7": {
      "hosts": ["web-server-01"],
      "children": null,
      "vars": {}
    },
    "status_running": {
      "hosts": ["web-server-01", "db-server-01"],
      "children": null,
      "vars": {}
    },
    "all": {
      "hosts": ["web-server-01", "db-server-01"],
      "children": null,
      "vars": {}
    }
  }
}
```

### INI Format

Returns traditional INI inventory format:

```bash
curl "http://localhost:8080/api/v1/ansible/inventory?format=ini"
```

**Response Example:**

```ini
[client-acme]
web-server-01 ansible_host=10.0.0.100 vmid=8200 node=hv7 status=running
db-server-01 ansible_host=10.0.0.101 vmid=8201 node=hv8 status=running

[env-prod]
web-server-01 ansible_host=10.0.0.100 vmid=8200 node=hv7 status=running
db-server-01 ansible_host=10.0.0.101 vmid=8201 node=hv8 status=running

[node_hv7]
web-server-01 ansible_host=10.0.0.100 vmid=8200 node=hv7 status=running

[status_running]
web-server-01 ansible_host=10.0.0.100 vmid=8200 node=hv7 status=running
db-server-01 ansible_host=10.0.0.101 vmid=8201 node=hv8 status=running

[all]
web-server-01
db-server-01
```

### Filtering Options

All filters can be combined:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `format` | Output format: `json` or `ini` | `format=ini` |
| `tags` | Filter by tags (comma-separated, AND logic) | `tags=env-prod,client-acme` |
| `client` | Filter by client (convenience for `client-<name>` tag) | `client=acme` |
| `environment` | Filter by environment (convenience for `env-<env>` tag) | `environment=prod` |
| `node` | Filter by Proxmox node name | `node=hv7` |
| `status` | Filter by VM status | `status=running` |
| `moxxieOnly` | Include only Moxxie-managed VMs (default: true) | `moxxieOnly=false` |

**Examples:**

```bash
# Get all production VMs for client 'acme'
curl "http://localhost:8080/api/v1/ansible/inventory?client=acme&environment=prod"

# Get INI inventory for all running VMs on node hv7
curl "http://localhost:8080/api/v1/ansible/inventory?format=ini&node=hv7&status=running"

# Get all VMs including non-Moxxie managed
curl "http://localhost:8080/api/v1/ansible/inventory?moxxieOnly=false"

# Complex filter: prod VMs for acme client with specific tags
curl "http://localhost:8080/api/v1/ansible/inventory?tags=env-prod,client-acme,k8s-worker"
```

### Using with Ansible

#### Option 1: Dynamic Inventory Script

Create a script to fetch inventory:

```bash
#!/bin/bash
# inventory.sh
curl -s "http://localhost:8080/api/v1/ansible/inventory?client=acme&format=json"
```

Make it executable and use with Ansible:

```bash
chmod +x inventory.sh
ansible all -i inventory.sh -m ping
```

#### Option 2: Inventory Plugin (Recommended)

Create `moxxie_inventory.yml`:

```yaml
plugin: community.general.json_uri
uri: http://localhost:8080/api/v1/ansible/inventory?format=json&client=acme
```

Use with Ansible:

```bash
ansible-inventory -i moxxie_inventory.yml --graph
ansible-playbook -i moxxie_inventory.yml site.yml
```

#### Option 3: Refresh Script

For static inventory with periodic refresh:

```bash
#!/bin/bash
# refresh-inventory.sh
curl "http://localhost:8080/api/v1/ansible/inventory?format=ini&client=acme" > inventory/moxxie.ini
```

Add to cron:

```
*/5 * * * * /path/to/refresh-inventory.sh
```

### Automatic Grouping

VMs are automatically grouped by:

1. **Tags** - Each tag becomes a group
   - Example: `client-acme`, `env-prod`, `k8s-worker`
   - Sanitized for Ansible (special chars replaced with `_`)

2. **Node** - Groups by Proxmox node
   - Format: `node_<nodename>`
   - Example: `node_hv7`, `node_hv8`

3. **Status** - Groups by VM status
   - Format: `status_<status>`
   - Example: `status_running`, `status_stopped`

4. **All** - Special group containing all hosts

### Host Variables

Each host includes these variables in `hostvars`:

| Variable | Description | Example |
|----------|-------------|---------|
| `ansible_host` | Primary IP address (future: from QEMU agent) | `10.0.0.100` |
| `vmid` | Proxmox VM ID | `8200` |
| `node` | Proxmox node name | `hv7` |
| `status` | VM status | `running` |
| `tags` | List of tags | `["env-prod", "moxxie"]` |
| `cores` | CPU cores | `4` |
| `memory` | Memory in MB | `8192` |
| `name` | VM name | `web-server-01` |
| `moxxie_managed` | Moxxie-managed flag | `true` |
| `custom` | Custom metadata (future) | `{}` |

**Note**: `ansible_host` is currently `null` as it requires QEMU guest agent integration. For now, use hostnames or configure SSH in your playbooks.

## Post-Creation Callbacks

### Overview

Moxxie can automatically trigger Ansible playbooks after VM creation, enabling fully automated provisioning workflows.

### Callback Methods

Three callback methods are supported:

1. **Ansible Tower/AWX** - Launch job templates via API
2. **Generic Webhook** - POST event to any webhook endpoint
3. **Direct Execution** - Future: Execute ansible-playbook directly

### Configuration

Enable callbacks in `application.properties` or via environment variables:

```properties
# Enable Ansible callbacks
moxxie.ansible.enabled=true

# Callback type: tower, awx, or webhook
moxxie.ansible.callback-type=webhook

# Default playbook to execute
moxxie.ansible.default-playbook=site.yml

# Retry configuration
moxxie.ansible.max-retries=3
moxxie.ansible.retry-delay-seconds=5
moxxie.ansible.timeout-seconds=30
```

### Method 1: Ansible Tower/AWX

Launch job templates in Ansible Tower or AWX:

```properties
moxxie.ansible.callback-type=tower

# Tower configuration
moxxie.ansible.tower.url=https://tower.example.com
moxxie.ansible.tower.token=your-api-token
moxxie.ansible.tower.job-template-id=42
```

**How it works:**

1. VM is created via Moxxie API
2. Moxxie calls Tower API: `POST /api/v2/job_templates/42/launch/`
3. Job launches with extra vars:
   ```json
   {
     "moxxie_vm_id": 8200,
     "moxxie_vm_name": "web-server-01",
     "moxxie_vm_node": "hv7",
     "moxxie_playbook": "site.yml"
   }
   ```
4. Tower executes playbook on new VM

**Environment Variables:**

```bash
export MOXXIE_ANSIBLE_ENABLED=true
export MOXXIE_ANSIBLE_CALLBACK_TYPE=tower
export MOXXIE_ANSIBLE_TOWER_URL=https://tower.example.com
export MOXXIE_ANSIBLE_TOWER_TOKEN=your-token
export MOXXIE_ANSIBLE_TOWER_JOB_TEMPLATE_ID=42
```

### Method 2: Generic Webhook

POST VM creation events to any webhook endpoint:

```properties
moxxie.ansible.callback-type=webhook
moxxie.ansible.webhook.url=https://automation.example.com/webhooks/vm-created
moxxie.ansible.webhook.token=optional-bearer-token
```

**Webhook Payload:**

```json
{
  "event": "vm_created",
  "vm": {
    "id": 8200,
    "name": "web-server-01",
    "node": "hv7",
    "status": "running",
    "tags": ["client-acme", "env-prod"]
  },
  "playbook": "site.yml",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Authentication:**

Optional bearer token authentication:

```bash
export MOXXIE_ANSIBLE_WEBHOOK_TOKEN=secret-token
```

Token sent as: `Authorization: Bearer secret-token`

### Method 3: AWX (Same as Tower)

AWX uses the same configuration as Tower:

```properties
moxxie.ansible.callback-type=awx
# ... same tower.* configuration
```

### Callback Behavior

- **Asynchronous** - Callbacks run in background, don't block VM creation
- **Retry Logic** - Exponential backoff retry on failure (configurable)
- **Failure Handling** - VM creation succeeds even if callback fails
- **Logging** - All callback attempts logged with detailed error messages

### Error Handling

If a callback fails:

1. **VM Creation Succeeds** - VM is created regardless of callback status
2. **Retry Attempts** - Callback retried up to `max-retries` times
3. **Exponential Backoff** - Delay doubles between retries (5s, 10s, 20s...)
4. **Error Logging** - Failure logged at ERROR level with stack trace
5. **No Rollback** - VM is not deleted if callback fails

**Log Example:**

```
ERROR Failed to trigger Ansible callback for VM 8200, but VM creation succeeded
Caused by: java.io.IOException: Webhook returned status 500: Internal Server Error
```

## Configuration

### Full Configuration Reference

```properties
# =============================================================================
# Ansible Integration Configuration
# =============================================================================

# Enable Ansible callbacks after VM creation
moxxie.ansible.enabled=${MOXXIE_ANSIBLE_ENABLED:false}

# Callback type: tower, awx, or webhook
moxxie.ansible.callback-type=${MOXXIE_ANSIBLE_CALLBACK_TYPE:webhook}

# Default playbook to execute if not specified
moxxie.ansible.default-playbook=${MOXXIE_ANSIBLE_DEFAULT_PLAYBOOK:site.yml}

# Ansible Tower/AWX Configuration
# Base URL (e.g., https://tower.example.com)
moxxie.ansible.tower.url=${MOXXIE_ANSIBLE_TOWER_URL:}
# API token for authentication
moxxie.ansible.tower.token=${MOXXIE_ANSIBLE_TOWER_TOKEN:}
# Job template ID to launch
moxxie.ansible.tower.job-template-id=${MOXXIE_ANSIBLE_TOWER_JOB_TEMPLATE_ID:}

# Generic Webhook Configuration
# Webhook URL for callbacks
moxxie.ansible.webhook.url=${MOXXIE_ANSIBLE_WEBHOOK_URL:}
# Optional bearer token for authentication
moxxie.ansible.webhook.token=${MOXXIE_ANSIBLE_WEBHOOK_TOKEN:}

# Retry Configuration
# Maximum retry attempts for failed callbacks
moxxie.ansible.max-retries=${MOXXIE_ANSIBLE_MAX_RETRIES:3}
# Initial delay between retries in seconds (uses exponential backoff)
moxxie.ansible.retry-delay-seconds=${MOXXIE_ANSIBLE_RETRY_DELAY:5}
# Timeout for callback requests in seconds
moxxie.ansible.timeout-seconds=${MOXXIE_ANSIBLE_TIMEOUT:30}
```

### Environment Variables

All configuration can be set via environment variables:

```bash
# Enable callbacks
export MOXXIE_ANSIBLE_ENABLED=true
export MOXXIE_ANSIBLE_CALLBACK_TYPE=webhook
export MOXXIE_ANSIBLE_DEFAULT_PLAYBOOK=site.yml

# Webhook configuration
export MOXXIE_ANSIBLE_WEBHOOK_URL=https://automation.example.com/webhook
export MOXXIE_ANSIBLE_WEBHOOK_TOKEN=secret-token

# Tower/AWX configuration
export MOXXIE_ANSIBLE_TOWER_URL=https://tower.example.com
export MOXXIE_ANSIBLE_TOWER_TOKEN=tower-api-token
export MOXXIE_ANSIBLE_TOWER_JOB_TEMPLATE_ID=42

# Retry settings
export MOXXIE_ANSIBLE_MAX_RETRIES=3
export MOXXIE_ANSIBLE_RETRY_DELAY=5
export MOXXIE_ANSIBLE_TIMEOUT=30
```

## Usage Examples

### Example 1: Basic Dynamic Inventory

Get inventory for all Moxxie-managed VMs:

```bash
curl http://localhost:8080/api/v1/ansible/inventory | jq . > inventory.json
ansible-playbook -i inventory.json site.yml
```

### Example 2: Client-Specific Inventory

Get inventory for specific client:

```bash
# Using convenience parameter
curl "http://localhost:8080/api/v1/ansible/inventory?client=acme" > acme-inventory.json

# Or using tags
curl "http://localhost:8080/api/v1/ansible/inventory?tags=client-acme" > acme-inventory.json
```

### Example 3: Environment-Specific Deployment

Deploy to production VMs only:

```bash
curl "http://localhost:8080/api/v1/ansible/inventory?environment=prod&format=ini" > prod.ini
ansible-playbook -i prod.ini deploy-prod.yml
```

### Example 4: Webhook-Based Automation

Set up webhook receiver to run Ansible:

```bash
# Start webhook listener (example using Python)
python3 -m http.server 8000 &

# Configure Moxxie to send webhooks
export MOXXIE_ANSIBLE_ENABLED=true
export MOXXIE_ANSIBLE_CALLBACK_TYPE=webhook
export MOXXIE_ANSIBLE_WEBHOOK_URL=http://localhost:8000/webhook
```

**Webhook Handler (Python example):**

```python
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import subprocess

class WebhookHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        body = self.rfile.read(content_length)
        event = json.loads(body)

        if event['event'] == 'vm_created':
            vm = event['vm']
            print(f"VM created: {vm['name']} (ID: {vm['id']})")

            # Run Ansible playbook
            subprocess.run([
                'ansible-playbook',
                '-i', 'moxxie-inventory.sh',
                '--limit', vm['name'],
                'site.yml'
            ])

        self.send_response(200)
        self.end_headers()

server = HTTPServer(('0.0.0.0', 8000), WebhookHandler)
server.serve_forever()
```

### Example 5: Tower/AWX Integration

Configure Tower job template:

1. **Create Job Template in Tower:**
   - Name: "Configure New VM"
   - Inventory: Moxxie Dynamic Inventory
   - Playbook: `site.yml`
   - Extra Variables: (will be provided by Moxxie)

2. **Configure Moxxie:**

```bash
export MOXXIE_ANSIBLE_ENABLED=true
export MOXXIE_ANSIBLE_CALLBACK_TYPE=tower
export MOXXIE_ANSIBLE_TOWER_URL=https://tower.example.com
export MOXXIE_ANSIBLE_TOWER_TOKEN=your-token
export MOXXIE_ANSIBLE_TOWER_JOB_TEMPLATE_ID=42
```

3. **Create VM:**

```bash
curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
  -H "Content-Type: application/json" \
  -d '{
    "name": "web-server-01",
    "node": "hv7",
    "imageSource": "local-zfs:base-9001-disk-0",
    "targetStorage": "local-zfs",
    "cores": 4,
    "memoryMB": 8192,
    "tags": "client-acme,env-prod,moxxie",
    "start": true
  }'
```

Tower job launches automatically with:

```json
{
  "moxxie_vm_id": 8200,
  "moxxie_vm_name": "web-server-01",
  "moxxie_vm_node": "hv7",
  "moxxie_playbook": "site.yml"
}
```

### Example 6: GitOps Workflow

Complete GitOps workflow with Moxxie + Ansible:

**1. VM Definitions in Git (`vms/acme/prod.yml`):**

```yaml
vms:
  - name: web-server-01
    node: hv7
    image: "local-zfs:base-9001-disk-0"
    cores: 4
    memory: 8192
    tags: "client-acme,env-prod,moxxie"

  - name: db-server-01
    node: hv8
    image: "local-zfs:base-9001-disk-0"
    cores: 8
    memory: 16384
    tags: "client-acme,env-prod,moxxie"
```

**2. Provisioning Script:**

```bash
#!/bin/bash
# provision-vms.sh
set -e

# Read VM definitions and create VMs
yq '.vms[]' vms/acme/prod.yml -o json | while read vm; do
  curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
    -H "Content-Type: application/json" \
    -d "$vm"
done

# Wait for VMs to be created (Moxxie triggers Ansible automatically)
echo "VMs created. Ansible callbacks triggered automatically."

# Optional: Verify all VMs are configured
sleep 60
ansible all -i <(curl -s "http://localhost:8080/api/v1/ansible/inventory?client=acme") -m ping
```

**3. CI/CD Pipeline (.gitlab-ci.yml):**

```yaml
stages:
  - validate
  - provision
  - configure

validate:
  script:
    - yq '.vms[]' vms/acme/prod.yml

provision:
  script:
    - ./provision-vms.sh
  only:
    - main

verify-ansible:
  script:
    - curl "http://localhost:8080/api/v1/ansible/inventory?client=acme" | jq .
    - ansible all -i <(curl -s "http://localhost:8080/api/v1/ansible/inventory?client=acme") -m ping
  only:
    - main
```

## Troubleshooting

### Dynamic Inventory Issues

**Problem: Empty inventory returned**

```bash
curl http://localhost:8080/api/v1/ansible/inventory
# Returns: {"_meta": {"hostvars": {}}, "groups": {"all": {"hosts": [], ...}}}
```

**Solutions:**

1. Check `moxxieOnly` parameter:
   ```bash
   # Try including non-Moxxie VMs
   curl "http://localhost:8080/api/v1/ansible/inventory?moxxieOnly=false"
   ```

2. Verify VMs have tags:
   ```bash
   curl http://localhost:8080/api/v1/vms | jq '.[] | {vmid, name, tags}'
   ```

3. Check tag filter:
   ```bash
   # Remove filters to see all VMs
   curl "http://localhost:8080/api/v1/ansible/inventory?moxxieOnly=false"
   ```

**Problem: `ansible_host` is null**

This is expected. QEMU guest agent integration is not yet implemented.

**Workarounds:**

1. Use hostnames in DNS
2. Set `ansible_host` in group_vars
3. Use IP addresses from VM config

### Callback Issues

**Problem: Callbacks not firing**

Check configuration:

```bash
# View current config
curl http://localhost:8080/q/health | jq .

# Check logs
tail -f quarkus-dev.log | grep "Ansible callback"
```

**Solutions:**

1. Verify callbacks are enabled:
   ```bash
   export MOXXIE_ANSIBLE_ENABLED=true
   ```

2. Check callback type configuration:
   ```bash
   export MOXXIE_ANSIBLE_CALLBACK_TYPE=webhook
   export MOXXIE_ANSIBLE_WEBHOOK_URL=http://your-webhook-url
   ```

3. Review logs for errors:
   ```
   ERROR Failed to trigger Ansible callback for VM 8200: Connection refused
   ```

**Problem: Tower/AWX callbacks failing**

Check Tower configuration:

```bash
# Test Tower API manually
curl -H "Authorization: Bearer $MOXXIE_ANSIBLE_TOWER_TOKEN" \
  https://tower.example.com/api/v2/job_templates/42/
```

**Common Issues:**

1. **Invalid token** - Token expired or incorrect
2. **Wrong job template ID** - Use integer ID, not name
3. **Tower unreachable** - Check network connectivity
4. **Missing permissions** - Token needs `execute` permission on template

**Problem: Webhook callbacks failing**

Check webhook endpoint:

```bash
# Test webhook manually
curl -X POST http://your-webhook-url \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $MOXXIE_ANSIBLE_WEBHOOK_TOKEN" \
  -d '{"event": "test"}'
```

**Common Issues:**

1. **Connection refused** - Webhook server not running
2. **401 Unauthorized** - Token mismatch
3. **500 Internal Error** - Webhook handler crashed
4. **Timeout** - Increase `moxxie.ansible.timeout-seconds`

### Logging

Enable detailed logging for troubleshooting:

```properties
# In application.properties
quarkus.log.category."com.coffeesprout.service.AnsibleInventoryService".level=DEBUG
quarkus.log.category."com.coffeesprout.service.AnsibleCallbackService".level=DEBUG
```

Or via environment:

```bash
export QUARKUS_LOG_CATEGORY__COM_COFFEESPROUT_SERVICE_ANSIBLEINVENTORYSERVICE__LEVEL=DEBUG
export QUARKUS_LOG_CATEGORY__COM_COFFEESPROUT_SERVICE_ANSIBLECALLBACKSERVICE__LEVEL=DEBUG
```

**Useful Log Patterns:**

```bash
# Find callback attempts
grep "Ansible callback" quarkus-dev.log

# Find callback failures
grep "callback failed" quarkus-dev.log | grep ERROR

# Find inventory requests
grep "Generating.*inventory" quarkus-dev.log

# Find retry attempts
grep "Retrying in" quarkus-dev.log
```

## Next Steps

Future enhancements planned:

1. **QEMU Agent Integration** - Populate `ansible_host` with actual IP addresses
2. **Custom Metadata** - Store arbitrary metadata per VM for use in playbooks
3. **Drift Detection** - Compare desired state in git vs actual VM state
4. **Git Webhook Integration** - Trigger reconciliation on git push events
5. **Direct Playbook Execution** - Run ansible-playbook directly from Moxxie
6. **Callback History** - Track callback executions in database
7. **Health Checks** - Scheduled health checks with Ansible
8. **Bulk Operations** - Apply playbooks to VM groups

## Related Documentation

- [API Examples](./API_EXAMPLES.md) - VM creation and management examples
- [CLAUDE.md](./CLAUDE.md) - Complete project documentation
- [API Specification](./API_SPECIFICATION.md) - Full API reference

## Support

For issues or questions:

1. Check logs: `tail -f quarkus-dev.log`
2. Review configuration: `application.properties`
3. Test connectivity: Verify Moxxie and webhook/Tower endpoints are reachable
4. Report issues: Create GitHub issue with logs and configuration (sanitize secrets!)
