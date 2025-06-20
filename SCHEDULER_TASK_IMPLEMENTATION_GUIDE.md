# Scheduler Task Implementation Guide

This guide explains how to implement new tasks for the Moxxie scheduler system, covering common patterns, pitfalls, and solutions.

## Table of Contents
1. [Overview](#overview)
2. [Basic Task Implementation](#basic-task-implementation)
3. [VM-Based Tasks](#vm-based-tasks)
4. [Common Pitfalls and Solutions](#common-pitfalls-and-solutions)
5. [Testing Your Task](#testing-your-task)
6. [Best Practices](#best-practices)

## Overview

The Moxxie scheduler system uses Quartz for job scheduling with a database-backed job store. Tasks are implementations of the `ScheduledTask` interface that perform specific operations like creating snapshots, managing backups, or controlling VM power states.

### Architecture
- **ScheduledJob**: Database entity representing a scheduled job
- **ScheduledTask**: Interface for task implementations
- **AbstractVMTask**: Base class for tasks that operate on VMs
- **TaskContext**: Provides job parameters and execution context
- **TaskResult**: Reports task execution results

## Basic Task Implementation

### Step 1: Create Your Task Class

```java
package com.coffeesprout.scheduler.task;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MyCustomTask implements ScheduledTask {
    
    private static final Logger log = LoggerFactory.getLogger(MyCustomTask.class);
    
    @Override
    public String getTaskType() {
        return "my_custom_task";
    }
    
    @Override
    public void validateConfiguration(TaskContext context) {
        // Validate required parameters
        String requiredParam = context.getParameter("requiredParam");
        if (requiredParam == null || requiredParam.isBlank()) {
            throw new IllegalArgumentException("requiredParam is required");
        }
    }
    
    @Override
    public TaskResult execute(TaskContext context) {
        try {
            // Your task logic here
            String param = context.getParameter("requiredParam");
            log.info("Executing task with param: {}", param);
            
            // Do the work...
            
            return TaskResult.success()
                .withDetail("message", "Task completed successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Task execution failed", e);
            return TaskResult.failure(e.getMessage())
                .withDetail("error", e.getClass().getSimpleName())
                .build();
        }
    }
}
```

### Step 2: Register Task Type in Migration

Add your task type to the Flyway migration or create a new migration:

```sql
INSERT INTO task_types (id, name, display_name, task_class, description, created_at) VALUES
(5, 'my_custom_task', 'My Custom Task', 'com.coffeesprout.scheduler.task.MyCustomTask', 
 'Description of what this task does', CURRENT_TIMESTAMP);
```

## VM-Based Tasks

For tasks that operate on VMs, extend `AbstractVMTask`:

```java
@ApplicationScoped
public class VMMaintenanceTask extends AbstractVMTask {
    
    @Inject
    VMService vmService;
    
    @Override
    public String getTaskType() {
        return "vm_maintenance";
    }
    
    @Override
    protected Map<String, Object> processVM(TaskContext context, VMResponse vm) throws Exception {
        // Process individual VM
        String action = context.getParameter("action", "check");
        
        Map<String, Object> result = new HashMap<>();
        result.put("vmId", vm.vmid());
        result.put("vmName", vm.name());
        
        switch (action) {
            case "check":
                // Perform maintenance check
                boolean needsMaintenance = checkVMHealth(vm);
                result.put("needsMaintenance", needsMaintenance);
                break;
                
            case "fix":
                // Perform maintenance
                performMaintenance(vm);
                result.put("maintenancePerformed", true);
                break;
                
            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }
        
        return result;
    }
    
    @Override
    public void validateConfiguration(TaskContext context) {
        super.validateConfiguration(context); // Validates VM selectors
        
        String action = context.getParameter("action");
        if (action != null && !Set.of("check", "fix").contains(action)) {
            throw new IllegalArgumentException("Invalid action. Must be 'check' or 'fix'");
        }
    }
}
```

## Common Pitfalls and Solutions

### 1. CDI Bean Resolution Issues

**Problem**: `No bean found for required type` or beans marked as unused and removed.

**Solution**: Add `@Unremovable` annotation to beans used via programmatic lookup:

```java
@ApplicationScoped
@Unremovable  // Prevent removal by Quarkus build optimization
public class MyDynamicallyLoadedTask implements ScheduledTask {
    // ...
}
```

### 2. Authentication Injection Issues

**Problem**: Authentication tickets not properly injected or appear in wrong parameter positions.

**Solution**: Use `@AuthTicket` annotation and `@AutoAuthenticate`:

```java
@ApplicationScoped
@AutoAuthenticate
public class AuthRequiredTask implements ScheduledTask {
    
    @Inject
    VMService vmService;
    
    public void doOperation(@AuthTicket String ticket) {
        // ticket is automatically injected
    }
}
```

### 3. JSONB Column Type Errors

**Problem**: `column "execution_details" is of type jsonb but expression is of type character varying`

**Solution**: Use `@JdbcTypeCode` instead of custom converters:

```java
@Column(name = "result_data", columnDefinition = "JSONB")
@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
public Map<String, Object> resultData;
```

### 4. Enum Value Mapping Issues

**Problem**: Enum values not matching between API and database (e.g., "VM_LIST" vs "vm_list").

**Solution**: Make enum parsing flexible:

```java
public static SelectorType fromValue(String value) {
    for (SelectorType type : values()) {
        if (type.value.equals(value) || type.name().equals(value)) {
            return type;
        }
    }
    throw new IllegalArgumentException("Unknown selector type: " + value);
}
```

### 5. Transaction Issues with Quartz

**Problem**: Transaction hanging when scheduling jobs within JTA transaction.

**Solution**: Use event-driven approach:

```java
// Fire event instead of direct scheduling
jobCreatedEvent.fire(new JobCreatedEvent(job.id, job.name, job.enabled));

// Handle scheduling after transaction
@Observes(during = TransactionPhase.AFTER_SUCCESS)
public void onJobCreated(JobCreatedEvent event) {
    scheduleJobInNewTransaction(event.getJobId());
}
```

### 6. Database Constraint Violations on Update

**Problem**: Duplicate key violations when updating job parameters.

**Solution**: Explicitly delete before recreating:

```java
// Delete existing parameters first
JobParameter.delete("job.id = ?1", jobId);
job.parameters.clear();
// Then add new parameters
```

### 7. Dev Mode Database Resets

**Problem**: Database is cleaned on every restart in dev mode.

**Solution**: This is by design with `%dev.quarkus.flyway.clean-at-start=true`. For persistent testing, either:
- Comment out the clean-at-start property temporarily
- Use a test endpoint to quickly recreate test data
- Create a SQL script with test data

### 8. Tag Expression Parsing Errors

**Problem**: Complex tag expressions not parsing correctly.

**Solution**: The tag parser supports:
- Boolean operators: AND, OR, NOT
- Parentheses for grouping
- Tag patterns with wildcards: `env:*`

Examples:
```
env:prod AND NOT always-on
(client:nixz OR client:test) AND k8s-worker
env:dev AND maint-ok
```

## Testing Your Task

### 1. Unit Testing

```java
@QuarkusTest
class MyTaskTest {
    
    @Inject
    MyCustomTask task;
    
    @Test
    void testExecute_Success() {
        // Create context
        TaskContext context = new TaskContext();
        context.addParameter("requiredParam", "test-value");
        
        // Execute
        TaskResult result = task.execute(context);
        
        // Verify
        assertTrue(result.isSuccess());
        assertEquals("Task completed successfully", 
                    result.getDetails().get("message"));
    }
    
    @Test
    void testValidateConfiguration_MissingParam() {
        TaskContext context = new TaskContext();
        
        assertThrows(IllegalArgumentException.class, 
                    () -> task.validateConfiguration(context));
    }
}
```

### 2. Integration Testing

Create a test job via REST API:

```bash
curl -X POST http://localhost:8080/api/v1/scheduler/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-my-task",
    "taskType": "my_custom_task",
    "cronExpression": "0 */5 * * * ?",
    "enabled": true,
    "parameters": {
      "requiredParam": "test-value"
    }
  }'
```

### 3. Manual Triggering

Trigger job immediately for testing:

```bash
curl -X POST http://localhost:8080/api/v1/scheduler/jobs/{id}/trigger
```

## Best Practices

### 1. Error Handling

- Always catch exceptions and return appropriate TaskResult
- For VM tasks, continue processing other VMs on individual failures
- Include detailed error information in results

```java
try {
    // risky operation
} catch (SpecificException e) {
    log.error("Failed to process VM {}: {}", vm.name(), e.getMessage());
    return TaskResult.failure("Partial failure")
        .withDetail("failedVm", vm.name())
        .withDetail("error", e.getMessage())
        .build();
}
```

### 2. Progress Reporting

For long-running tasks, update the execution record:

```java
@Inject
JobExecution execution;

// In your task
execution.processedVMs++;
execution.persist();
```

### 3. Configuration Validation

Always validate parameters in `validateConfiguration()`:

```java
@Override
public void validateConfiguration(TaskContext context) {
    Integer maxItems = context.getIntParameter("maxItems", null);
    if (maxItems != null && maxItems <= 0) {
        throw new IllegalArgumentException("maxItems must be positive");
    }
}
```

### 4. Idempotency

Design tasks to be idempotent - running them multiple times should be safe:

```java
// Check if operation already done
if (snapshotExists(vm, snapshotName)) {
    log.info("Snapshot {} already exists, skipping", snapshotName);
    return result;
}
```

### 5. Resource Cleanup

Always clean up resources:

```java
@PreDestroy
void cleanup() {
    // Close connections, clean temporary files, etc.
}
```

### 6. Logging

Use structured logging with appropriate levels:

```java
log.debug("Processing VM {} with parameters: {}", vm.name(), params);
log.info("Successfully created snapshot {} for VM {}", name, vm.name());
log.warn("VM {} has no recent backups", vm.name());
log.error("Failed to connect to VM {}: {}", vm.name(), e.getMessage(), e);
```

## Task Implementation Checklist

- [ ] Create task class implementing ScheduledTask or extending AbstractVMTask
- [ ] Add @ApplicationScoped and @Unremovable annotations
- [ ] Implement required methods (getTaskType, execute, validateConfiguration)
- [ ] Add task type to database via migration
- [ ] Handle all exceptions and return appropriate TaskResult
- [ ] Add unit tests for success and failure cases
- [ ] Test with real job via REST API
- [ ] Document task parameters and behavior
- [ ] Consider idempotency and partial failure handling
- [ ] Add appropriate logging

## Example Tasks

See the following implementations for reference:
- `CreateSnapshotTask` - Creates VM snapshots with rotation
- `TestTask` - Simple example task
- `AbstractVMTask` - Base class showing VM selection patterns

For more complex examples with S3 integration, notification systems, or distributed coordination, see the planned implementations in the GitHub issue tracker.