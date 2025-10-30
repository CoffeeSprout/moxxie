package com.coffeesprout.api.dto.cluster;

import java.time.Instant;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Response for cluster provisioning operation")
public record ClusterProvisioningResponse(
    @Schema(description = "Unique operation ID for tracking", example = "op-12345")
    String operationId,

    @Schema(description = "Cluster name", example = "talos-prod-01")
    String clusterName,

    @Schema(description = "Current provisioning status")
    ClusterProvisioningState.ClusterStatus status,

    @Schema(description = "Progress percentage (0-100)", example = "45")
    int progressPercentage,

    @Schema(description = "Current operation being performed", example = "Creating control plane nodes")
    String currentOperation,

    @Schema(description = "Number of nodes to provision", example = "6")
    int totalNodes,

    @Schema(description = "Number of successfully provisioned nodes", example = "3")
    int successfulNodes,

    @Schema(description = "Number of failed nodes", example = "0")
    int failedNodes,

    @Schema(description = "Provisioning start time")
    Instant startTime,

    @Schema(description = "Provisioning end time (if completed)")
    Instant endTime,

    @Schema(description = "Error message if provisioning failed")
    String errorMessage,

    @Schema(description = "Individual node states")
    List<NodeStateInfo> nodeStates,

    @Schema(description = "Links for operation management")
    OperationLinks links
) {
    @Schema(description = "Simplified node state information")
    public record NodeStateInfo(
        @Schema(description = "Node name", example = "talos-prod-01-control-01")
        String name,

        @Schema(description = "Node group", example = "control-plane")
        String nodeGroup,

        @Schema(description = "Assigned VM ID", example = "201")
        Integer vmId,

        @Schema(description = "Host where VM is placed", example = "pve-node-01")
        String host,

        @Schema(description = "Node provisioning status")
        ClusterProvisioningState.NodeProvisioningState.NodeStatus status,

        @Schema(description = "Error message if node provisioning failed")
        String errorMessage
    ) {}

    @Schema(description = "Links for operation management")
    public record OperationLinks(
        @Schema(description = "URL to get operation status")
        String status,

        @Schema(description = "URL to cancel operation")
        String cancel,

        @Schema(description = "URL to get operation logs")
        String logs,

        @Schema(description = "URL to get cluster details (when ready)")
        String cluster
    ) {}

    public static ClusterProvisioningResponse fromState(ClusterProvisioningState state, String baseUrl) {
        List<NodeStateInfo> nodeInfos = state.getNodeStates().values().stream()
            .map(node -> new NodeStateInfo(
                node.getNodeName(),
                node.getNodeGroup(),
                node.getVmId(),
                node.getAssignedHost(),
                node.getStatus(),
                node.getErrorMessage()
            ))
            .toList();

        String opId = state.getOperationId();
        OperationLinks links = new OperationLinks(
            baseUrl + "/api/v1/clusters/operations/" + opId,
            baseUrl + "/api/v1/clusters/operations/" + opId + "/cancel",
            baseUrl + "/api/v1/clusters/operations/" + opId + "/logs",
            state.getStatus() == ClusterProvisioningState.ClusterStatus.COMPLETED
                ? baseUrl + "/api/v1/clusters/" + state.getSpec().name()
                : null
        );

        return new ClusterProvisioningResponse(
            opId,
            state.getSpec().name(),
            state.getStatus(),
            state.getProgressPercentage(),
            state.getCurrentOperation(),
            state.getNodeStates().size(),
            (int) state.getSuccessfulNodes().size(),
            (int) state.getFailedNodes().size(),
            state.getStartTime(),
            state.getEndTime(),
            state.getErrorMessage(),
            nodeInfos,
            links
        );
    }
}
