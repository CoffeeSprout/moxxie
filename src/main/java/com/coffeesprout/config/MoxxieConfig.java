package com.coffeesprout.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * Simplified configuration for single Proxmox cluster operation.
 * Uses MicroProfile Config for environment variable support.
 */
@ConfigMapping(prefix = "moxxie")
public interface MoxxieConfig {
    
    /**
     * Instance configuration
     */
    Instance instance();
    
    /**
     * Proxmox cluster configuration
     */
    Proxmox proxmox();
    
    /**
     * Resource management configuration
     */
    Resources resources();
    
    /**
     * API server configuration
     */
    Api api();
    
    /**
     * Location configuration for federation support
     */
    Location location();
    
    interface Instance {
        @WithDefault("moxxie-default")
        String id();
        
        /**
         * @deprecated Use location.* properties instead
         */
        @Deprecated
        @WithDefault("default-location")
        String location();
        
        @WithDefault("unknown")
        String version();
        
        @WithDefault("default")
        String environment();
    }
    
    interface Proxmox {
        /**
         * Proxmox API URL
         * Can be set via MOXXIE_PROXMOX_URL
         */
        @WithDefault("https://localhost:8006/api2/json")
        String url();
        
        /**
         * Proxmox username
         * Can be set via MOXXIE_PROXMOX_USERNAME
         */
        @WithDefault("root@pam")
        String username();
        
        /**
         * Proxmox password
         * Should be set via MOXXIE_PROXMOX_PASSWORD
         */
        String password();
        
        /**
         * Whether to verify SSL certificates
         * Can be set via MOXXIE_PROXMOX_VERIFY_SSL
         */
        @WithDefault("false")
        boolean verifySsl();
        
        /**
         * Connection timeout in seconds
         */
        @WithDefault("30")
        int connectionTimeout();
        
        /**
         * Default storage for VM creation
         */
        @WithDefault("local-lvm")
        String defaultStorage();
        
        /**
         * Default network bridge
         */
        @WithDefault("vmbr0")
        String defaultBridge();
        
        /**
         * Default CPU type for VM creation
         * Can be set via MOXXIE_PROXMOX_DEFAULT_CPU_TYPE
         */
        @WithDefault("x86-64-v2-AES")
        String defaultCpuType();
        
        /**
         * Default VGA type for VM creation
         * Can be set via MOXXIE_PROXMOX_DEFAULT_VGA_TYPE
         */
        @WithDefault("std")
        String defaultVgaType();
    }
    
    interface Api {
        /**
         * Enable API key authentication
         */
        @WithDefault("false")
        boolean authEnabled();
        
        /**
         * API key for authentication
         * Should be set via MOXXIE_API_KEY
         */
        Optional<String> key();
        
        /**
         * Enable read-only mode
         */
        @WithDefault("false")
        boolean readOnly();
        
        /**
         * Rate limiting - requests per minute
         */
        @WithDefault("60")
        int rateLimit();
    }
    
    interface Location {
        /**
         * Infrastructure provider: proxmox, hetzner, scaleway
         */
        @WithDefault("proxmox")
        String provider();
        
        /**
         * Region identifier (e.g., nl-west-1, nl-ams-1, de-fsn-1)
         */
        String region();
        
        /**
         * Datacenter identifier (e.g., wsdc1, dbna1)
         */
        String datacenter();
        
        /**
         * Human-readable location name
         */
        String name();
        
        /**
         * ISO 3166-1 alpha-2 country code (e.g., NL, DE, FR)
         */
        String country();
        
        /**
         * Geographic latitude (-90 to 90)
         */
        Double latitude();
        
        /**
         * Geographic longitude (-180 to 180)
         */
        Double longitude();
        
        /**
         * Unique instance identifier (auto-generated if not provided)
         */
        Optional<String> instanceId();
    }
    
    /**
     * Resource management configuration
     */
    interface Resources {
        
        interface Cpu {
            @WithDefault("4.0")
            double overcommitRatio();
            
            @WithDefault("10")
            double reservePercent();
            
            @WithDefault("0")
            int maxCoresPerVm();
        }
        
        interface Memory {
            @WithDefault("1.0")
            double overcommitRatio();
            
            @WithDefault("15")
            double reservePercent();
            
            @WithDefault("false")
            boolean includeSwap();
            
            @WithDefault("0")
            int maxGbPerVm();
        }
        
        interface Storage {
            @WithDefault("1.5")
            double overprovisionRatio();
            
            @WithDefault("10")
            double reservePercent();
            
            @WithDefault("true")
            boolean thinProvisioningEnabled();
            
            @WithDefault("80")
            double warningThreshold();
        }
        
        Cpu cpu();
        Memory memory();
        Storage storage();
    }
}