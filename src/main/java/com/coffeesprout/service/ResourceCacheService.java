package com.coffeesprout.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Simple in-memory cache service for resource data.
 * In production, this could be replaced with Caffeine or another caching library.
 */
@ApplicationScoped
public class ResourceCacheService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResourceCacheService.class);
    
    // Default cache TTL of 5 minutes
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    
    /**
     * Get a value from cache or compute it if missing/expired
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        CacheEntry<?> entry = cache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            LOG.debug("Cache hit for key: {}", key);
            return (T) entry.getValue();
        }
        
        // Miss or expired
        misses.incrementAndGet();
        LOG.debug("Cache miss for key: {}", key);
        
        // Remove expired entry
        if (entry != null) {
            cache.remove(key);
            evictions.incrementAndGet();
        }
        
        // Load new value
        T value = loader.get();
        put(key, value);
        
        return value;
    }
    
    /**
     * Get a value from cache without loading if missing
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getIfPresent(String key) {
        CacheEntry<?> entry = cache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return Optional.of((T) entry.getValue());
        }
        
        misses.incrementAndGet();
        
        // Remove expired entry
        if (entry != null) {
            cache.remove(key);
            evictions.incrementAndGet();
        }
        
        return Optional.empty();
    }
    
    /**
     * Put a value in the cache with default TTL
     */
    public <T> void put(String key, T value) {
        put(key, value, DEFAULT_TTL);
    }
    
    /**
     * Put a value in the cache with custom TTL
     */
    public <T> void put(String key, T value, Duration ttl) {
        cache.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
        LOG.debug("Cached value for key: {} with TTL: {}", key, ttl);
    }
    
    /**
     * Invalidate a specific cache entry
     */
    public void invalidate(String key) {
        CacheEntry<?> removed = cache.remove(key);
        if (removed != null) {
            evictions.incrementAndGet();
            LOG.debug("Invalidated cache key: {}", key);
        }
    }
    
    /**
     * Invalidate all cache entries matching a pattern
     */
    public void invalidatePattern(String pattern) {
        String regex = pattern.replace("*", ".*");
        cache.entrySet().removeIf(entry -> {
            if (entry.getKey().matches(regex)) {
                evictions.incrementAndGet();
                LOG.debug("Invalidated cache key: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Clear all cache entries
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        evictions.addAndGet(size);
        LOG.info("Cleared cache, removed {} entries", size);
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long totalRequests = totalHits + totalMisses;
        double hitRate = totalRequests > 0 ? (double) totalHits / totalRequests : 0.0;
        
        return new CacheStatistics(
            cache.size(),
            totalHits,
            totalMisses,
            hitRate,
            evictions.get()
        );
    }
    
    /**
     * Clean up expired entries (can be called periodically)
     */
    public void cleanupExpired() {
        cache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                evictions.incrementAndGet();
                LOG.debug("Evicted expired cache key: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Cache entry with expiration
     */
    private static class CacheEntry<T> {
        private final T value;
        private final Instant expiresAt;
        
        public CacheEntry(T value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
        
        public T getValue() {
            return value;
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStatistics {
        private final int size;
        private final long hits;
        private final long misses;
        private final double hitRate;
        private final long evictions;
        
        public CacheStatistics(int size, long hits, long misses, double hitRate, long evictions) {
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
            this.evictions = evictions;
        }
        
        public int getSize() {
            return size;
        }
        
        public long getHits() {
            return hits;
        }
        
        public long getMisses() {
            return misses;
        }
        
        public double getHitRate() {
            return hitRate;
        }
        
        public long getEvictions() {
            return evictions;
        }
    }
}