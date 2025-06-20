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
}