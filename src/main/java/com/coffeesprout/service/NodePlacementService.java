package com.coffeesprout.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.cluster.ClusterSpec;
import com.coffeesprout.api.dto.cluster.NodeGroupSpec;
import com.coffeesprout.api.dto.cluster.PlacementConstraints;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.Node;
import com.coffeesprout.client.NodeStatusResponse;
import com.coffeesprout.client.NodesResponse;
import com.coffeesprout.client.ProxmoxClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@AutoAuthenticate
public class NodePlacementService {

    private static final Logger LOG = LoggerFactory.getLogger(NodePlacementService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Inject
    TicketManager ticketManager;

    // Track node assignments during cluster provisioning
    private final Map<String, ClusterNodeAssignments> clusterAssignments = new ConcurrentHashMap<>();

    public String selectHost(NodeGroupSpec group, int nodeIndex, ClusterSpec clusterSpec, @AuthTicket String ticket) {
        String clusterId = clusterSpec.name();
        PlacementConstraints constraints = group.placement();

        // Get or create cluster assignments
        ClusterNodeAssignments assignments = clusterAssignments.computeIfAbsent(
            clusterId, k -> new ClusterNodeAssignments()
        );

        try {
            // Get available nodes
            List<NodeInfo> availableNodes = getAvailableNodes(constraints, ticket);

            if (availableNodes.isEmpty()) {
                throw ProxmoxException.prerequisiteFailed(
                    "node placement",
                    "available nodes matching placement constraints",
                    "Relax placement constraints or add more nodes to the cluster"
                );
            }

            // Select best node based on strategy
            NodeInfo selectedNode = selectBestNode(
                availableNodes,
                group,
                nodeIndex,
                assignments,
                constraints.antiAffinity()
            );

            // Record assignment
            assignments.recordAssignment(group.name(), selectedNode.name());

            LOG.info("Selected node '{}' for {}-{} with strategy {}",
                selectedNode.name(), group.name(), nodeIndex, constraints.antiAffinity());

            return selectedNode.name();

        } catch (Exception e) {
            LOG.error("Failed to select host for node placement", e);
            // Fallback to first available node
            return getFirstAvailableNode(constraints);
        }
    }

    private List<NodeInfo> getAvailableNodes(PlacementConstraints constraints, @AuthTicket String ticket) {
        NodesResponse response = proxmoxClient.getNodes(ticket);

        return response.getData().stream()
            .filter(node -> isNodeEligible(node, constraints))
            .map(node -> {
                try {
                    NodeStatusResponse status = proxmoxClient.getNodeStatus(node.getName(), ticket);
                    return new NodeInfo(node, status.getData());
                } catch (Exception e) {
                    LOG.warn("Failed to get status for node {}, excluding from placement", node.getName());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(NodeInfo::getAvailableScore).reversed())
            .collect(Collectors.toList());
    }

    private boolean isNodeEligible(Node node, PlacementConstraints constraints) {
        // Check if node is online
        if (!"online".equals(node.getStatus())) {
            return false;
        }

        // Check preferred nodes
        if (!constraints.preferredNodes().isEmpty() &&
            !constraints.preferredNodes().contains(node.getName())) {
            return false;
        }

        // Check avoided nodes
        // TODO: Check required node tags when tag support is added to nodes
        return !constraints.avoidNodes().contains(node.getName());
    }

    private NodeInfo selectBestNode(
        List<NodeInfo> availableNodes,
        NodeGroupSpec group,
        int nodeIndex,
        ClusterNodeAssignments assignments,
        PlacementConstraints.AntiAffinityStrategy strategy
    ) {
        Set<String> groupAssignments = assignments.getGroupAssignments(group.name());

        switch (strategy) {
            case NONE:
                // Simple round-robin
                return availableNodes.get(nodeIndex % availableNodes.size());

            case SOFT:
                // Prefer nodes without existing group members, but allow same host if needed
                return availableNodes.stream()
                    .min(Comparator.comparing(node -> {
                        int sameGroupCount = groupAssignments.contains(node.name()) ?
                            Collections.frequency(new ArrayList<>(groupAssignments), node.name()) : 0;
                        // Lower score is better (fewer same-group nodes)
                        return sameGroupCount * 1000 - node.getAvailableScore();
                    }))
                    .orElse(availableNodes.get(0));

            case HARD:
                // Strictly enforce anti-affinity
                List<NodeInfo> eligibleNodes = availableNodes.stream()
                    .filter(node -> !groupAssignments.contains(node.name()))
                    .collect(Collectors.toList());

                if (eligibleNodes.isEmpty()) {
                    throw ProxmoxException.prerequisiteFailed(
                        "hard anti-affinity placement",
                        "nodes without existing members of group " + group.name(),
                        "Use 'soft' anti-affinity strategy or add more nodes to the cluster"
                    );
                }

                // Select node with most available resources
                return eligibleNodes.get(0);

            case ZONE_AWARE:
                // TODO: Implement zone-aware placement when zone metadata is available
                // For now, fall back to SOFT strategy
                return selectBestNode(availableNodes, group, nodeIndex, assignments,
                    PlacementConstraints.AntiAffinityStrategy.SOFT);

            default:
                return availableNodes.get(0);
        }
    }

    private String getFirstAvailableNode(PlacementConstraints constraints) {
        try {
            String ticket = ticketManager.getTicket();
            NodesResponse response = proxmoxClient.getNodes(ticket);

            return response.getData().stream()
                .filter(node -> "online".equals(node.getStatus()))
                .filter(node -> !constraints.avoidNodes().contains(node.getName()))
                .map(Node::getName)
                .findFirst()
                .orElseThrow(() -> ProxmoxException.notFound(
                    "available nodes",
                    "online nodes not in avoid list",
                    "Check node status and placement constraints"
                ));

        } catch (Exception e) {
            LOG.error("Failed to get first available node", e);
            throw ProxmoxException.internalError("determine node placement", e);
        }
    }

    public void clearClusterAssignments(String clusterId) {
        clusterAssignments.remove(clusterId);
    }

    // Helper classes

    private static class NodeInfo {
        private final Node node;
        private final com.coffeesprout.client.NodeStatus status;

        public NodeInfo(Node node, com.coffeesprout.client.NodeStatus status) {
            this.node = node;
            this.status = status;
        }

        public String name() {
            return node.getName();
        }

        public double getAvailableScore() {
            // Calculate a score based on available resources
            // Higher score = more resources available

            double cpuScore = 1.0 - node.getCpu(); // CPU usage (0-1)

            double memScore = 0.0;
            if (status != null && status.getMemory() != null) {
                long total = status.getMemory().getTotal();
                long used = status.getMemory().getUsed();
                if (total > 0) {
                    memScore = 1.0 - ((double) used / total);
                }
            }

            // Weight CPU and memory equally
            return (cpuScore + memScore) / 2.0 * 100;
        }
    }

    private static class ClusterNodeAssignments {
        private final Map<String, List<String>> groupAssignments = new ConcurrentHashMap<>();

        public void recordAssignment(String groupName, String nodeName) {
            groupAssignments.computeIfAbsent(groupName, k -> new ArrayList<>()).add(nodeName);
        }

        public Set<String> getGroupAssignments(String groupName) {
            return new HashSet<>(groupAssignments.getOrDefault(groupName, Collections.emptyList()));
        }

        public Map<String, Integer> getNodeCounts() {
            Map<String, Integer> counts = new HashMap<>();
            groupAssignments.values().forEach(assignments -> {
                assignments.forEach(node -> counts.merge(node, 1, Integer::sum));
            });
            return counts;
        }
    }
}
