package com.coffeesprout.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.coffeesprout.api.dto.ErrorResponse;
import com.coffeesprout.api.dto.TaskListResponse;
import com.coffeesprout.api.dto.TaskLogResponse;
import com.coffeesprout.api.dto.TaskStatusDetailResponse;
import com.coffeesprout.service.SafeMode;
import com.coffeesprout.service.TaskService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/v1/tasks")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RunOnVirtualThread
@Tag(name = "Tasks", description = "Task monitoring and management endpoints")
public class TaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(TaskResource.class);

    @Inject
    TaskService taskService;

    @GET
    @SafeMode(false)  // Read operation
    @Operation(summary = "List tasks",
               description = "List tasks across the cluster with optional filtering")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Tasks retrieved successfully",
            content = @Content(schema = @Schema(implementation = TaskListResponse.class))),
        @APIResponse(responseCode = "401", description = "Unauthorized - check Proxmox credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve tasks",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response listTasks(
            @Parameter(description = "Filter by specific node", example = "pve1")
            @QueryParam("node") String node,
            @Parameter(description = "Filter by task type", example = "qmstart")
            @QueryParam("type") String typeFilter,
            @Parameter(description = "Filter by status (running, stopped)",
                      schema = @Schema(enumeration = {"running", "stopped"}))
            @QueryParam("status") String statusFilter,
            @Parameter(description = "Filter by user", example = "root@pam")
            @QueryParam("user") String userFilter,
            @Parameter(description = "Filter by VM ID", example = "100")
            @QueryParam("vmid") Integer vmid,
            @Parameter(description = "Start index for pagination", example = "0")
            @QueryParam("start") @DefaultValue("0") Integer start,
            @Parameter(description = "Number of results to return", example = "50")
            @QueryParam("limit") @DefaultValue("50") Integer limit) {
        try {
            TaskListResponse tasks = taskService.listTasks(
                node, typeFilter, statusFilter, userFilter, vmid, start, limit, null
            );
            return Response.ok(tasks).build();
        } catch (Exception e) {
            LOG.error("Failed to list tasks", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list tasks: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{taskId}")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get task status",
               description = "Get detailed status of a specific task by UPID")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task status retrieved successfully",
            content = @Content(schema = @Schema(implementation = TaskStatusDetailResponse.class))),
        @APIResponse(responseCode = "404", description = "Task not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve task status",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getTaskStatus(
            @Parameter(description = "Task UPID", required = true,
                      example = "UPID:pve1:00001234:12345678:5F3E8B7C:qmstart:100:root@pam:")
            @PathParam("taskId") String taskId) {
        try {
            TaskStatusDetailResponse status = taskService.getTaskStatus(taskId, null);
            return Response.ok(status).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid task ID format"))
                    .build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Invalid UPID")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid task ID format"))
                        .build();
            } else if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Task not found"))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to get task status for: " + taskId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get task status: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{taskId}/log")
    @SafeMode(false)  // Read operation
    @Operation(summary = "Get task log",
               description = "Get execution log of a specific task with pagination")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task log retrieved successfully",
            content = @Content(schema = @Schema(implementation = TaskLogResponse.class))),
        @APIResponse(responseCode = "404", description = "Task not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to retrieve task log",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getTaskLog(
            @Parameter(description = "Task UPID", required = true,
                      example = "UPID:pve1:00001234:12345678:5F3E8B7C:qmstart:100:root@pam:")
            @PathParam("taskId") String taskId,
            @Parameter(description = "Start line number", example = "0")
            @QueryParam("start") @DefaultValue("0") Integer start,
            @Parameter(description = "Number of lines to return", example = "50")
            @QueryParam("limit") @DefaultValue("50") Integer limit) {
        try {
            TaskLogResponse log = taskService.getTaskLog(taskId, start, limit, null);
            return Response.ok(log).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid task ID format"))
                    .build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Invalid UPID")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid task ID format"))
                        .build();
            } else if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Task not found"))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to get task log for: " + taskId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get task log: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{taskId}")
    @SafeMode(true)  // Write operation - stopping a task
    @Operation(summary = "Stop task",
               description = "Stop a running task (requires appropriate permissions)")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Task stop initiated successfully"),
        @APIResponse(responseCode = "400", description = "Invalid task ID or task not running",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "403", description = "Insufficient permissions to stop task",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "404", description = "Task not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @APIResponse(responseCode = "500", description = "Failed to stop task",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response stopTask(
            @Parameter(description = "Task UPID", required = true,
                      example = "UPID:pve1:00001234:12345678:5F3E8B7C:qmstart:100:root@pam:")
            @PathParam("taskId") String taskId) {
        try {
            // First check if task exists and is running
            TaskStatusDetailResponse status = taskService.getTaskStatus(taskId, null);
            if (Boolean.TRUE.equals(status.finished())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Task is not running"))
                        .build();
            }

            taskService.stopTask(taskId, null);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid task ID format"))
                    .build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Invalid UPID")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Invalid task ID format"))
                        .build();
            } else if (e.getMessage().contains("not found")) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Task not found"))
                        .build();
            } else if (e.getMessage().contains("permission")) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("Insufficient permissions to stop task"))
                        .build();
            }
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to stop task: " + taskId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to stop task: " + e.getMessage()))
                    .build();
        }
    }
}
