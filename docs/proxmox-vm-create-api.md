# Proxmox VE API: Create or Restore Virtual Machine

## Endpoint Information

**Path:** `/nodes/{node}/qemu`

**Description:** Create or restore a virtual machine.

**HTTP Method:** `POST /api2/json/nodes/{node}/qemu`

**CLI:** `pvesh create /nodes/{node}/qemu`

## Required Parameters

| Parameter | Type | Format | Description |
|-----------|------|--------|-------------|
| `node` | string | `<string>` | The cluster node name |
| `vmid` | integer | `<integer>` (100 - 999999999) | The (unique) ID of the VM |

## Core VM Configuration

### CPU & Memory
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `cores` | integer | 1 | `<integer>` (1 - N) | The number of cores per socket |
| `sockets` | integer | 1 | `<integer>` (1 - N) | The number of CPU sockets |
| `memory` | string | - | `[current=]<integer>` | Memory properties |
| `cpu` | string | - | `[[cputype=]<string>] [,flags=<+FLAG[;-FLAG...]>] [,hidden=<1|0>] [,hv-vendor-id=<vendor-id>] [,phys-bits=<8-64|host>] [,reported-model=<enum>]` | Emulated CPU type |
| `cpulimit` | number | 0 | `<number>` (0 - 128) | Limit of CPU usage. Value '0' indicates no CPU limit |
| `cpuunits` | integer | cgroup v1: 1024, cgroup v2: 100 | `<integer>` (1 - 262144) | CPU weight for a VM |
| `vcpus` | integer | 0 | `<integer>` (1 - N) | Number of hotplugged vcpus |

### Storage Devices
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `ide[n]` | string | - | - | Use volume as IDE hard disk or CD-ROM (n is 0 to 3) |
| `sata[n]` | string | - | - | Use volume as SATA hard disk or CD-ROM (n is 0 to 5) |
| `scsi[n]` | string | - | - | Use volume as SCSI hard disk or CD-ROM (n is 0 to 30) |
| `virtio[n]` | string | - | - | Use volume as VIRTIO hard disk (n is 0 to 15) |
| `cdrom` | string | - | `<volume>` | Alias for option -ide2 |
| `scsihw` | enum | lsi | `lsi | lsi53c810 | virtio-scsi-pci | virtio-scsi-single | megasas | pvscsi` | SCSI controller model |

### Network Configuration
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `net[n]` | string | - | `[model=]<enum> [,bridge=<bridge>] [,firewall=<1|0>] [,link_down=<1|0>] [,macaddr=<XX:XX:XX:XX:XX:XX>] [,mtu=<integer>] [,queues=<integer>] [,rate=<number>] [,tag=<integer>] [,trunks=<vlanid[;vlanid...]>] [,<model>=<macaddr>]` | Specify network devices |

## System Configuration

### BIOS & Boot
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `bios` | enum | seabios | `seabios | ovmf` | Select BIOS implementation |
| `boot` | string | - | `[[legacy=]<[acdn]{1,4}>] [,order=<device[;device...]>]` | Specify guest boot order |
| `bootdisk` | string | - | `pve-qm-bootdisk` | Enable booting from specified disk (deprecated) |
| `machine` | string | - | `[[type=]<machine type>] [,enable-s3=<1|0>] [,enable-s4=<1|0>] [,viommu=<intel|virtio>]` | Specify the QEMU machine |

### Operating System
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `ostype` | enum | - | `other | wxp | w2k | w2k3 | w2k8 | wvista | win7 | win8 | win10 | win11 | l24 | l26 | solaris` | Specify guest operating system |
| `arch` | enum | - | `x86_64 | aarch64` | Virtual processor architecture. Defaults to the host |

### Hardware Features
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `acpi` | boolean | 1 | `<boolean>` | Enable/disable ACPI |
| `kvm` | boolean | 1 | `<boolean>` | Enable/disable KVM hardware virtualization |
| `numa` | boolean | 0 | `<boolean>` | Enable/disable NUMA |
| `tablet` | boolean | 1 | `<boolean>` | Enable/disable the USB tablet device |
| `localtime` | boolean | - | `<boolean>` | Set the real time clock (RTC) to local time |

## Cloud-Init Configuration
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `ciuser` | string | - | `<string>` | User name to change ssh keys and password for |
| `cipassword` | string | - | `<string>` | Password for the cloud-init user |
| `cicustom` | string | - | `[meta=<volume>] [,network=<volume>] [,user=<volume>] [,vendor=<volume>]` | Specify custom files to replace automatically generated ones |
| `citype` | enum | - | `configdrive2 | nocloud | opennebula` | Specifies the cloud-init configuration format |
| `ciupgrade` | boolean | 1 | `<boolean>` | Do an automatic package upgrade after the first boot |
| `nameserver` | string | - | `<string>` | Sets DNS server IP address for a container |
| `searchdomain` | string | - | `<string>` | Sets DNS search domains for a container |
| `sshkeys` | string | - | `<string>` | Setup public SSH keys (one key per line, OpenSSH format) |
| `ipconfig[n]` | string | - | `[gw=<GatewayIPv4>] [,gw6=<GatewayIPv6>] [,ip=<IPv4Format/CIDR>] [,ip6=<IPv6Format/CIDR>]` | Specify IP addresses and gateways for the corresponding interface |

## Advanced Configuration

### Virtualization Features
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `agent` | string | - | `[enabled=]<1|0> [,freeze-fs-on-backup=<1|0>] [,fstrim_cloned_disks=<1|0>] [,type=<virtio|isa>]` | Enable/disable communication with the QEMU Guest Agent |
| `balloon` | integer | - | `<integer>` (0 - N) | Amount of target RAM for the VM in MiB |
| `hugepages` | enum | - | `any | 2 | 1024` | Enable/disable hugepages memory |
| `keephugepages` | boolean | 0 | `<boolean>` | Keep hugepages after VM shutdown |

### Security & Isolation
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `protection` | boolean | 0 | `<boolean>` | Sets the protection flag of the VM |
| `lock` | enum | - | `backup | clone | create | migrate | rollback | snapshot | snapshot-delete | suspending | suspended` | Lock/unlock the VM |

### Display & Audio
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `vga` | string | - | `[[type=]<enum>] [,clipboard=<vnc>] [,memory=<integer>]` | Configure the VGA Hardware |
| `audio0` | string | - | `device=<ich9-intel-hda|intel-hda|AC97> [,driver=<spice|none>]` | Configure a audio device |
| `keyboard` | enum | - | Various layouts | Keyboard layout for VNC server |

### Hardware Passthrough
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `hostpci[n]` | string | - | Complex format | Map host PCI devices into guest |
| `usb[n]` | string | - | `[[host=]<HOSTUSBDEVICE|spice>] [,mapping=<mapping-id>] [,usb3=<1|0>]` | Configure an USB device |
| `serial[n]` | string | - | `(/dev/.+|socket)` | Create a serial device inside the VM |

## Management & Lifecycle

### VM Lifecycle
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `start` | boolean | 0 | `<boolean>` | Start VM after it was created successfully |
| `onboot` | boolean | 0 | `<boolean>` | Specifies whether a VM will be started during system bootup |
| `autostart` | boolean | 0 | `<boolean>` | Automatic restart after crash (currently ignored) |
| `startup` | string | - | `[[order=]\d+] [,up=\d+] [,down=\d+]` | Startup and shutdown behavior |
| `reboot` | boolean | 1 | `<boolean>` | Allow reboot. If set to '0' the VM exit on reboot |

### Backup & Restore
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `archive` | string | - | `<string>` | The backup archive for restore operations |
| `force` | boolean | - | `<boolean>` | Allow to overwrite existing VM |
| `live-restore` | boolean | - | `<boolean>` | Start the VM immediately while importing or restoring |
| `bwlimit` | integer | datacenter/storage limit | `<integer>` (0 - N) | Override I/O bandwidth limit (in KiB/s) |

### Storage & Pool Management
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `storage` | string | - | `<storage ID>` | Default storage |
| `pool` | string | - | `<string>` | Add the VM to the specified pool |
| `vmstatestorage` | string | - | `<storage ID>` | Default storage for VM state volumes/files |

### Metadata
| Parameter | Type | Default | Format | Description |
|-----------|------|---------|--------|-------------|
| `name` | string | - | `<string>` | Set a name for the VM |
| `description` | string | - | `<string>` | Description for the VM |
| `tags` | string | - | `<string>` | Tags of the VM (meta information only) |
| `template` | boolean | 0 | `<boolean>` | Enable/disable Template |

## Storage Syntax

For storage devices (`ide[n]`, `sata[n]`, `scsi[n]`, `virtio[n]`):
- **Allocate new volume:** `STORAGE_ID:SIZE_IN_GiB`
- **Import from existing:** `STORAGE_ID:0` with `import-from` parameter
- **Use existing volume:** `<volume>`

## Required Permissions

- **VM Creation:** `VM.Allocate` permissions on `/vms/{vmid}` or on the VM pool `/pool/{pool}`
- **Restore:** `VM.Backup` permission (if VM already exists)
- **Disk Creation:** `Datastore.AllocateSpace` on any used storage
- **Network:** `SDN.Use` on any used bridge/vlan

## Return Value
Returns: `string` (task ID for asynchronous operation)