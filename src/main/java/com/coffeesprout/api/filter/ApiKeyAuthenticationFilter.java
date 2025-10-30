package com.coffeesprout.api.filter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.config.MoxxieConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional API key authentication filter for Moxxie REST API.
 *
 * <p>This filter provides simple API key authentication for internal use.
 * Since Moxxie is deployed behind Cafn8 and not directly accessible to clients,
 * this authentication is optional and disabled by default.</p>
 *
 * <h2>Configuration</h2>
 * <pre>
 * # Enable authentication
 * moxxie.api.auth-enabled=true
 * # Set API key (required when auth is enabled)
 * moxxie.api.key=your-secure-api-key
 * </pre>
 *
 * <h2>Usage</h2>
 * <p>When enabled, clients must include the API key in the X-API-Key header:</p>
 * <pre>
 * curl -H "X-API-Key: your-api-key" http://localhost:8080/api/v1/vms
 * </pre>
 *
 * <h2>Exemptions</h2>
 * <p>The following paths are exempt from authentication:</p>
 * <ul>
 *   <li>Health checks: /q/health/*</li>
 *   <li>Metrics: /q/metrics</li>
 *   <li>OpenAPI spec: /q/openapi</li>
 *   <li>Swagger UI: /q/swagger-ui/*</li>
 * </ul>
 *
 * @see MoxxieConfig.ApiConfig
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyAuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    @Inject
    MoxxieConfig config;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        // Skip authentication for Quarkus internal endpoints
        if (isExemptPath(path)) {
            LOG.trace("Skipping authentication for exempt path: {}", path);
            return;
        }

        // Check if authentication is enabled
        if (!config.api().authEnabled()) {
            LOG.trace("API authentication is disabled, allowing request");
            return;
        }

        // Validate API key
        String providedKey = requestContext.getHeaderString(API_KEY_HEADER);
        String configuredKey = config.api().key().orElse("");

        if (providedKey == null || providedKey.isEmpty()) {
            LOG.warn("Request to {} rejected: Missing API key", path);
            abortWithUnauthorized(requestContext, "Missing API key. Include X-API-Key header.");
            return;
        }

        if (configuredKey.isEmpty() || !providedKey.equals(configuredKey)) {
            LOG.warn("Request to {} rejected: Invalid API key", path);
            abortWithUnauthorized(requestContext, "Invalid API key");
            return;
        }

        // Authentication successful
        LOG.debug("Request to {} authenticated successfully", path);
    }

    /**
     * Check if the path is exempt from authentication.
     * Quarkus management endpoints and public documentation are exempt.
     */
    private boolean isExemptPath(String path) {
        return path.startsWith("q/health") ||
               path.startsWith("q/metrics") ||
               path.equals("q/openapi") ||
               path.startsWith("q/swagger-ui");
    }

    /**
     * Abort the request with 401 Unauthorized response.
     */
    private void abortWithUnauthorized(ContainerRequestContext requestContext, String message) {
        ErrorResponse error = new ErrorResponse("UNAUTHORIZED", message);
        requestContext.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(error)
                .build()
        );
    }
}
