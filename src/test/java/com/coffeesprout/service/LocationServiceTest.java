package com.coffeesprout.service;

import jakarta.inject.Inject;

import com.coffeesprout.model.LocationInfo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LocationServiceTest {

    @Inject
    LocationService locationService;

    @Test
    void testLocationInitialization() {
        // Service should already be initialized by Quarkus startup
        assertTrue(locationService.isInitialized());

        LocationInfo location = locationService.getLocationInfo();
        assertNotNull(location);
        assertEquals("proxmox", location.provider());
        assertEquals("test-region", location.region());
        assertEquals("test-dc", location.datacenter());
        assertEquals("Test Datacenter", location.name());
        assertEquals("NL", location.country());
        assertEquals(52.3676, location.latitude());
        assertEquals(4.9041, location.longitude());
        assertEquals("moxxie-test-instance", location.instanceId());
    }

    @Test
    void testFullLocation() {
        String fullLocation = locationService.getFullLocation();
        assertEquals("test-region/test-dc", fullLocation);
    }

    @Test
    void testLocationInfoValidation() {
        // Test that a valid LocationInfo passes validation
        LocationInfo validLocation = new LocationInfo(
            "proxmox",
            "nl-west-1",
            "wsdc1",
            "Test DC",
            "NL",
            52.3676,
            4.9041,
            "test-id"
        );

        // Should not throw
        assertDoesNotThrow(() -> validLocation.validate());
    }

    @Test
    void testLocationInfoValidation_InvalidLatitude() {
        LocationInfo invalidLocation = new LocationInfo(
            "proxmox",
            "nl-west-1",
            "wsdc1",
            "Test DC",
            "NL",
            91.0, // Invalid latitude
            4.9041,
            "test-id"
        );

        assertThrows(IllegalArgumentException.class, () -> invalidLocation.validate());
    }

    @Test
    void testLocationInfoValidation_InvalidCountryCode() {
        LocationInfo invalidLocation = new LocationInfo(
            "proxmox",
            "nl-west-1",
            "wsdc1",
            "Test DC",
            "NLD", // Should be 2 chars
            52.3676,
            4.9041,
            "test-id"
        );

        assertThrows(IllegalArgumentException.class, () -> invalidLocation.validate());
    }
}
