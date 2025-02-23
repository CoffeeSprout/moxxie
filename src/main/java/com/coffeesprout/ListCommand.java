package com.coffeesprout;

// List Command: Displays currently running VMs in a structured table.

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(name = "list", description = "List currently running VMs and summarize resource usage")
public class ListCommand implements Runnable {

    @Option(names = {"--dry-run"}, description = "Run in dry-run mode")
    boolean dryRun;

    private static final Logger log = LoggerFactory.getLogger(ListCommand.class);

    @Override
    public void run() {
        log.info("Listing VMs (dry-run: {})", dryRun);
        // 1. Fetch running VMs from Proxmox API.
        // 2. Display details in a structured table format:
        //    NAME         ROLE           NODE      CPU    RAM    STORAGE
        //    cp-1         control-plane  pve1      4      8GB    local-lvm
        //    worker-1     worker         pve2      6      16GB   nfs-share
        // 3. Calculate and display CPU, RAM, and storage totals per pool/project.
        // TODO: Implement API integration and table formatting.
        log.info("Listing complete.");
    }
}
