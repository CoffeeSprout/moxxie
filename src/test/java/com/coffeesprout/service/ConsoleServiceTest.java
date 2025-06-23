package com.coffeesprout.service;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.*;
import com.coffeesprout.config.MoxxieConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@Disabled("Temporarily disabled due to InjectMock issues - needs migration to new Quarkus mock approach")
class ConsoleServiceTest {

    @Inject
    ConsoleService consoleService;
    
    @InjectMock
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @InjectMock
    VMService vmService;
    
    @InjectMock
    TicketManager ticketManager;
    
    @InjectMock
    MoxxieConfig config;
    
    private final String TEST_TICKET = "PVE:user@pve:TOKEN";
    private final String TEST_CSRF = "csrf-token";
    private final int TEST_VM_ID = 100;
    private final String TEST_NODE = "pve-node1";
    
    @BeforeEach
    void setUp() {
        when(ticketManager.getCsrfToken()).thenReturn(TEST_CSRF);
        
        // Mock config
        MoxxieConfig.Proxmox proxmoxConfig = mock(MoxxieConfig.Proxmox.class);
        when(proxmoxConfig.url()).thenReturn("https://10.0.0.10:8006/api2/json");
        when(config.proxmox()).thenReturn(proxmoxConfig);
    }
    
    @Test
    void testCreateVNCConsoleAccess() {
        // Arrange
        VMResponse testVM = new VMResponse(
            TEST_VM_ID,         // vmid
            "test-vm",          // name
            TEST_NODE,          // node
            "running",          // status
            2,                  // cpus
            2147483648L,        // maxmem
            0L,                 // maxdisk
            0L,                 // uptime
            "qemu",             // type
            List.of(),          // tags
            null,               // pool
            0                   // template
        );
        
        List<VMResponse> vmList = Arrays.asList(testVM);
        when(vmService.listVMsWithFilters(any(), any(), any(), any(), anyString())).thenReturn(vmList);
        
        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setTicket("VNC-TICKET-123");
        data.setPort("5901");
        data.setPassword("vnc-password");
        data.setCert("cert-data");
        data.setUser("user@pve");
        proxmoxResponse.setData(data);
        
        when(proxmoxClient.createVNCProxy(eq(TEST_NODE), eq(TEST_VM_ID), anyString(), eq(TEST_CSRF)))
            .thenReturn(proxmoxResponse);
        
        ConsoleRequest request = new ConsoleRequest(ConsoleType.VNC, true);
        
        // Act
        ConsoleResponse response = consoleService.createConsoleAccess(TEST_VM_ID, request, TEST_TICKET);
        
        // Assert
        assertNotNull(response);
        assertEquals("vnc", response.getType());
        assertEquals("VNC-TICKET-123", response.getTicket());
        assertEquals("vnc-password", response.getPassword());
        assertEquals(5901, response.getPort());
        assertEquals(443, response.getWebsocketPort());
        assertTrue(response.getWebsocketPath().contains("/vncwebsocket"));
        assertNotNull(response.getValidUntil());
        
        verify(proxmoxClient).createVNCProxy(TEST_NODE, TEST_VM_ID, TEST_TICKET, TEST_CSRF);
    }
    
    @Test
    void testCreateSPICEConsoleAccess() {
        // Arrange
        VMResponse testVM = new VMResponse(
            TEST_VM_ID,         // vmid
            "test-vm",          // name
            TEST_NODE,          // node
            "running",          // status
            2,                  // cpus
            2147483648L,        // maxmem
            0L,                 // maxdisk
            0L,                 // uptime
            "qemu",             // type
            List.of(),          // tags
            null,               // pool
            0                   // template
        );
        
        List<VMResponse> vmList = Arrays.asList(testVM);
        when(vmService.listVMsWithFilters(any(), any(), any(), any(), anyString())).thenReturn(vmList);
        
        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setTicket("SPICE-TICKET-456");
        data.setPort("3128");
        data.setPassword("spice-password");
        proxmoxResponse.setData(data);
        
        when(proxmoxClient.createSPICEProxy(eq(TEST_NODE), eq(TEST_VM_ID), anyString(), eq(TEST_CSRF)))
            .thenReturn(proxmoxResponse);
        
        ConsoleRequest request = new ConsoleRequest(ConsoleType.SPICE, true);
        
        // Act
        ConsoleResponse response = consoleService.createConsoleAccess(TEST_VM_ID, request, TEST_TICKET);
        
        // Assert
        assertNotNull(response);
        assertEquals("spice", response.getType());
        assertEquals("SPICE-TICKET-456", response.getTicket());
        assertEquals("spice-password", response.getPassword());
        assertEquals(3128, response.getPort());
        
        verify(proxmoxClient).createSPICEProxy(TEST_NODE, TEST_VM_ID, TEST_TICKET, TEST_CSRF);
    }
    
    @Test
    void testCreateConsoleAccessVMNotFound() {
        // Arrange
        when(vmService.listVMs(anyString())).thenReturn(Arrays.asList());
        
        ConsoleRequest request = new ConsoleRequest(ConsoleType.VNC, true);
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            consoleService.createConsoleAccess(999, request, TEST_TICKET);
        });
    }
    
    @Test
    void testGetWebSocketDetails() {
        // Arrange
        VMResponse testVM = new VMResponse(
            TEST_VM_ID,         // vmid
            "test-vm",          // name
            TEST_NODE,          // node
            "running",          // status
            2,                  // cpus
            2147483648L,        // maxmem
            0L,                 // maxdisk
            0L,                 // uptime
            "qemu",             // type
            List.of(),          // tags
            null,               // pool
            0                   // template
        );
        
        List<VMResponse> vmList = Arrays.asList(testVM);
        when(vmService.listVMsWithFilters(any(), any(), any(), any(), anyString())).thenReturn(vmList);
        
        // Act
        ConsoleWebSocketResponse response = consoleService.getWebSocketDetails(TEST_VM_ID, "console-ticket", TEST_TICKET);
        
        // Assert
        assertNotNull(response);
        assertTrue(response.getUrl().startsWith("wss://"));
        assertTrue(response.getUrl().contains("/vncwebsocket"));
        assertEquals("binary", response.getProtocol());
        assertNotNull(response.getHeaders());
        assertTrue(response.getHeaders().containsKey("Cookie"));
        assertTrue(response.getHeaders().containsKey("Sec-WebSocket-Protocol"));
    }
    
    @Test
    void testGenerateSpiceFile() {
        // Arrange
        VMResponse testVM = new VMResponse(
            TEST_VM_ID,         // vmid
            "test-vm",          // name
            TEST_NODE,          // node
            "running",          // status
            2,                  // cpus
            2147483648L,        // maxmem
            0L,                 // maxdisk
            0L,                 // uptime
            "qemu",             // type
            List.of(),          // tags
            null,               // pool
            0                   // template
        );
        
        List<VMResponse> vmList = Arrays.asList(testVM);
        when(vmService.listVMsWithFilters(any(), any(), any(), any(), anyString())).thenReturn(vmList);
        
        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setPort("3128");
        data.setPassword("spice-password");
        proxmoxResponse.setData(data);
        
        when(proxmoxClient.createSPICEProxy(eq(TEST_NODE), eq(TEST_VM_ID), anyString(), eq(TEST_CSRF)))
            .thenReturn(proxmoxResponse);
        
        // Act
        SpiceConnectionFile file = consoleService.generateSpiceFile(TEST_VM_ID, "console-ticket", TEST_TICKET);
        
        // Assert
        assertNotNull(file);
        assertEquals("vm-100.vv", file.getFilename());
        assertEquals("application/x-virt-viewer", file.getMimeType());
        assertNotNull(file.getContent());
        assertTrue(file.getContent().contains("[virt-viewer]"));
        assertTrue(file.getContent().contains("type=spice"));
        assertTrue(file.getContent().contains("host=10.0.0.10"));
        assertTrue(file.getContent().contains("port=3128"));
        assertTrue(file.getContent().contains("password=spice-password"));
    }
    
    @Test
    void testConsoleWithCustomNode() {
        // Arrange
        VMResponse testVM = new VMResponse(
            TEST_VM_ID,         // vmid
            "test-vm",          // name
            TEST_NODE,          // node
            "running",          // status
            2,                  // cpus
            2147483648L,        // maxmem
            0L,                 // maxdisk
            0L,                 // uptime
            "qemu",             // type
            List.of(),          // tags
            null,               // pool
            0                   // template
        );
        
        List<VMResponse> vmList = Arrays.asList(testVM);
        when(vmService.listVMsWithFilters(any(), any(), any(), any(), anyString())).thenReturn(vmList);
        
        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setTicket("TERMINAL-TICKET-789");
        proxmoxResponse.setData(data);
        
        String customNode = "pve-node2";
        when(proxmoxClient.createTermProxy(eq(customNode), eq(TEST_VM_ID), anyString(), eq(TEST_CSRF)))
            .thenReturn(proxmoxResponse);
        
        ConsoleRequest request = new ConsoleRequest(ConsoleType.TERMINAL, false);
        request.setNode(customNode);
        
        // Act
        ConsoleResponse response = consoleService.createConsoleAccess(TEST_VM_ID, request, TEST_TICKET);
        
        // Assert
        assertNotNull(response);
        assertEquals("terminal", response.getType());
        
        // Verify custom node was used
        verify(proxmoxClient).createTermProxy(customNode, TEST_VM_ID, TEST_TICKET, TEST_CSRF);
        verify(proxmoxClient, never()).createTermProxy(eq(TEST_NODE), anyInt(), anyString(), anyString());
    }
}