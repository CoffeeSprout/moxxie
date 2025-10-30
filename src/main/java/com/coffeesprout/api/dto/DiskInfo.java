package com.coffeesprout.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Information about a VM disk")
public record DiskInfo(
    @Schema(description = "Disk interface (e.g., scsi0, virtio0, ide0)", example = "scsi0")
    String diskInterface,

    @Schema(description = "Storage backend (e.g., local-lvm, local-zfs)", example = "local-zfs")
    String storage,

    @Schema(description = "Disk size in bytes", example = "21474836480")
    Long sizeBytes,

    @Schema(description = "Human-readable disk size", example = "20G")
    String sizeHuman,

    @Schema(description = "Disk format (e.g., raw, qcow2)", example = "raw")
    String format,

    @Schema(description = "Full disk specification", example = "local-zfs:vm-100-disk-0,format=raw,size=20G")
    String rawConfig
) {}
