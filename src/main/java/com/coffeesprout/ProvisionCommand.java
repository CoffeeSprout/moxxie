package com.coffeesprout;

// Provision Command: Reads YAML, selects nodes, and provisions VMs.

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(name = "provision", description = "Provision Talos Linux VMs on Proxmox based on YAML configuration")
public class ProvisionCommand implements Runnable {

    @Option(names = {"--dry-run"}, description = "Run in dry-run mode, simulating provisioning actions")
    boolean dryRun;

    private static final Logger log = LoggerFactory.getLogger(ProvisionCommand.class);

    @Override
    public void run() {
        log.info("Starting provisioning process (dry-run: {})", dryRun);
        // 1. Read proxmox-cluster.yaml and clusters/{cluster-name}/cluster.yaml.
        // 2. Use round-robin and resource-aware placement to auto-assign nodes.
        // 3. Interact with the Proxmox API to create VMs with the specified resources and networking.
        // 4. Output a summary of CPU, RAM, and storage usage per cluster.
        // 5. Log all actions performed.
        // TODO: Add YAML parsing and API integration.
        log.info("Provisioning process complete. (dry-run: {})", dryRun);
    }
}