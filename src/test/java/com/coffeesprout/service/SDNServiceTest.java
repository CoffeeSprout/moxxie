package com.coffeesprout.service;

import java.util.Arrays;
import java.util.Collections;

import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.*;
import com.coffeesprout.test.support.TestSDNConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class SDNServiceTest {

    SDNService sdnService;

    ProxmoxClient proxmoxClient;

    TicketManager ticketManager;

    TestSDNConfig sdnConfig;

    @BeforeEach
    void setUp() {
        proxmoxClient = mock(ProxmoxClient.class);
        ticketManager = mock(TicketManager.class);
        sdnConfig = new TestSDNConfig();

        sdnService = new SDNService();
        sdnService.proxmoxClient = proxmoxClient;
        sdnService.ticketManager = ticketManager;
        sdnService.sdnConfig = sdnConfig;

        when(ticketManager.getTicket()).thenReturn("test-ticket");
        when(ticketManager.getCsrfToken()).thenReturn("test-csrf");

        sdnConfig.reset();
        sdnConfig.setEnabled(true);
        sdnConfig.setDefaultZone("localzone");
        sdnConfig.setVlanRange(100, 4000);
        sdnConfig.setVnetNamingPattern("{client}-{project}");
        sdnConfig.setApplyOnChange(true);
        sdnConfig.setAutoCreateVnets(true);
    }

    @Test
    void testGetOrAllocateVlan_NewClient() {
        String clientId = "test-client-1";

        int vlan = sdnService.getOrAllocateVlan(clientId);

        assertEquals(100, vlan);

        int vlan2 = sdnService.getOrAllocateVlan(clientId);
        assertEquals(vlan, vlan2);
    }

    @Test
    void testGetOrAllocateVlan_MultipleClients() {
        String client1 = "test-client-1";
        String client2 = "test-client-2";
        String client3 = "test-client-3";

        int vlan1 = sdnService.getOrAllocateVlan(client1);
        int vlan2 = sdnService.getOrAllocateVlan(client2);
        int vlan3 = sdnService.getOrAllocateVlan(client3);

        assertNotEquals(vlan1, vlan2);
        assertNotEquals(vlan2, vlan3);
        assertNotEquals(vlan1, vlan3);
    }

    @Test
    void testCreateClientVNet() throws Exception {
        String clientId = "test-client";
        String projectName = "web-app";

        CreateVNetResponse createResponse = new CreateVNetResponse();
        createResponse.setData("UPID:test");

        when(proxmoxClient.createVNet(anyString(), anyString(), anyInt(), anyString(), nullable(String.class), anyString()))
            .thenReturn(createResponse);

        ApplySDNResponse applyResponse = new ApplySDNResponse();
        applyResponse.setData("UPID:apply");

        when(proxmoxClient.applySDNConfig(nullable(String.class), anyString())).thenReturn(applyResponse);

        VNet vnet = sdnService.createClientVNet(clientId, projectName, null);

        assertNotNull(vnet);
        assertEquals("test-client-web-app", vnet.getVnet());
        assertEquals("localzone", vnet.getZone());
        assertEquals(100, vnet.getTag());
        assertEquals(clientId, vnet.getAlias());

        verify(proxmoxClient).createVNet(
            eq("test-client-web-app"),
            eq("localzone"),
            eq(100),
            eq(clientId),
            isNull(),
            eq("test-csrf")
        );

        verify(proxmoxClient).applySDNConfig(isNull(), eq("test-csrf"));
    }

    @Test
    void testListZones() {
        NetworkZonesResponse response = new NetworkZonesResponse();
        NetworkZone zone1 = new NetworkZone();
        zone1.setZone("localzone");
        zone1.setType("simple");

        NetworkZone zone2 = new NetworkZone();
        zone2.setZone("vlanzone");
        zone2.setType("vlan");

        response.setData(Arrays.asList(zone1, zone2));

        when(proxmoxClient.listSDNZones(nullable(String.class))).thenReturn(response);

        NetworkZonesResponse zones = sdnService.listZones(null);

        assertNotNull(zones);
        assertEquals(2, zones.getData().size());
    }

    @Test
    void testListVNets() {
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

        when(proxmoxClient.listVNets(anyString(), nullable(String.class))).thenReturn(response);

        VNetsResponse vnets = sdnService.listVNets("localzone", null);

        assertNotNull(vnets);
        assertEquals(2, vnets.getData().size());
    }

    @Test
    void testDeleteVNet() {
        DeleteResponse response = new DeleteResponse();
        response.setData("UPID:delete");

        when(proxmoxClient.deleteVNet(anyString(), nullable(String.class), anyString())).thenReturn(response);

        ApplySDNResponse applyResponse = new ApplySDNResponse();
        applyResponse.setData("UPID:apply");

        when(proxmoxClient.applySDNConfig(nullable(String.class), anyString())).thenReturn(applyResponse);

        assertDoesNotThrow(() -> sdnService.deleteVNet("test-vnet", null));

        verify(proxmoxClient).deleteVNet(eq("test-vnet"), isNull(), eq("test-csrf"));
        verify(proxmoxClient).applySDNConfig(isNull(), eq("test-csrf"));
    }

    @Test
    void testEnsureClientVNet_ExistingVNet() {
        VNetsResponse response = new VNetsResponse();
        VNet existingVnet = new VNet();
        existingVnet.setVnet("client1-webapp");
        existingVnet.setTag(100);
        existingVnet.setAlias("client1");
        response.setData(Collections.singletonList(existingVnet));

        when(proxmoxClient.listVNets(anyString(), nullable(String.class))).thenReturn(response);

        String vnetId = sdnService.ensureClientVNet("client1", "webapp", null);

        assertEquals("client1-webapp", vnetId);
        verify(proxmoxClient, never()).createVNet(anyString(), anyString(), anyInt(), anyString(), any(), anyString());
    }

    @Test
    void testEnsureClientVNet_CreateNew() {
        VNetsResponse emptyResponse = new VNetsResponse();
        emptyResponse.setData(Collections.emptyList());

        when(proxmoxClient.listVNets(anyString(), anyString())).thenReturn(emptyResponse);

        CreateVNetResponse createResponse = new CreateVNetResponse();
        createResponse.setData("UPID:create");

        when(proxmoxClient.createVNet(anyString(), anyString(), anyInt(), anyString(), nullable(String.class), anyString()))
            .thenReturn(createResponse);

        ApplySDNResponse applyResponse = new ApplySDNResponse();
        applyResponse.setData("UPID:apply");

        when(proxmoxClient.applySDNConfig(nullable(String.class), anyString())).thenReturn(applyResponse);

        String vnetId = sdnService.ensureClientVNet("client1", "webapp", null);

        assertEquals("client1-webapp", vnetId);
        verify(proxmoxClient).createVNet(eq("client1-webapp"), eq("localzone"), anyInt(), eq("client1"), isNull(), eq("test-csrf"));
        verify(proxmoxClient).applySDNConfig(isNull(), eq("test-csrf"));
    }

    @Test
    void testSDNException() {
        when(proxmoxClient.listSDNZones(nullable(String.class))).thenThrow(new RuntimeException("Connection failed"));

        ProxmoxException exception = assertThrows(ProxmoxException.class, () -> sdnService.listZones(null));

        assertEquals("INTERNAL_ERROR", exception.getErrorCode());
    }
}
