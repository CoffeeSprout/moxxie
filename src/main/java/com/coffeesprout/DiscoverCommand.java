package com.coffeesprout;

// Discover Command: Fetches Proxmox details and generates proxmox-cluster.yaml

import com.coffeesprout.client.*;
import com.coffeesprout.config.ProxmoxClusterConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Command(name = "discover", description = "Discover Proxmox cluster details and generate a default YAML configuration")
public class DiscoverCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DiscoverCommand.class);

    @Option(names = {"--dry-run"}, description = "Run in dry-run mode, no changes will be applied")
    boolean dryRun;
    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;
    @Option(names = {"--api-url"}, description = "Proxmox API URL", defaultValue = "https://proxmox.example.com/api2/json")
    private String apiUrl;
    @Option(names = {"--username"}, description = "Proxmox username", defaultValue = "root@pam")
    private String username;
    @Option(names = {"--password"}, description = "Proxmox password. Can also be set using the PROXMOX_PASSWORD environment variable")
    private String password;

    @Override
    public void run() {
        log.info("Starting discovery process (dry-run: {})", dryRun);
        try {
            // Retrieve password from CLI or environment
            if (password == null || password.isEmpty()) {
                password = System.getenv("PROXMOX_PASSWORD");
            }
            if (password == null || password.isEmpty()) {
                System.err.println("Error: Proxmox password must be provided via --password option or PROXMOX_PASSWORD env variable.");
                return;
            }

            // --- Login ---
            LoginRequest loginRequest = new LoginRequest(username, password);
            LoginResponse loginResponse = proxmoxClient.login(loginRequest);
            String ticket = loginResponse.getData().getTicket();

            // --- Discover Nodes ---
            NodesResponse nodesResponse = proxmoxClient.getNodes(ticket);
            List<ProxmoxClusterConfig.Node> configNodes = new ArrayList<>();

            nodesResponse.getData().forEach(discoveredNode -> {
                // Fetch detailed status from /nodes/{node}/status
                NodeStatusResponse statusResponse = proxmoxClient.getNodeStatus(discoveredNode.getName(), ticket);
                NodeStatus status = statusResponse.getData();

                // Calculate memory details using the memory object
                long totalMemBytes = status.getMemory().getTotal();
                long availableMemBytes = status.getMemory().getFree(); // using 'free' as available memory
                long totalMemGB = totalMemBytes / (1024L * 1024L * 1024L);
                long availableMemGB = availableMemBytes / (1024L * 1024L * 1024L);

                // Get CPU count from the cpuInfo field (logical CPUs)
                int cpuCount = status.getCpuInfo().getCpus();

                ProxmoxClusterConfig.Node node = new ProxmoxClusterConfig.Node();
                node.setName(discoveredNode.getName());
                node.setTags(new ArrayList<>()); // You can add tags if desired
                node.setMemory(totalMemGB + "GB (available: " + availableMemGB + "GB)");
                node.setCpu(cpuCount);

                // --- Get storage for this node ---
                StorageResponse nodeStorageResponse = proxmoxClient.getNodeStorage(discoveredNode.getName(), ticket);
                List<ProxmoxClusterConfig.StorageOption> nodeStorageOptions = new ArrayList<>();
                nodeStorageResponse.getData().forEach(pool -> {
                    // Filter out storage entries with zero total capacity.
                    if (pool.getTotal() == 0) {
                        return; // skip this storage entry
                    }
                    ProxmoxClusterConfig.StorageOption option = new ProxmoxClusterConfig.StorageOption();
                    option.setName(pool.getStorage());
                    option.setType(pool.getType());
                    option.setCapacity(bytesToGB(pool.getTotal()));
                    option.setAvailable(bytesToGB(pool.getAvail()));
                    option.setNode(discoveredNode.getName());
                    nodeStorageOptions.add(option);
                });
                node.setStorage(nodeStorageOptions);

                configNodes.add(node);
            });

            // --- Discover Additional Bridges (if available) ---
            // This example uses a dummy list. Replace with real data if available.
            List<String> bridges = Arrays.asList("vmbr0", "vmbr1", "vlan100", "vlan101");

            // --- Build the final configuration object ---
            ProxmoxClusterConfig config = new ProxmoxClusterConfig();

            ProxmoxClusterConfig.ProxmoxDetails details = new ProxmoxClusterConfig.ProxmoxDetails();
            details.setApiUrl(apiUrl);
            details.setUsername(username);
            details.setPasswordEnv("PROXMOX_PASSWORD");
            details.setNodes(configNodes);
            config.setProxmox(details);

            ProxmoxClusterConfig.NetworkConfig network = new ProxmoxClusterConfig.NetworkConfig();
            network.setVlanRange(Arrays.asList(100, 200));
            network.setBridges(bridges);
            config.setNetwork(network);

            // Optionally, you could still build a global storage config—but now every node has its own storage info.
            // For this example, we omit the global storage section.

            // --- Generate YAML output ---
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            String yamlOutput = mapper.writeValueAsString(config);
            System.out.println("Generated YAML configuration:");
            System.out.println(yamlOutput);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper method to convert bytes to a GB string
    private String bytesToGB(long bytes) {
        long gb = bytes / (1024L * 1024L * 1024L);
        return gb + "GB";
    }
}