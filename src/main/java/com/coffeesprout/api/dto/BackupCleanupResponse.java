package com.coffeesprout.api.dto;

import java.util.List;

public record BackupCleanupResponse(
    int totalBackupsAnalyzed,
    int backupsToDelete,
    int backupsProtected,
    int backupsRetained,
    long totalSizeToFree,
    String totalSizeToFreeHuman,
    List<BackupDeletionCandidate> deletionCandidates,
    boolean dryRun
) {}
