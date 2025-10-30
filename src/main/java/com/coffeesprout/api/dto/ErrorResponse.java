package com.coffeesprout.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponse(
    @JsonProperty("error") String error,
    @JsonProperty("message") String message,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("path") String path
) {
    public ErrorResponse(String error) {
        this(error, error, Instant.now(), null);
    }

    public ErrorResponse(String error, String message) {
        this(error, message, Instant.now(), null);
    }
}
