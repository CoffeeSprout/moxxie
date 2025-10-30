package com.coffeesprout.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.coffeesprout.config.MoxxieConfig;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates critical configuration at startup to fail-fast on misconfiguration.
 *
 * <p>This validator checks for common configuration issues that would cause
 * runtime failures or security problems. It runs during application startup
 * and throws IllegalStateException if critical problems are detected.</p>
 *
 * <h2>Validation Checks</h2>
 * <ul>
 *   <li>Production database configuration (password, connection URL)</li>
 *   <li>API authentication configuration (when enabled)</li>
 *   <li>SSL certificate configuration (warnings in production)</li>
 *   <li>Required Proxmox configuration</li>
 * </ul>
 *
 * <h2>Environment-Specific Behavior</h2>
 * <p>Most validation is skipped in development environment to allow easy local setup.
 * In production and staging, all checks are enforced to prevent misconfigurations.</p>
 *
 * @see MoxxieConfig
 * @see TicketManager
 */
@ApplicationScoped
@Startup
public class ConfigValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigValidator.class);

    @Inject
    MoxxieConfig config;

    @Inject
    SafetyConfig safetyConfig;

    /**
     * Validate configuration on application startup.
     * Observes the StartupEvent to run validation early in the lifecycle.
     */
    void validateOnStartup(@Observes StartupEvent event) {
        LOG.info("Validating Moxxie configuration...");

        String environment = config.instance().environment();
        boolean isProduction = !"development".equalsIgnoreCase(environment);

        // Always validate basic configuration
        validateProxmoxUrl();

        if (isProduction) {
            // Enforce strict validation in production
            validateProductionDatabase();
            validateApiAuthentication();
            warnOnInsecureSettings();
            LOG.info("✓ Production configuration validated successfully");
        } else {
            // Development mode - warn about missing configuration
            warnOnDevelopmentIssues();
            LOG.info("✓ Development configuration validated (relaxed mode)");
        }
    }

    /**
     * Validate Proxmox URL is configured.
     */
    private void validateProxmoxUrl() {
        String url = config.proxmox().url();
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException(
                "Proxmox URL is not configured. " +
                "Set MOXXIE_PROXMOX_URL environment variable."
            );
        }

        // Warn if using default local address in production
        if (!"development".equalsIgnoreCase(config.instance().environment())) {
            if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.0.10")) {
                LOG.warn("⚠️  Proxmox URL '{}' looks like a development address. " +
                        "Ensure this is correct for your environment.", url);
            }
        }
    }

    /**
     * Validate production database configuration.
     * Ensures database password is set and connection URL is valid.
     */
    private void validateProductionDatabase() {
        // Note: Database password validation is handled by Quarkus datasource validation
        // We just add additional checks here

        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            LOG.warn("⚠️  DB_URL not set, using default: jdbc:postgresql://localhost:5432/moxxie");
        }

        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isEmpty()) {
            throw new IllegalStateException(
                "Database password is not configured in production. " +
                "Set DB_PASSWORD environment variable."
            );
        }

        LOG.debug("✓ Database configuration validated");
    }

    /**
     * Validate API authentication configuration when enabled.
     */
    private void validateApiAuthentication() {
        if (config.api().authEnabled()) {
            Optional<String> apiKeyOpt = config.api().key();
            if (apiKeyOpt.isEmpty() || apiKeyOpt.get().isEmpty()) {
                throw new IllegalStateException(
                    "API authentication is enabled but no API key is configured. " +
                    "Set MOXXIE_API_KEY environment variable or disable authentication."
                );
            }

            String apiKey = apiKeyOpt.get();
            // Warn about weak keys in production
            if (apiKey.length() < 32) {
                LOG.warn("⚠️  API key is shorter than 32 characters. " +
                        "Use a strong random key: openssl rand -hex 32");
            }

            LOG.debug("✓ API authentication configured with key length: {}", apiKey.length());
        } else {
            LOG.warn("⚠️  API authentication is DISABLED in production. " +
                    "Consider enabling with MOXXIE_API_AUTH_ENABLED=true for audit trail.");
        }
    }

    /**
     * Warn about insecure settings in production.
     * These don't block startup but should be addressed.
     */
    private void warnOnInsecureSettings() {
        // Check SSL verification
        if (!config.proxmox().verifySsl()) {
            LOG.warn("⚠️  SSL certificate verification is DISABLED in production! " +
                    "This is a security risk. Set up certificate bundle with MOXXIE_CERT_BUNDLE.");
        }

        // Check CORS configuration
        String corsOrigins = System.getenv("QUARKUS_HTTP_CORS_ORIGINS");
        if (corsOrigins != null && corsOrigins.contains("*")) {
            LOG.warn("⚠️  CORS is set to allow all origins (*) in production. " +
                    "Restrict to specific origins for security.");
        }

        // Check Safe Mode
        if (!safetyConfig.enabled()) {
            LOG.warn("⚠️  Safe Mode is DISABLED. " +
                    "Enable with MOXXIE_SAFETY_ENABLED=true to prevent accidental operations on non-Moxxie VMs.");
        }
    }

    /**
     * Warn about common development configuration issues.
     * Non-blocking warnings to help developers set up correctly.
     */
    private void warnOnDevelopmentIssues() {
        // Check if using default Proxmox URL
        if (config.proxmox().url().contains("10.0.0.10")) {
            LOG.info("ℹ️  Using default Proxmox URL. Update MOXXIE_PROXMOX_URL if different.");
        }

        // Note about SSL verification
        if (!config.proxmox().verifySsl()) {
            LOG.info("ℹ️  SSL verification is disabled (ok for development with self-signed certs)");
        }

        // Note about API authentication
        if (!config.api().authEnabled()) {
            LOG.info("ℹ️  API authentication is disabled (ok for local development)");
        }
    }
}
