package com.coffeesprout.service;

import com.coffeesprout.client.*;
import com.coffeesprout.config.MoxxieConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class SDNServiceTest {
    
    @Inject
    SDNService sdnService;
    
    @InjectMock
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @InjectMock
    TicketManager ticketManager;
    
    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        Mockito.reset(proxmoxClient, ticketManager);
        
        // Setup default ticket manager behavior
        when(ticketManager.getTicket()).thenReturn("test-ticket");
        when(ticketManager.getCsrfToken()).thenReturn("test-csrf");
    }
    
    @Test
    void testGetOrAllocateVlan_NewClient() {
        // Test VLAN allocation for a new client
        String clientId = "test-client-1";
        
        int vlan = sdnService.getOrAllocateVlan(clientId);
        
        // Should allocate first available VLAN (100 by default)
        assertEquals(100, vlan);
        
        // Calling again should return the same VLAN
        int vlan2 = sdnService.getOrAllocateVlan(clientId);
        assertEquals(vlan, vlan2);
    }
    
    @Test
    void testGetOrAllocateVlan_MultipleClients() {
        // Test VLAN allocation for multiple clients
        String client1 = "test-client-1";
        String client2 = "test-client-2";
        String client3 = "test-client-3";
        
        int vlan1 = sdnService.getOrAllocateVlan(client1);
        int vlan2 = sdnService.getOrAllocateVlan(client2);
        int vlan3 = sdnService.getOrAllocateVlan(client3);
        
        // Each client should get a different VLAN
        assertNotEquals(vlan1, vlan2);
        assertNotEquals(vlan2, vlan3);
        assertNotEquals(vlan1, vlan3);
        
        // VLANs should be in the configured range
        assertTrue(vlan1 >= 100 && vlan1 <= 4000);
        assertTrue(vlan2 >= 100 && vlan2 <= 4000);
        assertTrue(vlan3 >= 100 && vlan3 <= 4000);
    }
    
    @Test
    void testCreateClientVNet() throws Exception {
        // Setup mocks
        String clientId = "test-client";
        String projectName = "web-app";
        
        CreateVNetResponse createResponse = new CreateVNetResponse();
        createResponse.setData("UPID:test");
        
        when(proxmoxClient.createVNet(
            anyString(), anyString(), anyInt(), anyString(), 
            anyString(), anyString()
        )).thenReturn(createResponse);
        
        ApplySDNResponse applyResponse = new ApplySDNResponse();
        applyResponse.setData("UPID:apply");
        
        when(proxmoxClient.applySDNConfig(anyString(), anyString()))
            .thenReturn(applyResponse);
        
        // Test VNet creation
        VNet vnet = sdnService.createClientVNet(clientId, projectName);
        
        assertNotNull(vnet);
        assertEquals("test-client-web-app", vnet.getVnet());
        assertEquals("localzone", vnet.getZone());
        assertEquals(100, vnet.getTag()); // First allocated VLAN
        assertEquals(clientId, vnet.getAlias());
        
        // Verify API calls
        verify(proxmoxClient).createVNet(
            eq("test-client-web-app"),
            eq("localzone"),
            eq(100),
            eq(clientId),
            anyString(),
            anyString()
        );
        
        verify(proxmoxClient).applySDNConfig(anyString(), anyString());
    }
    
    @Test
    void testListZones() {
        // Setup mock response
        NetworkZonesResponse response = new NetworkZonesResponse();
        NetworkZone zone1 = new NetworkZone();
        zone1.setZone("localzone");
        zone1.setType("simple");
        
        NetworkZone zone2 = new NetworkZone();
        zone2.setZone("vlanzone");
        zone2.setType("vlan");
        
        response.setData(Arrays.asList(zone1, zone2));
        
        when(proxmoxClient.listSDNZones(anyString())).thenReturn(response);
        
        // Test listing zones
        NetworkZonesResponse zones = sdnService.listZones();
        
        assertNotNull(zones);
        assertEquals(2, zones.getData().size());
        assertEquals("localzone", zones.getData().get(0).getZone());
        assertEquals("vlanzone", zones.getData().get(1).getZone());
    }
    
    @Test
    void testListVNets() {
        // Setup mock response
        VNetsResponse response = new VNetsResponse();
        VNet vnet1 = new VNet();
        vnet1.setVnet("client1-webapp");
        vnet1.setTag(100);
        vnet1.setAlias("client1");
        
        VNet vnet2 = new VNet();
        vnet2.setVnet("client2-api");
        vnet2.setTag(101);
        vnet2.setAlias("client2");
        
        response.setData(Arrays.asList(vnet1, vnet2));
        
        when(proxmoxClient.listVNets(anyString(), anyString())).thenReturn(response);
        
        // Test listing VNets
        VNetsResponse vnets = sdnService.listVNets("localzone");
        
        assertNotNull(vnets);
        assertEquals(2, vnets.getData().size());
        assertEquals("client1-webapp", vnets.getData().get(0).getVnet());
        assertEquals("client2-api", vnets.getData().get(1).getVnet());
    }
    
    @Test
    void testDeleteVNet() {
        // Setup mock response
        DeleteResponse response = new DeleteResponse();
        response.setData("UPID:delete");
        
        when(proxmoxClient.deleteVNet(anyString(), anyString(), anyString()))
            .thenReturn(response);
        
        ApplySDNResponse applyResponse = new ApplySDNResponse();
        applyResponse.setData("UPID:apply");
        
        when(proxmoxClient.applySDNConfig(anyString(), anyString()))
            .thenReturn(applyResponse);
        
        // Test VNet deletion
        assertDoesNotThrow(() -> sdnService.deleteVNet("test-vnet"));
        
        // Verify API calls
        verify(proxmoxClient).deleteVNet(eq("test-vnet"), anyString(), anyString());
        verify(proxmoxClient).applySDNConfig(anyString(), anyString());
    }
    
    @Test
    void testEnsureClientVNet_ExistingVNet() {
        // Setup mock - VNet already exists
        VNetsResponse response = new VNetsResponse();
        VNet existingVnet = new VNet();
        existingVnet.setVnet("client1-webapp");
        existingVnet.setTag(100);
        existingVnet.setAlias("client1");
        response.setData(Collections.singletonList(existingVnet));
        
        when(proxmoxClient.listVNets(anyString(), anyString())).thenReturn(response);
        
        // Test ensuring VNet exists
        String vnetId = sdnService.ensureClientVNet("client1", "webapp");
        
        assertEquals("client1-webapp", vnetId);
        
        // Should not create a new VNet
        verify(proxmoxClient, never()).createVNet(
            anyString(), anyString(), anyInt(), anyString(), 
            anyString(), anyString()
        );
    }
    
    @Test
    void testEnsureClientVNet_CreateNew() {
        // Setup mock - no existing VNet
        VNetsResponse emptyResponse = new VNetsResponse();
        emptyResponse.setData(Collections.emptyList());
        
        when(proxmoxClient.listVNets(anyString(), anyString())).thenReturn(emptyResponse);
        
        CreateVNetResponse createResponse = new CreateVNetResponse();
        createResponse.setData("UPID:create");
        
        when(proxmoxClient.createVNet(
            anyString(), anyString(), anyInt(), anyString(), 
            anyString(), anyString()
        )).thenReturn(createResponse);
        
        ApplySDNResponse applyResponse = new ApplySDNResponse();
        applyResponse.setData("UPID:apply");
        
        when(proxmoxClient.applySDNConfig(anyString(), anyString()))
            .thenReturn(applyResponse);
        
        // Test ensuring VNet exists (should create new)
        String vnetId = sdnService.ensureClientVNet("client1", "webapp");
        
        assertEquals("client1-webapp", vnetId);
        
        // Should create a new VNet
        verify(proxmoxClient).createVNet(
            eq("client1-webapp"),
            eq("localzone"),
            anyInt(),
            eq("client1"),
            anyString(),
            anyString()
        );
    }
    
    @Test
    void testSDNException() {
        // Test SDN exception handling
        when(proxmoxClient.listSDNZones(anyString()))
            .thenThrow(new RuntimeException("Connection failed"));
        
        SDNService.SDNException exception = assertThrows(
            SDNService.SDNException.class,
            () -> sdnService.listZones()
        );
        
        assertTrue(exception.getMessage().contains("Failed to list SDN zones"));
        assertNotNull(exception.getCause());
    }
}