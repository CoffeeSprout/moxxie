package com.coffeesprout.api;

import com.coffeesprout.api.dto.CloudInitVMRequest;
import com.coffeesprout.api.dto.FirmwareConfig;
import com.coffeesprout.client.CreateVMResponse;
import com.coffeesprout.service.VMService;
import com.coffeesprout.service.VMIdService;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.SDNService;
import com.coffeesprout.service.SnapshotService;
import com.coffeesprout.service.BackupService;
import com.coffeesprout.service.TicketManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for UEFI/OVMF functionality in VMResource
 */
@QuarkusTest
public class VMResourceUEFITest {
    
    @InjectMock
    VMService vmService;
    
    @InjectMock
    VMIdService vmIdService;
    
    @InjectMock
    TagService tagService;
    
    @InjectMock
    SDNService sdnService;
    
    @InjectMock
    SnapshotService snapshotService;
    
    @InjectMock
    BackupService backupService;
    
    @InjectMock
    TicketManager ticketManager;
    
    @BeforeEach
    void setUp() {
        // Mock ticket manager
        when(ticketManager.getTicket()).thenReturn("PVE:mock-ticket");
        when(ticketManager.getCsrfToken()).thenReturn("mock-csrf-token");
    }
    
    @Test
    @DisplayName("Create VM with UEFI firmware configuration")
    void testCreateVMWithUEFIFirmware() {
        // Mock VM ID allocation
        when(vmIdService.getNextAvailableVmId(anyString())).thenReturn(10710);
        
        // Mock successful VM creation
        CreateVMResponse mockResponse = new CreateVMResponse();
        mockResponse.setVmid(10710);
        mockResponse.setStatus("created");
        
        when(vmService.createCloudInitVM(org.mockito.ArgumentMatchers.any(CloudInitVMRequest.class), isNull()))
            .thenReturn(mockResponse);
        
        String requestBody = """
            {
                "name": "okd-bootstrap",
                "node": "storage01",
                "cores": 4,
                "memoryMB": 16384,
                "imageSource": "local-zfs:9002/base-9002-disk-0.raw",
                "targetStorage": "local-zfs",
                "diskSizeGB": 120,
                "cloudInitUser": "admin",
                "sshKeys": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI... admin@example.com",
                "searchDomain": "cluster.local",
                "nameservers": "8.8.8.8,8.8.4.4",
                "cpuType": "host",
                "description": "OKD Bootstrap Node",
                "tags": "okd,bootstrap",
                "firmware": {
                    "type": "UEFI",
                    "machine": "Q35",
                    "efidisk": {
                        "storage": "local-zfs",
                        "efitype": "LARGE",
                        "preEnrolledKeys": false
                    },
                    "secureboot": false
                },
                "scsihw": "virtio-scsi-single",
                "serial0": "socket",
                "vgaType": "serial0"
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/api/v1/vms/cloud-init")
        .then()
            .statusCode(201)
            .body("vmid", equalTo(10710))
            .body("status", equalTo("created"));
        
        // Verify the service was called with correct firmware configuration
        verify(vmService).createCloudInitVM(argThat(request -> {
            FirmwareConfig firmware = request.firmware();
            return firmware != null &&
                   firmware.type() == FirmwareConfig.FirmwareType.UEFI &&
                   firmware.machine() == FirmwareConfig.MachineType.Q35 &&
                   firmware.efidisk() != null &&
                   firmware.efidisk().storage().equals("local-zfs") &&
                   firmware.efidisk().efitype() == FirmwareConfig.EFIDiskConfig.EFIType.LARGE &&
                   !firmware.efidisk().preEnrolledKeys() &&
                   !firmware.secureboot();
        }), isNull());
    }
    
    @Test
    @DisplayName("Create VM with SeaBIOS firmware configuration")
    void testCreateVMWithSeaBIOSFirmware() {
        // Mock VM ID allocation
        when(vmIdService.getNextAvailableVmId(anyString())).thenReturn(200);
        
        // Mock successful VM creation
        CreateVMResponse mockResponse = new CreateVMResponse();
        mockResponse.setVmid(200);
        mockResponse.setStatus("created");
        
        when(vmService.createCloudInitVM(org.mockito.ArgumentMatchers.any(CloudInitVMRequest.class), isNull()))
            .thenReturn(mockResponse);
        
        String requestBody = """
            {
                "name": "debian-server",
                "node": "hv7",
                "cores": 2,
                "memoryMB": 4096,
                "imageSource": "local-zfs:9001/base-9001-disk-0.raw",
                "targetStorage": "local-zfs",
                "diskSizeGB": 50,
                "cloudInitUser": "debian",
                "description": "Debian Server",
                "firmware": {
                    "type": "SEABIOS",
                    "machine": "PC",
                    "secureboot": false
                },
                "scsihw": "virtio-scsi-pci",
                "vgaType": "std"
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/api/v1/vms/cloud-init")
        .then()
            .statusCode(201)
            .body("vmid", equalTo(200))
            .body("status", equalTo("created"));
        
        // Verify the service was called with correct firmware configuration
        verify(vmService).createCloudInitVM(argThat(request -> {
            FirmwareConfig firmware = request.firmware();
            return firmware != null &&
                   firmware.type() == FirmwareConfig.FirmwareType.SEABIOS &&
                   firmware.machine() == FirmwareConfig.MachineType.PC &&
                   firmware.efidisk() == null &&
                   !firmware.secureboot();
        }), isNull());
    }
    
    @Test
    @DisplayName("Create VM without firmware configuration uses defaults")
    void testCreateVMWithoutFirmware() {
        // Mock VM ID allocation
        when(vmIdService.getNextAvailableVmId(anyString())).thenReturn(300);
        
        // Mock successful VM creation
        CreateVMResponse mockResponse = new CreateVMResponse();
        mockResponse.setVmid(300);
        mockResponse.setStatus("created");
        
        when(vmService.createCloudInitVM(org.mockito.ArgumentMatchers.any(CloudInitVMRequest.class), isNull()))
            .thenReturn(mockResponse);
        
        String requestBody = """
            {
                "name": "simple-vm",
                "node": "hv1",
                "cores": 2,
                "memoryMB": 2048,
                "imageSource": "local-zfs:9000/base-9000-disk-0.raw",
                "targetStorage": "local-zfs",
                "diskSizeGB": 20
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/api/v1/vms/cloud-init")
        .then()
            .statusCode(201)
            .body("vmid", equalTo(300))
            .body("status", equalTo("created"));
        
        // Verify the service was called with null firmware (defaults will be applied)
        verify(vmService).createCloudInitVM(argThat(request -> 
            request.firmware() == null
        ), isNull());
    }
    
    @Test
    @DisplayName("Reject invalid UEFI configuration - missing EFI disk")
    void testRejectInvalidUEFIConfiguration() {
        // Mock validation failure
        when(vmService.createCloudInitVM(org.mockito.ArgumentMatchers.any(CloudInitVMRequest.class), isNull()))
            .thenThrow(new IllegalArgumentException("EFI disk configuration is required for UEFI firmware"));
        
        String requestBody = """
            {
                "name": "invalid-uefi-vm",
                "node": "storage01",
                "cores": 4,
                "memoryMB": 16384,
                "imageSource": "local-zfs:9002/base-9002-disk-0.raw",
                "targetStorage": "local-zfs",
                "diskSizeGB": 120,
                "firmware": {
                    "type": "UEFI",
                    "machine": "Q35",
                    "secureboot": false
                }
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/api/v1/vms/cloud-init")
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"));
    }
    
    @Test
    @DisplayName("Reject invalid UEFI configuration - wrong machine type")
    void testRejectUEFIWithWrongMachineType() {
        // Mock validation failure
        when(vmService.createCloudInitVM(org.mockito.ArgumentMatchers.any(CloudInitVMRequest.class), isNull()))
            .thenThrow(new IllegalArgumentException("UEFI firmware requires q35 machine type, not pc"));
        
        String requestBody = """
            {
                "name": "invalid-machine-vm",
                "node": "storage01",
                "cores": 4,
                "memoryMB": 16384,
                "imageSource": "local-zfs:9002/base-9002-disk-0.raw",
                "targetStorage": "local-zfs",
                "diskSizeGB": 120,
                "firmware": {
                    "type": "UEFI",
                    "machine": "PC",
                    "efidisk": {
                        "storage": "local-zfs",
                        "efitype": "LARGE",
                        "preEnrolledKeys": false
                    },
                    "secureboot": false
                }
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/api/v1/vms/cloud-init")
        .then()
            .statusCode(400)
            .body("error", equalTo("VALIDATION_ERROR"));
    }
    
    @Test
    @DisplayName("Get VM configuration endpoint")
    void testGetVMConfiguration() {
        // Mock VM lookup
        com.coffeesprout.api.dto.VMResponse vm = new com.coffeesprout.api.dto.VMResponse(
            10710, "okd-bootstrap", "storage01", "stopped", 4, 16384L, 120000L, 0L, "qemu", null, null, 0
        );
        
        when(vmService.listVMs(isNull()))
            .thenReturn(java.util.List.of(vm));
        
        // Mock VM configuration
        Map<String, Object> config = Map.of(
            "vmid", 10710,
            "name", "okd-bootstrap",
            "machine", "q35",
            "bios", "ovmf",
            "efidisk0", "local-zfs:1,efitype=4m,pre-enrolled-keys=0",
            "cores", 4,
            "memory", 16384,
            "scsihw", "virtio-scsi-single"
        );
        
        when(vmService.getVMConfig(eq("storage01"), eq(10710), isNull()))
            .thenReturn(config);
        
        given()
        .when()
            .get("/api/v1/vms/10710/config")
        .then()
            .statusCode(200)
            .body("vmid", equalTo(10710))
            .body("machine", equalTo("q35"))
            .body("bios", equalTo("ovmf"))
            .body("efidisk0", equalTo("local-zfs:1,efitype=4m,pre-enrolled-keys=0"));
    }
    
}