package com.coffeesprout.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record VMStorageUsage(
    int vmId,
    String vmName,
    long totalSize,
    String totalSizeHuman,
    int backupCount,
    Instant oldestBackup,
    Instant newestBackup,
    Map<String, Long> sizeByStorage,  // storage -> size
    List<String> tags
) {}