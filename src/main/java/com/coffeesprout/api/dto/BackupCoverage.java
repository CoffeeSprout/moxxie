package com.coffeesprout.api.dto;

import java.time.Instant;
import java.util.List;

public record BackupCoverage(
    int vmId,
    String vmName,
    List<String> tags,
    Instant lastBackup,
    String lastBackupStatus,
    boolean isOverdue,
    String overdueReason
) {}
