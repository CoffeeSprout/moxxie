package com.coffeesprout.api.dto;

import java.util.Map;

public record ClientStorageUsage(
    String clientTag,
    long totalSize,
    String totalSizeHuman,
    int vmCount,
    int backupCount,
    Map<Integer, Long> sizeByVm,      // vmId -> size
    Map<String, Long> sizeByStorage   // storage -> size
) {}
