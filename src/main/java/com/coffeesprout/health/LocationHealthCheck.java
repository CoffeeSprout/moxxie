package com.coffeesprout.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.model.LocationInfo;
import com.coffeesprout.service.LocationService;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Health check that provides detailed location information
 * for federation-aware monitoring and discovery
 */
@Readiness
@ApplicationScoped
public class LocationHealthCheck implements HealthCheck {

    @Inject
    LocationService locationService;

    @Override
    public HealthCheckResponse call() {
        try {
            if (!locationService.isInitialized()) {
                return HealthCheckResponse.builder()
                    .name("Location Service")
                    .down()
                    .withData("error", "Location information not initialized")
                    .build();
            }

            LocationInfo location = locationService.getLocationInfo();

            return HealthCheckResponse.builder()
                .name("Location Service")
                .up()
                .withData("provider", location.provider())
                .withData("region", location.region())
                .withData("datacenter", location.datacenter())
                .withData("name", location.name())
                .withData("country", location.country())
                .withData("latitude", location.latitude().toString())
                .withData("longitude", location.longitude().toString())
                .withData("instanceId", location.instanceId())
                .withData("fullLocation", location.fullLocation())
                .build();

        } catch (Exception e) {
            return HealthCheckResponse.builder()
                .name("Location Service")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}
