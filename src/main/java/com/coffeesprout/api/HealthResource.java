package com.coffeesprout.api;

import com.coffeesprout.model.LocationInfo;
import com.coffeesprout.service.LocationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom health endpoint that provides location-aware health status
 * for federation support
 */
@Path("/health")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    
    @Inject
    LocationService locationService;
    
    @GET
    public Response getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // Basic health status
        health.put("status", "UP");
        
        // Add location information
        if (locationService.isInitialized()) {
            LocationInfo location = locationService.getLocationInfo();
            
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("provider", location.provider());
            locationData.put("region", location.region());
            locationData.put("datacenter", location.datacenter());
            locationData.put("name", location.name());
            
            Map<String, Double> coordinates = new HashMap<>();
            coordinates.put("latitude", location.latitude());
            coordinates.put("longitude", location.longitude());
            locationData.put("coordinates", coordinates);
            
            health.put("location", locationData);
            health.put("instance_id", location.instanceId());
        }
        
        return Response.ok(health).build();
    }
}