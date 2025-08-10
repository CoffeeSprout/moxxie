package com.coffeesprout.scheduler.service;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.scheduler.model.VMSelector;
import com.coffeesprout.scheduler.tag.TagExpression;
import com.coffeesprout.scheduler.tag.TagExpressionParser;
import com.coffeesprout.service.AuthTicket;
import com.coffeesprout.service.AutoAuthenticate;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.VMService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
@AutoAuthenticate
public class VMSelectorService {
    
    private static final Logger LOG = LoggerFactory.getLogger(VMSelectorService.class);
    
    @Inject
    VMService vmService;
    
    @Inject
    TagService tagService;
    
    /**
     * Select VMs based on the given selector
     */
    public List<VMResponse> selectVMs(VMSelector selector, @AuthTicket String ticket) {
        LOG.debug("Selecting VMs with selector type: {}, value: {}", selector.type(), selector.value());
        
        // Get all VMs first
        List<VMResponse> allVMs = vmService.listVMs(ticket);
        
        Set<Integer> selectedVMIds = new HashSet<>();
        
        switch (selector.type()) {
            case ALL:
                // Select all non-template VMs
                selectedVMIds.addAll(allVMs.stream()
                    .map(VMResponse::vmid)
                    .collect(Collectors.toSet()));
                break;
                
            case VM_IDS:
                // Parse comma-separated VM IDs
                String[] ids = selector.value().split(",");
                for (String id : ids) {
                    try {
                        selectedVMIds.add(Integer.parseInt(id.trim()));
                    } catch (NumberFormatException e) {
                        LOG.warn("Invalid VM ID in selector: {}", id);
                    }
                }
                break;
                
            case NAME_PATTERN:
                // Convert wildcard pattern to regex
                String regex = wildcardToRegex(selector.value());
                Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                
                for (VMResponse vm : allVMs) {
                    if (vm.name() != null && pattern.matcher(vm.name()).matches()) {
                        selectedVMIds.add(vm.vmid());
                    }
                }
                break;
                
            case TAG_EXPRESSION:
                // Evaluate tag expression
                selectedVMIds.addAll(evaluateTagExpression(selector.value(), allVMs, ticket));
                break;
                
            default:
                LOG.warn("Unknown selector type: {}", selector.type());
        }
        
        // Filter VMs to only include selected ones
        List<VMResponse> selectedVMs = allVMs.stream()
            .filter(vm -> selectedVMIds.contains(vm.vmid()))
            .collect(Collectors.toList());
        
        LOG.info("Selected {} VMs using selector type: {}", selectedVMs.size(), selector.type());
        return selectedVMs;
    }
    
    /**
     * Convert wildcard pattern to regex
     */
    private String wildcardToRegex(String wildcard) {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        
        for (char c : wildcard.toCharArray()) {
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '.':
                case '\\':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '^':
                case '$':
                case '|':
                case '+':
                    sb.append("\\").append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        
        sb.append("$");
        return sb.toString();
    }
    
    /**
     * Evaluate a tag expression and return matching VM IDs
     */
    private Set<Integer> evaluateTagExpression(String expression, List<VMResponse> allVMs, String ticket) {
        if (expression == null || expression.trim().isEmpty()) {
            return new HashSet<>();
        }
        
        try {
            // Parse the tag expression
            TagExpression tagExpr = TagExpressionParser.parse(expression);
            LOG.debug("Evaluating tag expression: {}", expression);
            
            Set<Integer> matchingVMs = new HashSet<>();
            
            for (VMResponse vm : allVMs) {
                // Get tags for this VM
                Set<String> vmTags = tagService.getVMTags(vm.vmid(), ticket);
                
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
            // Fall back to simple tag matching
            try {
                List<Integer> vmIds = tagService.getVMsByTag(expression.trim(), ticket);
                LOG.warn("Fell back to simple tag matching for '{}', found {} VMs", expression, vmIds.size());
                return new HashSet<>(vmIds);
            } catch (Exception ex) {
                LOG.error("Simple tag matching also failed: {}", ex.getMessage());
                return new HashSet<>();
            }
        }
    }
}