package com.coffeesprout.health;

import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.StatusResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
public class ProxmoxHealthCheck implements HealthCheck {

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Override
    public HealthCheckResponse call() {
        try {
            proxmoxClient.getStatus();
            return HealthCheckResponse.up("Proxmox API connection");
        } catch (Exception e) {
            return HealthCheckResponse.down("Proxmox API connection");
        }
    }
}