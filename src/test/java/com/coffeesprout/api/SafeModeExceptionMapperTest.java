package com.coffeesprout.api;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.service.SafeModeViolationException;
import com.coffeesprout.service.SafetyDecision;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SafeModeExceptionMapperTest {

    @Inject
    SafeModeExceptionMapper exceptionMapper;

    @Test
    @DisplayName("Should map SafeModeViolationException to 403 response")
    void testMapException() {
        // Given
        SafetyDecision decision = SafetyDecision.blocked("VM not tagged as Moxxie-managed");
        SafeModeViolationException exception = new SafeModeViolationException(decision);

        // When
        Response response = exceptionMapper.toResponse(exception);

        // Then
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("SAFE_MODE_VIOLATION", entity.get("error"));
        assertEquals("Safe mode violation: VM not tagged as Moxxie-managed", entity.get("message"));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) entity.get("details");
        assertEquals("VM not tagged as Moxxie-managed", details.get("reason"));
        assertEquals("Use force=true to override or tag the VM with 'moxxie'", details.get("suggestion"));

        assertNotNull(entity.get("timestamp"));
    }

    @Test
    @DisplayName("Should preserve original decision reason")
    void testPreserveDecisionReason() {
        // Given
        String customReason = "Destructive operation on non-Moxxie VM";
        SafetyDecision decision = SafetyDecision.blocked(customReason);
        SafeModeViolationException exception = new SafeModeViolationException(decision);

        // When
        Response response = exceptionMapper.toResponse(exception);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) entity.get("details");
        assertEquals(customReason, details.get("reason"));
    }

    @Test
    @DisplayName("Response should include timestamp")
    void testResponseIncludesTimestamp() {
        // Given
        SafetyDecision decision = SafetyDecision.blocked("Test reason");
        SafeModeViolationException exception = new SafeModeViolationException(decision);

        // When
        Response response = exceptionMapper.toResponse(exception);

        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        String timestamp = (String) entity.get("timestamp");

        assertNotNull(timestamp);
        // Basic check that it's an ISO-8601 timestamp
        assertTrue(timestamp.contains("T"));
        assertTrue(timestamp.contains("-"));
    }
}
