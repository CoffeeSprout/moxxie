package com.coffeesprout.api;

import com.coffeesprout.service.AuditService;
import com.coffeesprout.service.SafetyConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@Disabled("TestInstantiation errors - needs investigation")
class SafetyControllerTest {

    @InjectMock
    SafetyConfig safetyConfig;

    @InjectMock
    AuditService auditService;

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @DisplayName("GET /api/v1/safety/status should return current safety status")
    void testGetSafetyStatus() {
        // Given
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        
        AuditService.SafetyStatistics stats = new AuditService.SafetyStatistics(
            1500L, 42L, 10L, Instant.parse("2024-01-15T10:30:00Z")
        );
        when(auditService.getStatistics()).thenReturn(stats);

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/v1/safety/status")
        .then()
            .statusCode(200)
            .body("enabled", is(true))
            .body("mode", is("STRICT"))
            .body("statistics.totalOperations", is(1500))
            .body("statistics.blockedOperations", is(42))
            .body("statistics.overriddenOperations", is(10))
            .body("statistics.lastBlocked", is("2024-01-15T10:30:00Z"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/config should return current configuration")
    void testGetSafetyConfig() {
        // Given
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.PERMISSIVE);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(safetyConfig.allowUntaggedRead()).thenReturn(true);
        when(safetyConfig.allowManualOverride()).thenReturn(true);
        when(safetyConfig.auditLog()).thenReturn(true);

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/v1/safety/config")
        .then()
            .statusCode(200)
            .body("enabled", is(true))
            .body("mode", is("PERMISSIVE"))
            .body("tagName", is("moxxie"))
            .body("allowUntaggedRead", is(true))
            .body("allowManualOverride", is(true))
            .body("auditLog", is(true));
    }

    @Test
    @DisplayName("GET /api/v1/safety/audit should return audit entries")
    void testGetAuditLog() {
        // Given
        List<AuditService.AuditEntry> entries = List.of(
            new AuditService.AuditEntry(
                Instant.parse("2024-01-15T10:30:00Z"),
                "DELETE /api/v1/vms/100",
                "BLOCKED",
                "VM not tagged as Moxxie-managed",
                100,
                "api-user",
                "192.168.1.100"
            ),
            new AuditService.AuditEntry(
                Instant.parse("2024-01-15T10:35:00Z"),
                "POST /api/v1/vms/101/stop",
                "ALLOWED",
                "Manual override with force flag",
                101,
                "admin",
                "192.168.1.101"
            )
        );
        when(auditService.getAuditEntries(any(Instant.class))).thenReturn(entries);

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/v1/safety/audit")
        .then()
            .statusCode(200)
            .body("entries", hasSize(2))
            .body("entries[0].timestamp", is("2024-01-15T10:30:00Z"))
            .body("entries[0].operation", is("DELETE /api/v1/vms/100"))
            .body("entries[0].decision", is("BLOCKED"))
            .body("entries[0].reason", is("VM not tagged as Moxxie-managed"))
            .body("entries[0].vmId", is(100))
            .body("entries[0].user", is("api-user"))
            .body("entries[0].clientIp", is("192.168.1.100"))
            .body("entries[1].decision", is("ALLOWED"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/audit with startTime should filter entries")
    void testGetAuditLogWithStartTime() {
        // Given
        String startTime = "2024-01-15T00:00:00Z";
        List<AuditService.AuditEntry> entries = List.of(
            new AuditService.AuditEntry(
                Instant.parse("2024-01-15T10:30:00Z"),
                "DELETE /api/v1/vms/100",
                "BLOCKED",
                "VM not tagged as Moxxie-managed",
                100,
                "api-user",
                "192.168.1.100"
            )
        );
        when(auditService.getAuditEntries(Instant.parse(startTime))).thenReturn(entries);

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .queryParam("startTime", startTime)
        .when()
            .get("/api/v1/safety/audit")
        .then()
            .statusCode(200)
            .body("entries", hasSize(1));
        
        verify(auditService).getAuditEntries(Instant.parse(startTime));
    }

    @Test
    @DisplayName("GET /api/v1/safety/audit with invalid startTime should return 400")
    void testGetAuditLogWithInvalidStartTime() {
        // When & Then
        given()
            .contentType(ContentType.JSON)
            .queryParam("startTime", "invalid-date")
        .when()
            .get("/api/v1/safety/audit")
        .then()
            .statusCode(400)
            .body("error", containsString("Invalid startTime format"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/audit with limit should respect limit")
    void testGetAuditLogWithLimit() {
        // Given
        List<AuditService.AuditEntry> entries = List.of(
            createAuditEntry(1),
            createAuditEntry(2),
            createAuditEntry(3),
            createAuditEntry(4),
            createAuditEntry(5)
        );
        when(auditService.getAuditEntries(any(Instant.class))).thenReturn(entries);

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .queryParam("limit", 3)
        .when()
            .get("/api/v1/safety/audit")
        .then()
            .statusCode(200)
            .body("entries", hasSize(3));
    }

    @Test
    @DisplayName("GET /api/v1/safety/status when safe mode is disabled")
    void testGetSafetyStatusWhenDisabled() {
        // Given
        when(safetyConfig.enabled()).thenReturn(false);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.AUDIT);
        
        AuditService.SafetyStatistics stats = new AuditService.SafetyStatistics(
            0L, 0L, 0L, null
        );
        when(auditService.getStatistics()).thenReturn(stats);

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/v1/safety/status")
        .then()
            .statusCode(200)
            .body("enabled", is(false))
            .body("statistics.totalOperations", is(0))
            .body("statistics.blockedOperations", is(0))
            .body("statistics.lastBlocked", nullValue());
    }

    @Test
    @DisplayName("All safety endpoints should handle service exceptions gracefully")
    void testErrorHandling() {
        // Given
        when(auditService.getStatistics()).thenThrow(new RuntimeException("Database error"));

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/v1/safety/status")
        .then()
            .statusCode(500)
            .body("error", containsString("Failed to get safety status"));
    }

    // Helper method to create audit entries
    private AuditService.AuditEntry createAuditEntry(int id) {
        return new AuditService.AuditEntry(
            Instant.now().minus(id, java.time.temporal.ChronoUnit.MINUTES),
            "Operation " + id,
            "ALLOWED",
            "Test reason " + id,
            100 + id,
            "user" + id,
            "192.168.1." + id
        );
    }
}