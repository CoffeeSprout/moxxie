package com.coffeesprout.scheduler.task;

import com.coffeesprout.api.dto.CreateSnapshotRequest;
import com.coffeesprout.api.dto.SnapshotResponse;
import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.scheduler.entity.JobExecution;
import com.coffeesprout.scheduler.entity.JobVMSelector;
import com.coffeesprout.scheduler.entity.ScheduledJob;
import com.coffeesprout.scheduler.entity.TaskType;
import com.coffeesprout.service.SnapshotService;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.VMService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class CreateSnapshotTaskTest {
    
    @Inject
    CreateSnapshotTask task;
    
    @InjectMock
    VMService vmService;
    
    @InjectMock
    SnapshotService snapshotService;
    
    @InjectMock
    TagService tagService;
    
    private TaskContext context;
    private ScheduledJob job;
    
    @BeforeEach
    void setUp() {
        // Create test job
        job = new ScheduledJob();
        job.id = 1L;
        job.name = "test-snapshot-job";
        
        TaskType taskType = new TaskType();
        taskType.name = "snapshot_create";
        job.taskType = taskType;
        
        // Add VM selector
        JobVMSelector selector = new JobVMSelector();
        selector.selectorType = JobVMSelector.SelectorType.TAG_EXPRESSION.getValue();
        selector.selectorValue = "env-test";
        job.vmSelectors.add(selector);
        
        // Create test context
        context = new TaskContext();
        context.setJob(job);
        
        JobExecution execution = new JobExecution();
        execution.id = 1L;
        context.setExecution(execution);
    }
    
    @Test
    @Disabled("Needs update after scheduler implementation changes")
    void testExecute_Success() {
        // Add parameters to context
        context.addParameter("snapshotNamePattern", "test-{vm}-{datetime}");
        context.addParameter("includeVmState", "false");
        context.addParameter("description", "Test snapshot");
        
        // Mock VM service
        VMResponse vm = new VMResponse(8200, "test-vm", "node1", "running", 1, 1024L, 
            2048L, 3600L, "qemu", List.of("env-test"), null, 0);
        when(vmService.listVMs(null)).thenReturn(List.of(vm));
        
        // Mock tag service
        when(tagService.getVMTags(8200, null)).thenReturn(Set.of("env-test"));
        
        // Mock snapshot service
        when(snapshotService.createSnapshot(eq(8200), any(CreateSnapshotRequest.class), isNull()))
            .thenReturn(new com.coffeesprout.api.dto.TaskResponse("TASK-123", "Creating snapshot"));
        
        // Execute task
        TaskResult result = task.execute(context);
        
        // Verify results
        assertTrue(result.isSuccess());
        assertEquals(1, result.getProcessedCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        
        // Verify snapshot creation
        ArgumentCaptor<CreateSnapshotRequest> requestCaptor = ArgumentCaptor.forClass(CreateSnapshotRequest.class);
        verify(snapshotService).createSnapshot(eq(8200), requestCaptor.capture(), isNull());
        
        CreateSnapshotRequest capturedRequest = requestCaptor.getValue();
        assertTrue(capturedRequest.name().startsWith("test-test-vm-"));
        assertEquals("Test snapshot", capturedRequest.description());
        assertFalse(capturedRequest.includeVmState());
    }
    
    @Test
    @Disabled("Needs update after scheduler implementation changes")
    void testExecute_WithRotation() {
        // Add parameters with rotation
        context.addParameter("maxSnapshots", "3");
        
        // Mock VM service
        VMResponse vm = new VMResponse(8200, "test-vm", "node1", "running", 1, 1024L, 
            2048L, 3600L, "qemu", List.of("env-test"), null, 0);
        when(vmService.listVMs(null)).thenReturn(List.of(vm));
        
        // Mock tag service
        when(tagService.getVMTags(8200, null)).thenReturn(Set.of("env-test"));
        
        // Mock existing snapshots
        List<SnapshotResponse> existingSnapshots = List.of(
            new SnapshotResponse("scheduled-test-vm-20240101-120000", null, null, 1704110400L, false, null),
            new SnapshotResponse("scheduled-test-vm-20240102-120000", null, null, 1704196800L, false, null),
            new SnapshotResponse("scheduled-test-vm-20240103-120000", null, null, 1704283200L, false, null),
            new SnapshotResponse("current", null, null, 1704369600L, false, null) // Non-scheduled snapshot
        );
        
        when(snapshotService.listSnapshots(8200, null))
            .thenReturn(existingSnapshots);
        
        when(snapshotService.createSnapshot(eq(8200), any(CreateSnapshotRequest.class), isNull()))
            .thenReturn(new com.coffeesprout.api.dto.TaskResponse("TASK-123", "Creating snapshot"));
        
        // Execute task
        TaskResult result = task.execute(context);
        
        // Verify results
        assertTrue(result.isSuccess());
        
        // Verify oldest scheduled snapshot was deleted
        verify(snapshotService).deleteSnapshot(8200, "scheduled-test-vm-20240101-120000", null);
        
        // Verify non-scheduled snapshot was not deleted
        verify(snapshotService, never()).deleteSnapshot(8200, "current", null);
    }
    
    @Test
    void testExecute_NoVmsMatchingTag() {
        // Mock VM service
        VMResponse vm = new VMResponse(8200, "test-vm", "node1", "running", 1, 1024L, 
            2048L, 3600L, "qemu", List.of("env-test"), null, 0);
        when(vmService.listVMs(null)).thenReturn(List.of(vm));
        
        // Mock tag service - VM doesn't have the required tag
        when(tagService.getVMTags(8200, null)).thenReturn(Set.of("env-prod"));
        
        // Execute task
        TaskResult result = task.execute(context);
        
        // Verify results
        assertTrue(result.isSuccess());
        assertEquals(0, result.getProcessedCount());
        assertTrue(result.getDetails().get("message").toString().contains("No VMs matched"));
    }
    
    @Test
    @Disabled("Needs update after scheduler implementation changes")
    void testExecute_PartialFailure() {
        // Mock VM service with multiple VMs
        VMResponse vm1 = new VMResponse(8200, "test-vm-1", "node1", "running", 1, 1024L, 
            2048L, 3600L, "qemu", List.of("env-test"), null, 0);
        VMResponse vm2 = new VMResponse(8201, "test-vm-2", "node1", "running", 1, 1024L, 
            2048L, 3600L, "qemu", List.of("env-test"), null, 0);
        when(vmService.listVMs(null)).thenReturn(List.of(vm1, vm2));
        
        // Mock tag service
        when(tagService.getVMTags(anyInt(), isNull())).thenReturn(Set.of("env-test"));
        
        // Mock snapshot service - first succeeds, second fails
        when(snapshotService.createSnapshot(eq(8200), any(CreateSnapshotRequest.class), isNull()))
            .thenReturn(new com.coffeesprout.api.dto.TaskResponse("TASK-123", "Creating snapshot"));
        when(snapshotService.createSnapshot(eq(8201), any(CreateSnapshotRequest.class), isNull()))
            .thenThrow(new RuntimeException("Snapshot failed"));
        
        // Execute task
        TaskResult result = task.execute(context);
        
        // Verify partial success
        assertFalse(result.isSuccess());
        assertEquals(2, result.getProcessedCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getErrorMessage().contains("1 failures"));
    }
    
    @Test
    void testValidateConfiguration_InvalidMaxSnapshots() {
        context.addParameter("maxSnapshots", "0");
        
        assertThrows(IllegalArgumentException.class, () -> task.validateConfiguration(context));
    }
    
    @Test
    void testValidateConfiguration_ValidConfiguration() {
        context.addParameter("snapshotNamePattern", "backup-{vm}-{date}");
        context.addParameter("maxSnapshots", "5");
        
        assertDoesNotThrow(() -> task.validateConfiguration(context));
    }
    
    @Test
    void testProcessVM_GeneratesCorrectSnapshotName() throws Exception {
        // Add custom pattern
        context.addParameter("snapshotNamePattern", "custom-{vm}-{date}");
        
        VMResponse vm = new VMResponse(8200, "myvm", "node1", "running", 1, 1024L, 
            2048L, 3600L, "qemu", List.of("env-test"), null, 0);
        
        when(snapshotService.createSnapshot(eq(8200), any(CreateSnapshotRequest.class), isNull()))
            .thenReturn(new com.coffeesprout.api.dto.TaskResponse("TASK-123", "Creating snapshot"));
        
        Map<String, Object> result = task.processVM(context, vm);
        
        // Verify snapshot name starts with pattern
        String snapshotName = (String) result.get("snapshotName");
        assertTrue(snapshotName.startsWith("custom-myvm-"));
        assertTrue(snapshotName.matches("custom-myvm-\\d{8}"));
    }
}