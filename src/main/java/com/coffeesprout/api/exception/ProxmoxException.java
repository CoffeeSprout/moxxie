package com.coffeesprout.api.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom exception for Proxmox-related errors with enhanced error information.
 */
public class ProxmoxException extends RuntimeException {
    
    private final int httpStatus;
    private final String errorCode;
    private final Map<String, String> details;
    
    public ProxmoxException(int httpStatus, String errorCode, String message) {
        this(httpStatus, errorCode, message, null, null);
    }
    
    public ProxmoxException(int httpStatus, String errorCode, String message, Map<String, String> details) {
        this(httpStatus, errorCode, message, details, null);
    }
    
    public ProxmoxException(int httpStatus, String errorCode, String message, Throwable cause) {
        this(httpStatus, errorCode, message, null, cause);
    }
    
    public ProxmoxException(int httpStatus, String errorCode, String message, Map<String, String> details, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Map<String, String> getDetails() {
        return new HashMap<>(details);
    }
    
    public ProxmoxException withDetail(String key, String value) {
        this.details.put(key, value);
        return this;
    }
    
    // Common exception factory methods
    
    public static ProxmoxException notFound(String resourceType, String identifier) {
        return new ProxmoxException(
            404, 
            "RESOURCE_NOT_FOUND",
            String.format("%s with identifier '%s' not found", resourceType, identifier)
        ).withDetail("resourceType", resourceType)
         .withDetail("identifier", identifier);
    }
    
    public static ProxmoxException conflict(String resource, String reason) {
        return new ProxmoxException(
            409,
            "RESOURCE_CONFLICT",
            String.format("Conflict with %s: %s", resource, reason)
        ).withDetail("resource", resource);
    }
    
    public static ProxmoxException validation(String field, String value, String constraint) {
        return new ProxmoxException(
            400,
            "VALIDATION_ERROR",
            String.format("Field '%s' with value '%s' failed constraint: %s", field, value, constraint)
        ).withDetail("field", field)
         .withDetail("value", value)
         .withDetail("constraint", constraint);
    }
    
    public static ProxmoxException unauthorized(String reason) {
        return new ProxmoxException(
            401,
            "UNAUTHORIZED",
            reason != null ? reason : "Authentication required"
        );
    }
    
    public static ProxmoxException forbidden(String resource, String action) {
        return new ProxmoxException(
            403,
            "FORBIDDEN",
            String.format("Access denied: cannot %s %s", action, resource)
        ).withDetail("resource", resource)
         .withDetail("action", action);
    }
    
    public static ProxmoxException serviceUnavailable(String service, String reason) {
        return new ProxmoxException(
            503,
            "SERVICE_UNAVAILABLE",
            String.format("%s is unavailable: %s", service, reason)
        ).withDetail("service", service);
    }
}