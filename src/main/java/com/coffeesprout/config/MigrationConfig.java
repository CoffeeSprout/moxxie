package com.coffeesprout.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for VM migration behavior
 */
@ConfigMapping(prefix = "moxxie.migration")
public interface MigrationConfig {
    
    /**
     * Enable/disable auto-detection of local disks during migration
     */
    @WithDefault("true")
    boolean autoDetectLocalDisks();
    
    /**
     * Enable/disable fallback to naming convention when storage query fails
     */
    @WithDefault("true")
    boolean useNamingFallback();
    
    /**
     * Custom patterns to identify local storage by name
     */
    @WithDefault("local,local-lvm,local-zfs")
    List<String> localStoragePatterns();
    
    /**
     * Cache duration for storage configuration in seconds
     */
    @WithDefault("60")
    int storageCacheSeconds();
    
    /**
     * Log level for auto-detection details (DEBUG or INFO)
     */
    @WithDefault("INFO")
    String autoDetectionLogLevel();
    
    /**
     * Maximum retries for storage configuration query
     */
    @WithDefault("3")
    int storageQueryMaxRetries();
    
    /**
     * Timeout for storage configuration query in milliseconds
     */
    @WithDefault("5000")
    int storageQueryTimeoutMs();
}