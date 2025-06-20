package com.coffeesprout.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Immutable record representing the location information for a Moxxie instance.
 * This is the foundation for federation support, uniquely identifying where
 * this Moxxie instance is running.
 */
@RegisterForReflection
public record LocationInfo(
    String provider,      // proxmox, hetzner, scaleway
    String region,        // nl-west-1, nl-ams-1, de-fsn-1, etc.
    String datacenter,    // wsdc1, dbna1, etc.
    String name,          // Human-readable name like "Worldstream DC 1"
    String country,       // ISO 3166-1 alpha-2 code: NL, DE, FR
    Double latitude,      // Geographic latitude
    Double longitude,     // Geographic longitude
    String instanceId     // Unique instance identifier (generated if not provided)
) {
    
    /**
     * Get the full location identifier in the format "region/datacenter"
     */
    public String fullLocation() {
        return region + "/" + datacenter;
    }
    
    /**
     * Validate that all required fields are present
     */
    public void validate() {
        requireNonBlank(provider, "provider");
        requireNonBlank(region, "region");
        requireNonBlank(datacenter, "datacenter");
        requireNonBlank(name, "name");
        requireNonBlank(country, "country");
        requireNonNull(latitude, "latitude");
        requireNonNull(longitude, "longitude");
        requireNonBlank(instanceId, "instanceId");
        
        // Validate latitude and longitude ranges
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        
        // Validate country code is 2 characters
        if (country.length() != 2) {
            throw new IllegalArgumentException("Country must be a 2-letter ISO code");
        }
    }
    
    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Location field '" + field + "' is required");
        }
    }
    
    private void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Location field '" + field + "' is required");
        }
    }
}