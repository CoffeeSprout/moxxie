# OKD Load Balancer VM Complete Configuration Guide

This document describes the complete configuration of the dual-homed load balancer VM that serves as the network gateway, PXE server, DNS/DHCP server, and HAProxy load balancer for the OKD 4.19 cluster.

## Infrastructure Overview

The load balancer VM (ID: 500) serves multiple critical functions:
- **Network Gateway**: NAT router between public (VLAN 3) and private (VLAN 107) networks
- **DHCP/DNS Server**: dnsmasq providing network services to cluster nodes
- **PXE Boot Server**: TFTP and HTTP services for network-based OS installation
- **Load Balancer**: HAProxy distributing traffic to cluster nodes
- **Firewall**: nftables providing security and traffic filtering

```
Public Network (VLAN 3)          Private Cluster Network (VLAN 107)
185.173.163.42/24               10.1.107.1/24
        │                               │
        ├── eth0 ──────┐       ┌────── eth1 ──┤
                       │       │               │
                  ┌────▼───────▼────┐         │
                  │  Load Balancer  │         │
                  │      VM 500     │         │
                  │                 │         │
                  │ • HAProxy       │         │
                  │ • dnsmasq       │         │
                  │ • nginx (PXE)   │         │
                  │ • nftables      │         │
                  └─────────────────┘         │
                                              │
            ┌─────────────────────────────────┼─────────────────────────────────┐
            │                                 │                                 │
       ┌────▼────┐  ┌────▼────┐  ┌────▼────┐ │ ┌────▼────┐  ┌────▼────┐  ┌────▼────┐
       │Bootstrap│  │Master-0 │  │Master-1 │ │ │Master-2 │  │Worker-0 │  │Worker-1 │
       │10.1.107.│  │10.1.107.│  │10.1.107.│ │ │10.1.107.│  │10.1.107.│  │10.1.107.│
       │   10    │  │   11    │  │   12    │ │ │   13    │  │   21    │  │   22    │
       └─────────┘  └─────────┘  └─────────┘ │ └─────────┘  └─────────┘  └─────────┘
                                              │
                                         ┌────▼────┐
                                         │Worker-2 │
                                         │10.1.107.│
                                         │   23    │
                                         └─────────┘
```

## VM Configuration

### Basic VM Settings
- **VM ID**: 500
- **Name**: vlan3-vlan107-lb (dual-homed load balancer)
- **CPU**: 4 cores
- **Memory**: 8GB
- **Storage**: 100GB SSD
- **OS**: Debian 12 (Bookworm)

### Network Interfaces
```bash
# eth0: Public interface (VLAN 3)
iface eth0 inet static
address 185.173.163.42/24
gateway 185.173.163.1
dns-nameservers 1.1.1.1 8.8.8.8

# eth1: Private interface (VLAN 107)  
iface eth1 inet static
address 10.1.107.1/24
# No gateway - this is the private network
```

## Network Configuration

### 1. Network Interface Configuration
**File**: `/etc/network/interfaces`

```bash
# The loopback network interface
auto lo
iface lo inet loopback

# Public interface - VLAN 3
auto eth0
iface eth0 inet static
address 185.173.163.42/24
gateway 185.173.163.1
dns-nameservers 1.1.1.1 8.8.8.8

# Private interface - VLAN 107 (cluster network)
auto eth1  
iface eth1 inet static
address 10.1.107.1/24
# No gateway - we ARE the gateway for this network
```

### 2. IP Forwarding
**File**: `/etc/sysctl.conf`

```bash
# Enable IP forwarding for NAT functionality
net.ipv4.ip_forward = 1
net.ipv6.conf.all.forwarding = 1
```

Apply immediately:
```bash
sudo sysctl -p
```

## Firewall Configuration (nftables)

### Complete nftables Configuration
**File**: `/etc/nftables.conf`

```bash
#!/usr/sbin/nft -f

# Clear existing rules
flush ruleset

# Define table for NAT
table ip nat {
    # NAT chain for postrouting
    chain postrouting {
        type nat hook postrouting priority 100; policy accept;
        
        # Masquerade traffic from private network going out public interface
        ip saddr 10.1.107.0/24 oifname "eth0" masquerade
    }
    
    # Prerouting chain (for any future port forwarding)
    chain prerouting {
        type nat hook prerouting priority -100; policy accept;
    }
}

# Filter table for basic firewall rules
table ip filter {
    # Input chain
    chain input {
        type filter hook input priority 0; policy accept;
        
        # Allow loopback
        iifname "lo" accept
        
        # Allow established and related connections
        ct state established,related accept
        
        # Allow SSH
        tcp dport 22 accept
        
        # Allow ICMP
        ip protocol icmp accept
    }
    
    # Forward chain to allow routing between interfaces
    chain forward {
        type filter hook forward priority 0; policy accept;
        
        # Allow established and related connections
        ct state established,related accept
        
        # Allow forwarding from private network to public
        iifname "eth1" oifname "eth0" accept
        
        # Allow forwarding back from public to private (established connections)
        iifname "eth0" oifname "eth1" ct state established,related accept
    }
    
    # Output chain
    chain output {
        type filter hook output priority 0; policy accept;
    }
}
```

### Enable and Start nftables
```bash
sudo systemctl enable nftables
sudo systemctl start nftables
sudo systemctl status nftables
```

## DNS and DHCP Configuration (dnsmasq)

### Complete dnsmasq Configuration
**File**: `/etc/dnsmasq.conf`

```bash
# Basic Configuration
# Bind only to the internal interface and avoid conflicts with systemd-resolved
interface=eth1
bind-interfaces

# Don't read /etc/resolv.conf, specify DNS servers directly
no-resolv
server=1.1.1.1
server=8.8.8.8

# Only listen on specific interface to avoid conflicts
listen-address=10.1.107.1

# Basic DHCP
dhcp-range=10.1.107.100,10.1.107.150,255.255.255.0,12h
dhcp-option=option:router,10.1.107.1
dhcp-option=option:dns-server,10.1.107.1
domain=sdb.coffeesprout.cloud
expand-hosts

# (Optional) provide hostnames for nicer logs
address=/bootstrap.sdb.coffeesprout.cloud/10.1.107.10
address=/master-0.sdb.coffeesprout.cloud/10.1.107.11
address=/master-1.sdb.coffeesprout.cloud/10.1.107.12
address=/master-2.sdb.coffeesprout.cloud/10.1.107.13
address=/worker-0.sdb.coffeesprout.cloud/10.1.107.21
address=/worker-1.sdb.coffeesprout.cloud/10.1.107.22
address=/worker-2.sdb.coffeesprout.cloud/10.1.107.23

# Split-horizon DNS for OKD cluster FQDNs - resolve to LB LAN IP
address=/api.dev.sdb.coffeesprout.cloud/10.1.107.1
address=/api-int.dev.sdb.coffeesprout.cloud/10.1.107.1
address=/*.apps.dev.sdb.coffeesprout.cloud/10.1.107.1

# Optional: pin hosts later by MAC (fill in MACs once VMs exist)
dhcp-host=BC:24:11:B8:90:3B,bootstrap,10.1.107.10
dhcp-host=BC:24:11:76:66:77,master-0,10.1.107.11
dhcp-host=BC:24:11:D6:17:83,master-1,10.1.107.12
dhcp-host=BC:24:11:71:E7:69,master-2,10.1.107.13
dhcp-host=BC:24:11:F6:74:68,worker-0,10.1.107.21
dhcp-host=BC:24:11:23:E2:5B,worker-1,10.1.107.22
dhcp-host=BC:24:11:D5:19:B2,worker-2,10.1.107.23

# PXE/TFTP Configuration

# PXE boot filename
dhcp-boot=grubnetx64.efi

# PXE menu system
dhcp-option-force=209,pxelinux.cfg
dhcp-option-force=210,/var/lib/tftpboot/

# Enable TFTP properly - no address conflict
enable-tftp
tftp-root=/var/lib/tftpboot
```

### Enable and Start dnsmasq
```bash
sudo systemctl enable dnsmasq
sudo systemctl start dnsmasq
sudo systemctl status dnsmasq
```

## PXE Boot Server Configuration

### 1. TFTP Directory Structure
```bash
sudo mkdir -p /var/lib/tftpboot/grub/x86_64-efi
sudo mkdir -p /var/lib/tftpboot/pxelinux.cfg
```

### 2. GRUB EFI Bootloader
Download GRUB EFI bootloader for UEFI PXE boot:
```bash
sudo wget -O /var/lib/tftpboot/grubnetx64.efi \
  http://archive.ubuntu.com/ubuntu/dists/jammy/main/uefi/grub2-amd64/current/grubnetx64.efi.signed
```

### 3. GRUB Modules
Copy required GRUB modules:
```bash
sudo cp -r /usr/lib/grub/x86_64-efi/* /var/lib/tftpboot/grub/x86_64-efi/
```

### 4. MAC-Aware GRUB Configuration
**File**: `/var/lib/tftpboot/grub/grub.cfg`

```bash
# GRUB configuration for UEFI PXE boot - MAC-based selection
set timeout=5
set default=0

# Load required modules
insmod http
insmod efinet

# MAC-based menu selection
if [ -n "${net_default_mac}" ]; then
    # Bootstrap VM (BC:24:11:B8:90:3B)
    if [ "${net_default_mac}" = "bc:24:11:b8:90:3b" ]; then
        menuentry "SCOS Bootstrap Install" {
            set root=(http,10.1.107.1:8080)
            linux /scos-9.0.20250510-0-live-kernel.x86_64 \
                coreos.live.rootfs_url=http://10.1.107.1:8080/scos-9.0.20250510-0-live-rootfs.x86_64.img \
                coreos.inst.install_dev=/dev/sda \
                coreos.inst.ignition_url=http://10.1.107.1:8080/bootstrap.ign \
                ip=dhcp
            initrd /scos-9.0.20250510-0-live-initramfs.x86_64.img
        }
    fi
    
    # Master VMs (3 different MAC addresses)
    if [ "${net_default_mac}" = "bc:24:11:76:66:77" -o \
         "${net_default_mac}" = "bc:24:11:d6:17:83" -o \
         "${net_default_mac}" = "bc:24:11:71:e7:69" ]; then
        menuentry "SCOS Master Install" {
            set root=(http,10.1.107.1:8080)
            linux /scos-9.0.20250510-0-live-kernel.x86_64 \
                coreos.live.rootfs_url=http://10.1.107.1:8080/scos-9.0.20250510-0-live-rootfs.x86_64.img \
                coreos.inst.install_dev=/dev/sda \
                coreos.inst.ignition_url=http://10.1.107.1:8080/master.ign \
                ip=dhcp
            initrd /scos-9.0.20250510-0-live-initramfs.x86_64.img
        }
    fi
    
    # Worker VMs (3 different MAC addresses)
    if [ "${net_default_mac}" = "bc:24:11:f6:74:68" -o \
         "${net_default_mac}" = "bc:24:11:23:e2:5b" -o \
         "${net_default_mac}" = "bc:24:11:d5:19:b2" ]; then
        menuentry "SCOS Worker Install" {
            set root=(http,10.1.107.1:8080)
            linux /scos-9.0.20250510-0-live-kernel.x86_64 \
                coreos.live.rootfs_url=http://10.1.107.1:8080/scos-9.0.20250510-0-live-rootfs.x86_64.img \
                coreos.inst.install_dev=/dev/sda \
                coreos.inst.ignition_url=http://10.1.107.1:8080/worker.ign \
                ip=dhcp
            initrd /scos-9.0.20250510-0-live-initramfs.x86_64.img
        }
    fi
else
    # Fallback menu if MAC detection fails
    menuentry "SCOS Generic Install" {
        set root=(http,10.1.107.1:8080)
        linux /scos-9.0.20250510-0-live-kernel.x86_64 \
            coreos.live.rootfs_url=http://10.1.107.1:8080/scos-9.0.20250510-0-live-rootfs.x86_64.img \
            coreos.inst.install_dev=/dev/sda \
            ip=dhcp
        initrd /scos-9.0.20250510-0-live-initramfs.x86_64.img
    }
fi
```

## HTTP Server for PXE Files (nginx)

### 1. Install nginx
```bash
sudo apt update && sudo apt install -y nginx
```

### 2. PXE Site Configuration
**File**: `/etc/nginx/sites-available/pxe`

```nginx
server {
    # Listen only on internal interface for security
    listen 10.1.107.1:8080;
    server_name _;
    root /var/www/pxe;
    
    # Logging for troubleshooting
    access_log /var/log/nginx/pxe.access.log;
    error_log /var/log/nginx/pxe.error.log;
    
    location / {
        # Enable directory browsing
        autoindex on;
        autoindex_exact_size off;
        autoindex_localtime on;
        
        # Prevent caching of boot files
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma "no-cache";
        add_header Expires "0";
    }
}
```

### 3. Enable PXE Site
```bash
sudo ln -sf /etc/nginx/sites-available/pxe /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl enable nginx
```

### 4. Create PXE Web Root and Files
```bash
# Create web root directory
sudo mkdir -p /var/www/pxe
sudo chown -R www-data:www-data /var/www/pxe

# Download SCOS boot files
cd /var/www/pxe

# Download SCOS 9.0.20250510-0 files
sudo wget -O scos-9.0.20250510-0-live-kernel.x86_64 \
  https://cloud.centos.org/centos/scos/9/prod/streams/latest/x86_64/scos-9.0.20250510-0-live-kernel.x86_64

sudo wget -O scos-9.0.20250510-0-live-initramfs.x86_64.img \
  https://cloud.centos.org/centos/scos/9/prod/streams/latest/x86_64/scos-9.0.20250510-0-live-initramfs.x86_64.img

sudo wget -O scos-9.0.20250510-0-live-rootfs.x86_64.img \
  https://cloud.centos.org/centos/scos/9/prod/streams/latest/x86_64/scos-9.0.20250510-0-live-rootfs.x86_64.img

# Copy OKD ignition files (created during cluster setup)
sudo cp ~/okd-installation/*.ign /var/www/pxe/

# Set proper ownership
sudo chown -R www-data:www-data /var/www/pxe
```

## HAProxy Load Balancer Configuration

### Install HAProxy 3.2 LTS
```bash
# Add HAProxy official repository
curl -fsSL https://haproxy.debian.net/bernat.debian.org.gpg | \
sudo gpg --dearmor -o /usr/share/keyrings/haproxy.debian.net.gpg

echo 'deb [signed-by=/usr/share/keyrings/haproxy.debian.net.gpg] https://haproxy.debian.net bookworm-backports-3.2 main' | \
sudo tee /etc/apt/sources.list.d/haproxy.list

# Install specific version
sudo apt update
sudo apt install -y haproxy=3.2.4-1~bpo12+1
```

### Complete HAProxy Configuration
**File**: `/etc/haproxy/haproxy.cfg`

```bash
global
    log /dev/log local0
    log /dev/log local1 notice
    maxconn 10000
    daemon
    
    # Performance tuning
    nbthread 4
    cpu-map auto:1/1-4 0-3

defaults
    log     global
    mode    tcp
    option  tcplog
    option  dontlognull
    timeout connect 10s
    timeout client  1m
    timeout server  1m
    retries 3

# Statistics page (optional)
stats enable
stats uri /stats
stats refresh 30s
stats admin if TRUE

# --- Kubernetes API Server (6443) ---
frontend fe_api
    bind *:6443
    mode tcp
    default_backend be_api

backend be_api
    mode tcp
    option tcp-check
    balance roundrobin
    default-server check inter 3000 fall 3 rise 2
    
    # Bootstrap node - remove after installation
    server bootstrap 10.1.107.10:6443 check
    server master-0 10.1.107.11:6443 check
    server master-1 10.1.107.12:6443 check
    server master-2 10.1.107.13:6443 check

# --- Machine Config Server (22623) ---
# Serves ignition configs and machine configuration
frontend fe_mcs
    bind *:22623
    mode tcp
    default_backend be_mcs

backend be_mcs
    mode tcp
    option tcp-check
    balance roundrobin
    default-server check inter 3000 fall 3 rise 2
    
    # Bootstrap node - remove after installation
    server bootstrap 10.1.107.10:22623 check
    server master-0 10.1.107.11:22623 check
    server master-1 10.1.107.12:22623 check
    server master-2 10.1.107.13:22623 check

# --- HTTP Ingress (80) ---
# Routes application traffic
frontend fe_http
    bind *:80
    mode tcp
    default_backend be_http

backend be_http
    mode tcp
    balance roundrobin
    option tcp-check
    default-server check inter 3000 fall 3 rise 2
    
    # Initially route to masters (router pods run there)
    # Later can include workers when they join
    server master-0 10.1.107.11:80 check
    server master-1 10.1.107.12:80 check
    server master-2 10.1.107.13:80 check
    
    # Workers (add when ready for application workloads)
    server worker-0 10.1.107.21:80 check
    server worker-1 10.1.107.22:80 check
    server worker-2 10.1.107.23:80 check

# --- HTTPS Ingress (443) ---
# Routes secure application traffic
frontend fe_https
    bind *:443
    mode tcp
    default_backend be_https

backend be_https
    mode tcp
    balance roundrobin
    option tcp-check
    default-server check inter 3000 fall 3 rise 2
    
    # Initially route to masters
    server master-0 10.1.107.11:443 check
    server master-1 10.1.107.12:443 check
    server master-2 10.1.107.13:443 check
    
    # Workers
    server worker-0 10.1.107.21:443 check
    server worker-1 10.1.107.22:443 check
    server worker-2 10.1.107.23:443 check
```

### Enable and Start HAProxy
```bash
sudo systemctl enable haproxy
sudo systemctl start haproxy
sudo systemctl status haproxy
```

## System Services Configuration

### Service Start Order and Dependencies
The services must start in the correct order:

1. **Network interfaces** (automatic)
2. **nftables** (firewall)
3. **dnsmasq** (DNS/DHCP/TFTP)
4. **nginx** (HTTP server for PXE files)
5. **haproxy** (load balancer)

### Service Status Monitoring
```bash
#!/bin/bash
# Check all critical services
services=("nftables" "dnsmasq" "nginx" "haproxy")

for service in "${services[@]}"; do
    if systemctl is-active --quiet "$service"; then
        echo "✅ $service is running"
    else
        echo "❌ $service is not running"
        systemctl status "$service" --no-pager -l
    fi
done
```

## Monitoring and Troubleshooting

### Log File Locations
```bash
# System logs
/var/log/syslog                    # General system log
/var/log/kern.log                  # Kernel messages

# Network services
/var/log/dnsmasq.log              # DHCP/DNS transactions
journalctl -u dnsmasq -f          # Real-time dnsmasq logs

# PXE server
/var/log/nginx/pxe.access.log     # PXE file downloads
/var/log/nginx/pxe.error.log      # PXE server errors

# Load balancer
/var/log/haproxy.log              # HAProxy connections and health

# Firewall
journalctl -u nftables            # Firewall rules and blocked traffic
```

### Common Troubleshooting Commands
```bash
# Check network connectivity
ping 8.8.8.8                      # Internet connectivity
ping 10.1.107.11                  # Cluster node connectivity

# Check DHCP leases
cat /var/lib/dhcp/dhcpd.leases    # Active DHCP leases
grep DHCP /var/log/syslog         # DHCP transactions

# Check PXE boot activity
tail -f /var/log/nginx/pxe.access.log  # PXE file downloads
journalctl -u dnsmasq -f | grep TFTP    # TFTP transfers

# Check load balancer health
curl -s http://localhost:6443     # API server health
systemctl status haproxy          # HAProxy status

# Check firewall rules
nft list ruleset                  # Current firewall rules
```

### Performance Monitoring
```bash
# Network interface statistics
ip -s link show eth0              # Public interface stats
ip -s link show eth1              # Private interface stats

# Service resource usage
systemctl status dnsmasq          # Memory and CPU usage
systemctl status haproxy
systemctl status nginx

# Network connections
ss -tuln                          # Listening services
ss -tupn | grep :6443            # Kubernetes API connections
```

## Security Considerations

### Network Security
- **Interface Isolation**: PXE server only listens on internal interface
- **Firewall Rules**: Strict nftables configuration with logging
- **NAT Security**: Only outbound traffic from private network allowed
- **Service Binding**: Services bound to specific interfaces only

### Access Control
- **SSH Access**: Only from public network for management
- **Internal Services**: DHCP, DNS, TFTP only on private network
- **Load Balancer**: Accessible from both networks as designed
- **Cluster Access**: API server accessible externally through load balancer

### Monitoring Security
- **Log Analysis**: Regular review of firewall and access logs
- **Connection Monitoring**: Track HAProxy connection patterns
- **DHCP Security**: Static MAC assignments prevent IP spoofing
- **DNS Security**: Split-horizon DNS prevents external enumeration

## Backup and Recovery

### Configuration Backup
```bash
#!/bin/bash
# Backup all configuration files
backup_dir="/root/config-backup-$(date +%Y%m%d)"
mkdir -p "$backup_dir"

# Network configuration
cp -r /etc/network "$backup_dir/"
cp /etc/nftables.conf "$backup_dir/"

# Service configurations  
cp /etc/dnsmasq.conf "$backup_dir/"
cp /etc/haproxy/haproxy.cfg "$backup_dir/"
cp -r /etc/nginx "$backup_dir/"

# PXE files
cp -r /var/lib/tftpboot "$backup_dir/"
cp -r /var/www/pxe "$backup_dir/"

# OKD configuration
cp -r ~/okd-installation "$backup_dir/"

echo "Configuration backed up to $backup_dir"
```

### Disaster Recovery
In case of load balancer failure:
1. **Deploy new VM** with same network configuration
2. **Restore configurations** from backup
3. **Update DNS records** if IP changed
4. **Restart services** in correct order
5. **Validate cluster connectivity**

## Performance Optimization

### System Tuning
```bash
# Network buffer optimization
echo 'net.core.rmem_max = 134217728' >> /etc/sysctl.conf
echo 'net.core.wmem_max = 134217728' >> /etc/sysctl.conf
echo 'net.ipv4.tcp_rmem = 4096 87380 134217728' >> /etc/sysctl.conf
echo 'net.ipv4.tcp_wmem = 4096 65536 134217728' >> /etc/sysctl.conf

# Apply changes
sysctl -p
```

### Service Optimization
```bash
# HAProxy: Adjust thread count based on CPU cores
# nginx: Tune worker processes and connections
# dnsmasq: Increase cache size for DNS queries
```

This load balancer configuration provides a robust, scalable foundation for OKD cluster operations, combining network gateway functionality, PXE boot services, and intelligent load balancing in a single, well-integrated solution.