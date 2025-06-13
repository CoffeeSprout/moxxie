package com.coffeesprout.api;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.service.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@Disabled("Temporarily disabled due to InjectMock issues - needs migration to new Quarkus mock approach")
class VMResourceSafeModeTest {

    @InjectMock
    VMService vmService;

    @InjectMock
    TagService tagService;

    @InjectMock
    SafetyConfig safetyConfig;

    @InjectMock
    AuditService auditService;

    private VMResponse testVM;

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        // Setup test VM
        testVM = new VMResponse(
            100,                // vmid
            "test-vm",          // name
            "node1",            // node
            "running",          // status
            2,                  // cpus
            2147483648L,        // maxmem (2GB)
            0L,                 // maxdisk
            0L,                 // uptime
            "qemu",             // type
            List.of(),          // tags
            null                // pool
        );
        
        // Default safety config
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(safetyConfig.allowManualOverride()).thenReturn(true);
        when(safetyConfig.auditLog()).thenReturn(true);
    }

    @Test
    @DisplayName("DELETE /api/v1/vms/{vmId} should be blocked for non-Moxxie VMs in strict mode")
    void testDeleteBlockedInStrictMode() {
        // Given
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("production", "critical"));

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/api/v1/vms/100")
        .then()
            .statusCode(403)
            .body("error", is("SAFE_MODE_VIOLATION"))
            .body("message", containsString("VM not tagged as Moxxie-managed"))
            .body("details.suggestion", containsString("force=true"));
        
        verify(vmService, never()).deleteVM(anyString(), anyInt(), any());
        verify(auditService).logBlocked(any(), argThat(decision -> decision instanceof SafetyDecision));
    }

    @Test
    @DisplayName("DELETE /api/v1/vms/{vmId} should be allowed for Moxxie VMs in strict mode")
    void testDeleteAllowedForMoxxieVMs() throws Exception {
        // Given
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("moxxie", "test"));
        doNothing().when(vmService).deleteVM("node1", 100, null);

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/api/v1/vms/100")
        .then()
            .statusCode(204);
        
        verify(vmService).deleteVM("node1", 100, null);
        verify(auditService).logAllowed(any(), argThat(decision -> decision instanceof SafetyDecision));
    }

    @Test
    @DisplayName("DELETE /api/v1/vms/{vmId}?force=true should override strict mode")
    void testDeleteWithForceOverride() throws Exception {
        // Given
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        doNothing().when(vmService).deleteVM("node1", 100, null);

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .queryParam("force", true)
        .when()
            .delete("/api/v1/vms/100")
        .then()
            .statusCode(204);
        
        verify(vmService).deleteVM("node1", 100, null);
        verify(auditService).logAllowed(any(), argThat(decision -> 
            decision.getReason().contains("Manual override")));
    }

    @Test
    @DisplayName("POST /api/v1/vms/{vmId}/stop should respect permissive mode")
    void testStopInPermissiveMode() throws Exception {
        // Given
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.PERMISSIVE);
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        
        // When & Then - should block destructive operation
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/vms/100/stop")
        .then()
            .statusCode(403)
            .body("error", is("SAFE_MODE_VIOLATION"));
        
        verify(vmService, never()).stopVM(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("POST /api/v1/vms/{vmId}/start should be allowed in permissive mode")
    void testStartInPermissiveMode() throws Exception {
        // Given
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.PERMISSIVE);
        VMResponse stoppedVM = new VMResponse(
            testVM.vmid(),
            testVM.name(),
            testVM.node(),
            "stopped",          // changed status
            testVM.cpus(),
            testVM.maxmem(),
            testVM.maxdisk(),
            testVM.uptime(),
            testVM.type(),
            testVM.tags(),
            testVM.pool()
        );
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(stoppedVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        doNothing().when(vmService).startVM("node1", 100, null);

        // When & Then - should allow non-destructive operation
        given()
            .contentType(ContentType.JSON)
        .when()
            .post("/api/v1/vms/100/start")
        .then()
            .statusCode(202);
        
        verify(vmService).startVM("node1", 100, null);
    }

    @Test
    @DisplayName("All operations should be allowed in audit mode")
    void testAuditModeAllowsAll() throws Exception {
        // Given
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.AUDIT);
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        doNothing().when(vmService).deleteVM("node1", 100, null);

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/api/v1/vms/100")
        .then()
            .statusCode(204);
        
        verify(vmService).deleteVM("node1", 100, null);
        verify(auditService).logWarning(eq("Operating on non-Moxxie VM"), any());
    }

    @Test
    @DisplayName("GET operations should always be allowed")
    void testReadOperationsAlwaysAllowed() {
        // Given
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/api/v1/vms/100")
        .then()
            .statusCode(200)
            .body("vmId", is(100))
            .body("name", is("test-vm"));
        
        // Should not interact with tag service for read operations
        verify(tagService, never()).getVMTags(anyInt());
    }

    @Test
    @DisplayName("POST /api/v1/vms should tag new VMs as moxxie-managed")
    void testCreateVMAddsTag() throws Exception {
        // Given
        var createRequest = """
            {
                "name": "new-test-vm",
                "node": "node1",
                "cores": 2,
                "memoryMB": 2048
            }
            """;
        
        var response = new com.coffeesprout.client.CreateVMResponse();
        response.setStatus("UPID:node1:12345");
        when(vmService.createVM(eq("node1"), any(), any())).thenReturn(response);
        doNothing().when(tagService).addTag(anyInt(), eq("moxxie"));

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .body(createRequest)
        .when()
            .post("/api/v1/vms")
        .then()
            .statusCode(201)
            .header("Location", containsString("/api/v1/vms/"));
        
        verify(tagService).addTag(anyInt(), eq("moxxie"));
    }

    @Test
    @DisplayName("Tag operations should respect safe mode")
    void testTagOperationsRespectSafeMode() {
        // Given
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        
        var tagRequest = """
            {
                "tag": "new-tag"
            }
            """;

        // When & Then - should block in strict mode
        given()
            .contentType(ContentType.JSON)
            .body(tagRequest)
        .when()
            .post("/api/v1/vms/100/tags")
        .then()
            .statusCode(403)
            .body("error", is("SAFE_MODE_VIOLATION"));
        
        verify(tagService, never()).addTag(100, "new-tag");
    }

    @Test
    @DisplayName("Tag operations with force flag should override safe mode")
    void testTagOperationsWithForce() throws Exception {
        // Given
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        doNothing().when(tagService).addTag(100, "new-tag");
        
        var tagRequest = """
            {
                "tag": "new-tag"
            }
            """;

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .queryParam("force", true)
            .body(tagRequest)
        .when()
            .post("/api/v1/vms/100/tags")
        .then()
            .statusCode(200);
        
        verify(tagService).addTag(100, "new-tag");
    }

    @Test
    @DisplayName("Safe mode disabled should allow all operations")
    void testSafeModeDisabled() throws Exception {
        // Given
        when(safetyConfig.enabled()).thenReturn(false);
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        doNothing().when(vmService).deleteVM("node1", 100, null);

        // When & Then
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/api/v1/vms/100")
        .then()
            .statusCode(204);
        
        verify(vmService).deleteVM("node1", 100, null);
        verify(tagService, never()).getVMTags(anyInt());
    }

    @Test
    @DisplayName("Force override disabled should block even with force flag")
    void testForceOverrideDisabled() {
        // Given
        when(safetyConfig.allowManualOverride()).thenReturn(false);
        when(vmService.listVMsWithFilters(null, null, null, null, null)).thenReturn(List.of(testVM));
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .queryParam("force", true)
        .when()
            .delete("/api/v1/vms/100")
        .then()
            .statusCode(403)
            .body("error", is("SAFE_MODE_VIOLATION"));
        
        verify(vmService, never()).deleteVM(anyString(), anyInt(), any());
    }
}