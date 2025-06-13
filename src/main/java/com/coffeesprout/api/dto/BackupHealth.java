package com.coffeesprout.api.dto;

import java.util.List;
import java.util.Map;

public record BackupHealth(
    int totalVMs,
    int vmsWithRecentBackup,    // Within threshold
    int vmsWithoutBackup,
    int vmsOverdue,
    List<BackupCoverage> coverage,
    Map<String, Integer> backupsByAge  // "< 1 day": 5, "1-7 days": 10
) {}