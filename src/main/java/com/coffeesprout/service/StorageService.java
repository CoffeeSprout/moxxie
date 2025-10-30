package com.coffeesprout.service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.*;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@AutoAuthenticate
public class StorageService {

    private static final Logger LOG = LoggerFactory.getLogger(StorageService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    @Inject
    NodeService nodeService;

    @Inject
    TicketManager ticketManager;

    /**
     * List all storage pools across the cluster with aggregated statistics
     */
    public List<StoragePoolResponse> listStoragePools(@AuthTicket String ticket) {
        LOG.debug("Listing all storage pools");

        try {
            // Get cluster-wide storage configuration
            StorageResponse storageResponse = proxmoxClient.getStorage(ticket);

            if (storageResponse.getData() == null) {
                return Collections.emptyList();
            }

            // Get nodes to check storage availability
            var nodes = nodeService.listNodes(ticket);

            // Map to hold aggregated storage info
            Map<String, StoragePoolInfo> storageMap = new HashMap<>();

            // Process cluster storage configuration
            for (StoragePool storage : storageResponse.getData()) {
                StoragePoolInfo info = new StoragePoolInfo(storage);

                // For shared storage, we need to get stats from one active node
                if (storage.getShared() == 1 && storage.getActive() == 1) {
                    // Find a node that has this storage
                    for (var node : nodes) {
                        try {
                            StorageResponse nodeStorage = proxmoxClient.getNodeStorage(node.getName(), ticket);
                            if (nodeStorage.getData() != null) {
                                var nodePool = nodeStorage.getData().stream()
                                        .filter(s -> s.getStorage().equals(storage.getStorage()))
                                        .findFirst();

                                if (nodePool.isPresent()) {
                                    info.updateStats(nodePool.get());
                                    info.addNode(node.getName());
                                    break; // Only need stats from one node for shared storage
                                }
                            }
                        } catch (Exception e) {
                            LOG.debug("Failed to get storage {} from node {}: {}",
                                     storage.getStorage(), node.getName(), e.getMessage());
                        }
                    }
                } else {
                    // For non-shared storage, aggregate stats from all nodes
                    for (var node : nodes) {
                        try {
                            StorageResponse nodeStorage = proxmoxClient.getNodeStorage(node.getName(), ticket);
                            if (nodeStorage.getData() != null) {
                                var nodePool = nodeStorage.getData().stream()
                                        .filter(s -> s.getStorage().equals(storage.getStorage()))
                                        .findFirst();

                                if (nodePool.isPresent()) {
                                    info.updateStats(nodePool.get());
                                    info.addNode(node.getName());
                                }
                            }
                        } catch (Exception e) {
                            LOG.debug("Failed to get storage {} from node {}: {}",
                                     storage.getStorage(), node.getName(), e.getMessage());
                        }
                    }
                }

                storageMap.put(storage.getStorage(), info);
            }

            // Convert to response DTOs
            return storageMap.values().stream()
                    .map(StoragePoolInfo::toResponse)
                    .sorted(Comparator.comparing(StoragePoolResponse::storage))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.error("Failed to list storage pools: {}", e.getMessage());
            throw ProxmoxException.internalError("list storage pools", e);
        }
    }

    /**
     * List content of a specific storage pool
     */
    public List<com.coffeesprout.api.dto.StorageContentResponse> listStorageContent(String storageId, String contentType, @AuthTicket String ticket) {
        LOG.debug("Listing content of storage {} with type {}", storageId, contentType);

        try {
            List<com.coffeesprout.api.dto.StorageContentResponse> allContent = new ArrayList<>();

            // Get nodes where this storage exists
            var nodes = nodeService.listNodes(ticket);

            for (var node : nodes) {
                try {
                    // Check if this node has the storage
                    StorageResponse nodeStorage = proxmoxClient.getNodeStorage(node.getName(), ticket);
                    if (nodeStorage.getData() == null) {
                        continue;
                    }

                    boolean hasStorage = nodeStorage.getData().stream()
                            .anyMatch(s -> s.getStorage().equals(storageId));

                    if (hasStorage) {
                        // List content on this node
                        com.coffeesprout.client.StorageContentResponse contentResponse = proxmoxClient.listStorageContent(
                                node.getName(),
                                storageId,
                                contentType,
                                null,
                                ticket
                        );

                        if (contentResponse.getData() != null) {
                            // Convert to API response DTOs
                            List<com.coffeesprout.api.dto.StorageContentResponse> nodeContent =
                                    contentResponse.getData().stream()
                                            .filter(c -> contentType == null || c.getContent().equals(contentType))
                                            .map(c -> com.coffeesprout.api.dto.StorageContentResponse.create(
                                                    c.getVolid(),
                                                    c.getFilename(),
                                                    c.getSize(),
                                                    c.getFormat(),
                                                    c.getCtime(),
                                                    c.getNotes(),
                                                    c.isProtected(),
                                                    c.getContent(),
                                                    c.getVmid(),
                                                    c.getVerification() != null ? c.getVerification().getState() : null
                                            ))
                                            .collect(Collectors.toList());

                            allContent.addAll(nodeContent);
                        }

                        // For shared storage, we only need content from one node
                        StoragePool storage = nodeStorage.getData().stream()
                                .filter(s -> s.getStorage().equals(storageId))
                                .findFirst()
                                .orElse(null);

                        if (storage != null && storage.getShared() == 1) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Failed to list content for storage {} on node {}: {}",
                             storageId, node.getName(), e.getMessage());
                }
            }

            // Remove duplicates for shared storage (by volid)
            Map<String, com.coffeesprout.api.dto.StorageContentResponse> uniqueContent = new LinkedHashMap<>();
            for (var content : allContent) {
                uniqueContent.put(content.volid(), content);
            }

            // Sort by creation time (newest first)
            return uniqueContent.values().stream()
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.error("Failed to list storage content: {}", e.getMessage());
            throw ProxmoxException.internalError("list storage content", e);
        }
    }

    /**
     * Get detailed status of a specific storage on a specific node
     */
    public com.coffeesprout.api.dto.StorageStatusResponse getStorageStatus(String node, String storageId, @AuthTicket String ticket) {
        LOG.debug("Getting status of storage {} on node {}", storageId, node);

        try {
            // Get storage configuration
            StorageResponse nodeStorage = proxmoxClient.getNodeStorage(node, ticket);
            StoragePool storageConfig = null;

            if (nodeStorage.getData() != null) {
                storageConfig = nodeStorage.getData().stream()
                        .filter(s -> s.getStorage().equals(storageId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Storage not found on node: " + storageId));
            }

            // Get storage status
            com.coffeesprout.client.StorageStatusResponse statusResponse =
                    proxmoxClient.getStorageStatus(node, storageId, ticket);

            if (statusResponse.getData() == null) {
                throw ProxmoxException.notFound("Storage status", storageId);
            }

            var status = statusResponse.getData();

            // Parse content types
            String[] contentTypes = storageConfig.getContent() != null ?
                    storageConfig.getContent().split(",") : new String[0];

            // Determine which nodes have this storage
            String[] nodes = new String[] { node };
            if (storageConfig.getShared() == 1) {
                // For shared storage, list all nodes
                var allNodes = nodeService.listNodes(ticket);
                nodes = allNodes.stream()
                        .map(Node::getName)
                        .toArray(String[]::new);
            }

            return com.coffeesprout.api.dto.StorageStatusResponse.create(
                    storageId,
                    status.getType(),
                    status.getActive() == 1,
                    status.getEnabled() == 1,
                    status.getTotal(),
                    status.getUsed(),
                    status.getAvail(),
                    contentTypes,
                    status.getShared() == 1,
                    nodes
            );

        } catch (Exception e) {
            LOG.error("Failed to get storage status: {}", e.getMessage());
            throw ProxmoxException.internalError("get storage status", e);
        }
    }

    /**
     * Delete content from storage
     */
    public void deleteStorageContent(String volid, @AuthTicket String ticket) {
        LOG.info("Deleting storage content: {}", volid);

        try {
            // Parse volume ID to extract storage and volume path
            String[] parts = volid.split(":", 2);
            if (parts.length != 2) {
                throw ProxmoxException.badRequest("Invalid volume ID format: " + volid +
                    ". Expected format: storage:volume");
            }

            String storage = parts[0];
            String volumePath = parts[1];

            // Find which node has this storage and content
            String targetNode = findNodeWithContent(storage, volid, ticket);
            if (targetNode == null) {
                throw ProxmoxException.notFound("Storage content", volid);
            }

            // Check if content is protected
            com.coffeesprout.client.StorageContentResponse contentResponse = proxmoxClient.listStorageContent(
                    targetNode,
                    storage,
                    null,
                    null,
                    ticket
            );

            if (contentResponse.getData() != null) {
                var content = contentResponse.getData().stream()
                        .filter(c -> volid.equals(c.getVolid()))
                        .findFirst()
                        .orElse(null);

                if (content != null && content.isProtected()) {
                    throw ProxmoxException.forbidden("protected content", "delete",
                        "Remove protection flag before deletion: " + volid);
                }
            }

            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();

            // Delete the content
            TaskStatusResponse response = proxmoxClient.deleteBackup(
                    targetNode,
                    storage,
                    volumePath,
                    ticket,
                    csrfToken
            );

            if (response.getData() == null) {
                throw ProxmoxException.internalError("No task ID returned from Proxmox for content deletion");
            }

            LOG.info("Content deletion task started: {}", response.getData());

        } catch (Exception e) {
            LOG.error("Failed to delete storage content {}: {}", volid, e.getMessage());
            throw ProxmoxException.internalError("delete storage content", e);
        }
    }

    /**
     * Download content from URL to storage
     */
    public TaskResponse downloadFromUrl(String node, String storageId, DownloadUrlRequest request, @AuthTicket String ticket) {
        LOG.info("Downloading from URL {} to storage {} on node {}",
                request.url(), storageId, node);

        try {
            // Verify storage exists and supports the content type
            verifyStorageSupportsContent(node, storageId, "iso", ticket);

            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();

            // Prepare download parameters
            Integer verifyCerts = request.verifyCertificate() ? 1 : 0;

            // Determine content type from filename
            String contentType = request.filename().toLowerCase().endsWith(".qcow2") ? "images" :
                                request.filename().toLowerCase().endsWith(".img") ? "images" :
                                request.filename().toLowerCase().endsWith(".raw") ? "images" :
                                request.filename().toLowerCase().endsWith(".iso") ? "iso" :
                                request.filename().toLowerCase().endsWith(".tar.gz") ? "vztmpl" : "iso";

            // Call Proxmox API
            TaskStatusResponse response = proxmoxClient.downloadUrlToStorage(
                    node,
                    storageId,
                    request.url(),
                    contentType,
                    request.filename(),
                    request.checksum(),
                    request.checksumAlgorithm(),
                    verifyCerts,
                    ticket,
                    csrfToken
            );

            if (response.getData() == null) {
                throw ProxmoxException.internalError("No task ID returned from Proxmox");
            }

            LOG.info("Download task started: {}", response.getData());
            return new TaskResponse(response.getData(),
                    String.format("Download of %s to storage '%s' started", request.filename(), storageId));

        } catch (Exception e) {
            LOG.error("Failed to download from URL: {}", e.getMessage());
            throw ProxmoxException.internalError("download content from URL", e);
        }
    }

    /**
     * Upload file to storage
     */
    public UploadResponse uploadToStorage(String node, String storageId, String contentType,
                                         InputStream fileStream, String filename, @AuthTicket String ticket) {
        LOG.info("Uploading file to storage {} on node {}", storageId, node);

        try {
            // Verify storage exists and supports the content type
            verifyStorageSupportsContent(node, storageId, contentType, ticket);

            // Get CSRF token
            String csrfToken = ticketManager.getCsrfToken();

            // Upload the file
            TaskStatusResponse response = proxmoxClient.uploadToStorage(
                    node,
                    storageId,
                    contentType,
                    fileStream,
                    ticket,
                    csrfToken
            );

            if (response.getData() == null) {
                throw ProxmoxException.internalError("No task ID returned from Proxmox");
            }

            LOG.info("Upload task started: {}", response.getData());
            return UploadResponse.create(response.getData(), filename, storageId);

        } catch (Exception e) {
            LOG.error("Failed to upload file: {}", e.getMessage());
            throw ProxmoxException.internalError("upload file to storage", e);
        }
    }

    /**
     * Find which node has specific storage content
     */
    private String findNodeWithContent(String storageId, String volid, String ticket) {
        var nodes = nodeService.listNodes(ticket);

        for (var node : nodes) {
            try {
                com.coffeesprout.client.StorageContentResponse contentResponse = proxmoxClient.listStorageContent(
                        node.getName(),
                        storageId,
                        null,
                        null,
                        ticket
                );

                if (contentResponse.getData() != null) {
                    boolean hasContent = contentResponse.getData().stream()
                            .anyMatch(c -> volid.equals(c.getVolid()));

                    if (hasContent) {
                        return node.getName();
                    }
                }
            } catch (Exception e) {
                LOG.debug("Failed to check content on node {}: {}", node.getName(), e.getMessage());
            }
        }

        return null;
    }

    /**
     * Verify that a storage supports a specific content type
     */
    private void verifyStorageSupportsContent(String node, String storageId, String contentType, String ticket) {
        StorageResponse nodeStorage = proxmoxClient.getNodeStorage(node, ticket);

        if (nodeStorage.getData() == null) {
            throw ProxmoxException.notFound("Storage", storageId, "Storage not available on node " + node);
        }

        StoragePool storage = nodeStorage.getData().stream()
                .filter(s -> s.getStorage().equals(storageId))
                .findFirst()
                .orElseThrow(() -> ProxmoxException.notFound("Storage", storageId));

        if (storage.getContent() == null || !storage.getContent().contains(contentType)) {
            throw ProxmoxException.operationNotSupported("content type '" + contentType + "' on storage '" + storageId + "'",
                "this storage does not support this content type");
        }

        if (storage.getActive() != 1) {
            throw ProxmoxException.prerequisiteFailed("use storage",
                "storage is not active: " + storageId,
                "Activate the storage in Proxmox configuration");
        }
    }

    /**
     * Helper class to aggregate storage pool information
     */
    private static class StoragePoolInfo {
        private final StoragePool baseConfig;
        private final Set<String> nodes = new HashSet<>();
        private long totalCapacity = 0;
        private long totalUsed = 0;
        private long totalAvailable = 0;
        private boolean hasStats = false;

        StoragePoolInfo(StoragePool config) {
            this.baseConfig = config;
        }

        void updateStats(StoragePool nodePool) {
            if (!hasStats) {
                // First node with stats
                totalCapacity = nodePool.getTotal();
                totalUsed = nodePool.getUsed();
                totalAvailable = nodePool.getAvail();
                hasStats = true;
            } else if (baseConfig.getShared() != 1) {
                // Aggregate for non-shared storage
                totalCapacity += nodePool.getTotal();
                totalUsed += nodePool.getUsed();
                totalAvailable += nodePool.getAvail();
            }
        }

        void addNode(String node) {
            nodes.add(node);
        }

        StoragePoolResponse toResponse() {
            List<String> contentTypes = baseConfig.getContent() != null ?
                    Arrays.asList(baseConfig.getContent().split(",")) :
                    Collections.emptyList();

            return StoragePoolResponse.create(
                    baseConfig.getStorage(),
                    baseConfig.getType(),
                    baseConfig.getActive() == 1,
                    baseConfig.getShared() == 1,
                    contentTypes,
                    new ArrayList<>(nodes),
                    totalCapacity,
                    totalUsed,
                    totalAvailable
            );
        }
    }
}
