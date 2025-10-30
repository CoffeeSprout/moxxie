package com.coffeesprout.api.dto;

public record StorageLocationUsage(
    String storage,
    long totalSize,
    String totalSizeHuman,
    int backupCount,
    long available,
    String availableHuman,
    double usagePercent
) {}
