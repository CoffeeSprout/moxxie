package com.coffeesprout.api.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.MDC;

/**
 * Filter that adds correlation IDs to all requests for tracing across services.
 * The correlation ID is added to MDC for logging and propagated in response headers.
 */
@Provider
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Check if correlation ID exists in request headers
        String correlationId = requestContext.getHeaderString(CORRELATION_ID_HEADER);

        // Generate new correlation ID if not present
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Generate unique request ID
        String requestId = UUID.randomUUID().toString();

        // Add to MDC for logging
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);

        // Store in request context for response filter
        requestContext.setProperty(CORRELATION_ID_HEADER, correlationId);

        // Add request metadata to MDC
        MDC.put("method", requestContext.getMethod());
        MDC.put("path", requestContext.getUriInfo().getPath());
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                      ContainerResponseContext responseContext) throws IOException {
        // Get correlation ID from request context
        String correlationId = (String) requestContext.getProperty(CORRELATION_ID_HEADER);

        // Add correlation ID to response headers
        if (correlationId != null) {
            responseContext.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        }

        // Clean up MDC
        MDC.remove(CORRELATION_ID_MDC_KEY);
        MDC.remove(REQUEST_ID_MDC_KEY);
        MDC.remove("method");
        MDC.remove("path");
    }
}
