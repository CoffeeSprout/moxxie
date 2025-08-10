package com.coffeesprout.test;

import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.StorageResponse;
import com.coffeesprout.client.StoragePool;
import com.coffeesprout.service.TicketManager;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Base test class for Service tests.
 * Provides common mocking setup and helper methods for service testing.
 */
@QuarkusTest
public abstract class BaseServiceTest {
    
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
        // Note: refreshTicket() is private in TicketManager, it's called internally
        
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
     * Helper method to create mock storage pools for testing
     */
    protected StorageResponse createMockStorageResponse(boolean includeLocal, boolean includeShared) {
        List<StoragePool> pools = new ArrayList<>();
        
        if (includeLocal) {
            pools.add(createStoragePool("local-zfs", "zfspool", 0)); // local
            pools.add(createStoragePool("local-lvm", "lvmthin", 0)); // local
        }
        
        if (includeShared) {
            pools.add(createStoragePool("nfs-storage", "nfs", 1)); // shared
            pools.add(createStoragePool("ceph-pool", "rbd", 1)); // shared
        }
        
        StorageResponse response = new StorageResponse();
        response.setData(pools);
        return response;
    }
    
    /**
     * Helper method to create a single storage pool
     */
    protected StoragePool createStoragePool(String name, String type, int shared) {
        StoragePool pool = new StoragePool();
        pool.setStorage(name);
        pool.setType(type);
        pool.setShared(shared);
        pool.setContent("images,rootdir");
        pool.setActive(1);
        return pool;
    }
    
    /**
     * Helper method to capture arguments passed to mocked methods
     */
    protected <T> ArgumentCaptor<T> captureArgument(Class<T> clazz) {
        return ArgumentCaptor.forClass(clazz);
    }
    
    /**
     * Helper method to create test VM configuration
     */
    protected Map<String, Object> createTestVMConfig(int vmId, String... diskStorages) {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("vmid", vmId);
        config.put("name", "test-vm-" + vmId);
        config.put("cores", 2);
        config.put("memory", 4096);
        
        // Add disk configurations
        for (int i = 0; i < diskStorages.length; i++) {
            config.put("scsi" + i, diskStorages[i] + ":vm-" + vmId + "-disk-" + i + ",size=20G");
        }
        
        return config;
    }
    
    /**
     * Helper method to verify method was called with specific parameters
     * Useful for services that use @AuthTicket
     */
    protected void verifyAuthTicketUsed() {
        // Can be extended to verify specific auth ticket usage patterns
    }
    
    /**
     * Helper method to simulate authentication failure
     */
    protected void simulateAuthenticationFailure() {
        when(ticketManager.getTicket()).thenThrow(
            new RuntimeException("Authentication failed: 401 Unauthorized")
        );
    }
    
    /**
     * Helper method to simulate network/connection failures
     */
    protected void simulateNetworkFailure(String errorMessage) {
        when(proxmoxClient.getNodes(TEST_TICKET)).thenThrow(
            new RuntimeException("Network error: " + errorMessage)
        );
    }
    
    /**
     * Helper method to create test data with specific patterns
     * Useful for testing filtering and search functionality
     */
    protected <T> List<T> createTestDataWithPattern(int count, java.util.function.Function<Integer, T> generator) {
        List<T> data = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            data.add(generator.apply(i));
        }
        return data;
    }
}