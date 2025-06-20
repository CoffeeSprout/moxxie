package com.coffeesprout.api.filter;

import com.coffeesprout.model.LocationInfo;
import com.coffeesprout.service.LocationService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class LocationResponseFilterTest {
    
    @Inject
    LocationResponseFilter filter;
    
    @InjectMock
    LocationService locationService;
    
    private ContainerRequestContext requestContext;
    private ContainerResponseContext responseContext;
    private MultivaluedMap<String, Object> headers;
    
    @BeforeEach
    void setup() {
        requestContext = mock(ContainerRequestContext.class);
        responseContext = mock(ContainerResponseContext.class);
        headers = new MultivaluedHashMap<>();
        
        when(responseContext.getHeaders()).thenReturn(headers);
    }
    
    @Test
    void testLocationHeadersAdded() throws Exception {
        // Mock location info
        LocationInfo locationInfo = new LocationInfo(
            "proxmox",
            "nl-west-1",
            "wsdc1",
            "Worldstream DC 1",
            "NL",
            52.3676,
            4.9041,
            "moxxie-test-001"
        );
        
        when(locationService.isInitialized()).thenReturn(true);
        when(locationService.getLocationInfo()).thenReturn(locationInfo);
        
        // Apply filter
        filter.filter(requestContext, responseContext);
        
        // Verify headers were added
        assertTrue(headers.containsKey("X-Moxxie-Location"));
        assertEquals("nl-west-1/wsdc1", headers.getFirst("X-Moxxie-Location"));
        
        assertTrue(headers.containsKey("X-Moxxie-Provider"));
        assertEquals("proxmox", headers.getFirst("X-Moxxie-Provider"));
        
        assertTrue(headers.containsKey("X-Moxxie-Instance-Id"));
        assertEquals("moxxie-test-001", headers.getFirst("X-Moxxie-Instance-Id"));
    }
    
    @Test
    void testNoHeadersWhenNotInitialized() throws Exception {
        // Mock uninitialized service
        when(locationService.isInitialized()).thenReturn(false);
        
        // Apply filter
        filter.filter(requestContext, responseContext);
        
        // Verify no headers were added
        assertFalse(headers.containsKey("X-Moxxie-Location"));
        assertFalse(headers.containsKey("X-Moxxie-Provider"));
        assertFalse(headers.containsKey("X-Moxxie-Instance-Id"));
    }
    
    @Test
    void testExceptionHandling() throws Exception {
        // Mock exception
        when(locationService.isInitialized()).thenReturn(true);
        when(locationService.getLocationInfo()).thenThrow(new RuntimeException("Test error"));
        
        // Filter should not throw exception
        assertDoesNotThrow(() -> filter.filter(requestContext, responseContext));
        
        // Verify no headers were added
        assertFalse(headers.containsKey("X-Moxxie-Location"));
        assertFalse(headers.containsKey("X-Moxxie-Provider"));
        assertFalse(headers.containsKey("X-Moxxie-Instance-Id"));
    }
}