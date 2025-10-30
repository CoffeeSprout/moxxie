package com.coffeesprout.api.dto;

import java.time.Instant;

public record BackupDeletionCandidate(
    String volid,
    int vmId,
    String vmName,
    long size,
    String sizeHuman,
    Instant createdAt,
    String reason,
    boolean isProtected
) {}
