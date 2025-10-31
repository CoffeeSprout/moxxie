package com.coffeesprout.scheduler.tag;

import java.util.*;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.scheduler.entity.JobVMSelector;
import com.coffeesprout.scheduler.entity.ScheduledJob;
import com.coffeesprout.scheduler.task.AbstractVMTask;
import com.coffeesprout.scheduler.task.TaskContext;
import com.coffeesprout.service.VMTagLookupService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test to verify tag expression evaluation in AbstractVMTask
 */
class TagExpressionWithAbstractVMTaskTest {

    @Test
    void testEvaluateTagExpression() {
        // Create test implementation
        TestVMTask task = new TestVMTask();

        // Test simple expression
        Set<Integer> result = task.callEvaluateTagExpression("env-prod");
        assertNotNull(result);

        // Test complex expression
        result = task.callEvaluateTagExpression("(env-prod OR env-staging) AND NOT always-on");
        assertNotNull(result);

        // Test wildcard expression
        result = task.callEvaluateTagExpression("client-* AND env-prod");
        assertNotNull(result);

        // Test empty expression
        result = task.callEvaluateTagExpression("");
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Test null expression
        result = task.callEvaluateTagExpression(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectVMsWithTagExpression() {
        // Create test implementation
        TestVMTask task = new TestVMTask();

        // Create a job with tag selector
        ScheduledJob job = new ScheduledJob();
        job.name = "test-job";
        job.vmSelectors = new ArrayList<>();

        JobVMSelector selector = new JobVMSelector();
        selector.selectorType = JobVMSelector.SelectorType.TAG_EXPRESSION.getValue();
        selector.selectorValue = "env-prod";
        job.vmSelectors.add(selector);

        // Create task context
        TaskContext context = new TaskContext();
        context.setJob(job);

        List<VMResponse> selectedVMs = task.callSelectVMs(context);
        assertNotNull(selectedVMs);
        assertEquals(2, selectedVMs.size());
    }

    /**
     * Test implementation of AbstractVMTask for unit testing
     */
    static class TestVMTask extends AbstractVMTask {

        private final FakeVMTagLookupService fakeLookup = new FakeVMTagLookupService();

        TestVMTask() {
            this.vmTagLookupService = fakeLookup;
        }

        Set<Integer> callEvaluateTagExpression(String expression) {
            return super.evaluateTagExpression(expression);
        }

        List<VMResponse> callSelectVMs(TaskContext context) {
            return super.selectVMs(context);
        }

        @Override
        protected Map<String, Object> processVM(TaskContext context, VMResponse vm) throws Exception {
            // Simple test implementation
            return Map.of("result", "success", "vmId", vm.vmid());
        }

        @Override
        public void validateConfiguration(TaskContext context) {
            // No validation needed for test
        }

        @Override
        public String getTaskType() {
            return "test-vm-task";
        }
    }

    static class FakeVMTagLookupService extends VMTagLookupService {
        private final List<VMResponse> vms = List.of(
            new VMResponse(101, "vm1", "node1", "running", 2, 1024L, 10240L, 3600L, "qemu", List.of("env-prod", "client-alpha"), null, 0),
            new VMResponse(102, "vm2", "node1", "running", 2, 1024L, 10240L, 3600L, "qemu", List.of("env-staging"), null, 0),
            new VMResponse(103, "vm3", "node1", "running", 2, 1024L, 10240L, 3600L, "qemu", List.of("env-prod", "client-beta"), null, 0)
        );

        @Override
        public List<VMResponse> listVMs(String ticket) {
            return vms;
        }

        @Override
        public Set<String> getVMTags(int vmId, String ticket) {
            return vms.stream()
                .filter(vm -> vm.vmid() == vmId)
                .findFirst()
                .map(vm -> new HashSet<>(vm.tags()))
                .orElseGet(HashSet::new);
        }

        @Override
        public List<Integer> getVMsByTag(String tag, String ticket) {
            return vms.stream()
                .filter(vm -> vm.tags() != null && vm.tags().contains(tag))
                .map(VMResponse::vmid)
                .toList();
        }
    }
}
