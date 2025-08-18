package com.coffeesprout;

import com.coffeesprout.api.dto.CreateVMRequestDTO;
import com.coffeesprout.api.dto.CreateSnapshotRequest;
import com.coffeesprout.api.dto.BulkPowerRequest;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.dto.DiskConfig;
import com.coffeesprout.api.dto.NetworkConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test suite for Moxxie.
 * 
 * IMPORTANT: This test creates real resources in Proxmox. It is designed to:
 * 1. Only touch resources it creates (using specific naming prefix)
 * 2. Clean up all resources in @AfterEach and @AfterAll
 * 3. Err on the side of caution - skip tests if environment is not safe
 * 
 * Configuration:
 * - Set MOXXIE_TEST_NODE environment variable to target node (default: first available)
 * - Set MOXXIE_TEST_STORAGE for storage backend (default: local-lvm)
 * - Set MOXXIE_TEST_BRIDGE for network bridge (default: vmbr0)
 * - Set MOXXIE_TEST_ENABLED=true to enable these tests (default: false)
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MoxxieIntegrationTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(MoxxieIntegrationTest.class);
    
    // Test resource prefix to ensure we only touch our own resources
    private static final String TEST_PREFIX = "moxxie-test-";
    private static final String TEST_TAG = "moxxie-integration-test";
    
    // Test configuration from environment
    private static final boolean TESTS_ENABLED = Boolean.parseBoolean(
        System.getenv().getOrDefault("MOXXIE_TEST_ENABLED", "false")
    );
    private static final String TEST_NODE = System.getenv().getOrDefault("MOXXIE_TEST_NODE", "");
    private static final String TEST_STORAGE = System.getenv().getOrDefault("MOXXIE_TEST_STORAGE", "local-lvm");
    private static final String TEST_BRIDGE = System.getenv().getOrDefault("MOXXIE_TEST_BRIDGE", "vmbr0");
    
    // Track created resources for cleanup
    private final Set<Integer> createdVMs = new HashSet<>();
    private final Map<Integer, Set<String>> createdSnapshots = new HashMap<>();
    private String testRunId;
    private String selectedNode;
    
    @BeforeAll
    void setup() {
        if (!TESTS_ENABLED) {
            LOG.warn("Integration tests are disabled. Set MOXXIE_TEST_ENABLED=true to run.");
            return;
        }
        
        // Generate unique test run ID
        testRunId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        LOG.info("Starting integration test run: {}", testRunId);
        
        // Determine which node to use
        if (!TEST_NODE.isEmpty()) {
            selectedNode = TEST_NODE;
        } else {
            // Get first available node
            Response response = given()
                .when()
                .get("/api/v1/nodes")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            List<Map<String, Object>> nodes = response.jsonPath().getList("$");
            assertTrue(!nodes.isEmpty(), "No nodes available for testing");
            selectedNode = (String) nodes.get(0).get("node");
        }
        
        LOG.info("Using node {} for testing", selectedNode);
        
        // Clean up any leftover test resources from previous runs
        cleanupTestResources();
    }
    
    @BeforeEach
    void checkEnabled() {
        Assumptions.assumeTrue(TESTS_ENABLED, "Integration tests are disabled");
        Assumptions.assumeTrue(selectedNode != null, "No node selected for testing");
    }
    
    @AfterEach
    void cleanupAfterEach() {
        if (!TESTS_ENABLED) return;
        
        // Clean up snapshots first
        for (Map.Entry<Integer, Set<String>> entry : createdSnapshots.entrySet()) {
            int vmId = entry.getKey();
            for (String snapshotName : entry.getValue()) {
                try {
                    given()
                        .when()
                        .delete("/api/v1/vms/{vmId}/snapshots/{name}", vmId, snapshotName)
                        .then()
                        .statusCode(anyOf(is(200), is(202), is(404)));
                } catch (Exception e) {
                    LOG.warn("Failed to delete snapshot {} for VM {}: {}", snapshotName, vmId, e.getMessage());
                }
            }
        }
        createdSnapshots.clear();
        
        // Clean up VMs
        for (Integer vmId : new HashSet<>(createdVMs)) {
            try {
                deleteVMSafely(vmId);
                createdVMs.remove(vmId);
            } catch (Exception e) {
                LOG.warn("Failed to delete VM {}: {}", vmId, e.getMessage());
            }
        }
    }
    
    @AfterAll
    void finalCleanup() {
        if (!TESTS_ENABLED) return;
        
        LOG.info("Final cleanup for test run: {}", testRunId);
        
        // Final sweep to ensure all test resources are removed
        cleanupTestResources();
        
        LOG.info("Integration test run completed: {}", testRunId);
    }
    
    @Test
    @Order(1)
    @DisplayName("Create VM - Basic Configuration")
    void testCreateVM() {
        String vmName = TEST_PREFIX + "vm-" + testRunId;
        
        CreateVMRequestDTO request = new CreateVMRequestDTO(
            null,                  // vmId (auto-assign)
            vmName,                // name
            selectedNode,          // node
            null,                  // template
            2,                     // cores
            2048,                  // memory (MB)
            null,                  // diskGB (deprecated)
            List.of(               // disks
                new DiskConfig(
                    DiskConfig.DiskInterface.SCSI,  // interface
                    0,              // slot
                    TEST_STORAGE,   // storage
                    10,             // sizeGB
                    null,           // ssd
                    null,           // iothread
                    null,           // cache
                    null,           // discard
                    null,           // format
                    true,           // backup
                    false,          // replicate
                    null,           // importFrom
                    null            // additionalOptions
                )
            ),
            List.of(               // networks
                NetworkConfig.simple(TEST_BRIDGE)
            ),
            null,                  // network (deprecated)
            false,                 // startOnBoot
            null,                  // pool
            null,                  // clientId
            null,                  // project
            List.of(TEST_TAG),     // tags
            "scsi0",               // bootOrder
            null,                  // cpuType
            null,                  // vgaType
            null,                  // firmware
            null,                  // scsihw
            null                   // serial0
        );
        
        Response response = given()
            .contentType("application/json")
            .body(request)
            .when()
            .post("/api/v1/vms")
            .then()
            .statusCode(201)
            .body("vmId", notNullValue())
            .extract()
            .response();
        
        Integer vmId = response.jsonPath().getInt("vmId");
        assertNotNull(vmId);
        createdVMs.add(vmId);
        
        LOG.info("Created VM {} with ID {}", vmName, vmId);
        
        // Wait for VM creation to complete
        waitForTask(response.jsonPath().getString("task"));
        
        // Verify VM exists
        given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(200)
            .body("vmid", equalTo(vmId))
            .body("name", equalTo(vmName))
            .body("tags", hasItem(TEST_TAG));
    }
    
    @Test
    @Order(2)
    @DisplayName("Query VMs - Filter by Tag")
    void testQueryVMs() {
        // Create a VM if none exist
        if (createdVMs.isEmpty()) {
            testCreateVM();
        }
        
        // Query by our test tag
        Response response = given()
            .queryParam("tags", TEST_TAG)
            .when()
            .get("/api/v1/vms")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        List<VMResponse> vms = response.jsonPath().getList("$", VMResponse.class);
        assertFalse(vms.isEmpty(), "Should find at least one test VM");
        
        // Verify all returned VMs have our test tag
        for (VMResponse vm : vms) {
            assertTrue(vm.tags().contains(TEST_TAG), 
                "VM " + vm.vmid() + " should have test tag");
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("Start VM")
    void testStartVM() {
        Integer vmId = getOrCreateTestVM();
        
        // Ensure VM is stopped first
        stopVMIfRunning(vmId);
        
        // Start the VM
        Response response = given()
            .when()
            .post("/api/v1/vms/{vmId}/start", vmId)
            .then()
            .statusCode(202)
            .extract()
            .response();
        
        String task = response.jsonPath().getString("task");
        waitForTask(task);
        
        // Verify VM is running
        given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(200)
            .body("status", equalTo("running"));
    }
    
    @Test
    @Order(4)
    @DisplayName("Stop VM")
    void testStopVM() {
        Integer vmId = getOrCreateTestVM();
        
        // Ensure VM is running first
        startVMIfStopped(vmId);
        
        // Stop the VM
        Response response = given()
            .when()
            .post("/api/v1/vms/{vmId}/stop", vmId)
            .then()
            .statusCode(202)
            .extract()
            .response();
        
        String task = response.jsonPath().getString("task");
        waitForTask(task);
        
        // Verify VM is stopped
        given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(200)
            .body("status", equalTo("stopped"));
    }
    
    @Test
    @Order(5)
    @DisplayName("Reboot VM")
    void testRebootVM() {
        Integer vmId = getOrCreateTestVM();
        
        // Ensure VM is running first
        startVMIfStopped(vmId);
        
        // Get initial uptime
        Response initialResponse = given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Integer initialUptime = initialResponse.jsonPath().getInt("uptime");
        
        // Reboot the VM
        given()
            .when()
            .post("/api/v1/vms/{vmId}/reboot", vmId)
            .then()
            .statusCode(202);
        
        // Wait a bit for reboot
        sleep(10);
        
        // Verify VM is still running (reboot doesn't change status to stopped)
        given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(200)
            .body("status", equalTo("running"));
    }
    
    @Test
    @Order(6)
    @DisplayName("Create Snapshot")
    void testCreateSnapshot() {
        Integer vmId = getOrCreateTestVM();
        String snapshotName = TEST_PREFIX + "snap-" + System.currentTimeMillis();
        
        CreateSnapshotRequest request = new CreateSnapshotRequest(
            snapshotName,
            "Test snapshot created at " + LocalDateTime.now(),
            false,  // includeVMState
            null    // ttlHours
        );
        
        Response response = given()
            .contentType("application/json")
            .body(request)
            .when()
            .post("/api/v1/vms/{vmId}/snapshots", vmId)
            .then()
            .statusCode(anyOf(is(201), is(202)))
            .extract()
            .response();
        
        // Track snapshot for cleanup
        createdSnapshots.computeIfAbsent(vmId, k -> new HashSet<>()).add(snapshotName);
        
        // Wait for snapshot creation
        if (response.jsonPath().getString("task") != null) {
            waitForTask(response.jsonPath().getString("task"));
        }
        
        // Verify snapshot exists
        given()
            .when()
            .get("/api/v1/vms/{vmId}/snapshots", vmId)
            .then()
            .statusCode(200)
            .body("name", hasItem(snapshotName));
        
        LOG.info("Created snapshot {} for VM {}", snapshotName, vmId);
    }
    
    @Test
    @Order(7)
    @DisplayName("Delete Snapshot")
    void testDeleteSnapshot() {
        Integer vmId = getOrCreateTestVM();
        
        // Create a snapshot first
        String snapshotName = TEST_PREFIX + "snap-delete-" + System.currentTimeMillis();
        createSnapshot(vmId, snapshotName);
        
        // Delete the snapshot
        Response response = given()
            .when()
            .delete("/api/v1/vms/{vmId}/snapshots/{name}", vmId, snapshotName)
            .then()
            .statusCode(anyOf(is(200), is(202)))
            .extract()
            .response();
        
        // Wait for deletion
        if (response.jsonPath().getString("task") != null) {
            waitForTask(response.jsonPath().getString("task"));
        }
        
        // Remove from tracking
        createdSnapshots.getOrDefault(vmId, new HashSet<>()).remove(snapshotName);
        
        // Verify snapshot is gone
        given()
            .when()
            .get("/api/v1/vms/{vmId}/snapshots", vmId)
            .then()
            .statusCode(200)
            .body("name", not(hasItem(snapshotName)));
    }
    
    @Test
    @Order(8)
    @DisplayName("Delete VM")
    void testDeleteVM() {
        // Create a dedicated VM for deletion test
        Integer vmId = createTestVM(TEST_PREFIX + "delete-" + System.currentTimeMillis());
        
        // Stop VM first if running
        stopVMIfRunning(vmId);
        
        // Delete the VM
        Response response = given()
            .queryParam("purge", true)
            .when()
            .delete("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(anyOf(is(200), is(202)))
            .extract()
            .response();
        
        String task = response.jsonPath().getString("task");
        if (task != null) {
            waitForTask(task);
        }
        
        // Remove from tracking
        createdVMs.remove(vmId);
        
        // Verify VM is gone
        given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(404);
        
        LOG.info("Successfully deleted VM {}", vmId);
    }
    
    // Helper methods
    
    private Integer getOrCreateTestVM() {
        if (createdVMs.isEmpty()) {
            return createTestVM(TEST_PREFIX + "vm-" + testRunId);
        }
        return createdVMs.iterator().next();
    }
    
    private Integer createTestVM(String name) {
        CreateVMRequestDTO request = new CreateVMRequestDTO(
            null,               // vmId (auto-assign)
            name,               // name
            selectedNode,       // node
            null,               // template
            1,                  // cores
            1024,               // memoryMB
            null,               // diskGB (deprecated)
            List.of(new DiskConfig(DiskConfig.DiskInterface.SCSI, 0, TEST_STORAGE, 8, null, null, null, null, null, true, false, null, null)),
            List.of(NetworkConfig.simple(TEST_BRIDGE)),
            null,               // network (deprecated)
            false,              // startOnBoot
            null,               // pool
            null,               // clientId
            null,               // project
            List.of(TEST_TAG),  // tags
            "scsi0",            // bootOrder
            null,               // cpuType
            null,               // vgaType
            null,               // firmware
            null,               // scsihw
            null                // serial0
        );
        
        Response response = given()
            .contentType("application/json")
            .body(request)
            .when()
            .post("/api/v1/vms")
            .then()
            .statusCode(201)
            .extract()
            .response();
        
        Integer vmId = response.jsonPath().getInt("vmId");
        createdVMs.add(vmId);
        
        waitForTask(response.jsonPath().getString("task"));
        
        return vmId;
    }
    
    private void createSnapshot(Integer vmId, String name) {
        CreateSnapshotRequest request = new CreateSnapshotRequest(
            name, 
            "Test snapshot",
            false,  // vmstate
            null    // ttlHours
        );
        
        Response response = given()
            .contentType("application/json")
            .body(request)
            .when()
            .post("/api/v1/vms/{vmId}/snapshots", vmId)
            .then()
            .statusCode(anyOf(is(201), is(202)))
            .extract()
            .response();
        
        createdSnapshots.computeIfAbsent(vmId, k -> new HashSet<>()).add(name);
        
        if (response.jsonPath().getString("task") != null) {
            waitForTask(response.jsonPath().getString("task"));
        }
    }
    
    private void startVMIfStopped(Integer vmId) {
        Response response = given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String status = response.jsonPath().getString("status");
        if (!"running".equals(status)) {
            Response startResponse = given()
                .when()
                .post("/api/v1/vms/{vmId}/start", vmId)
                .then()
                .statusCode(202)
                .extract()
                .response();
            
            waitForTask(startResponse.jsonPath().getString("task"));
        }
    }
    
    private void stopVMIfRunning(Integer vmId) {
        Response response = given()
            .when()
            .get("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String status = response.jsonPath().getString("status");
        if ("running".equals(status)) {
            Response stopResponse = given()
                .when()
                .post("/api/v1/vms/{vmId}/stop", vmId)
                .then()
                .statusCode(202)
                .extract()
                .response();
            
            waitForTask(stopResponse.jsonPath().getString("task"));
        }
    }
    
    private void deleteVMSafely(Integer vmId) {
        try {
            // Stop VM first
            stopVMIfRunning(vmId);
        } catch (Exception e) {
            LOG.debug("VM {} might already be stopped", vmId);
        }
        
        // Delete VM
        given()
            .queryParam("purge", true)
            .when()
            .delete("/api/v1/vms/{vmId}", vmId)
            .then()
            .statusCode(anyOf(is(200), is(202), is(404)));
    }
    
    private void waitForTask(String taskId) {
        if (taskId == null) return;
        
        int maxWaitSeconds = 60;
        int waitedSeconds = 0;
        
        while (waitedSeconds < maxWaitSeconds) {
            Response response = given()
                .when()
                .get("/api/v1/tasks/{taskId}", taskId)
                .then()
                .extract()
                .response();
            
            if (response.statusCode() == 404) {
                // Task completed and removed
                return;
            }
            
            String status = response.jsonPath().getString("status");
            if (!"running".equals(status)) {
                // Task completed
                return;
            }
            
            sleep(2);
            waitedSeconds += 2;
        }
        
        throw new AssertionError("Task " + taskId + " did not complete within " + maxWaitSeconds + " seconds");
    }
    
    private void cleanupTestResources() {
        LOG.info("Cleaning up test resources with prefix: {}", TEST_PREFIX);
        
        // Get all VMs with our test tag
        try {
            Response response = given()
                .queryParam("tags", TEST_TAG)
                .when()
                .get("/api/v1/vms")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            List<Map<String, Object>> vms = response.jsonPath().getList("$");
            for (Map<String, Object> vm : vms) {
                Integer vmId = (Integer) vm.get("vmid");
                String name = (String) vm.get("name");
                
                if (name != null && name.startsWith(TEST_PREFIX)) {
                    LOG.info("Cleaning up test VM: {} ({})", name, vmId);
                    try {
                        deleteVMSafely(vmId);
                    } catch (Exception e) {
                        LOG.warn("Failed to cleanup VM {}: {}", vmId, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to query VMs for cleanup: {}", e.getMessage());
        }
    }
    
    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
    }
}