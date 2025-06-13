package com.coffeesprout.api.dto;

import java.time.Instant;

public record TrendPoint(
    Instant timestamp,
    long totalSize,
    int backupCount,
    long sizeAdded,
    long sizeRemoved
) {}