package com.coffeesprout.scheduler.task;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.scheduler.entity.JobVMExecution;
import com.coffeesprout.scheduler.entity.JobVMSelector;
import com.coffeesprout.scheduler.tag.TagExpression;
import com.coffeesprout.scheduler.tag.TagExpressionParser;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.VMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for tasks that operate on VMs
 */
public abstract class AbstractVMTask implements ScheduledTask {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVMTask.class);

    @Inject
    protected VMService vmService;

    @Inject
    protected TagService tagService;

    @Override
    public TaskResult execute(TaskContext context) {
        LOG.info("Starting {} task execution for job {}", getTaskType(), context.getJob().name);

        try {
            // Validate configuration
            validateConfiguration(context);

            // Get VMs to process
            List<VMResponse> vmsToProcess = selectVMs(context);
            LOG.info("Selected {} VMs to process", vmsToProcess.size());

            if (vmsToProcess.isEmpty()) {
                return TaskResult.success()
                    .withProcessedCount(0)
                    .withDetail("message", "No VMs matched selection criteria");
            }

            // Process each VM
            int processed = 0;
            int succeeded = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();

            for (VMResponse vm : vmsToProcess) {
                processed++;

                try {
                    LOG.debug("Processing VM {} ({})", vm.vmid(), vm.name());

                    // Create VM execution record
                    JobVMExecution vmExecution = createVMExecution(context, vm);

                    // Execute task for this VM
                    Map<String, Object> result = processVM(context, vm);

                    // Update execution record
                    updateVMExecution(vmExecution, JobVMExecution.Status.SUCCESS, result);
                    succeeded++;

                    LOG.debug("Successfully processed VM {} ({})", vm.vmid(), vm.name());

                } catch (Exception e) {
                    LOG.error("Failed to process VM {} ({}): {}", vm.vmid(), vm.name(), e.getMessage(), e);
                    failed++;
                    errors.add(String.format("VM %d (%s): %s", vm.vmid(), vm.name(), e.getMessage()));

                    // Update VM execution record if it exists
                    try {
                        JobVMExecution vmExecution = JobVMExecution.find("execution.id = ?1 and vmId = ?2",
                            context.getExecution().id, vm.vmid()).firstResult();
                        if (vmExecution != null) {
                            updateVMExecution(vmExecution, JobVMExecution.Status.FAILED,
                                Map.of("error", e.getMessage()));
                        }
                    } catch (Exception ex) {
                        LOG.error("Failed to update VM execution record: {}", ex.getMessage());
                    }
                }
            }

            // Build result
            TaskResult result = failed == 0 ? TaskResult.success() :
                TaskResult.failure("Task completed with " + failed + " failures");

            result.withCounts(processed, succeeded, failed)
                  .withDetail("errors", errors);

            return result;

        } catch (Exception e) {
            LOG.error("Task execution failed: {}", e.getMessage(), e);
            return TaskResult.failure("Task execution failed: " + e.getMessage());
        }
    }

    /**
     * Select VMs based on job configuration
     */
    protected List<VMResponse> selectVMs(TaskContext context) {
        List<VMResponse> allVMs = vmService.listVMs(null);

        if (context.getJob().vmSelectors.isEmpty()) {
            LOG.warn("No VM selectors configured, processing all VMs");
            return allVMs;
        }

        Set<Integer> selectedVMIds = new HashSet<>();

        for (JobVMSelector selector : context.getJob().vmSelectors) {
            Set<Integer> selectorVMIds = new HashSet<>();

            switch (JobVMSelector.SelectorType.fromValue(selector.selectorType)) {
                case ALL:
                    selectorVMIds.addAll(allVMs.stream()
                        .map(VMResponse::vmid)
                        .collect(Collectors.toSet()));
                    break;

                case VM_LIST:
                    // Parse comma-separated VM IDs
                    Arrays.stream(selector.selectorValue.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(id -> {
                            try {
                                selectorVMIds.add(Integer.parseInt(id));
                            } catch (NumberFormatException e) {
                                LOG.warn("Invalid VM ID in selector: {}", id);
                            }
                        });
                    break;

                case TAG_EXPRESSION:
                    // Parse and evaluate tag expression
                    Set<Integer> taggedVMs = evaluateTagExpression(selector.selectorValue);
                    selectorVMIds.addAll(taggedVMs);
                    break;
            }

            // Apply exclusion if specified
            if (selector.excludeExpression != null && !selector.excludeExpression.isBlank()) {
                Set<Integer> excludedVMs = evaluateTagExpression(selector.excludeExpression);
                selectorVMIds.removeAll(excludedVMs);
            }

            selectedVMIds.addAll(selectorVMIds);
        }

        // Filter allVMs to only include selected ones
        return allVMs.stream()
            .filter(vm -> selectedVMIds.contains(vm.vmid()))
            .collect(Collectors.toList());
    }

    /**
     * Evaluate a tag expression and return matching VM IDs
     */
    protected Set<Integer> evaluateTagExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return new HashSet<>();
        }

        try {
            // Parse the tag expression
            TagExpression tagExpr = TagExpressionParser.parse(expression);
            LOG.debug("Evaluating tag expression: {}", expression);

            // Get all VMs and evaluate expression against their tags
            List<VMResponse> allVMs = vmService.listVMs(null);
            Set<Integer> matchingVMs = new HashSet<>();

            for (VMResponse vm : allVMs) {
                // Get tags for this VM
                Set<String> vmTags = tagService.getVMTags(vm.vmid(), null);

                // Evaluate expression
                if (tagExpr.evaluate(vmTags)) {
                    matchingVMs.add(vm.vmid());
                    LOG.trace("VM {} ({}) matches expression", vm.vmid(), vm.name());
                }
            }

            LOG.debug("Tag expression '{}' matched {} VMs", expression, matchingVMs.size());
            return matchingVMs;

        } catch (Exception e) {
            LOG.error("Failed to evaluate tag expression '{}': {}", expression, e.getMessage());
            // Fall back to simple tag matching for backward compatibility
            try {
                List<Integer> vmIds = tagService.getVMsByTag(expression.trim(), null);
                LOG.warn("Fell back to simple tag matching for '{}', found {} VMs", expression, vmIds.size());
                return new HashSet<>(vmIds);
            } catch (Exception ex) {
                LOG.error("Simple tag matching also failed: {}", ex.getMessage());
                return new HashSet<>();
            }
        }
    }

    /**
     * Create VM execution record
     */
    @Transactional
    protected JobVMExecution createVMExecution(TaskContext context, VMResponse vm) {
        JobVMExecution vmExecution = new JobVMExecution();
        vmExecution.execution = context.getExecution();
        vmExecution.vmId = vm.vmid();
        vmExecution.vmName = vm.name();
        vmExecution.nodeName = vm.node();
        vmExecution.status = JobVMExecution.Status.SUCCESS.getValue(); // Will be updated
        vmExecution.startedAt = Instant.now();
        vmExecution.persist();
        return vmExecution;
    }

    /**
     * Update VM execution record
     */
    @Transactional
    protected void updateVMExecution(JobVMExecution vmExecution, JobVMExecution.Status status,
                                   Map<String, Object> resultData) {
        vmExecution.complete(status, resultData);
        vmExecution.persist();
    }

    /**
     * Process a single VM - to be implemented by subclasses
     */
    protected abstract Map<String, Object> processVM(TaskContext context, VMResponse vm) throws Exception;
}
