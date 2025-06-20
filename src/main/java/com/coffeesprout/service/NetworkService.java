package com.coffeesprout.service;

import com.coffeesprout.client.NetworkInterface;
import com.coffeesprout.client.NetworkResponse;
import com.coffeesprout.client.Node;
import com.coffeesprout.client.ProxmoxClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
@AutoAuthenticate
public class NetworkService {
    
    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);
    
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    
    @Inject
    NodeService nodeService;
    
    /**
     * Get network interfaces for a specific node
     */
    public List<NetworkInterface> getNodeNetworks(String nodeName, @AuthTicket String ticket) {
        log.debug("Getting network interfaces for node: {}", nodeName);
        NetworkResponse response = proxmoxClient.getNodeNetworks(nodeName, ticket);
        return response.getData();
    }
    
    /**
     * Get all network interfaces across all nodes
     */
    public List<NetworkInterface> getAllNetworks(@AuthTicket String ticket) {
        log.debug("Getting network interfaces for all nodes");
        
        // Get all nodes
        List<Node> nodes = nodeService.listNodes(ticket);
        
        // Fetch networks from all nodes in parallel using virtual threads
        List<CompletableFuture<List<NetworkInterface>>> futures = nodes.stream()
            .map(node -> CompletableFuture.supplyAsync(() -> {
                try {
                    return getNodeNetworks(node.getName(), ticket);
                } catch (Exception e) {
                    log.error("Failed to get networks for node {}: {}", node.getName(), e.getMessage());
                    return new ArrayList<NetworkInterface>();
                }
            }))
            .collect(Collectors.toList());
        
        // Collect all results
        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
}