package com.coffeesprout.api.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.coffeesprout.constants.HttpStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global exception mapper for consistent error handling across all REST endpoints.
 * This mapper intercepts all exceptions and converts them to standardized error responses.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionMapper.class);

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof WebApplicationException) {
            return handleWebApplicationException((WebApplicationException) exception);
        }

        if (exception instanceof IllegalArgumentException) {
            return handleValidationException(exception);
        }

        if (exception instanceof ProxmoxException) {
            return handleProxmoxException((ProxmoxException) exception);
        }

        // Generic server error
        return handleGenericException(exception);
    }

    private Response handleWebApplicationException(WebApplicationException e) {
        Response originalResponse = e.getResponse();
        int status = originalResponse.getStatus();

        // Try to extract Proxmox error details if available
        if (originalResponse.hasEntity()) {
            try {
                String body = originalResponse.readEntity(String.class);
                JsonNode errorJson = objectMapper.readTree(body);

                if (errorJson.has("errors")) {
                    // Parse Proxmox error format
                    Map<String, String> fieldErrors = new HashMap<>();
                    JsonNode errors = errorJson.get("errors");
                    errors.fields().forEachRemaining(field -> {
                        fieldErrors.put(field.getKey(), field.getValue().asText());
                    });

                    String message = buildDetailedErrorMessage(fieldErrors);
                    LOG.error("Proxmox API error: {}", message);

                    return Response.status(status)
                        .entity(new ApiErrorResponse(
                            "PROXMOX_ERROR",
                            message,
                            status,
                            Instant.now().toString(),
                            null,
                            fieldErrors
                        ))
                        .build();
                }
            } catch (Exception parseError) {
                LOG.debug("Failed to parse error response", parseError);
            }
        }

        // Default WebApplicationException handling
        String message = e.getMessage() != null ? e.getMessage() : getDefaultMessageForStatus(status);

        return Response.status(status)
            .entity(new ApiErrorResponse(
                getErrorCodeForStatus(status),
                message,
                status,
                Instant.now().toString(),
                null,
                null
            ))
            .build();
    }

    private Response handleValidationException(Exception e) {
        LOG.warn("Validation error: {}", e.getMessage());

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Invalid request: " + e.getMessage(),
                HttpStatus.BAD_REQUEST,
                Instant.now().toString(),
                null,
                null
            ))
            .build();
    }

    private Response handleProxmoxException(ProxmoxException e) {
        LOG.error("Proxmox operation failed: {}", e.getMessage());

        return Response.status(e.getHttpStatus())
            .entity(new ApiErrorResponse(
                e.getErrorCode(),
                e.getMessage(),
                e.getHttpStatus(),
                Instant.now().toString(),
                null,
                e.getDetails()
            ))
            .build();
    }

    private Response handleGenericException(Exception e) {
        LOG.error("Unexpected error", e);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ApiErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please check logs for details.",
                HttpStatus.INTERNAL_SERVER_ERROR,
                Instant.now().toString(),
                null,
                null
            ))
            .build();
    }

    private String buildDetailedErrorMessage(Map<String, String> fieldErrors) {
        if (fieldErrors.isEmpty()) {
            return "Validation failed";
        }

        if (fieldErrors.size() == 1) {
            Map.Entry<String, String> entry = fieldErrors.entrySet().iterator().next();
            return String.format("Field '%s': %s", entry.getKey(), cleanErrorMessage(entry.getValue()));
        }

        StringBuilder sb = new StringBuilder("Multiple validation errors: ");
        fieldErrors.forEach((field, error) -> {
            sb.append(String.format("[%s: %s] ", field, cleanErrorMessage(error)));
        });

        return sb.toString().trim();
    }

    private String cleanErrorMessage(String error) {
        // Clean up Proxmox error messages
        return error.replaceAll("\\n", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    private String getErrorCodeForStatus(int status) {
        switch (status) {
            case HttpStatus.BAD_REQUEST: return "BAD_REQUEST";
            case HttpStatus.UNAUTHORIZED: return "UNAUTHORIZED";
            case HttpStatus.FORBIDDEN: return "FORBIDDEN";
            case HttpStatus.NOT_FOUND: return "NOT_FOUND";
            case HttpStatus.CONFLICT: return "CONFLICT";
            case HttpStatus.UNPROCESSABLE_ENTITY: return "UNPROCESSABLE_ENTITY";
            case HttpStatus.INTERNAL_SERVER_ERROR: return "INTERNAL_ERROR";
            case HttpStatus.BAD_GATEWAY: return "BAD_GATEWAY";
            case HttpStatus.SERVICE_UNAVAILABLE: return "SERVICE_UNAVAILABLE";
            default: return "ERROR";
        }
    }

    private String getDefaultMessageForStatus(int status) {
        switch (status) {
            case HttpStatus.BAD_REQUEST: return "Bad request";
            case HttpStatus.UNAUTHORIZED: return "Authentication required";
            case HttpStatus.FORBIDDEN: return "Access denied";
            case HttpStatus.NOT_FOUND: return "Resource not found";
            case HttpStatus.CONFLICT: return "Resource conflict";
            case HttpStatus.UNPROCESSABLE_ENTITY: return "Unprocessable entity";
            case HttpStatus.INTERNAL_SERVER_ERROR: return "Internal server error";
            case HttpStatus.BAD_GATEWAY: return "Bad gateway";
            case HttpStatus.SERVICE_UNAVAILABLE: return "Service unavailable";
            default: return "Error";
        }
    }

    /**
     * Enhanced error response with additional fields
     */
    public static class ApiErrorResponse {
        private final String error;
        private final String message;
        private final int status;
        private final String timestamp;
        private final Map<String, Object> details;

        public ApiErrorResponse(String error, String message, int status, String timestamp,
                               String path, Map<String, ?> details) {
            this.error = error;
            this.message = message;
            this.status = status;
            this.timestamp = timestamp;
            this.details = details != null ? new HashMap<>(details) : null;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public int getStatus() {
            return status;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }
}
