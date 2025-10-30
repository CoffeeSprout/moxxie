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

    public static ProxmoxException notFound(String resourceType, String identifier, String suggestion) {
        return new ProxmoxException(
            404,
            "RESOURCE_NOT_FOUND",
            String.format("%s with identifier '%s' not found. %s", resourceType, identifier, suggestion)
        ).withDetail("resourceType", resourceType)
         .withDetail("identifier", identifier)
         .withDetail("suggestion", suggestion);
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

    public static ProxmoxException forbidden(String resource, String action, String suggestion) {
        return new ProxmoxException(
            403,
            "FORBIDDEN",
            String.format("Access denied: cannot %s %s. %s", action, resource, suggestion)
        ).withDetail("resource", resource)
         .withDetail("action", action)
         .withDetail("suggestion", suggestion);
    }

    public static ProxmoxException serviceUnavailable(String service, String reason) {
        return new ProxmoxException(
            503,
            "SERVICE_UNAVAILABLE",
            String.format("%s is unavailable: %s", service, reason)
        ).withDetail("service", service);
    }

    public static ProxmoxException internalError(String operation, Throwable cause) {
        String message = cause != null && cause.getMessage() != null ?
            cause.getMessage() : "Unknown error";
        return new ProxmoxException(
            500,
            "INTERNAL_ERROR",
            String.format("Failed to %s: %s", operation, message),
            cause
        ).withDetail("operation", operation);
    }

    public static ProxmoxException internalError(String message) {
        return new ProxmoxException(
            500,
            "INTERNAL_ERROR",
            message
        );
    }

    public static ProxmoxException badRequest(String message) {
        return new ProxmoxException(
            400,
            "BAD_REQUEST",
            message
        );
    }

    public static ProxmoxException timeout(String operation, int timeoutSeconds) {
        return new ProxmoxException(
            504,
            "OPERATION_TIMEOUT",
            String.format("Operation '%s' timed out after %d seconds", operation, timeoutSeconds)
        ).withDetail("operation", operation)
         .withDetail("timeout", String.valueOf(timeoutSeconds));
    }

    // Enhanced exception factory methods with context

    public static ProxmoxException vmOperationFailed(String operation, int vmId, String reason) {
        return new ProxmoxException(
            500,
            "VM_OPERATION_FAILED",
            String.format("Failed to %s VM %d: %s", operation, vmId, reason)
        ).withDetail("operation", operation)
         .withDetail("vmId", String.valueOf(vmId))
         .withDetail("reason", reason);
    }

    public static ProxmoxException vmOperationFailed(String operation, int vmId, String vmName, String reason, String suggestion) {
        return new ProxmoxException(
            500,
            "VM_OPERATION_FAILED",
            String.format("Failed to %s VM %d (%s): %s. %s", operation, vmId, vmName, reason, suggestion)
        ).withDetail("operation", operation)
         .withDetail("vmId", String.valueOf(vmId))
         .withDetail("vmName", vmName)
         .withDetail("reason", reason)
         .withDetail("suggestion", suggestion);
    }

    public static ProxmoxException invalidConfiguration(String component, String issue, String suggestion) {
        return new ProxmoxException(
            400,
            "INVALID_CONFIGURATION",
            String.format("Invalid %s configuration: %s. %s", component, issue, suggestion)
        ).withDetail("component", component)
         .withDetail("issue", issue)
         .withDetail("suggestion", suggestion);
    }

    public static ProxmoxException resourceLimitExceeded(String resource, int current, int max) {
        return new ProxmoxException(
            400,
            "RESOURCE_LIMIT_EXCEEDED",
            String.format("%s limit exceeded: %d (maximum: %d)", resource, current, max)
        ).withDetail("resource", resource)
         .withDetail("current", String.valueOf(current))
         .withDetail("maximum", String.valueOf(max));
    }

    public static ProxmoxException operationNotSupported(String operation, String reason) {
        return new ProxmoxException(
            400,
            "OPERATION_NOT_SUPPORTED",
            String.format("Operation '%s' is not supported: %s", operation, reason)
        ).withDetail("operation", operation)
         .withDetail("reason", reason);
    }

    public static ProxmoxException prerequisiteFailed(String operation, String prerequisite, String suggestion) {
        return new ProxmoxException(
            412,
            "PREREQUISITE_FAILED",
            String.format("Cannot %s: %s. %s", operation, prerequisite, suggestion)
        ).withDetail("operation", operation)
         .withDetail("prerequisite", prerequisite)
         .withDetail("suggestion", suggestion);
    }

    public static ProxmoxException resourceBusy(String resource, String currentOperation, String suggestion) {
        return new ProxmoxException(
            423,
            "RESOURCE_BUSY",
            String.format("%s is busy with %s. %s", resource, currentOperation, suggestion)
        ).withDetail("resource", resource)
         .withDetail("currentOperation", currentOperation)
         .withDetail("suggestion", suggestion);
    }

    public static ProxmoxException networkError(String operation, String target, Throwable cause) {
        String message = cause != null && cause.getMessage() != null ?
            cause.getMessage() : "Network communication failed";
        return new ProxmoxException(
            502,
            "NETWORK_ERROR",
            String.format("Network error during %s to %s: %s", operation, target, message),
            cause
        ).withDetail("operation", operation)
         .withDetail("target", target);
    }
}
