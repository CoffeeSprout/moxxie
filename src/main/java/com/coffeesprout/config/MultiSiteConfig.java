package com.coffeesprout.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Configuration model for multi-site, multi-provider Proxmox deployments.
 * Supports federated clusters with cross-site orchestration capabilities.
 */
public class MultiSiteConfig {
    
    private List<ClusterConfig> clusters;
    private FederationConfig federation;
    private List<ResourcePool> resourcePools;
    private MigrationPolicies migrationPolicies;

    // Getters and setters
    public List<ClusterConfig> getClusters() {
        return clusters;
    }
    public void setClusters(List<ClusterConfig> clusters) {
        this.clusters = clusters;
    }
    public FederationConfig getFederation() {
        return federation;
    }
    public void setFederation(FederationConfig federation) {
        this.federation = federation;
    }
    public List<ResourcePool> getResourcePools() {
        return resourcePools;
    }
    public void setResourcePools(List<ResourcePool> resourcePools) {
        this.resourcePools = resourcePools;
    }
    public MigrationPolicies getMigrationPolicies() {
        return migrationPolicies;
    }
    public void setMigrationPolicies(MigrationPolicies migrationPolicies) {
        this.migrationPolicies = migrationPolicies;
    }

    /**
     * Individual Proxmox cluster configuration
     */
    public static class ClusterConfig {
        private String id;  // Unique cluster identifier
        private String name;
        private String provider;  // Worldstream, Databarn, Hetzner
        private String location;  // Geographic location
        private String tier;  // primary, secondary, edge
        private ProxmoxConnection connection;
        private ClusterCapabilities capabilities;
        private NetworkTopology network;
        private StorageTopology storage;
        private List<String> tags;
        private Map<String, Object> metadata;

        // Getters and setters
        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getProvider() {
            return provider;
        }
        public void setProvider(String provider) {
            this.provider = provider;
        }
        public String getLocation() {
            return location;
        }
        public void setLocation(String location) {
            this.location = location;
        }
        public String getTier() {
            return tier;
        }
        public void setTier(String tier) {
            this.tier = tier;
        }
        public ProxmoxConnection getConnection() {
            return connection;
        }
        public void setConnection(ProxmoxConnection connection) {
            this.connection = connection;
        }
        public ClusterCapabilities getCapabilities() {
            return capabilities;
        }
        public void setCapabilities(ClusterCapabilities capabilities) {
            this.capabilities = capabilities;
        }
        public NetworkTopology getNetwork() {
            return network;
        }
        public void setNetwork(NetworkTopology network) {
            this.network = network;
        }
        public StorageTopology getStorage() {
            return storage;
        }
        public void setStorage(StorageTopology storage) {
            this.storage = storage;
        }
        public List<String> getTags() {
            return tags;
        }
        public void setTags(List<String> tags) {
            this.tags = tags;
        }
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * Connection details for a Proxmox cluster
     */
    public static class ProxmoxConnection {
        @JsonProperty("api_url")
        private String apiUrl;
        private String username;
        @JsonProperty("password_env")
        private String passwordEnv;
        @JsonProperty("verify_ssl")
        private boolean verifySsl = true;
        @JsonProperty("connection_timeout")
        private int connectionTimeout = 30;  // seconds

        // Getters and setters
        public String getApiUrl() {
            return apiUrl;
        }
        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }
        public String getUsername() {
            return username;
        }
        public void setUsername(String username) {
            this.username = username;
        }
        public String getPasswordEnv() {
            return passwordEnv;
        }
        public void setPasswordEnv(String passwordEnv) {
            this.passwordEnv = passwordEnv;
        }
        public boolean isVerifySsl() {
            return verifySsl;
        }
        public void setVerifySsl(boolean verifySsl) {
            this.verifySsl = verifySsl;
        }
        public int getConnectionTimeout() {
            return connectionTimeout;
        }
        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }

    /**
     * Cluster capabilities and features
     */
    public static class ClusterCapabilities {
        @JsonProperty("max_vms")
        private int maxVms;
        @JsonProperty("supports_ceph")
        private boolean supportsCeph;
        @JsonProperty("supports_ha")
        private boolean supportsHA;
        @JsonProperty("backup_enabled")
        private boolean backupEnabled;
        @JsonProperty("live_migration")
        private boolean liveMigration;
        @JsonProperty("nested_virtualization")
        private boolean nestedVirtualization;

        // Getters and setters
        public int getMaxVms() {
            return maxVms;
        }
        public void setMaxVms(int maxVms) {
            this.maxVms = maxVms;
        }
        public boolean isSupportsCeph() {
            return supportsCeph;
        }
        public void setSupportsCeph(boolean supportsCeph) {
            this.supportsCeph = supportsCeph;
        }
        public boolean isSupportsHA() {
            return supportsHA;
        }
        public void setSupportsHA(boolean supportsHA) {
            this.supportsHA = supportsHA;
        }
        public boolean isBackupEnabled() {
            return backupEnabled;
        }
        public void setBackupEnabled(boolean backupEnabled) {
            this.backupEnabled = backupEnabled;
        }
        public boolean isLiveMigration() {
            return liveMigration;
        }
        public void setLiveMigration(boolean liveMigration) {
            this.liveMigration = liveMigration;
        }
        public boolean isNestedVirtualization() {
            return nestedVirtualization;
        }
        public void setNestedVirtualization(boolean nestedVirtualization) {
            this.nestedVirtualization = nestedVirtualization;
        }
    }

    /**
     * Network topology and connectivity
     */
    public static class NetworkTopology {
        @JsonProperty("public_subnet")
        private String publicSubnet;
        @JsonProperty("private_subnet")
        private String privateSubnet;
        @JsonProperty("vlan_range")
        private List<Integer> vlanRange;
        private List<String> bridges;
        @JsonProperty("uplink_speed")
        private String uplinkSpeed;  // e.g., "10Gbps"
        @JsonProperty("cross_connect")
        private List<CrossConnect> crossConnects;

        // Getters and setters
        public String getPublicSubnet() {
            return publicSubnet;
        }
        public void setPublicSubnet(String publicSubnet) {
            this.publicSubnet = publicSubnet;
        }
        public String getPrivateSubnet() {
            return privateSubnet;
        }
        public void setPrivateSubnet(String privateSubnet) {
            this.privateSubnet = privateSubnet;
        }
        public List<Integer> getVlanRange() {
            return vlanRange;
        }
        public void setVlanRange(List<Integer> vlanRange) {
            this.vlanRange = vlanRange;
        }
        public List<String> getBridges() {
            return bridges;
        }
        public void setBridges(List<String> bridges) {
            this.bridges = bridges;
        }
        public String getUplinkSpeed() {
            return uplinkSpeed;
        }
        public void setUplinkSpeed(String uplinkSpeed) {
            this.uplinkSpeed = uplinkSpeed;
        }
        public List<CrossConnect> getCrossConnects() {
            return crossConnects;
        }
        public void setCrossConnects(List<CrossConnect> crossConnects) {
            this.crossConnects = crossConnects;
        }
    }

    /**
     * Cross-connection to other clusters
     */
    public static class CrossConnect {
        @JsonProperty("target_cluster")
        private String targetCluster;
        private String type;  // vpn, mpls, direct
        private String latency;  // e.g., "5ms"
        private String bandwidth;  // e.g., "1Gbps"

        // Getters and setters
        public String getTargetCluster() {
            return targetCluster;
        }
        public void setTargetCluster(String targetCluster) {
            this.targetCluster = targetCluster;
        }
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getLatency() {
            return latency;
        }
        public void setLatency(String latency) {
            this.latency = latency;
        }
        public String getBandwidth() {
            return bandwidth;
        }
        public void setBandwidth(String bandwidth) {
            this.bandwidth = bandwidth;
        }
    }

    /**
     * Storage topology and capabilities
     */
    public static class StorageTopology {
        @JsonProperty("primary_storage")
        private String primaryStorage;
        private List<StorageBackend> backends;
        @JsonProperty("replication_targets")
        private List<ReplicationTarget> replicationTargets;

        // Getters and setters
        public String getPrimaryStorage() {
            return primaryStorage;
        }
        public void setPrimaryStorage(String primaryStorage) {
            this.primaryStorage = primaryStorage;
        }
        public List<StorageBackend> getBackends() {
            return backends;
        }
        public void setBackends(List<StorageBackend> backends) {
            this.backends = backends;
        }
        public List<ReplicationTarget> getReplicationTargets() {
            return replicationTargets;
        }
        public void setReplicationTargets(List<ReplicationTarget> replicationTargets) {
            this.replicationTargets = replicationTargets;
        }
    }

    /**
     * Storage backend configuration
     */
    public static class StorageBackend {
        private String name;
        private String type;  // ceph, lvm, nfs, zfs
        private String capacity;
        private String available;
        @JsonProperty("performance_tier")
        private String performanceTier;  // nvme, ssd, hdd
        private List<String> features;  // snapshots, thin-provision, encryption

        // Getters and setters
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getCapacity() {
            return capacity;
        }
        public void setCapacity(String capacity) {
            this.capacity = capacity;
        }
        public String getAvailable() {
            return available;
        }
        public void setAvailable(String available) {
            this.available = available;
        }
        public String getPerformanceTier() {
            return performanceTier;
        }
        public void setPerformanceTier(String performanceTier) {
            this.performanceTier = performanceTier;
        }
        public List<String> getFeatures() {
            return features;
        }
        public void setFeatures(List<String> features) {
            this.features = features;
        }
    }

    /**
     * Storage replication target
     */
    public static class ReplicationTarget {
        @JsonProperty("target_cluster")
        private String targetCluster;
        @JsonProperty("target_storage")
        private String targetStorage;
        private String schedule;  // cron expression
        private String type;  // async, sync

        // Getters and setters
        public String getTargetCluster() {
            return targetCluster;
        }
        public void setTargetCluster(String targetCluster) {
            this.targetCluster = targetCluster;
        }
        public String getTargetStorage() {
            return targetStorage;
        }
        public void setTargetStorage(String targetStorage) {
            this.targetStorage = targetStorage;
        }
        public String getSchedule() {
            return schedule;
        }
        public void setSchedule(String schedule) {
            this.schedule = schedule;
        }
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * Federation configuration for cross-cluster management
     */
    public static class FederationConfig {
        @JsonProperty("primary_cluster")
        private String primaryCluster;
        @JsonProperty("failover_priority")
        private List<String> failoverPriority;  // Ordered list of cluster IDs
        @JsonProperty("sync_interval")
        private int syncInterval;  // seconds
        @JsonProperty("health_check_interval")
        private int healthCheckInterval;  // seconds

        // Getters and setters
        public String getPrimaryCluster() {
            return primaryCluster;
        }
        public void setPrimaryCluster(String primaryCluster) {
            this.primaryCluster = primaryCluster;
        }
        public List<String> getFailoverPriority() {
            return failoverPriority;
        }
        public void setFailoverPriority(List<String> failoverPriority) {
            this.failoverPriority = failoverPriority;
        }
        public int getSyncInterval() {
            return syncInterval;
        }
        public void setSyncInterval(int syncInterval) {
            this.syncInterval = syncInterval;
        }
        public int getHealthCheckInterval() {
            return healthCheckInterval;
        }
        public void setHealthCheckInterval(int healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
        }
    }

    /**
     * Logical resource pools that can span clusters
     */
    public static class ResourcePool {
        private String name;
        private String description;
        private List<String> clusters;  // List of cluster IDs
        private ResourceLimits limits;
        private PlacementPolicy placementPolicy;

        // Getters and setters
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getDescription() {
            return description;
        }
        public void setDescription(String description) {
            this.description = description;
        }
        public List<String> getClusters() {
            return clusters;
        }
        public void setClusters(List<String> clusters) {
            this.clusters = clusters;
        }
        public ResourceLimits getLimits() {
            return limits;
        }
        public void setLimits(ResourceLimits limits) {
            this.limits = limits;
        }
        public PlacementPolicy getPlacementPolicy() {
            return placementPolicy;
        }
        public void setPlacementPolicy(PlacementPolicy placementPolicy) {
            this.placementPolicy = placementPolicy;
        }
    }

    /**
     * Resource limits for a pool
     */
    public static class ResourceLimits {
        @JsonProperty("max_vms")
        private int maxVms;
        @JsonProperty("max_memory")
        private String maxMemory;  // e.g., "1TB"
        @JsonProperty("max_cpu_cores")
        private int maxCpuCores;
        @JsonProperty("max_storage")
        private String maxStorage;  // e.g., "10TB"

        // Getters and setters
        public int getMaxVms() {
            return maxVms;
        }
        public void setMaxVms(int maxVms) {
            this.maxVms = maxVms;
        }
        public String getMaxMemory() {
            return maxMemory;
        }
        public void setMaxMemory(String maxMemory) {
            this.maxMemory = maxMemory;
        }
        public int getMaxCpuCores() {
            return maxCpuCores;
        }
        public void setMaxCpuCores(int maxCpuCores) {
            this.maxCpuCores = maxCpuCores;
        }
        public String getMaxStorage() {
            return maxStorage;
        }
        public void setMaxStorage(String maxStorage) {
            this.maxStorage = maxStorage;
        }
    }

    /**
     * Placement policy for VMs within a resource pool
     */
    public static class PlacementPolicy {
        private String strategy;  // balanced, performance, cost, geographic
        private List<PlacementRule> rules;
        private Map<String, Integer> clusterWeights;  // Cluster ID -> weight

        // Getters and setters
        public String getStrategy() {
            return strategy;
        }
        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }
        public List<PlacementRule> getRules() {
            return rules;
        }
        public void setRules(List<PlacementRule> rules) {
            this.rules = rules;
        }
        public Map<String, Integer> getClusterWeights() {
            return clusterWeights;
        }
        public void setClusterWeights(Map<String, Integer> clusterWeights) {
            this.clusterWeights = clusterWeights;
        }
    }

    /**
     * Placement rule for VM scheduling
     */
    public static class PlacementRule {
        private String type;  // affinity, anti-affinity, location
        private String target;  // cluster, node, tag
        private String operator;  // must, should, must_not
        private String value;

        // Getters and setters
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getTarget() {
            return target;
        }
        public void setTarget(String target) {
            this.target = target;
        }
        public String getOperator() {
            return operator;
        }
        public void setOperator(String operator) {
            this.operator = operator;
        }
        public String getValue() {
            return value;
        }
        public void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * Migration policies for cross-cluster VM movement
     */
    public static class MigrationPolicies {
        @JsonProperty("default_method")
        private String defaultMethod;  // offline, backup-restore, storage-migration
        private List<MigrationPath> paths;
        @JsonProperty("pre_migration_checks")
        private List<String> preMigrationChecks;

        // Getters and setters
        public String getDefaultMethod() {
            return defaultMethod;
        }
        public void setDefaultMethod(String defaultMethod) {
            this.defaultMethod = defaultMethod;
        }
        public List<MigrationPath> getPaths() {
            return paths;
        }
        public void setPaths(List<MigrationPath> paths) {
            this.paths = paths;
        }
        public List<String> getPreMigrationChecks() {
            return preMigrationChecks;
        }
        public void setPreMigrationChecks(List<String> preMigrationChecks) {
            this.preMigrationChecks = preMigrationChecks;
        }
    }

    /**
     * Migration path between clusters
     */
    public static class MigrationPath {
        @JsonProperty("source_cluster")
        private String sourceCluster;
        @JsonProperty("target_cluster")
        private String targetCluster;
        private String method;  // offline, backup-restore, storage-migration
        @JsonProperty("max_bandwidth")
        private String maxBandwidth;  // e.g., "100Mbps"
        @JsonProperty("compression_enabled")
        private boolean compressionEnabled;
        @JsonProperty("encryption_required")
        private boolean encryptionRequired;

        // Getters and setters
        public String getSourceCluster() {
            return sourceCluster;
        }
        public void setSourceCluster(String sourceCluster) {
            this.sourceCluster = sourceCluster;
        }
        public String getTargetCluster() {
            return targetCluster;
        }
        public void setTargetCluster(String targetCluster) {
            this.targetCluster = targetCluster;
        }
        public String getMethod() {
            return method;
        }
        public void setMethod(String method) {
            this.method = method;
        }
        public String getMaxBandwidth() {
            return maxBandwidth;
        }
        public void setMaxBandwidth(String maxBandwidth) {
            this.maxBandwidth = maxBandwidth;
        }
        public boolean isCompressionEnabled() {
            return compressionEnabled;
        }
        public void setCompressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
        }
        public boolean isEncryptionRequired() {
            return encryptionRequired;
        }
        public void setEncryptionRequired(boolean encryptionRequired) {
            this.encryptionRequired = encryptionRequired;
        }
    }
}