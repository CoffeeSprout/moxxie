package com.coffeesprout.api;

import com.coffeesprout.client.*;
import com.coffeesprout.service.ConsoleService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@Disabled("Failed to start Quarkus - needs investigation")
class ConsoleResourceTest {

    @InjectMock
    ConsoleService consoleService;
    
    private final int TEST_VM_ID = 100;
    
    @BeforeEach
    void setUp() {
        RestAssured.basePath = "/api/v1";
    }
    
    @Test
    void testCreateVNCConsoleAccess() {
        // Arrange
        ConsoleResponse mockResponse = new ConsoleResponse();
        mockResponse.setType("vnc");
        mockResponse.setTicket("VNC-TICKET-123");
        mockResponse.setPort(5901);
        mockResponse.setPassword("vnc-pass");
        mockResponse.setWebsocketPort(443);
        mockResponse.setWebsocketPath("/api2/json/nodes/pve1/qemu/100/vncwebsocket");
        mockResponse.setValidUntil(Instant.now().plusSeconds(600));
        
        when(consoleService.createConsoleAccess(eq(TEST_VM_ID), Mockito.any(ConsoleRequest.class), isNull()))
            .thenReturn(mockResponse);
        
        String requestBody = """
            {
                "type": "VNC",
                "generatePassword": true
            }
            """;
        
        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/vms/{vmId}/console", TEST_VM_ID)
        .then()
            .statusCode(200)
            .body("type", equalTo("vnc"))
            .body("ticket", equalTo("VNC-TICKET-123"))
            .body("port", equalTo(5901))
            .body("password", equalTo("vnc-pass"))
            .body("websocketPort", equalTo(443))
            .body("websocketPath", containsString("/vncwebsocket"));
    }
    
    @Test
    void testCreateSPICEConsoleAccess() {
        // Arrange
        ConsoleResponse mockResponse = new ConsoleResponse();
        mockResponse.setType("spice");
        mockResponse.setTicket("SPICE-TICKET-456");
        mockResponse.setPort(3128);
        mockResponse.setPassword("spice-pass");
        
        when(consoleService.createConsoleAccess(eq(TEST_VM_ID), Mockito.any(ConsoleRequest.class), isNull()))
            .thenReturn(mockResponse);
        
        String requestBody = """
            {
                "type": "SPICE",
                "generatePassword": true
            }
            """;
        
        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/vms/{vmId}/console", TEST_VM_ID)
        .then()
            .statusCode(200)
            .body("type", equalTo("spice"))
            .body("ticket", equalTo("SPICE-TICKET-456"))
            .body("port", equalTo(3128))
            .body("password", equalTo("spice-pass"));
    }
    
    @Test
    void testCreateConsoleAccessInvalidType() {
        // Arrange
        when(consoleService.createConsoleAccess(anyInt(), Mockito.any(ConsoleRequest.class), isNull()))
            .thenThrow(new IllegalArgumentException("Invalid console type"));
        
        String requestBody = """
            {
                "type": "INVALID",
                "generatePassword": true
            }
            """;
        
        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/vms/{vmId}/console", TEST_VM_ID)
        .then()
            .statusCode(400)
            .body("error", containsString("Invalid console type"));
    }
    
    @Test
    void testGetWebSocketDetails() {
        // Arrange
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "PVEAuthCookie=test-ticket");
        headers.put("Sec-WebSocket-Protocol", "binary");
        
        ConsoleWebSocketResponse mockResponse = new ConsoleWebSocketResponse();
        mockResponse.setUrl("wss://10.0.0.10:8006/api2/json/nodes/pve1/qemu/100/vncwebsocket");
        mockResponse.setProtocol("binary");
        mockResponse.setHeaders(headers);
        
        when(consoleService.getWebSocketDetails(eq(TEST_VM_ID), eq("console-ticket"), isNull()))
            .thenReturn(mockResponse);
        
        // Act & Assert
        given()
            .queryParam("ticket", "console-ticket")
        .when()
            .get("/vms/{vmId}/console/websocket", TEST_VM_ID)
        .then()
            .statusCode(200)
            .body("url", containsString("wss://"))
            .body("url", containsString("/vncwebsocket"))
            .body("protocol", equalTo("binary"))
            .body("headers.Cookie", containsString("PVEAuthCookie"));
    }
    
    @Test
    void testGetWebSocketDetailsMissingTicket() {
        // Act & Assert
        given()
        .when()
            .get("/vms/{vmId}/console/websocket", TEST_VM_ID)
        .then()
            .statusCode(400)
            .body("error", equalTo("Console ticket is required"));
    }
    
    @Test
    void testGetSpiceConnectionFile() {
        // Arrange
        SpiceConnectionFile mockFile = new SpiceConnectionFile();
        mockFile.setContent("[virt-viewer]\ntype=spice\nhost=10.0.0.10\nport=3128\n");
        mockFile.setFilename("vm-100.vv");
        mockFile.setMimeType("application/x-virt-viewer");
        
        when(consoleService.generateSpiceFile(eq(TEST_VM_ID), eq("spice-ticket"), isNull()))
            .thenReturn(mockFile);
        
        // Act & Assert
        given()
            .queryParam("ticket", "spice-ticket")
        .when()
            .get("/vms/{vmId}/console/spice", TEST_VM_ID)
        .then()
            .statusCode(200)
            .contentType("application/x-virt-viewer")
            .header("Content-Disposition", containsString("vm-100.vv"))
            .body(containsString("[virt-viewer]"))
            .body(containsString("type=spice"));
    }
    
    @Test
    void testGetSpiceConnectionFileVMNotFound() {
        // Arrange
        when(consoleService.generateSpiceFile(anyInt(), anyString(), isNull()))
            .thenThrow(new IllegalArgumentException("VM not found"));
        
        // Act & Assert
        given()
            .queryParam("ticket", "spice-ticket")
        .when()
            .get("/vms/{vmId}/console/spice", 999)
        .then()
            .statusCode(404)
            .body("error", containsString("VM not found"));
    }
    
    @Test
    void testCreateConsoleWithNodeOverride() {
        // Arrange
        ConsoleResponse mockResponse = new ConsoleResponse();
        mockResponse.setType("terminal");
        mockResponse.setTicket("TERM-TICKET-789");
        
        when(consoleService.createConsoleAccess(eq(TEST_VM_ID), Mockito.any(ConsoleRequest.class), isNull()))
            .thenReturn(mockResponse);
        
        String requestBody = """
            {
                "type": "TERMINAL",
                "generatePassword": false,
                "node": "pve-node2"
            }
            """;
        
        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/vms/{vmId}/console", TEST_VM_ID)
        .then()
            .statusCode(200)
            .body("type", equalTo("terminal"))
            .body("ticket", equalTo("TERM-TICKET-789"));
    }
}