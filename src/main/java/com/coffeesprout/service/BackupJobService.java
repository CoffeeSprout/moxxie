package com.coffeesprout.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.BackupJobResponse;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.client.BackupJobData;
import com.coffeesprout.client.BackupJobDetailResponse;
import com.coffeesprout.client.BackupJobsResponse;
import com.coffeesprout.client.ProxmoxClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@AutoAuthenticate
public class BackupJobService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupJobService.class);

    @Inject
    @RestClient
    ProxmoxClient proxmoxClient;

    /**
     * List all backup jobs
     */
    public List<BackupJobResponse> listBackupJobs(@AuthTicket String ticket) {
        LOG.debug("Listing all backup jobs");

        try {
            BackupJobsResponse response = proxmoxClient.listBackupJobs(ticket);

            if (response.getData() == null) {
                return new ArrayList<>();
            }

            return response.getData().stream()
                    .map(this::convertToBackupJobResponse)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.error("Failed to list backup jobs: {}", e.getMessage());
            throw ProxmoxException.internalError("list backup jobs", e);
        }
    }

    /**
     * Get specific backup job details
     */
    public BackupJobResponse getBackupJob(String jobId, @AuthTicket String ticket) {
        LOG.debug("Getting backup job: {}", jobId);

        try {
            BackupJobDetailResponse response = proxmoxClient.getBackupJob(jobId, ticket);

            if (response.getData() == null) {
                throw ProxmoxException.notFound("Backup job", jobId);
            }

            return convertToBackupJobResponse(response.getData());

        } catch (Exception e) {
            LOG.error("Failed to get backup job {}: {}", jobId, e.getMessage());
            throw ProxmoxException.internalError("get backup job details", e);
        }
    }

    /**
     * Convert Proxmox backup job data to our API response format
     */
    private BackupJobResponse convertToBackupJobResponse(BackupJobData data) {
        // Parse VM IDs
        List<Integer> vmIds = new ArrayList<>();
        if (data.getVmid() != null && !data.getVmid().isEmpty()) {
            try {
                Arrays.stream(data.getVmid().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(id -> {
                            try {
                                vmIds.add(Integer.parseInt(id));
                            } catch (NumberFormatException e) {
                                LOG.warn("Invalid VM ID in backup job: {}", id);
                            }
                        });
            } catch (Exception e) {
                LOG.warn("Failed to parse VM IDs: {}", data.getVmid());
            }
        }

        // Parse schedule into cron expression
        String cronExpression = buildCronExpression(data);

        // Parse next run time
        Instant nextRun = parseTimestamp(data.getNext_run());

        // TODO: Get last run info from task history
        Instant lastRun = null;
        String lastRunStatus = null;

        return new BackupJobResponse(
                data.getId(),
                data.getComment(),
                cronExpression,
                data.getEnabled() != null && data.getEnabled() == 1,
                vmIds,
                data.getStorage(),
                data.getMode(),
                data.getCompress(),
                data.getMailto(),
                data.getMailnotification(),
                data.getMaxFiles(),
                nextRun,
                lastRun,
                lastRunStatus
        );
    }

    /**
     * Build cron expression from Proxmox schedule fields
     */
    private String buildCronExpression(BackupJobData data) {
        // Proxmox uses dow (day of week), starttime, and schedule fields
        // Example: dow=mon,tue,wed,thu,fri starttime=02:00

        if (data.getSchedule() != null && !data.getSchedule().isEmpty()) {
            // If schedule field is present, it might already be in cron format
            return data.getSchedule();
        }

        // Build from dow and starttime
        String minute = "0";
        String hour = "*";

        if (data.getStarttime() != null && !data.getStarttime().isEmpty()) {
            String[] timeParts = data.getStarttime().split(":");
            if (timeParts.length == 2) {
                hour = timeParts[0];
                minute = timeParts[1];
            }
        }

        String dayOfWeek = "*";
        if (data.getDow() != null && !data.getDow().isEmpty()) {
            // Convert Proxmox day names to cron numbers
            dayOfWeek = data.getDow()
                    .replace("mon", "1")
                    .replace("tue", "2")
                    .replace("wed", "3")
                    .replace("thu", "4")
                    .replace("fri", "5")
                    .replace("sat", "6")
                    .replace("sun", "0");
        }

        // Cron format: minute hour day month dayOfWeek
        return String.format("%s %s * * %s", minute, hour, dayOfWeek);
    }

    /**
     * Parse ISO timestamp string to Instant
     */
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }

        try {
            // Try parsing as ISO instant first
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            try {
                // Try parsing as local datetime
                LocalDateTime ldt = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e2) {
                LOG.warn("Failed to parse timestamp: {}", timestamp);
                return null;
            }
        }
    }
}
