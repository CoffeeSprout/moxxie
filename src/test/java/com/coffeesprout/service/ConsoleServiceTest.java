package com.coffeesprout.service;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@Disabled("Complex ProxmoxClient REST client mocking - requires component test refactoring")
class ConsoleServiceTest {

    @Inject
    ConsoleService consoleService;

    @InjectMock
    VMService vmService;

    @InjectMock
    TicketManager ticketManager;

    @InjectMock
    VMLocatorService vmLocatorService;

    private ProxmoxClient proxmoxClient;

    private final String TEST_TICKET = "PVE:user@pve:TOKEN";
    private final String TEST_CSRF = "csrf-token";
    private final int TEST_VM_ID = 100;
    private final String TEST_NODE = "pve-node1";

    @BeforeEach
    void setUp() {
        // Mock ProxmoxClient using QuarkusMock
        proxmoxClient = mock(ProxmoxClient.class);
        QuarkusMock.installMockForType(proxmoxClient, ProxmoxClient.class);

        when(ticketManager.getCsrfToken()).thenReturn(TEST_CSRF);

        // Mock default VM for most tests
        VMResponse testVM = new VMResponse(
            TEST_VM_ID, "test-vm", TEST_NODE, "running", 2, 2147483648L,
            0L, 0L, "qemu", List.of(), null, 0
        );
        when(vmLocatorService.findVM(eq(TEST_VM_ID), isNull()))
            .thenReturn(Optional.of(testVM));
    }

    @Test
    void testCreateVNCConsoleAccess() {
        // Arrange
        // VM is already mocked in setUp()

        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setTicket("VNC-TICKET-123");
        data.setPort("5901");
        data.setPassword("vnc-password");
        data.setCert("cert-data");
        data.setUser("user@pve");
        proxmoxResponse.setData(data);

        when(proxmoxClient.createVNCProxy(eq(TEST_NODE), eq(TEST_VM_ID), isNull(), eq(TEST_CSRF)))
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
        // VM is already mocked in setUp()

        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setTicket("SPICE-TICKET-456");
        data.setPort("3128");
        data.setPassword("spice-password");
        proxmoxResponse.setData(data);

        when(proxmoxClient.createSPICEProxy(eq(TEST_NODE), eq(TEST_VM_ID), isNull(), eq(TEST_CSRF)))
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
        when(vmLocatorService.findVM(eq(999), isNull())).thenReturn(Optional.empty());

        ConsoleRequest request = new ConsoleRequest(ConsoleType.VNC, true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            consoleService.createConsoleAccess(999, request, TEST_TICKET);
        });
    }

    @Test
    void testGetWebSocketDetails() {
        // Arrange
        // VM is already mocked in setUp()

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
        // VM is already mocked in setUp()

        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setPort("3128");
        data.setPassword("spice-password");
        proxmoxResponse.setData(data);

        when(proxmoxClient.createSPICEProxy(eq(TEST_NODE), eq(TEST_VM_ID), isNull(), eq(TEST_CSRF)))
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
        // VM is already mocked in setUp()

        ProxmoxConsoleResponse proxmoxResponse = new ProxmoxConsoleResponse();
        ProxmoxConsoleResponse.ProxmoxConsoleData data = new ProxmoxConsoleResponse.ProxmoxConsoleData();
        data.setTicket("TERMINAL-TICKET-789");
        proxmoxResponse.setData(data);

        String customNode = "pve-node2";
        when(proxmoxClient.createTermProxy(eq(customNode), eq(TEST_VM_ID), isNull(), eq(TEST_CSRF)))
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
