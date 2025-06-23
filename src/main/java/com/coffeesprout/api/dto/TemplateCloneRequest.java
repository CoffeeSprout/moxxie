package com.coffeesprout.api.dto;

import jakarta.validation.constraints.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.util.List;

/**
 * Request DTO for cloning a VM from a template
 */
@Schema(description = "Request to clone a VM from a template")
public record TemplateCloneRequest(
    @Schema(description = "Template VM ID to clone from", example = "9001", required = true)
    @NotNull(message = "Template ID is required")
    @Min(value = 100, message = "Template ID must be at least 100")
    @Max(value = 999999999, message = "Template ID must be less than 999999999")
    Integer templateId,
    
    @Schema(description = "New VM ID", example = "100", required = true)
    @NotNull(message = "New VM ID is required")
    @Min(value = 100, message = "VM ID must be at least 100")
    @Max(value = 999999999, message = "VM ID must be less than 999999999")
    Integer newVmId,
    
    @Schema(description = "VM name", example = "k8s-control-01", required = true)
    @NotBlank(message = "VM name is required")
    @Pattern(regexp = "^[a-zA-Z0-9.-]+$", message = "VM name can only contain alphanumeric characters, dots, and dashes")
    String name,
    
    @Schema(description = "Target node where VM should ultimately run", example = "hv7", required = true)
    @NotBlank(message = "Target node is required")
    String targetNode,
    
    @Schema(description = "VM description", example = "Kubernetes control plane node")
    String description,
    
    @Schema(description = "Perform full clone (true) or linked clone (false)", defaultValue = "true")
    Boolean fullClone,
    
    @Schema(description = "Target storage for the cloned disks", example = "local-zfs")
    String targetStorage,
    
    @Schema(description = "Cloud-init configuration")
    CloudInitConfig cloudInit,
    
    @Schema(description = "Disk resize configuration")
    DiskResizeConfig diskResize,
    
    @Schema(description = "Start VM after creation", defaultValue = "false")
    Boolean start,
    
    @Schema(description = "Tags to apply to the VM", example = "k8s-controlplane,env:prod")
    List<String> tags,
    
    @Schema(description = "Resource pool to assign VM to")
    String pool
) {
    /**
     * Cloud-init configuration for the cloned VM
     */
    @Schema(description = "Cloud-init configuration settings")
    public record CloudInitConfig(
        @Schema(description = "Cloud-init user", example = "debian")
        String user,
        
        @Schema(description = "Cloud-init password")
        String password,
        
        @Schema(description = "SSH public keys (one per line)")
        String sshKeys,
        
        @Schema(description = "IP configuration", example = "ip=192.168.1.100/24,gw=192.168.1.1")
        String ipConfig,
        
        @Schema(description = "DNS search domain", example = "cluster.local")
        String searchDomain,
        
        @Schema(description = "DNS nameservers (comma-separated)", example = "8.8.8.8,8.8.4.4")
        String nameservers
    ) {}
    
    /**
     * Disk resize configuration
     */
    @Schema(description = "Configuration for resizing disks after cloning")
    public record DiskResizeConfig(
        @Schema(description = "Disk identifier", example = "scsi0", defaultValue = "scsi0")
        String disk,
        
        @Schema(description = "New disk size", example = "+20G or 50G")
        @Pattern(regexp = "^\\+?\\d+[KMGT]?$", message = "Size must be in format like '50G', '+20G', '512M'")
        String size
    ) {
        public DiskResizeConfig {
            if (disk == null) {
                disk = "scsi0";
            }
        }
    }
    
    // Default values
    public TemplateCloneRequest {
        if (fullClone == null) {
            fullClone = true;
        }
        if (start == null) {
            start = false;
        }
    }
}