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
    
    interface Instance {
        @WithDefault("moxxie-default")
        String id();
        
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
}