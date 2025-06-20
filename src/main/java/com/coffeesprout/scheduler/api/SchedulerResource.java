package com.coffeesprout.scheduler.api;

import com.coffeesprout.scheduler.api.dto.JobExecutionResponse;
import com.coffeesprout.scheduler.api.dto.ScheduledJobRequest;
import com.coffeesprout.scheduler.api.dto.ScheduledJobResponse;
import com.coffeesprout.scheduler.entity.*;
import com.coffeesprout.scheduler.event.JobCreatedEvent;
import com.coffeesprout.scheduler.service.SchedulerService;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for scheduler management
 */
@Path("/api/v1/scheduler")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Scheduler", description = "Manage scheduled jobs and executions")
public class SchedulerResource {
    
    private static final Logger log = LoggerFactory.getLogger(SchedulerResource.class);
    
    @Inject
    SchedulerService schedulerService;
    
    @Inject
    Event<JobCreatedEvent> jobCreatedEvent;
    
    @GET
    @Path("/jobs")
    @Operation(summary = "List all scheduled jobs")
    @APIResponse(responseCode = "200", description = "List of scheduled jobs")
    @Transactional
    public Response listJobs(
        @QueryParam("enabled") @Parameter(description = "Filter by enabled status") Boolean enabled,
        @QueryParam("taskType") @Parameter(description = "Filter by task type") String taskType,
        @QueryParam("page") @DefaultValue("0") @Parameter(description = "Page number") int page,
        @QueryParam("size") @DefaultValue("20") @Parameter(description = "Page size") int size
    ) {
        try {
            // Build query
            Map<String, Object> params = new HashMap<>();
            StringBuilder query = new StringBuilder();
            
            if (enabled != null) {
                query.append("enabled = :enabled");
                params.put("enabled", enabled);
            }
            
            if (taskType != null && !taskType.isBlank()) {
                if (query.length() > 0) query.append(" AND ");
                query.append("taskType.name = :taskType");
                params.put("taskType", taskType);
            }
            
            // Execute query
            List<ScheduledJob> jobs;
            long total;
            
            if (query.length() > 0) {
                jobs = ScheduledJob.find(query.toString(), Sort.by("name"), params)
                    .page(Page.of(page, size))
                    .list();
                total = ScheduledJob.count(query.toString(), params);
            } else {
                jobs = ScheduledJob.findAll(Sort.by("name"))
                    .page(Page.of(page, size))
                    .list();
                total = ScheduledJob.count();
            }
            
            // Convert to response DTOs
            List<ScheduledJobResponse> responses = jobs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
            
            Map<String, Object> result = Map.of(
                "jobs", responses,
                "total", total,
                "page", page,
                "size", size,
                "totalPages", (total + size - 1) / size
            );
            
            return Response.ok(result).build();
            
        } catch (Exception e) {
            log.error("Failed to list jobs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/jobs/{id}")
    @Operation(summary = "Get scheduled job by ID")
    @APIResponse(responseCode = "200", description = "Job details")
    @APIResponse(responseCode = "404", description = "Job not found")
    @Transactional
    public Response getJob(@PathParam("id") Long id) {
        ScheduledJob job = ScheduledJob.findById(id);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Job not found"))
                .build();
        }
        
        return Response.ok(toResponse(job)).build();
    }
    
    @POST
    @Path("/jobs")
    @Operation(summary = "Create a new scheduled job")
    @APIResponse(responseCode = "201", description = "Job created successfully")
    @APIResponse(responseCode = "400", description = "Invalid request")
    @Transactional
    public Response createJob(@Valid ScheduledJobRequest request) {
        try {
            // Validate task type
            TaskType taskType = TaskType.findByName(request.taskType());
            if (taskType == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid task type: " + request.taskType()))
                    .build();
            }
            
            // Check for duplicate name
            if (ScheduledJob.findByName(request.name()) != null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Job with name already exists: " + request.name()))
                    .build();
            }
            
            // Create job
            ScheduledJob job = schedulerService.createJob(
                request.name(),
                request.description(),
                request.taskType(),
                request.cronExpression(),
                "API" // TODO: Get from security context
            );
            
            // Set optional fields
            if (request.enabled() != null) {
                job.enabled = request.enabled();
            }
            if (request.maxRetries() != null) {
                job.maxRetries = request.maxRetries();
            }
            if (request.retryDelaySeconds() != null) {
                job.retryDelaySeconds = request.retryDelaySeconds();
            }
            if (request.timeoutSeconds() != null) {
                job.timeoutSeconds = request.timeoutSeconds();
            }
            
            // Add parameters
            if (request.parameters() != null) {
                for (Map.Entry<String, String> entry : request.parameters().entrySet()) {
                    job.addParameter(entry.getKey(), entry.getValue());
                }
            }
            
            // Add VM selectors
            if (request.vmSelectors() != null) {
                for (var selectorReq : request.vmSelectors()) {
                    JobVMSelector selector = new JobVMSelector();
                    selector.job = job;
                    selector.selectorType = selectorReq.type();
                    selector.selectorValue = selectorReq.value();
                    selector.excludeExpression = selectorReq.excludeExpression();
                    selector.persist();
                    job.vmSelectors.add(selector);
                }
            }
            
            job.persist();
            
            // Fire event to schedule job after transaction commits
            // This avoids transaction conflicts between JTA and Quartz jdbc-cmt
            jobCreatedEvent.fire(new JobCreatedEvent(job.id, job.name, job.enabled));
            
            if (job.enabled) {
                log.info("Job {} created and will be scheduled after transaction commit", job.name);
            }
            
            return Response.status(Response.Status.CREATED)
                .entity(toResponse(job))
                .build();
            
        } catch (Exception e) {
            log.error("Failed to create job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @PUT
    @Path("/jobs/{id}")
    @Operation(summary = "Update a scheduled job")
    @APIResponse(responseCode = "200", description = "Job updated successfully")
    @APIResponse(responseCode = "404", description = "Job not found")
    @Transactional
    public Response updateJob(@PathParam("id") Long id, @Valid ScheduledJobRequest request) {
        try {
            ScheduledJob job = ScheduledJob.findById(id);
            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Job not found"))
                    .build();
            }
            
            // Update basic fields
            if (!job.name.equals(request.name())) {
                // Check for duplicate name
                if (ScheduledJob.findByName(request.name()) != null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Job with name already exists: " + request.name()))
                        .build();
                }
                job.name = request.name();
            }
            
            job.description = request.description();
            
            // Update task type if changed
            if (!job.taskType.name.equals(request.taskType())) {
                TaskType taskType = TaskType.findByName(request.taskType());
                if (taskType == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid task type: " + request.taskType()))
                        .build();
                }
                job.taskType = taskType;
            }
            
            // Update schedule if changed
            boolean scheduleChanged = !job.cronExpression.equals(request.cronExpression());
            if (scheduleChanged) {
                schedulerService.updateJobSchedule(id, request.cronExpression());
            }
            
            // Update optional fields
            if (request.maxRetries() != null) {
                job.maxRetries = request.maxRetries();
            }
            if (request.retryDelaySeconds() != null) {
                job.retryDelaySeconds = request.retryDelaySeconds();
            }
            if (request.timeoutSeconds() != null) {
                job.timeoutSeconds = request.timeoutSeconds();
            }
            
            // Update enabled status if changed
            if (request.enabled() != null && job.enabled != request.enabled()) {
                schedulerService.setJobEnabled(id, request.enabled());
            }
            
            // Update parameters
            job.parameters.clear();
            if (request.parameters() != null) {
                for (Map.Entry<String, String> entry : request.parameters().entrySet()) {
                    job.addParameter(entry.getKey(), entry.getValue());
                }
            }
            
            // Update VM selectors
            // Delete existing selectors
            JobVMSelector.delete("job.id = ?1", id);
            job.vmSelectors.clear();
            
            // Add new selectors
            if (request.vmSelectors() != null) {
                for (var selectorReq : request.vmSelectors()) {
                    JobVMSelector selector = new JobVMSelector();
                    selector.job = job;
                    selector.selectorType = selectorReq.type();
                    selector.selectorValue = selectorReq.value();
                    selector.excludeExpression = selectorReq.excludeExpression();
                    selector.persist();
                    job.vmSelectors.add(selector);
                }
            }
            
            job.updatedAt = Instant.now();
            job.updatedBy = "API"; // TODO: Get from security context
            job.persist();
            
            return Response.ok(toResponse(job)).build();
            
        } catch (Exception e) {
            log.error("Failed to update job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @DELETE
    @Path("/jobs/{id}")
    @Operation(summary = "Delete a scheduled job")
    @APIResponse(responseCode = "204", description = "Job deleted successfully")
    @APIResponse(responseCode = "404", description = "Job not found")
    @Transactional
    public Response deleteJob(@PathParam("id") Long id) {
        try {
            ScheduledJob job = ScheduledJob.findById(id);
            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Job not found"))
                    .build();
            }
            
            schedulerService.deleteJob(id);
            return Response.noContent().build();
            
        } catch (Exception e) {
            log.error("Failed to delete job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/jobs/{id}/trigger")
    @Operation(summary = "Trigger a job manually")
    @APIResponse(responseCode = "200", description = "Job triggered successfully")
    @APIResponse(responseCode = "404", description = "Job not found")
    public Response triggerJob(@PathParam("id") Long id) {
        try {
            ScheduledJob job = ScheduledJob.findById(id);
            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Job not found"))
                    .build();
            }
            
            String executionId = schedulerService.triggerJobNow(job.name);
            
            return Response.ok(Map.of(
                "message", "Job triggered successfully",
                "executionId", executionId
            )).build();
            
        } catch (Exception e) {
            log.error("Failed to trigger job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/jobs/{id}/enable")
    @Operation(summary = "Enable a scheduled job")
    @APIResponse(responseCode = "200", description = "Job enabled successfully")
    @APIResponse(responseCode = "404", description = "Job not found")
    public Response enableJob(@PathParam("id") Long id) {
        try {
            schedulerService.setJobEnabled(id, true);
            return Response.ok(Map.of("message", "Job enabled successfully")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage()))
                .build();
        } catch (Exception e) {
            log.error("Failed to enable job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/jobs/{id}/disable")
    @Operation(summary = "Disable a scheduled job")
    @APIResponse(responseCode = "200", description = "Job disabled successfully")
    @APIResponse(responseCode = "404", description = "Job not found")
    public Response disableJob(@PathParam("id") Long id) {
        try {
            schedulerService.setJobEnabled(id, false);
            return Response.ok(Map.of("message", "Job disabled successfully")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage()))
                .build();
        } catch (Exception e) {
            log.error("Failed to disable job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/jobs/{id}/executions")
    @Operation(summary = "Get job execution history")
    @APIResponse(responseCode = "200", description = "List of job executions")
    @APIResponse(responseCode = "404", description = "Job not found")
    @Transactional
    public Response getJobExecutions(
        @PathParam("id") Long id,
        @QueryParam("status") @Parameter(description = "Filter by status") String status,
        @QueryParam("page") @DefaultValue("0") @Parameter(description = "Page number") int page,
        @QueryParam("size") @DefaultValue("20") @Parameter(description = "Page size") int size
    ) {
        try {
            ScheduledJob job = ScheduledJob.findById(id);
            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Job not found"))
                    .build();
            }
            
            // Build query
            Map<String, Object> params = new HashMap<>();
            params.put("jobId", id);
            String query = "job.id = :jobId";
            
            if (status != null && !status.isBlank()) {
                query += " AND status = :status";
                params.put("status", status);
            }
            
            // Execute query
            List<JobExecution> executions = JobExecution.find(query, Sort.by("startedAt").descending(), params)
                .page(Page.of(page, size))
                .list();
            long total = JobExecution.count(query, params);
            
            // Convert to response DTOs
            List<JobExecutionResponse> responses = executions.stream()
                .map(JobExecutionResponse::from)
                .collect(Collectors.toList());
            
            Map<String, Object> result = Map.of(
                "executions", responses,
                "total", total,
                "page", page,
                "size", size,
                "totalPages", (total + size - 1) / size
            );
            
            return Response.ok(result).build();
            
        } catch (Exception e) {
            log.error("Failed to get job executions", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/executions/{executionId}")
    @Operation(summary = "Get execution details by execution ID")
    @APIResponse(responseCode = "200", description = "Execution details")
    @APIResponse(responseCode = "404", description = "Execution not found")
    @Transactional
    public Response getExecution(@PathParam("executionId") String executionId) {
        JobExecution execution = JobExecution.find("executionId", executionId).firstResult();
        if (execution == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Execution not found"))
                .build();
        }
        
        return Response.ok(JobExecutionResponse.from(execution)).build();
    }
    
    @GET
    @Path("/task-types")
    @Operation(summary = "List available task types")
    @APIResponse(responseCode = "200", description = "List of task types")
    @Transactional
    public Response getTaskTypes() {
        List<TaskType> taskTypes = TaskType.listAll(Sort.by("displayName"));
        
        List<Map<String, Object>> response = taskTypes.stream()
            .map(tt -> Map.<String, Object>of(
                "name", tt.name,
                "displayName", tt.displayName,
                "description", tt.description != null ? tt.description : ""
            ))
            .collect(Collectors.toList());
        
        return Response.ok(response).build();
    }
    
    /**
     * Convert ScheduledJob entity to response DTO
     */
    private ScheduledJobResponse toResponse(ScheduledJob job) {
        // Get next fire time
        Date nextFireTime = null;
        try {
            if (job.enabled) {
                nextFireTime = schedulerService.getNextFireTime(job.name);
            }
        } catch (SchedulerException e) {
            log.warn("Failed to get next fire time for job {}: {}", job.name, e.getMessage());
        }
        
        // Get last execution info
        JobExecution lastExecution = JobExecution.find("job.id = ?1", Sort.by("startedAt").descending(), job.id)
            .firstResult();
        
        String lastStatus = null;
        Instant lastTime = null;
        if (lastExecution != null) {
            lastStatus = lastExecution.status;
            lastTime = lastExecution.startedAt;
        }
        
        // Convert parameters
        Map<String, String> parameters = job.parameters.stream()
            .collect(Collectors.toMap(
                p -> p.paramKey,
                p -> p.paramValue
            ));
        
        // Convert VM selectors
        List<ScheduledJobResponse.VMSelectorResponse> selectors = job.vmSelectors.stream()
            .map(s -> new ScheduledJobResponse.VMSelectorResponse(
                s.id,
                s.selectorType,
                s.selectorValue,
                s.excludeExpression
            ))
            .collect(Collectors.toList());
        
        return new ScheduledJobResponse(
            job.id,
            job.name,
            job.description,
            job.taskType.name,
            job.taskType.displayName,
            job.cronExpression,
            job.enabled,
            job.maxRetries,
            job.retryDelaySeconds,
            job.timeoutSeconds,
            parameters,
            selectors,
            job.createdAt,
            job.updatedAt,
            job.createdBy,
            job.updatedBy,
            nextFireTime,
            lastStatus,
            lastTime
        );
    }
}