# VM Network Details

## Overview
When querying a VM (e.g., VM 8200), Moxxie now provides comprehensive network information through the detailed VM endpoint.

## Endpoints

### Basic VM Information
```
GET /api/v1/vms/8200
```

Returns basic VM information without network details:
```json
{
  "vmid": 8200,
  "name": "web-server-01",
  "node": "hv6",
  "status": "running",
  "cpus": 4,
  "maxmem": 8589934592,
  "maxdisk": 107374182400,
  "uptime": 3600,
  "type": "qemu"
}
```

### Detailed VM Information with Network
```
GET /api/v1/vms/8200/detail
```

Returns comprehensive VM information including network configuration:
```json
{
  "vmid": 8200,
  "name": "web-server-01",
  "node": "hv6",
  "status": "running",
  "cpus": 4,
  "maxmem": 8589934592,
  "maxdisk": 107374182400,
  "uptime": 3600,
  "type": "qemu",
  "networkInterfaces": [
    {
      "name": "net0",
      "macAddress": "BC:24:11:5E:7D:2C",
      "bridge": "vmbr0",
      "vlan": 101,
      "model": "virtio",
      "firewall": false,
      "rawConfig": "virtio=BC:24:11:5E:7D:2C,bridge=vmbr0,tag=101"
    }
  ],
  "tags": ["moxxie", "production"],
  "config": {
    "cores": 4,
    "memory": 8192,
    "net0": "virtio=BC:24:11:5E:7D:2C,bridge=vmbr0,tag=101",
    "scsi0": "local-lvm:vm-8200-disk-0,size=100G",
    "scsihw": "virtio-scsi-pci",
    "boot": "c",
    "ostype": "l26",
    "name": "web-server-01"
  }
}
```

## Network Interface Details

Each network interface includes:
- **name**: Interface identifier (net0, net1, etc.)
- **macAddress**: MAC address of the interface
- **bridge**: Network bridge the interface is connected to
- **vlan**: VLAN tag if configured (null if no VLAN)
- **model**: Network card model (usually "virtio")
- **firewall**: Whether firewall is enabled for this interface
- **rawConfig**: Raw Proxmox configuration string

## Cross-Referencing with SDN

To understand which client/SDN VNet a VM belongs to:

1. Get VM network details:
```bash
GET /api/v1/vms/8200/detail
```

2. Check the VLAN tag (e.g., 101)

3. Query VLAN assignments to find the owner:
```bash
GET /api/v1/sdn/vlan-assignments?rangeStart=101&rangeEnd=101
```

Response:
```json
[
  {
    "vlanTag": 101,
    "clientId": "client1",
    "vnetIds": ["client1-webapp", "client1-api"],
    "status": "allocated",
    "description": null
  }
]
```

This shows VM 8200 is using VLAN 101, which is allocated to "client1".

## Use Cases

1. **Network Troubleshooting**: See exact network configuration including VLAN tags and bridges
2. **Multi-Tenant Verification**: Confirm VMs are using correct VLANs for client isolation
3. **Inventory Management**: Track which VMs belong to which clients based on VLAN usage
4. **Security Auditing**: Verify network isolation and firewall settings