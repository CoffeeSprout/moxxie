package com.coffeesprout.service;

import com.coffeesprout.api.dto.CloudInitVMRequest;
import com.coffeesprout.api.dto.CloudInitVMRequestBuilder;
import com.coffeesprout.api.dto.DiskConfig;
import com.coffeesprout.api.dto.MigrationRequest;
import com.coffeesprout.api.dto.NetworkConfig;
import com.coffeesprout.api.dto.cluster.*;
import com.coffeesprout.client.CreateVMResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@ApplicationScoped
@AutoAuthenticate
public class ClusterProvisioningService {
    
    private static final Logger log = LoggerFactory.getLogger(ClusterProvisioningService.class);
    
    @Inject
    VMService vmService;
    
    @Inject
    VMIdService vmIdService;
    
    @Inject
    NodePlacementService nodePlacementService;
    
    @Inject
    TagService tagService;
    
    @Inject
    TicketManager ticketManager;
    
    
    // In-memory state tracking (could be replaced with persistent storage)
    private final Map<String, ClusterProvisioningState> provisioningStates = new ConcurrentHashMap<>();
    
    public Uni<ClusterProvisioningResponse> provisionCluster(ClusterSpec spec, String baseUrl) {
        String operationId = generateOperationId();
        ClusterProvisioningState state = new ClusterProvisioningState(operationId, spec);
        provisioningStates.put(operationId, state);
        
        log.info("Starting cluster provisioning for '{}' with operation ID: {}", spec.name(), operationId);
        
        // Check for dry run mode
        if (spec.options() != null && Boolean.TRUE.equals(spec.options().dryRun())) {
            log.info("DRY RUN mode - validating configuration without creating VMs");
            return validateClusterSpec(state)
                .onItem().transform(s -> {
                    s.setStatus(ClusterProvisioningState.ClusterStatus.COMPLETED);
                    s.setCurrentOperation("Dry run completed - no VMs created");
                    s.setProgressPercentage(100);
                    s.setEndTime(Instant.now());
                    return ClusterProvisioningResponse.fromState(s, baseUrl);
                });
        }
        
        // Start async provisioning
        provisionClusterAsync(state)
            .subscribe().with(
                result -> log.info("Cluster provisioning completed: {}", result),
                error -> {
                    log.error("Cluster provisioning failed", error);
                    state.setStatus(ClusterProvisioningState.ClusterStatus.FAILED);
                    state.setErrorMessage(error.getMessage());
                    state.setEndTime(Instant.now());
                }
            );
        
        // Return immediate response with operation tracking
        return Uni.createFrom().item(ClusterProvisioningResponse.fromState(state, baseUrl));
    }
    
    private Uni<ClusterProvisioningState> provisionClusterAsync(ClusterProvisioningState state) {
        return Uni.createFrom().item(state)
            .onItem().invoke(s -> {
                s.setStatus(ClusterProvisioningState.ClusterStatus.VALIDATING);
                s.setCurrentOperation("Validating cluster specification");
            })
            .onItem().transformToUni(this::validateClusterSpec)
            .onItem().invoke(s -> {
                s.setStatus(ClusterProvisioningState.ClusterStatus.PROVISIONING);
                s.setCurrentOperation("Provisioning cluster nodes");
            })
            .onItem().transformToUni(this::provisionAllNodes)
            .onItem().invoke(s -> {
                s.setStatus(ClusterProvisioningState.ClusterStatus.CONFIGURING_NETWORK);
                s.setCurrentOperation("Configuring cluster networking");
            })
            .onItem().transformToUni(this::configureNetworking)
            .onItem().invoke(s -> {
                s.setStatus(ClusterProvisioningState.ClusterStatus.POST_PROVISIONING);
                s.setCurrentOperation("Applying post-provisioning configuration");
            })
            .onItem().transformToUni(this::applyPostProvisioning)
            .onItem().invoke(s -> {
                s.setStatus(ClusterProvisioningState.ClusterStatus.COMPLETED);
                s.setCurrentOperation("Cluster provisioning completed");
                s.setProgressPercentage(100);
                s.setEndTime(Instant.now());
            })
            .onFailure().recoverWithUni(error -> handleProvisioningFailure(state, error));
    }
    
    private Uni<ClusterProvisioningState> validateClusterSpec(ClusterProvisioningState state) {
        log.debug("Validating cluster specification for '{}'", state.getSpec().name());
        
        // Pre-populate node states for tracking
        state.getSpec().nodeGroups().forEach(group -> {
            IntStream.range(0, group.count()).forEach(index -> {
                String nodeName = generateNodeName(state.getSpec(), group, index);
                ClusterProvisioningState.NodeProvisioningState nodeState = 
                    new ClusterProvisioningState.NodeProvisioningState(nodeName, group.name());
                state.addNodeState(nodeName, nodeState);
            });
        });
        
        // TODO: Add more validation logic
        // - Check resource availability
        // - Validate network configuration
        // - Check storage availability
        // - Verify image sources exist
        
        return Uni.createFrom().item(state);
    }
    
    private Uni<ClusterProvisioningState> provisionAllNodes(ClusterProvisioningState state) {
        ClusterSpec spec = state.getSpec();
        ProvisioningOptions options = spec.options();
        
        List<Uni<NodeProvisioningResult>> nodeProvisioningUnis = new ArrayList<>();
        
        // Create provisioning tasks for each node group
        spec.nodeGroups().forEach(group -> {
            IntStream.range(0, group.count()).forEach(index -> {
                String nodeName = generateNodeName(spec, group, index);
                ClusterProvisioningState.NodeProvisioningState nodeState = state.getNodeStates().get(nodeName);
                
                Uni<NodeProvisioningResult> nodeUni = provisionNode(spec, group, index, nodeState)
                    .onItem().invoke(result -> {
                        nodeState.setVmId(result.vmId());
                        nodeState.setAssignedHost(result.host());
                        nodeState.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.READY);
                        nodeState.setEndTime(Instant.now());
                        state.updateProgress();
                    })
                    .onFailure().recoverWithItem(error -> {
                        log.error("Failed to provision node '{}': {}", nodeName, error.getMessage());
                        nodeState.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.FAILED);
                        nodeState.setErrorMessage(error.getMessage());
                        nodeState.setEndTime(Instant.now());
                        state.updateProgress();
                        return new NodeProvisioningResult(nodeName, null, null, error.getMessage());
                    });
                
                nodeProvisioningUnis.add(nodeUni);
            });
        });
        
        // Execute provisioning based on options
        Multi<NodeProvisioningResult> multi;
        
        if (options.parallelProvisioning()) {
            // Convert Unis to Multis and merge with concurrency limit
            List<Multi<NodeProvisioningResult>> multis = nodeProvisioningUnis.stream()
                .map(uni -> uni.toMulti())
                .toList();
            
            multi = Multi.createBy().merging().streams(multis);
        } else {
            multi = Multi.createFrom().iterable(nodeProvisioningUnis)
                .onItem().transformToUniAndConcatenate(uni -> uni);
        }
        
        return multi.collect().asList()
            .onItem().transformToUni(results -> {
                // Check if any nodes failed
                long failedCount = results.stream()
                    .filter(r -> r.error() != null)
                    .count();
                
                if (failedCount > 0 && shouldRollback(options.rollbackStrategy())) {
                    return rollbackCluster(state)
                        .onItem().transform(s -> {
                            throw new RuntimeException(
                                String.format("Cluster provisioning failed: %d nodes failed", failedCount)
                            );
                        });
                }
                
                return Uni.createFrom().item(state);
            });
    }
    
    private Uni<NodeProvisioningResult> provisionNode(
        ClusterSpec spec,
        NodeGroupSpec group,
        int index,
        ClusterProvisioningState.NodeProvisioningState nodeState
    ) {
        nodeState.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.ALLOCATING_RESOURCES);
        nodeState.setStartTime(Instant.now());
        
        String nodeName = nodeState.getNodeName();
        NodeTemplate template = group.template();
        
        // Determine target host based on placement constraints
        String targetHost = nodePlacementService.selectHost(group, index, spec, null);
        nodeState.setAssignedHost(targetHost);
        
        log.info("Provisioning node '{}' on host '{}'", nodeName, targetHost);
        nodeState.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.CREATING_VM);
        
        // Check if this is an FCOS node (for OKD)
        boolean isFCOS = isNodeFCOS(spec, group, template);
        
        // Build cloud-init VM request (skip for FCOS)
        CloudInitConfig cloudInit = isFCOS ? null : mergeCloudInit(spec.globalCloudInit(), template.cloudInit());
        
        // Generate hostname
        String hostname = cloudInit != null ? 
            cloudInit.generateHostname(spec.name(), group.role().name().toLowerCase(), index) :
            String.format("%s-%s-%02d", spec.name(), group.name(), index + 1);
        
        // Build network configurations
        List<NetworkConfig> networks = buildNodeNetworks(spec, group, template, index);
        List<String> ipConfigs = cloudInit != null ? buildNodeIpConfigs(cloudInit, index) : List.of();
        
        // Prepare tags
        Set<String> tags = new HashSet<>(group.tags());
        tags.add("moxxie");
        tags.add("cluster-" + spec.name());
        tags.add("role-" + group.role().name().toLowerCase().replace("_", "-"));
        
        // Get next available VM ID - use range if specified
        Integer vmId;
        if (spec.options() != null && spec.options().vmIdRangeStart() != null) {
            // Calculate VM ID from range start
            int nodeIndex = 0;
            for (NodeGroupSpec g : spec.nodeGroups()) {
                if (g == group) {
                    break;
                }
                nodeIndex += g.count();
            }
            vmId = spec.options().vmIdRangeStart() + nodeIndex + index;
            log.debug("Using VM ID {} from specified range starting at {}", vmId, spec.options().vmIdRangeStart());
        } else {
            vmId = vmIdService.getNextAvailableVmId(null);
        }
        nodeState.setVmId(vmId);
        
        // Create VM request using builder pattern
        CloudInitVMRequest vmRequest;
        if (isFCOS) {
            // For FCOS nodes, create minimal VM without cloud-init
            log.info("Creating FCOS VM {} without cloud-init for OKD cluster", nodeName);
            vmRequest = CloudInitVMRequestBuilder.forFCOS(vmId, nodeName, targetHost, template)
                .networks(networks)
                .ipConfigs(ipConfigs)
                .description(buildNodeDescription(spec, group, index))
                .tags(String.join(",", tags))
                .build();
        } else {
            vmRequest = CloudInitVMRequestBuilder.forCloudInit(vmId, nodeName, targetHost, template)
                .cloudInitUser(cloudInit.user())
                .cloudInitPassword(cloudInit.password())
                .sshKeys(cloudInit.sshKeys())
                .searchDomain(cloudInit.searchDomain())
                .nameservers(cloudInit.nameservers())
                .networks(networks)
                .ipConfigs(ipConfigs)
                .start(spec.options().startAfterCreation())
                .description(buildNodeDescription(spec, group, index))
                .tags(String.join(",", tags))
                .build();
        }
        
        nodeState.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.CONFIGURING);
        
        // The VMService.createCloudInitVM method now handles creation and migration
        return Uni.createFrom().item(() -> vmService.createCloudInitVM(vmRequest, null))
            .onItem().transform(response -> {
                log.info("Successfully provisioned VM {} for node '{}' on target host '{}'", vmId, nodeName, targetHost);
                nodeState.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.READY);
                return new NodeProvisioningResult(nodeName, vmId, targetHost, null);
            });
    }
    
    private Uni<ClusterProvisioningState> configureNetworking(ClusterProvisioningState state) {
        // TODO: Implement cluster-specific networking configuration
        // This could include:
        // - Setting up VLANs
        // - Configuring SDN zones
        // - Setting up load balancer rules
        log.debug("Configuring networking for cluster '{}'", state.getSpec().name());
        return Uni.createFrom().item(state);
    }
    
    private Uni<ClusterProvisioningState> applyPostProvisioning(ClusterProvisioningState state) {
        // TODO: Implement post-provisioning steps
        // - Apply additional tags
        // - Configure monitoring
        // - Set up backup schedules
        // - Initialize cluster-specific configurations (e.g., Talos config)
        log.debug("Applying post-provisioning configuration for cluster '{}'", state.getSpec().name());
        return Uni.createFrom().item(state);
    }
    
    private Uni<ClusterProvisioningState> handleProvisioningFailure(ClusterProvisioningState state, Throwable error) {
        log.error("Cluster provisioning failed for '{}': {}", state.getSpec().name(), error.getMessage());
        state.setStatus(ClusterProvisioningState.ClusterStatus.FAILED);
        state.setErrorMessage(error.getMessage());
        state.setEndTime(Instant.now());
        
        if (shouldRollback(state.getSpec().options().rollbackStrategy())) {
            return rollbackCluster(state);
        }
        
        return Uni.createFrom().item(state);
    }
    
    private Uni<ClusterProvisioningState> rollbackCluster(ClusterProvisioningState state) {
        log.info("Rolling back cluster '{}'", state.getSpec().name());
        state.setStatus(ClusterProvisioningState.ClusterStatus.ROLLING_BACK);
        state.setCurrentOperation("Rolling back provisioned resources");
        
        // Delete all successfully created VMs
        List<Uni<Void>> deletionUnis = state.getSuccessfulNodes().stream()
            .filter(node -> node.getVmId() != null)
            .map(node -> {
                log.info("Deleting VM {} for rollback", node.getVmId());
                node.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.DELETING);
                return Uni.createFrom().voidItem()
                    .invoke(() -> vmService.deleteVM(node.getAssignedHost(), node.getVmId(), null))
                    .onItem().invoke(() -> {
                        node.setStatus(ClusterProvisioningState.NodeProvisioningState.NodeStatus.DELETED);
                    })
                    .onFailure().recoverWithNull();
            })
            .toList();
        
        List<Multi<Void>> deletionMultis = deletionUnis.stream()
            .map(uni -> uni.toMulti())
            .toList();
            
        return Multi.createBy().merging().streams(deletionMultis)
            .collect().asList()
            .onItem().transform(results -> {
                state.setStatus(ClusterProvisioningState.ClusterStatus.ROLLED_BACK);
                state.setCurrentOperation("Rollback completed");
                return state;
            });
    }
    
    // Helper methods
    
    private String generateOperationId() {
        return "op-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String generateNodeName(ClusterSpec spec, NodeGroupSpec group, int index) {
        return String.format("%s-%s-%02d", spec.name(), group.name(), index + 1);
    }
    
    private CloudInitConfig mergeCloudInit(GlobalCloudInit global, CloudInitConfig nodeConfig) {
        if (global == null) {
            return nodeConfig != null ? nodeConfig : new CloudInitConfig(
                null, null, null, null, null, null, null, null, null, null, null, null, null
            );
        }
        
        if (nodeConfig == null) {
            return new CloudInitConfig(
                global.defaultUser(),
                null, // password from global not used
                global.sshKeys(),
                null, // hostnamePattern
                global.searchDomain(),
                global.nameservers(),
                null, // ipConfigPatterns
                null, null, null, null, null, null
            );
        }
        
        // Merge with node config taking precedence
        return new CloudInitConfig(
            nodeConfig.user() != null ? nodeConfig.user() : global.defaultUser(),
            nodeConfig.password(),
            nodeConfig.sshKeys() != null ? nodeConfig.sshKeys() : global.sshKeys(),
            nodeConfig.hostnamePattern(),
            nodeConfig.searchDomain() != null ? nodeConfig.searchDomain() : global.searchDomain(),
            nodeConfig.nameservers() != null ? nodeConfig.nameservers() : global.nameservers(),
            nodeConfig.ipConfigPatterns(),
            nodeConfig.userData(),
            nodeConfig.metaData(),
            nodeConfig.networkConfig(),
            nodeConfig.packages(),
            nodeConfig.runCmd(),
            nodeConfig.modules()
        );
    }
    
    private List<NetworkConfig> buildNodeNetworks(ClusterSpec spec, NodeGroupSpec group, NodeTemplate template, int index) {
        List<NetworkConfig> networks = new ArrayList<>();
        NetworkTopology topology = spec.networkTopology();
        
        if (topology != null && topology.roleNetworkMappings().containsKey(group.role().name())) {
            // Use role-specific network mapping
            NetworkTopology.NetworkMapping mapping = topology.roleNetworkMappings().get(group.role().name());
            networks.add(new NetworkConfig(
                "virtio",
                mapping.bridge() != null ? mapping.bridge() : topology.primaryBridge(),
                mapping.vlan(),
                null, null, null, null, null, null
            ));
        } else if (template.networks() != null && !template.networks().isEmpty()) {
            // Use template networks
            networks.addAll(template.networks());
        } else {
            // Default network configuration
            networks.add(new NetworkConfig(
                "virtio",
                topology != null ? topology.primaryBridge() : "vmbr0",
                topology != null ? topology.clusterVlan() : null,
                null, null, null, null, null, null
            ));
        }
        
        return networks;
    }
    
    private List<String> buildNodeIpConfigs(CloudInitConfig cloudInit, int index) {
        if (cloudInit == null || cloudInit.ipConfigPatterns() == null) {
            return List.of("ip=dhcp");
        }
        
        return IntStream.range(0, cloudInit.ipConfigPatterns().size())
            .mapToObj(netIndex -> cloudInit.generateIpConfig(netIndex, index))
            .toList();
    }
    
    private String buildNodeDescription(ClusterSpec spec, NodeGroupSpec group, int index) {
        return String.format("Cluster: %s | Role: %s | Node: %d/%d",
            spec.name(),
            group.role().name(),
            index + 1,
            group.count()
        );
    }
    
    private boolean shouldRollback(ProvisioningOptions.RollbackStrategy strategy) {
        return strategy == ProvisioningOptions.RollbackStrategy.FULL;
    }
    
    // Operation tracking methods
    
    public ClusterProvisioningState getOperationState(String operationId) {
        return provisioningStates.get(operationId);
    }
    
    public Collection<ClusterProvisioningState> getAllOperations() {
        return provisioningStates.values();
    }
    
    public boolean cancelOperation(String operationId) {
        ClusterProvisioningState state = provisioningStates.get(operationId);
        if (state != null && state.getStatus() == ClusterProvisioningState.ClusterStatus.PROVISIONING) {
            state.setStatus(ClusterProvisioningState.ClusterStatus.CANCELLED);
            state.setEndTime(Instant.now());
            // TODO: Implement actual cancellation logic
            return true;
        }
        return false;
    }
    
    // Helper method to detect FCOS nodes
    private boolean isNodeFCOS(ClusterSpec spec, NodeGroupSpec group, NodeTemplate template) {
        // Check if this is an OKD cluster and not a bastion node
        if (spec.type() == ClusterSpec.ClusterType.OKD && 
            group.role() != NodeGroupSpec.NodeRole.BASTION) {
            return true;
        }
        
        // Also check if the template source contains "fcos" or template ID is the FCOS template
        if (template.imageSource() != null) {
            String source = template.imageSource().toLowerCase();
            return source.contains("fcos") || source.contains("10799");
        }
        
        return false;
    }
    
    // Result record
    private record NodeProvisioningResult(
        String nodeName,
        Integer vmId,
        String host,
        String error
    ) {}
}