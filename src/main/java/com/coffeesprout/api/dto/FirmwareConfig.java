package com.coffeesprout.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Configuration for VM firmware and machine type settings.
 * This class handles UEFI/OVMF configuration for modern operating systems
 * like SCOS (CentOS Stream CoreOS) used by OKD.
 */
@Schema(description = "VM firmware and machine type configuration")
public record FirmwareConfig(
    @Schema(description = "Firmware type", example = "UEFI", defaultValue = "SEABIOS")
    @NotNull(message = "Firmware type is required")
    FirmwareType type,
    
    @Schema(description = "Machine type", example = "q35", defaultValue = "pc")
    @NotNull(message = "Machine type is required")
    MachineType machine,
    
    @Schema(description = "EFI disk configuration (required for UEFI)")
    @Valid
    EFIDiskConfig efidisk,
    
    @Schema(description = "Enable secure boot (not compatible with OKD/FCOS)", example = "false", defaultValue = "false")
    Boolean secureboot
) {
    
    /**
     * Firmware types supported by Proxmox
     */
    @Schema(enumeration = {"SEABIOS", "UEFI"})
    public enum FirmwareType {
        SEABIOS("seabios", "pc"),
        UEFI("ovmf", "q35");
        
        private final String proxmoxValue;
        private final String recommendedMachine;
        
        FirmwareType(String proxmoxValue, String recommendedMachine) {
            this.proxmoxValue = proxmoxValue;
            this.recommendedMachine = recommendedMachine;
        }
        
        public String getProxmoxValue() {
            return proxmoxValue;
        }
        
        public String getRecommendedMachine() {
            return recommendedMachine;
        }
    }
    
    /**
     * Machine types supported by Proxmox
     */
    @Schema(enumeration = {"pc", "q35"})
    public enum MachineType {
        PC("pc"),
        Q35("q35");
        
        private final String value;
        
        MachineType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * EFI disk configuration for UEFI VMs
     */
    @Schema(description = "EFI disk configuration for UEFI boot")
    public record EFIDiskConfig(
        @Schema(description = "Storage pool for EFI disk", example = "local-zfs", required = true)
        @NotNull(message = "EFI disk storage is required for UEFI VMs")
        String storage,
        
        @Schema(description = "EFI type and size", example = "4m", defaultValue = "4m")
        EFIType efitype,
        
        @Schema(description = "Pre-enrolled keys for secure boot", example = "false", defaultValue = "false")
        Boolean preEnrolledKeys
    ) {
        
        @Schema(enumeration = {"2m", "4m"})
        public enum EFIType {
            SMALL("2m"),
            LARGE("4m");
            
            private final String value;
            
            EFIType(String value) {
                this.value = value;
            }
            
            public String getValue() {
                return value;
            }
        }
        
        /**
         * Generate the efidisk0 parameter for Proxmox API
         * Example: "local-zfs:1,efitype=4m,pre-enrolled-keys=0"
         */
        public String toProxmoxString() {
            StringBuilder config = new StringBuilder();
            config.append(storage).append(":1");  // Size is always 1 for EFI disks
            
            if (efitype != null) {
                config.append(",efitype=").append(efitype.getValue());
            } else {
                config.append(",efitype=4m");  // Default to 4m
            }
            
            // Pre-enrolled keys setting
            boolean usePreEnrolled = preEnrolledKeys != null && preEnrolledKeys;
            config.append(",pre-enrolled-keys=").append(usePreEnrolled ? "1" : "0");
            
            return config.toString();
        }
    }
    
    /**
     * Create default firmware configuration for legacy BIOS
     */
    public static FirmwareConfig defaultSeaBIOS() {
        return new FirmwareConfig(
            FirmwareType.SEABIOS,
            MachineType.PC,
            null,  // No EFI disk needed for SEABIOS
            false
        );
    }
    
    /**
     * Create default firmware configuration for UEFI
     */
    public static FirmwareConfig defaultUEFI(String storage) {
        return new FirmwareConfig(
            FirmwareType.UEFI,
            MachineType.Q35,
            new EFIDiskConfig(storage, EFIDiskConfig.EFIType.LARGE, false),
            false
        );
    }
    
    /**
     * Validate firmware configuration
     */
    public void validate() {
        if (type == FirmwareType.UEFI) {
            if (efidisk == null) {
                throw new IllegalArgumentException("EFI disk configuration is required for UEFI firmware");
            }
            if (machine == MachineType.PC) {
                throw new IllegalArgumentException("UEFI firmware requires q35 machine type, not pc");
            }
        }
        
        if (type == FirmwareType.SEABIOS && efidisk != null) {
            throw new IllegalArgumentException("EFI disk should not be configured for SEABIOS firmware");
        }
        
        if (secureboot != null && secureboot && type != FirmwareType.UEFI) {
            throw new IllegalArgumentException("Secure boot is only available with UEFI firmware");
        }
    }
    
    /**
     * Check if this configuration requires post-creation setup
     */
    public boolean requiresPostCreationSetup() {
        return type == FirmwareType.UEFI && efidisk != null;
    }
}