package com.coffeesprout.service;

import com.coffeesprout.client.Node;
import com.coffeesprout.client.NodeStatus;
import com.coffeesprout.client.NodeStatusResponse;
import com.coffeesprout.client.NodesResponse;
import com.coffeesprout.client.ProxmoxClient;
import com.coffeesprout.client.StoragePool;
import com.coffeesprout.client.StorageResponse;
import com.coffeesprout.client.VM;
import com.coffeesprout.client.VMsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@ApplicationScoped
@AutoAuthenticate
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    public List<Node> listNodes(@AuthTicket String ticket) {
        NodesResponse response = proxmoxClient.getNodes(ticket);
        return response.getData();
    }

    public NodeStatus getNodeStatus(String nodeName, @AuthTicket String ticket) {
        NodeStatusResponse response = proxmoxClient.getNodeStatus(nodeName, ticket);
        return response.getData();
    }

    public List<StoragePool> getNodeStorage(String nodeName, @AuthTicket String ticket) {
        StorageResponse response = proxmoxClient.getNodeStorage(nodeName, ticket);
        return response.getData();
    }

    public List<VM> getNodeVMs(String nodeName, @AuthTicket String ticket) {
        VMsResponse response = proxmoxClient.getNodeVMs(nodeName, ticket);
        return response.getData();
    }
}