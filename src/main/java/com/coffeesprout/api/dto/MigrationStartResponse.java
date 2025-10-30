package com.coffeesprout.api.dto;

/**
 * Response for migration start - returns immediately with task info
 */
public record MigrationStartResponse(
    Long migrationId,
    String taskUpid,
    String message,
    Integer vmId,
    String sourceNode,
    String targetNode,
    String statusUrl
) {}
