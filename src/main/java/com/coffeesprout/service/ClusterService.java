package com.coffeesprout.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.ClusterDiscoveryResponse;
import com.coffeesprout.client.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@AutoAuthenticate
public class ClusterService {

    private static final Logger LOG = LoggerFactory.getLogger(ClusterService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    public ClusterDiscoveryResponse discoverCluster(@AuthTicket String ticket) {
        // Note: ticket parameter will be automatically injected by AuthenticationInterceptor
        NodesResponse nodesResponse = proxmoxClient.getNodes(ticket);
        List<ClusterDiscoveryResponse.NodeInfo> nodeInfos = new ArrayList<>();

        // Discover nodes
        nodesResponse.getData().forEach(discoveredNode -> {
            NodeStatusResponse statusResponse = proxmoxClient.getNodeStatus(discoveredNode.getName(), ticket);
            NodeStatus status = statusResponse.getData();

            long totalMemBytes = status.getMemory().getTotal();
            long availableMemBytes = status.getMemory().getFree();
            long totalMemGB = totalMemBytes / (1024L * 1024L * 1024L);
            long availableMemGB = availableMemBytes / (1024L * 1024L * 1024L);

            int cpuCount = status.getCpuInfo().getCpus();
            // CPU usage is not available in NodeStatus, set to 0
            double cpuUsage = 0.0;

            ClusterDiscoveryResponse.NodeInfo nodeInfo = new ClusterDiscoveryResponse.NodeInfo(
                discoveredNode.getName(),
                "online",  // Default status as Node doesn't have getStatus()
                cpuCount,
                totalMemGB,
                availableMemGB,
                cpuUsage
            );
            nodeInfos.add(nodeInfo);
        });

        // Discover storage
        StorageResponse storageResponse = proxmoxClient.getStorage(ticket);
        List<ClusterDiscoveryResponse.StorageInfo> storageInfos = new ArrayList<>();

        storageResponse.getData().forEach(pool -> {
            if (pool.getTotal() == 0) {
                return; // Skip storage without capacity info
            }

            ClusterDiscoveryResponse.StorageInfo storageInfo = new ClusterDiscoveryResponse.StorageInfo(
                pool.getStorage(),
                pool.getType(),
                pool.getTotal() / (1024L * 1024L * 1024L),
                pool.getAvail() / (1024L * 1024L * 1024L),
                null  // Storage pools don't have node information in this API
            );
            storageInfos.add(storageInfo);
        });

        // Determine cluster name (could be enhanced to get actual cluster name from API)
        String clusterName = "proxmox-cluster";

        return new ClusterDiscoveryResponse(clusterName, nodeInfos, storageInfos);
    }

    public StatusResponse getStatus() {
        return proxmoxClient.getStatus();
    }
}
