package com.coffeesprout.service;

import com.coffeesprout.config.MoxxieConfig;
import com.coffeesprout.model.LocationInfo;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Service responsible for managing location information for this Moxxie instance.
 * Location data is immutable after startup and forms the foundation for federation support.
 */
@ApplicationScoped
public class LocationService {
    
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);
    
    @Inject
    MoxxieConfig config;
    
    private LocationInfo locationInfo;
    
    /**
     * Initialize location information at startup
     */
    void onStart(@Observes StartupEvent ev) {
        log.info("Initializing location information for Moxxie instance");
        
        try {
            // Generate instance ID if not provided
            String instanceId = config.location().instanceId()
                .orElseGet(() -> {
                    String generated = "moxxie-" + UUID.randomUUID().toString().substring(0, 8);
                    log.info("Generated instance ID: {}", generated);
                    return generated;
                });
            
            // Create LocationInfo from configuration
            locationInfo = new LocationInfo(
                config.location().provider(),
                config.location().region(),
                config.location().datacenter(),
                config.location().name(),
                config.location().country(),
                config.location().latitude(),
                config.location().longitude(),
                instanceId
            );
            
            // Validate location information
            locationInfo.validate();
            
            log.info("Location initialized: {} ({}) at {}/{}", 
                locationInfo.name(), 
                locationInfo.provider(),
                locationInfo.region(),
                locationInfo.datacenter()
            );
            log.info("Coordinates: {}, {}", locationInfo.latitude(), locationInfo.longitude());
            log.info("Instance ID: {}", locationInfo.instanceId());
            
        } catch (Exception e) {
            log.error("Failed to initialize location information", e);
            throw new RuntimeException("Invalid location configuration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the location information for this instance
     */
    public LocationInfo getLocationInfo() {
        if (locationInfo == null) {
            throw new IllegalStateException("Location information not initialized");
        }
        return locationInfo;
    }
    
    /**
     * Get the full location identifier (region/datacenter)
     */
    public String getFullLocation() {
        return getLocationInfo().fullLocation();
    }
    
    /**
     * Check if location information is initialized
     */
    public boolean isInitialized() {
        return locationInfo != null;
    }
}