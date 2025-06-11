package com.coffeesprout.service;

import com.coffeesprout.config.MoxxieConfig;
import com.coffeesprout.client.LoginResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages Proxmox authentication tickets with automatic refresh.
 * Thread-safe implementation for concurrent access.
 */
@ApplicationScoped
public class TicketManager {
    
    private static final Logger log = LoggerFactory.getLogger(TicketManager.class);
    
    // Proxmox tickets are valid for 2 hours, refresh after 1.5 hours to be safe
    private static final Duration TICKET_REFRESH_INTERVAL = Duration.ofMinutes(90);
    
    @Inject
    AuthService authService;
    
    @Inject
    MoxxieConfig config;
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private String currentTicket;
    private String currentCsrfToken;
    private Instant ticketExpiry;
    private boolean authenticationFailed = false;
    
    @PostConstruct
    void init() {
        // Eagerly authenticate on startup if credentials are configured
        if (config.proxmox().password() != null && !config.proxmox().password().isEmpty()) {
            try {
                refreshTicket();
                log.info("Successfully authenticated with Proxmox on startup");
            } catch (Exception e) {
                log.warn("Failed to authenticate on startup, will retry on first request: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Get a valid authentication ticket, refreshing if necessary.
     * Thread-safe method that handles concurrent access.
     */
    public String getTicket() {
        // Fast path - read lock for checking if ticket is valid
        lock.readLock().lock();
        try {
            if (isTicketValid()) {
                return currentTicket;
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Slow path - write lock for refreshing ticket
        lock.writeLock().lock();
        try {
            // Double-check pattern - another thread might have refreshed while we waited
            if (isTicketValid()) {
                return currentTicket;
            }
            
            refreshTicket();
            return currentTicket;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the CSRF prevention token.
     * Thread-safe method that returns the current CSRF token.
     */
    public String getCsrfToken() {
        lock.readLock().lock();
        try {
            return currentCsrfToken;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Force refresh of the authentication ticket.
     * Useful when a request fails with 401.
     */
    public void forceRefresh() {
        lock.writeLock().lock();
        try {
            log.debug("Forcing ticket refresh");
            refreshTicket();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if authentication has failed.
     * Used to provide better error messages.
     */
    public boolean hasAuthenticationFailed() {
        lock.readLock().lock();
        try {
            return authenticationFailed;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private boolean isTicketValid() {
        return currentTicket != null && 
               ticketExpiry != null && 
               Instant.now().isBefore(ticketExpiry) &&
               !authenticationFailed;
    }
    
    private void refreshTicket() {
        try {
            String username = config.proxmox().username();
            String password = config.proxmox().password();
            
            if (password == null || password.isEmpty()) {
                authenticationFailed = true;
                throw new IllegalStateException("Proxmox password not configured. Set MOXXIE_PROXMOX_PASSWORD environment variable.");
            }
            
            log.info("Authenticating with Proxmox as user: {}", username);
            LoginResponse response = authService.authenticate(username, password);
            currentTicket = response.getData().getTicket();
            currentCsrfToken = response.getData().getCsrfPreventionToken();
            ticketExpiry = Instant.now().plus(TICKET_REFRESH_INTERVAL);
            authenticationFailed = false;
            
            log.debug("Successfully refreshed authentication ticket, valid until: {}", ticketExpiry);
        } catch (Exception e) {
            authenticationFailed = true;
            currentTicket = null;
            currentCsrfToken = null;
            ticketExpiry = null;
            log.error("Failed to refresh authentication ticket: {}", e.getMessage());
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }
}