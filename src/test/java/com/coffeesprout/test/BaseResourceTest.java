package com.coffeesprout.test;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.service.TicketManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * Base test class for REST Resource tests.
 * Provides common mocking setup and helper methods for resource testing.
 */
@QuarkusTest
public abstract class BaseResourceTest {
    
    @InjectMock
    protected ProxmoxClient proxmoxClient;
    
    @InjectMock
    protected TicketManager ticketManager;
    
    protected static final String TEST_TICKET = "PVE:test@pve:TOKEN";
    protected static final String TEST_CSRF_TOKEN = "test-csrf-token";
    
    @BeforeEach
    void setupBaseMocks() {
        // Setup common ticket manager mocks
        when(ticketManager.getTicket()).thenReturn(TEST_TICKET);
        when(ticketManager.getCsrfToken()).thenReturn(TEST_CSRF_TOKEN);
        
        // Call child class setup if needed
        setupMocks();
    }
    
    /**
     * Override this method in child classes to add custom mock setup
     */
    protected void setupMocks() {
        // Default empty implementation - override in child classes
    }
    
    /**
     * Helper method to create a mock VM response
     */
    protected VMResponse createMockVM(int vmId, String name, String node, String status) {
        return new VMResponse(
            vmId,                    // vmid
            name,                    // name
            node,                    // node
            status,                  // status
            2,                       // cpus
            4294967296L,             // maxmem (4GB in bytes)
            107374182400L,           // maxdisk (100GB in bytes)
            3600L,                   // uptime in seconds
            "qemu",                  // type
            List.of("moxxie", "test"), // tags
            null,                    // pool
            0                        // template (0 = regular VM, 1 = template)
        );
    }
    
    /**
     * Helper method to create a list of mock VMs
     */
    protected List<VMResponse> createMockVMList(int count) {
        List<VMResponse> vms = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            vms.add(createMockVM(
                100 + i,
                "test-vm-" + i,
                "node" + (i % 3 + 1),
                i % 2 == 0 ? "running" : "stopped"
            ));
        }
        return vms;
    }
    
    /**
     * Helper method to mock a successful task response
     */
    protected void mockSuccessfulTask(String taskUpid) {
        String node = extractNodeFromUpid(taskUpid);
        
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode dataNode = mapper.createObjectNode();
        dataNode.put("status", "stopped");
        dataNode.put("exitstatus", "OK");
        dataNode.put("upid", taskUpid);
        dataNode.put("node", node);
        dataNode.put("pid", 100);
        dataNode.put("starttime", Instant.now().toString());
        rootNode.set("data", dataNode);
        
        when(proxmoxClient.getTaskStatus(
            ArgumentMatchers.eq(node),
            ArgumentMatchers.eq(taskUpid),
            ArgumentMatchers.anyString()
        )).thenReturn(rootNode);
    }
    
    /**
     * Helper method to mock a failed task response
     */
    protected void mockFailedTask(String taskUpid, String errorMessage) {
        String node = extractNodeFromUpid(taskUpid);
        
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode dataNode = mapper.createObjectNode();
        dataNode.put("status", "stopped");
        dataNode.put("exitstatus", "ERROR: " + errorMessage);
        dataNode.put("upid", taskUpid);
        dataNode.put("node", node);
        dataNode.put("pid", 100);
        dataNode.put("starttime", Instant.now().toString());
        rootNode.set("data", dataNode);
        
        when(proxmoxClient.getTaskStatus(
            ArgumentMatchers.eq(node),
            ArgumentMatchers.eq(taskUpid),
            ArgumentMatchers.anyString()
        )).thenReturn(rootNode);
    }
    
    /**
     * Helper method to extract node from UPID
     * UPID format: UPID:node:pid:starttime:type:id:user@realm:
     */
    private String extractNodeFromUpid(String upid) {
        if (upid == null || !upid.startsWith("UPID:")) {
            return "node1"; // default node
        }
        String[] parts = upid.split(":");
        return parts.length > 1 ? parts[1] : "node1";
    }
    
    /**
     * Helper method to verify authentication headers were used
     */
    protected void verifyAuthenticationUsed() {
        // This would be called after the test to verify auth was properly used
        // Implementation depends on specific test needs
    }
}