package com.coffeesprout.api.dto.cluster;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.coffeesprout.util.UnitConverter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Current state of cluster provisioning operation")
public class ClusterProvisioningState {
    @Schema(description = "Unique operation ID")
    private final String operationId;

    @Schema(description = "Cluster specification")
    private final ClusterSpec spec;

    @Schema(description = "Current status")
    private volatile ClusterStatus status;

    @Schema(description = "Node provisioning states")
    private final Map<String, NodeProvisioningState> nodeStates;

    @Schema(description = "Provisioning start time")
    private final Instant startTime;

    @Schema(description = "Provisioning end time")
    private volatile Instant endTime;

    @Schema(description = "Error message if failed")
    private volatile String errorMessage;

    @Schema(description = "Progress percentage (0-100)")
    private volatile int progressPercentage;

    @Schema(description = "Current operation description")
    private volatile String currentOperation;

    public ClusterProvisioningState(String operationId, ClusterSpec spec) {
        this.operationId = operationId;
        this.spec = spec;
        this.status = ClusterStatus.PENDING;
        this.nodeStates = new ConcurrentHashMap<>();
        this.startTime = Instant.now();
        this.progressPercentage = 0;
        this.currentOperation = "Initializing cluster provisioning";
    }

    @Schema(description = "Cluster provisioning status")
    public enum ClusterStatus {
        @Schema(description = "Provisioning not yet started")
        PENDING,

        @Schema(description = "Validating resources and configuration")
        VALIDATING,

        @Schema(description = "Actively provisioning nodes")
        PROVISIONING,

        @Schema(description = "Configuring cluster networking")
        CONFIGURING_NETWORK,

        @Schema(description = "Applying post-provisioning configuration")
        POST_PROVISIONING,

        @Schema(description = "All nodes provisioned successfully")
        COMPLETED,

        @Schema(description = "Provisioning failed")
        FAILED,

        @Schema(description = "Rolling back due to failure")
        ROLLING_BACK,

        @Schema(description = "Rollback completed")
        ROLLED_BACK,

        @Schema(description = "Provisioning cancelled by user")
        CANCELLED
    }

    @Schema(description = "Individual node provisioning state")
    public static class NodeProvisioningState {
        private final String nodeName;
        private final String nodeGroup;
        private volatile NodeStatus status;
        private volatile Integer vmId;
        private volatile String assignedHost;
        private volatile String errorMessage;
        private volatile Instant startTime;
        private volatile Instant endTime;

        public NodeProvisioningState(String nodeName, String nodeGroup) {
            this.nodeName = nodeName;
            this.nodeGroup = nodeGroup;
            this.status = NodeStatus.PENDING;
        }

        @Schema(description = "Node provisioning status")
        public enum NodeStatus {
            PENDING,
            ALLOCATING_RESOURCES,
            CREATING_VM,
            CONFIGURING,
            MIGRATING,
            STARTING,
            READY,
            FAILED,
            DELETING,
            DELETED
        }

        // Getters and setters
        public String getNodeName() { return nodeName; }
        public String getNodeGroup() { return nodeGroup; }
        public NodeStatus getStatus() { return status; }
        public void setStatus(NodeStatus status) { this.status = status; }
        public Integer getVmId() { return vmId; }
        public void setVmId(Integer vmId) { this.vmId = vmId; }
        public String getAssignedHost() { return assignedHost; }
        public void setAssignedHost(String assignedHost) { this.assignedHost = assignedHost; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
    }

    // Helper methods
    public void addNodeState(String nodeName, NodeProvisioningState state) {
        nodeStates.put(nodeName, state);
    }

    public List<NodeProvisioningState> getFailedNodes() {
        return nodeStates.values().stream()
            .filter(n -> n.getStatus() == NodeProvisioningState.NodeStatus.FAILED)
            .toList();
    }

    public List<NodeProvisioningState> getSuccessfulNodes() {
        return nodeStates.values().stream()
            .filter(n -> n.getStatus() == NodeProvisioningState.NodeStatus.READY)
            .toList();
    }

    public void updateProgress() {
        int totalNodes = nodeStates.size();
        if (totalNodes == 0) {
            progressPercentage = 0;
            return;
        }

        long completedNodes = nodeStates.values().stream()
            .filter(n -> n.getStatus() == NodeProvisioningState.NodeStatus.READY ||
                        n.getStatus() == NodeProvisioningState.NodeStatus.FAILED)
            .count();

        progressPercentage = (int) ((completedNodes * UnitConverter.Percentage.PERCENT_MULTIPLIER) / totalNodes);
    }

    // Getters and setters
    public String getOperationId() { return operationId; }
    public ClusterSpec getSpec() { return spec; }
    public ClusterStatus getStatus() { return status; }
    public void setStatus(ClusterStatus status) { this.status = status; }
    public Map<String, NodeProvisioningState> getNodeStates() { return nodeStates; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }
    public String getCurrentOperation() { return currentOperation; }
    public void setCurrentOperation(String currentOperation) { this.currentOperation = currentOperation; }
}
