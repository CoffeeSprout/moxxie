package com.coffeesprout.api.dto.cluster;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import com.coffeesprout.api.dto.DiskConfig;
import com.coffeesprout.api.dto.NetworkConfig;
import com.coffeesprout.constants.VMConstants;
import com.coffeesprout.validation.ValidImageSource;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "VM template specifications for cluster nodes")
public record NodeTemplate(
    @Schema(description = "Number of CPU cores", example = "4", required = true)
    @NotNull(message = "CPU cores is required") @Min(value = 1, message = "At least 1 CPU core is required")
    @Max(value = 128, message = "Cannot exceed 128 CPU cores")
    Integer cores,

    @Schema(description = "Memory in MB", example = "8192", required = true)
    @NotNull(message = "Memory is required") @Min(value = VMConstants.Resources.MIN_MEMORY_MB, message = "At least 512 MB of memory is required")
    @Max(value = VMConstants.Resources.MAX_MEMORY_MB_VALIDATION, message = "Cannot exceed 1TB of memory")
    Integer memoryMB,

    @Schema(description = "Disk configurations", required = true)
    @NotNull(message = "At least one disk is required") @Size(min = 1, message = "At least one disk is required")
    @Valid
    List<DiskConfig> disks,

    @Schema(description = "Network configurations", required = true)
    @NotNull(message = "At least one network is required") @Size(min = 1, message = "At least one network is required")
    @Valid
    List<NetworkConfig> networks,

    @Schema(description = "Source image or template (must reference a template VM disk)", example = "local-zfs:9002/base-9002-disk-0.raw", required = true)
    @NotBlank(message = "Image source is required")
    @ValidImageSource
    String imageSource,

    @Schema(description = "Cloud-init configuration")
    @Valid
    CloudInitConfig cloudInit,

    @Schema(description = "CPU type", example = "x86-64-v2-AES", defaultValue = "x86-64-v2-AES")
    String cpuType,

    @Schema(description = "Enable QEMU agent", defaultValue = "true")
    Boolean qemuAgent,

    @Schema(description = "Boot order", example = "scsi0", defaultValue = "scsi0")
    String boot,

    @Schema(description = "VGA type", example = "std", defaultValue = "std")
    String vga,

    @Schema(description = "Additional VM configuration options")
    Map<String, String> extraConfig
) {
    public NodeTemplate {
        if (cpuType == null) {
            cpuType = "x86-64-v2-AES";
        }
        if (qemuAgent == null) {
            qemuAgent = true;
        }
        if (boot == null && disks != null && !disks.isEmpty()) {
            boot = disks.get(0).interfaceType().name().toLowerCase() + disks.get(0).slot();
        }
        if (vga == null) {
            vga = "std";
        }
        if (extraConfig == null) {
            extraConfig = Map.of();
        }
    }
}
