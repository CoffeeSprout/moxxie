package com.coffeesprout.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.client.LoginResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.config.MoxxieConfig;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Inject
    MoxxieConfig config;

    public LoginResponse authenticate(String username, String password) {
        LoginResponse loginResponse = proxmoxClient.login(username, password);
        LOG.debug("Successfully authenticated user: {}", username);
        return loginResponse;
    }

    /**
     * Authenticate using configured credentials
     */
    public LoginResponse authenticateWithConfig() {
        return authenticate(config.proxmox().username(), config.proxmox().password());
    }

    public boolean validateTicket(String ticket) {
        // In a real implementation, this could validate the ticket with Proxmox
        // For now, just check if it's not null or empty
        return ticket != null && !ticket.isEmpty();
    }
}
