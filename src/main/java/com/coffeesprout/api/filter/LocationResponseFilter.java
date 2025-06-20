package com.coffeesprout.api.filter;

import com.coffeesprout.model.LocationInfo;
import com.coffeesprout.service.LocationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JAX-RS response filter that adds location headers to all API responses.
 * This enables federation-aware clients to understand which location they're talking to.
 */
@Provider
public class LocationResponseFilter implements ContainerResponseFilter {
    
    private static final Logger log = LoggerFactory.getLogger(LocationResponseFilter.class);
    
    @Inject
    LocationService locationService;
    
    @Override
    public void filter(ContainerRequestContext requestContext, 
                      ContainerResponseContext responseContext) throws IOException {
        
        // Only add headers if location service is initialized
        if (!locationService.isInitialized()) {
            return;
        }
        
        try {
            LocationInfo location = locationService.getLocationInfo();
            
            // Add location headers as specified in the federation spec
            responseContext.getHeaders().add("X-Moxxie-Location", location.fullLocation());
            responseContext.getHeaders().add("X-Moxxie-Provider", location.provider());
            responseContext.getHeaders().add("X-Moxxie-Instance-Id", location.instanceId());
            
        } catch (Exception e) {
            // Log but don't fail the request if headers can't be added
            log.warn("Failed to add location headers to response", e);
        }
    }
}