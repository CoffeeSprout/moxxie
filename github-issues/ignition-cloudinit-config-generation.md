# Ignition and Cloud-Init Configuration Generation (Optional Feature)

## Overview
Add optional support for generating ignition configs (for FCOS/SCOS) and enhanced cloud-init configurations directly in Moxxie. This is an optional feature that helps users who want Moxxie to handle configuration generation rather than using external tools.

## Background
While Moxxie's primary role is resource provisioning, generating boot configurations can significantly simplify cluster deployments:
- **Ignition**: Required for Fedora/CentOS CoreOS (FCOS/SCOS) used by OKD
- **Cloud-init**: Standard for traditional Linux distributions
- **Convenience**: Avoid switching between multiple tools
- **Integration**: Tight coupling with VM provisioning workflow

## Scope
This is an **OPTIONAL** feature. Users can:
- Generate configs via Moxxie API
- Provide their own pre-generated configs
- Use external tools and reference the configs

## Requirements

### Ignition Support (FCOS/SCOS)
1. Generate bootstrap, master, and worker ignition configs
2. Support custom CA certificates and SSH keys
3. Network configuration in ignition format
4. File and systemd unit injection
5. Config validation before use

### Cloud-Init Enhancements
1. Template library for common scenarios
2. Network configuration generation
3. Package installation lists
4. User creation with SSH keys
5. Script execution support

### Integration
1. Store generated configs in Proxmox storage
2. Serve configs via built-in HTTP endpoint
3. Automatic cleanup after successful provisioning
4. Version control for config templates

## Implementation

### API Examples

#### Generate OKD Ignition Configs
```bash
curl -X POST http://localhost:8080/api/v1/configs/ignition/generate \
  -H "Content-Type: application/json" \
  -d '{
    "platform": "OKD",
    "version": "4.19",
    "cluster": {
      "name": "prod",
      "baseDomain": "example.com",
      "network": "10.1.107.0/24"
    },
    "sshKey": "ssh-ed25519 AAAAC3...",
    "pullSecret": "...",
    "nodeRoles": ["bootstrap", "master", "worker"]
  }'

# Response:
{
  "configs": {
    "bootstrap": "local-zfs:snippets/okd-prod-bootstrap.ign",
    "master": "local-zfs:snippets/okd-prod-master.ign",
    "worker": "local-zfs:snippets/okd-prod-worker.ign"
  },
  "servingUrl": "http://10.1.107.1:8080/configs/"
}
```

#### Generate Enhanced Cloud-Init
```bash
curl -X POST http://localhost:8080/api/v1/configs/cloudinit/generate \
  -H "Content-Type: application/json" \
  -d '{
    "template": "kubernetes-node",
    "hostname": "k8s-master-01",
    "network": {
      "interfaces": [{
        "name": "eth0",
        "addresses": ["10.1.107.11/24"],
        "gateway": "10.1.107.1"
      }]
    },
    "packages": ["docker", "kubeadm", "kubelet"],
    "users": [{
      "name": "admin",
      "sshKeys": ["ssh-ed25519 AAAAC3..."]
    }]
  }'

# Response:
{
  "userdata": "local-zfs:snippets/k8s-master-01-user.yaml",
  "metadata": "local-zfs:snippets/k8s-master-01-meta.yaml"
}
```

#### Use External Configs (No Generation)
```bash
curl -X POST http://localhost:8080/api/v1/vms \
  -H "Content-Type: application/json" \
  -d '{
    "vmId": 10710,
    "name": "okd-bootstrap",
    "bootMethod": "PXE",
    "ignitionConfig": "http://external-server/bootstrap.ign"
  }'
```

## Benefits
1. **Convenience**: One-stop shop for VM + config
2. **Consistency**: Validated, tested templates
3. **Integration**: Seamless workflow
4. **Optional**: Use only if needed
5. **Flexibility**: Support multiple config formats

## Limitations
- Not a full configuration management system
- Basic templates only, complex configs need external tools
- No ongoing config management (use Ansible, etc.)

## Success Criteria
1. ✅ Can generate valid ignition configs for OKD
2. ✅ Can generate cloud-init for common scenarios
3. ✅ Configs are properly stored and served
4. ✅ External configs still supported
5. ✅ Clear documentation on when to use this feature

## Related Issues
- PXE Boot Infrastructure Support
- Boot Method Selection API
- #78: ISO Boot Support