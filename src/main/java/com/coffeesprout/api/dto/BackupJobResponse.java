package com.coffeesprout.api.dto;

import java.time.Instant;
import java.util.List;

public record BackupJobResponse(
    String id,
    String comment,
    String schedule,    // Cron expression
    boolean enabled,
    List<Integer> vmIds,
    String storage,
    String mode,
    String compress,
    String mailTo,
    String mailNotification,
    Integer maxFiles,   // Retention count
    Instant nextRun,
    Instant lastRun,
    String lastRunStatus
) {}
