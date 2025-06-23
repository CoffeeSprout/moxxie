package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Network configuration for a VM network interface.
 * Supports all Proxmox network device options.
 */
@Schema(description = "Network interface configuration")
public record NetworkConfig(
    @Schema(description = "Network model (virtio, e1000, e1000e, rtl8139, vmxnet3)", example = "virtio", defaultValue = "virtio")
    String model,
    
    @Schema(description = "Network bridge", example = "vmbr0", required = true)
    String bridge,
    
    @Schema(description = "VLAN tag (1-4094)", example = "100", minimum = "1", maximum = "4094")
    Integer vlan,
    
    @Schema(description = "MAC address in format XX:XX:XX:XX:XX:XX", 
            example = "52:54:00:12:34:56",
            pattern = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    String macaddr,
    
    @Schema(description = "Enable firewall for this interface", defaultValue = "true")
    Boolean firewall,
    
    @Schema(description = "Rate limit in MB/s", example = "100", minimum = "0")
    Integer rate,
    
    @Schema(description = "Enable link (cable connected)", defaultValue = "true")
    Boolean link,
    
    @Schema(description = "Multiqueue for virtio (number of queues)", example = "4", minimum = "0", maximum = "64")
    Integer queues,
    
    @Schema(description = "MTU (Maximum Transmission Unit)", example = "1500", minimum = "576", maximum = "65520")
    Integer mtu
) {
    // Constructor with defaults
    public NetworkConfig {
        if (model == null) model = "virtio";
        if (firewall == null) firewall = true;
        if (link == null) link = true;
    }
    
    /**
     * Convert to Proxmox network device string format.
     * Format: model=virtio,bridge=vmbr0,firewall=1,...
     */
    public String toProxmoxString() {
        StringBuilder sb = new StringBuilder();
        
        // Model is always first
        sb.append(model);
        
        // Bridge is required
        sb.append(",bridge=").append(bridge);
        
        // Optional parameters
        if (vlan != null) {
            sb.append(",tag=").append(vlan);
        }
        if (macaddr != null) {
            sb.append(",macaddr=").append(macaddr);
        }
        if (firewall != null && !firewall) {
            sb.append(",firewall=0");
        }
        if (rate != null && rate > 0) {
            sb.append(",rate=").append(rate);
        }
        if (link != null && !link) {
            sb.append(",link=0");
        }
        if (queues != null && queues > 0 && "virtio".equals(model)) {
            sb.append(",queues=").append(queues);
        }
        if (mtu != null) {
            sb.append(",mtu=").append(mtu);
        }
        
        return sb.toString();
    }
    
    /**
     * Create a simple network config with just bridge.
     */
    public static NetworkConfig simple(String bridge) {
        return new NetworkConfig("virtio", bridge, null, null, true, null, true, null, null);
    }
    
    /**
     * Create a network config with bridge and VLAN.
     */
    public static NetworkConfig withVlan(String bridge, int vlan) {
        return new NetworkConfig("virtio", bridge, vlan, null, true, null, true, null, null);
    }
}