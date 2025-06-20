package com.coffeesprout.service;

import com.coffeesprout.client.ProxmoxClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@Disabled("TestInstantiation errors - needs investigation")
class TagServiceTest {
    
    @Inject
    TagService tagService;
    
    @InjectMock
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @InjectMock
    TicketManager ticketManager;
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @BeforeEach
    void setUp() {
        when(ticketManager.getTicket()).thenReturn("test-ticket");
        when(ticketManager.getCsrfToken()).thenReturn("test-csrf");
    }
    
    @Test
    void testGetVMTags() {
        // Create mock VM config response with tags
        ObjectNode configData = objectMapper.createObjectNode();
        configData.put("tags", "moxxie,client:nixz,env:prod");
        
        ObjectNode configResponse = objectMapper.createObjectNode();
        configResponse.set("data", configData);
        
        // Mock finding the VM
        ObjectNode vm = objectMapper.createObjectNode();
        vm.put("vmid", 101);
        vm.put("node", "pve1");
        
        ArrayNode vmsArray = objectMapper.createArrayNode();
        vmsArray.add(vm);
        
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", vmsArray);
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        when(proxmoxClient.getVMConfig(eq("pve1"), eq(101), anyString(), anyString()))
            .thenReturn(configResponse);
        
        // Test
        Set<String> tags = tagService.getVMTags(101, null);
        
        assertEquals(3, tags.size());
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("client:nixz"));
        assertTrue(tags.contains("env:prod"));
    }
    
    @Test
    void testGetVMTagsNotFound() {
        // Mock empty cluster response
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", objectMapper.createArrayNode());
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        
        Set<String> tags = tagService.getVMTags(999, null);
        assertTrue(tags.isEmpty());
    }
    
    @Test
    void testAddTag() {
        // Setup existing tags
        ObjectNode configData = objectMapper.createObjectNode();
        configData.put("tags", "moxxie");
        
        ObjectNode configResponse = objectMapper.createObjectNode();
        configResponse.set("data", configData);
        
        // Mock finding the VM
        ObjectNode vm = objectMapper.createObjectNode();
        vm.put("vmid", 101);
        vm.put("node", "pve1");
        
        ArrayNode vmsArray = objectMapper.createArrayNode();
        vmsArray.add(vm);
        
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", vmsArray);
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        when(proxmoxClient.getVMConfig(eq("pve1"), eq(101), anyString(), anyString()))
            .thenReturn(configResponse);
        
        // Test adding tag
        tagService.addTag(101, "env:prod", null);
        
        // Verify update was called with correct tags
        ArgumentCaptor<String> formDataCaptor = ArgumentCaptor.forClass(String.class);
        verify(proxmoxClient).updateVMConfig(eq("pve1"), eq(101), anyString(), anyString(), 
            formDataCaptor.capture());
        
        String formData = formDataCaptor.getValue();
        assertTrue(formData.contains("tags="));
        assertTrue(formData.contains("moxxie"));
        assertTrue(formData.contains("env:prod"));
    }
    
    @Test
    void testRemoveTag() {
        // Setup existing tags
        ObjectNode configData = objectMapper.createObjectNode();
        configData.put("tags", "moxxie,client:nixz,env:prod");
        
        ObjectNode configResponse = objectMapper.createObjectNode();
        configResponse.set("data", configData);
        
        // Mock finding the VM
        ObjectNode vm = objectMapper.createObjectNode();
        vm.put("vmid", 101);
        vm.put("node", "pve1");
        
        ArrayNode vmsArray = objectMapper.createArrayNode();
        vmsArray.add(vm);
        
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", vmsArray);
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        when(proxmoxClient.getVMConfig(eq("pve1"), eq(101), anyString(), anyString()))
            .thenReturn(configResponse);
        
        // Test removing tag
        tagService.removeTag(101, "env:prod", null);
        
        // Verify update was called with correct tags
        ArgumentCaptor<String> formDataCaptor = ArgumentCaptor.forClass(String.class);
        verify(proxmoxClient).updateVMConfig(eq("pve1"), eq(101), anyString(), anyString(), 
            formDataCaptor.capture());
        
        String formData = formDataCaptor.getValue();
        assertTrue(formData.contains("tags="));
        assertTrue(formData.contains("moxxie"));
        assertTrue(formData.contains("client:nixz"));
        assertFalse(formData.contains("env:prod"));
    }
    
    @Test
    void testGetAllUniqueTags() {
        // Create VMs with various tags
        ObjectNode vm1 = objectMapper.createObjectNode();
        vm1.put("vmid", 101);
        vm1.put("tags", "moxxie,client:nixz");
        
        ObjectNode vm2 = objectMapper.createObjectNode();
        vm2.put("vmid", 102);
        vm2.put("tags", "moxxie,client:acme,env:prod");
        
        ObjectNode vm3 = objectMapper.createObjectNode();
        vm3.put("vmid", 103);
        vm3.put("tags", "moxxie,client:nixz,env:dev");
        
        ArrayNode vmsArray = objectMapper.createArrayNode();
        vmsArray.add(vm1).add(vm2).add(vm3);
        
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", vmsArray);
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        
        Set<String> allTags = tagService.getAllUniqueTags(null);
        
        assertEquals(5, allTags.size());
        assertTrue(allTags.contains("moxxie"));
        assertTrue(allTags.contains("client:nixz"));
        assertTrue(allTags.contains("client:acme"));
        assertTrue(allTags.contains("env:prod"));
        assertTrue(allTags.contains("env:dev"));
    }
    
    @Test
    void testGetVMsByTag() {
        // Create VMs with various tags
        ObjectNode vm1 = objectMapper.createObjectNode();
        vm1.put("vmid", 101);
        vm1.put("tags", "moxxie,client:nixz");
        
        ObjectNode vm2 = objectMapper.createObjectNode();
        vm2.put("vmid", 102);
        vm2.put("tags", "moxxie,client:acme,env:prod");
        
        ObjectNode vm3 = objectMapper.createObjectNode();
        vm3.put("vmid", 103);
        vm3.put("tags", "moxxie,client:nixz,env:dev");
        
        ArrayNode vmsArray = objectMapper.createArrayNode();
        vmsArray.add(vm1).add(vm2).add(vm3);
        
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", vmsArray);
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        
        // Test finding VMs with client:nixz tag
        List<Integer> nixzVMs = tagService.getVMsByTag("client:nixz", null);
        assertEquals(2, nixzVMs.size());
        assertTrue(nixzVMs.contains(101));
        assertTrue(nixzVMs.contains(103));
        
        // Test finding VMs with env:prod tag
        List<Integer> prodVMs = tagService.getVMsByTag("env:prod", null);
        assertEquals(1, prodVMs.size());
        assertTrue(prodVMs.contains(102));
    }
    
    @Test
    void testBulkAddTags() {
        // Mock finding VMs and their current tags
        setupBulkTestMocks();
        
        // Test bulk add
        List<Integer> vmIds = List.of(101, 102, 999); // 999 doesn't exist
        Set<String> tagsToAdd = Set.of("env:test", "bulk-tag");
        
        Map<Integer, String> results = tagService.bulkAddTags(vmIds, tagsToAdd, null);
        
        assertEquals("success", results.get(101));
        assertEquals("success", results.get(102));
        assertEquals("error: VM not found", results.get(999));
        
        // Verify updates were called
        verify(proxmoxClient, times(2)).updateVMConfig(anyString(), anyInt(), 
            anyString(), anyString(), anyString());
    }
    
    @Test
    void testBulkRemoveTags() {
        // Mock finding VMs and their current tags
        setupBulkTestMocks();
        
        // Test bulk remove
        List<Integer> vmIds = List.of(101, 102);
        Set<String> tagsToRemove = Set.of("moxxie");
        
        Map<Integer, String> results = tagService.bulkRemoveTags(vmIds, tagsToRemove, null);
        
        assertEquals("success", results.get(101));
        assertEquals("success", results.get(102));
        
        // Verify updates were called
        verify(proxmoxClient, times(2)).updateVMConfig(anyString(), anyInt(), 
            anyString(), anyString(), anyString());
    }
    
    @Test
    void testFindVMsByNamePattern() {
        // Create VMs with various names
        ObjectNode vm1 = objectMapper.createObjectNode();
        vm1.put("vmid", 101);
        vm1.put("name", "nixz-web-01");
        
        ObjectNode vm2 = objectMapper.createObjectNode();
        vm2.put("vmid", 102);
        vm2.put("name", "nixz-db-01");
        
        ObjectNode vm3 = objectMapper.createObjectNode();
        vm3.put("vmid", 103);
        vm3.put("name", "acme-web-01");
        
        ArrayNode vmsArray = objectMapper.createArrayNode();
        vmsArray.add(vm1).add(vm2).add(vm3);
        
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", vmsArray);
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        
        // Test pattern matching
        List<Integer> nixzVMs = tagService.findVMsByNamePattern("nixz-*", null);
        assertEquals(2, nixzVMs.size());
        assertTrue(nixzVMs.contains(101));
        assertTrue(nixzVMs.contains(102));
        
        // Test suffix pattern
        List<Integer> webVMs = tagService.findVMsByNamePattern("*-web-*", null);
        assertEquals(2, webVMs.size());
        assertTrue(webVMs.contains(101));
        assertTrue(webVMs.contains(103));
    }
    
    private void setupBulkTestMocks() {
        // VM 101
        ObjectNode vm1 = objectMapper.createObjectNode();
        vm1.put("vmid", 101);
        vm1.put("node", "pve1");
        
        ObjectNode vm1Config = objectMapper.createObjectNode();
        vm1Config.put("tags", "moxxie");
        ObjectNode vm1ConfigResponse = objectMapper.createObjectNode();
        vm1ConfigResponse.set("data", vm1Config);
        
        // VM 102
        ObjectNode vm2 = objectMapper.createObjectNode();
        vm2.put("vmid", 102);
        vm2.put("node", "pve1");
        
        ObjectNode vm2Config = objectMapper.createObjectNode();
        vm2Config.put("tags", "moxxie,client:acme");
        ObjectNode vm2ConfigResponse = objectMapper.createObjectNode();
        vm2ConfigResponse.set("data", vm2Config);
        
        ArrayNode vmsArray = objectMapper.createArrayNode();
        vmsArray.add(vm1).add(vm2);
        
        ObjectNode clusterResponse = objectMapper.createObjectNode();
        clusterResponse.set("data", vmsArray);
        
        when(proxmoxClient.getClusterResources(anyString(), anyString(), eq("vm")))
            .thenReturn(clusterResponse);
        
        when(proxmoxClient.getVMConfig(eq("pve1"), eq(101), anyString(), anyString()))
            .thenReturn(vm1ConfigResponse);
        when(proxmoxClient.getVMConfig(eq("pve1"), eq(102), anyString(), anyString()))
            .thenReturn(vm2ConfigResponse);
    }
}