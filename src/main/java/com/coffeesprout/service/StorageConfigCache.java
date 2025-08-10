package com.coffeesprout.service;

import com.coffeesprout.client.StorageResponse;
import com.coffeesprout.config.MigrationConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache for Proxmox storage configuration to reduce API calls during bulk migrations
 */
@ApplicationScoped
public class StorageConfigCache {
    
    private static final Logger log = LoggerFactory.getLogger(StorageConfigCache.class);
    
    @Inject
    MigrationConfig migrationConfig;
    
    private StorageResponse cachedResponse;
    private Instant cacheExpiry;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * Get cached storage configuration or null if cache is expired/empty
     */
    public StorageResponse getCached() {
        lock.readLock().lock();
        try {
            if (cachedResponse != null && cacheExpiry != null && Instant.now().isBefore(cacheExpiry)) {
                log.debug("Returning cached storage configuration (expires at {})", cacheExpiry);
                return cachedResponse;
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Update the cache with fresh storage configuration
     */
    public void updateCache(StorageResponse response) {
        if (response == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            this.cachedResponse = response;
            this.cacheExpiry = Instant.now().plus(Duration.ofSeconds(migrationConfig.storageCacheSeconds()));
            log.debug("Updated storage configuration cache (expires at {})", cacheExpiry);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear the cache
     */
    public void clearCache() {
        lock.writeLock().lock();
        try {
            this.cachedResponse = null;
            this.cacheExpiry = null;
            log.debug("Cleared storage configuration cache");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if cache is valid
     */
    public boolean isCacheValid() {
        lock.readLock().lock();
        try {
            return cachedResponse != null && cacheExpiry != null && Instant.now().isBefore(cacheExpiry);
        } finally {
            lock.readLock().unlock();
        }
    }
}