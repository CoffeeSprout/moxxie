package com.coffeesprout.service;

import com.coffeesprout.service.AuthTicket;
import com.coffeesprout.service.AutoAuthenticate;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.client.*;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.config.MoxxieConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@AutoAuthenticate
public class ConsoleService {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleService.class);
    private static final int CONSOLE_TICKET_VALIDITY_MINUTES = 10;

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    TicketManager ticketManager;
    
    @Inject
    VMService vmService;
    
    @Inject
    VMLocatorService vmLocatorService;
    
    @Inject
    MoxxieConfig config;
    
    @SafeMode(false)  // Read operation
    public ConsoleResponse createConsoleAccess(int vmId, ConsoleRequest request, @AuthTicket String ticket) {
        LOG.info("Creating console access for VM {} with type {}", vmId, request.getType());
        
        // First, get VM details to find the node
        VMResponse vm = vmLocatorService.findVM(vmId, ticket)
            .orElseThrow(() -> new IllegalArgumentException("VM with ID " + vmId + " not found"));
        
        String node = request.getNode() != null ? request.getNode() : vm.node();
        
        try {
            ProxmoxConsoleResponse proxmoxResponse;
            
            switch (request.getType()) {
                case VNC:
                    proxmoxResponse = proxmoxClient.createVNCProxy(node, vmId, ticket, ticketManager.getCsrfToken());
                    break;
                case SPICE:
                    proxmoxResponse = proxmoxClient.createSPICEProxy(node, vmId, ticket, ticketManager.getCsrfToken());
                    break;
                case TERMINAL:
                    proxmoxResponse = proxmoxClient.createTermProxy(node, vmId, ticket, ticketManager.getCsrfToken());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported console type: " + request.getType());
            }
            
            return buildConsoleResponse(request.getType(), proxmoxResponse, node, vmId);
            
        } catch (Exception e) {
            LOG.error("Failed to create console access for VM {}: {}", vmId, e.getMessage(), e);
            throw new RuntimeException("Failed to create console access: " + e.getMessage(), e);
        }
    }
    
    @SafeMode(false)  // Read operation
    public ConsoleWebSocketResponse getWebSocketDetails(int vmId, String consoleTicket, @AuthTicket String ticket) {
        LOG.debug("Getting WebSocket details for VM {} with console ticket", vmId);
        
        VMResponse vm = vmLocatorService.findVM(vmId, ticket)
            .orElseThrow(() -> new IllegalArgumentException("VM with ID " + vmId + " not found"));
        
        String baseUrl = config.proxmox().url();
        String wsUrl = baseUrl.replace("https://", "wss://").replace("http://", "ws://");
        String websocketPath = String.format("/api2/json/nodes/%s/qemu/%d/vncwebsocket", vm.node(), vmId);
        String fullUrl = wsUrl + websocketPath;
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "PVEAuthCookie=" + ticket);
        headers.put("Sec-WebSocket-Protocol", "binary");
        
        ConsoleWebSocketResponse response = new ConsoleWebSocketResponse();
        response.setUrl(fullUrl);
        response.setProtocol("binary");
        response.setHeaders(headers);
        
        return response;
    }
    
    @SafeMode(false)  // Read operation
    public SpiceConnectionFile generateSpiceFile(int vmId, String consoleTicket, @AuthTicket String ticket) {
        LOG.info("Generating SPICE connection file for VM {}", vmId);
        
        VMResponse vm = vmLocatorService.findVM(vmId, ticket)
            .orElseThrow(() -> new IllegalArgumentException("VM with ID " + vmId + " not found"));
        
        // Create SPICE proxy first to get connection details
        ProxmoxConsoleResponse spiceResponse = proxmoxClient.createSPICEProxy(
            vm.node(), vmId, ticket, ticketManager.getCsrfToken()
        );
        
        String host = extractHostFromUrl(config.proxmox().url());
        String port = spiceResponse.getData().getPort();
        String password = spiceResponse.getData().getPassword();
        
        StringBuilder content = new StringBuilder();
        content.append("[virt-viewer]\n");
        content.append("type=spice\n");
        content.append("host=").append(host).append("\n");
        content.append("port=").append(port).append("\n");
        content.append("password=").append(password).append("\n");
        content.append("delete-this-file=1\n");
        content.append("fullscreen=0\n");
        content.append("title=VM ").append(vmId).append(" - ").append(vm.name()).append("\n");
        content.append("toggle-fullscreen=shift+f11\n");
        content.append("release-cursor=shift+f12\n");
        content.append("secure-channels=main;inputs;cursor;playback;record;display;usbredir;smartcard\n");
        
        SpiceConnectionFile file = new SpiceConnectionFile();
        file.setContent(content.toString());
        file.setFilename("vm-" + vmId + ".vv");
        file.setMimeType("application/x-virt-viewer");
        
        return file;
    }
    
    
    private ConsoleResponse buildConsoleResponse(ConsoleType type, ProxmoxConsoleResponse proxmoxResponse, String node, int vmId) {
        ConsoleResponse response = new ConsoleResponse();
        response.setType(type.getValue());
        
        if (proxmoxResponse.getData() != null) {
            ProxmoxConsoleResponse.ProxmoxConsoleData data = proxmoxResponse.getData();
            response.setTicket(data.getTicket());
            response.setPassword(data.getPassword());
            response.setCert(data.getCert());
            response.setUpid(data.getUpid());
            response.setUser(data.getUser());
            
            if (data.getPort() != null) {
                try {
                    response.setPort(Integer.parseInt(data.getPort()));
                } catch (NumberFormatException e) {
                    LOG.warn("Could not parse port number: {}", data.getPort());
                }
            }
            
            // Set WebSocket details for VNC
            if (type == ConsoleType.VNC) {
                response.setWebsocketPort(443); // Default HTTPS port
                response.setWebsocketPath(String.format("/api2/json/nodes/%s/qemu/%d/vncwebsocket", node, vmId));
            }
        }
        
        // Set validity time
        response.setValidUntil(Instant.now().plus(CONSOLE_TICKET_VALIDITY_MINUTES, ChronoUnit.MINUTES));
        
        return response;
    }
    
    private String extractHostFromUrl(String url) {
        // Extract host from URL like https://10.0.0.10:8006/api2/json
        String host = url.replace("https://", "").replace("http://", "");
        int colonIndex = host.indexOf(':');
        if (colonIndex > 0) {
            host = host.substring(0, colonIndex);
        }
        int slashIndex = host.indexOf('/');
        if (slashIndex > 0) {
            host = host.substring(0, slashIndex);
        }
        return host;
    }
}