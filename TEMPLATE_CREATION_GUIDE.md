# Template Creation Guide for Moxxie

This guide explains how to create VM templates from cloud images for use with Moxxie's template cloning functionality.

## Prerequisites

- SSH access to a Proxmox node (preferably storage01 where templates will be created)
- Access to shared NFS storage accessible to all nodes
- Cloud images for the desired operating systems

## Creating a Debian 12 Template

### 1. Download the Debian Cloud Image

SSH into the storage01 node where we'll create templates:

```bash
ssh root@storage01
cd /tmp

# Download Debian 12 cloud image
wget https://cloud.debian.org/cdimage/cloud/bookworm/latest/debian-12-generic-amd64.qcow2
```

### 2. Create the Template VM

```bash
# Create a new VM that will become our template
# Using ID 9001 for Debian template (you can adjust as needed)
qm create 9001 \
  --name debian-12-template \
  --memory 2048 \
  --cores 2 \
  --net0 virtio,bridge=vmbr0 \
  --scsihw virtio-scsi-pci
```

### 3. Import the Cloud Image

```bash
# Import the downloaded image to local-zfs on storage01
qm importdisk 9001 debian-12-generic-amd64.qcow2 local-zfs
```

### 4. Configure the VM

```bash
# Attach the imported disk
qm set 9001 --scsi0 local-zfs:vm-9001-disk-0

# Add cloud-init drive (use scsi2 for better compatibility)
qm set 9001 --scsi2 local-zfs:cloudinit

# Set boot order
qm set 9001 --boot c --bootdisk scsi0

# Enable QEMU agent
qm set 9001 --agent enabled=1

# Set display to serial console
qm set 9001 --serial0 socket --vga serial0

# Optional: Set CPU type to host for better performance
qm set 9001 --cpu host

# Optional: Add description
qm set 9001 --description "Debian 12 cloud-init template - Created $(date)"
```

### 5. Convert to Template

```bash
# Convert the VM to a template
qm template 9001

# Clean up the downloaded image
rm /tmp/debian-12-generic-amd64.qcow2
```

## Creating a Talos Template

### 1. Download the Talos Cloud Image

```bash
cd /tmp

# Get the latest Talos version
TALOS_VERSION=$(curl -s https://api.github.com/repos/siderolabs/talos/releases/latest | grep tag_name | cut -d '"' -f 4)

# Download Talos cloud-init image (adjust architecture if needed)
wget https://factory.talos.dev/image/${TALOS_VERSION}/nocloud-amd64.raw.xz

# Extract the image
xz -d nocloud-amd64.raw.xz

# Convert to qcow2 format
qemu-img convert -f raw -O qcow2 nocloud-amd64.raw talos-${TALOS_VERSION}-amd64.qcow2
```

### 2. Create the Talos Template

```bash
# Create VM for Talos template (using ID 9002)
qm create 9002 \
  --name talos-template \
  --memory 2048 \
  --cores 2 \
  --net0 virtio,bridge=vmbr0 \
  --scsihw virtio-scsi-pci

# Import the disk
qm importdisk 9002 talos-${TALOS_VERSION}-amd64.qcow2 local-zfs

# Configure the VM
qm set 9002 --scsi0 local-zfs:vm-9002-disk-0
qm set 9002 --scsi2 local-zfs:cloudinit
qm set 9002 --boot c --bootdisk scsi0
qm set 9002 --serial0 socket --vga serial0
qm set 9002 --cpu host
qm set 9002 --description "Talos ${TALOS_VERSION} cloud-init template - Created $(date)"

# Convert to template
qm template 9002

# Clean up
rm /tmp/nocloud-amd64.raw
rm /tmp/talos-${TALOS_VERSION}-amd64.qcow2
```

## Template Management Best Practices

### 1. Naming Convention

Use descriptive names that include:
- OS name and version
- Template identifier
- Creation date (in description)

Examples:
- `debian-12-template`
- `talos-v1.6.1-template`
- `ubuntu-22.04-template`

### 2. Template IDs

Reserve a range for templates:
- 9000-9099: OS templates
- 9001: Debian 12
- 9002: Talos
- 9003: Ubuntu 22.04
- etc.

### 3. Regular Updates

Templates should be updated monthly:

```bash
# Clone template to temporary VM
qm clone 9001 999 --name temp-update

# Start the VM
qm start 999

# Wait for boot and perform updates
# For Debian:
ssh debian@<vm-ip>
sudo apt update && sudo apt upgrade -y
sudo apt autoremove -y
sudo cloud-init clean --logs
sudo shutdown -h now

# Convert back to template
qm template 999

# Remove old template and rename
qm destroy 9001
mv /etc/pve/qemu-server/999.conf /etc/pve/qemu-server/9001.conf
```

### 4. Verification

After creating a template, verify it works:

```bash
# Test clone
qm clone 9001 100 --name test-vm --full 1

# Check the VM was created
qm config 100

# Clean up test
qm destroy 100
```

## Using Templates with Moxxie

Once templates are created, you can use them with Moxxie's existing cloud-init API by referencing the template's disk:

```bash
# Create VM from Debian template using import-from
curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
  -H "Content-Type: application/json" \
  -d '{
    "vmid": 100,
    "name": "web-server-01",
    "node": "hv7",
    "cores": 4,
    "memoryMB": 8192,
    "imageSource": "local-zfs:9001/base-9001-disk-0.raw",
    "targetStorage": "local-zfs",
    "diskSizeGB": 50,
    "cloudInitUser": "debian",
    "sshKeys": "ssh-rsa AAAAB3...",
    "network": {
      "bridge": "vmbr0"
    },
    "ipConfig": "ip=192.168.1.100/24,gw=192.168.1.1",
    "start": true,
    "tags": "web,env-prod,client-acme"
  }'

# Create VM from Talos template
curl -X POST http://localhost:8080/api/v1/vms/cloud-init \
  -H "Content-Type: application/json" \
  -d '{
    "vmid": 101,
    "name": "k8s-control-01",
    "node": "hv7",
    "cores": 4,
    "memoryMB": 8192,
    "imageSource": "local-zfs:9002/base-9002-disk-0.raw",
    "targetStorage": "local-zfs",
    "diskSizeGB": 50,
    "network": {
      "bridge": "vmbr0"
    },
    "ipConfig": "ip=192.168.1.101/24,gw=192.168.1.1",
    "start": true,
    "tags": "k8s-controlplane,env-prod"
  }'
```

**Important**: The `imageSource` must reference the template's disk path in the format:
- `storage:vmid/base-vmid-disk-N.ext`
- For template 9001 on local-zfs: `local-zfs:9001/base-9001-disk-0.raw`
- For template 9002 on local-zfs: `local-zfs:9002/base-9002-disk-0.raw`

## Important Notes

### Talos Specifics
- Talos only uses cloud-init for network configuration
- Machine configuration is applied post-boot using talosctl
- Do not set user/password for Talos VMs

### Storage Considerations
- Templates are created on storage01 node using local-zfs storage
- When creating VMs from templates, they must be created on the same node (storage01)
- After creation, VMs can be migrated to other nodes
- Use thin provisioning for templates to save space

### Migration Requirements
- VMs must be created on the same node as the template when using import-from
- After creation, use Moxxie's migration API to move VMs to target nodes:
  ```bash
  curl -X POST http://localhost:8080/api/v1/vms/100/migrate \
    -H "Content-Type: application/json" \
    -d '{
      "targetNode": "hv8",
      "online": true,
      "withLocalDisks": false
    }'
  ```
- Ensure target node has access to the specified storage

### Quick VM Creation Examples

**Create 2 empty VMs from templates (command line):**

```bash
# Create Debian VM on same node as template (required for import-from)
qm create 100 --name test-debian --cores 2 --memory 4096 --net0 virtio,bridge=vmbr0
qm set 100 --scsi0 local-zfs:0,import-from=local-zfs:9001/base-9001-disk-0.raw,discard=on
qm set 100 --scsi2 local-zfs:cloudinit --boot order=scsi0
qm set 100 --ipconfig0 ip=dhcp --agent enabled=1

# Create Talos VM 
qm create 101 --name test-talos --cores 2 --memory 4096 --net0 virtio,bridge=vmbr0
qm set 101 --scsi0 local-zfs:0,import-from=local-zfs:9002/base-9002-disk-0.raw
qm set 101 --scsi2 local-zfs:cloudinit --boot order=scsi0
qm set 101 --ipconfig0 ip=dhcp
```