package com.coffeesprout.service;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

@SafeMode
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class SafeModeInterceptor {
    
    private static final Logger LOG = Logger.getLogger(SafeModeInterceptor.class);
    
    @Inject
    SafetyConfig safetyConfig;
    
    @Inject
    TagService tagService;
    
    @Inject
    AuditService auditService;
    
    @AroundInvoke
    public Object checkSafeMode(InvocationContext context) throws Exception {
        if (!safetyConfig.enabled()) {
            return context.proceed();
        }
        
        // Extract VM ID from method parameters
        Optional<Integer> vmId = extractVmId(context);
        if (vmId.isEmpty()) {
            return context.proceed(); // Not a VM operation
        }
        
        // Check if operation is allowed
        SafetyDecision decision = evaluateSafety(vmId.get(), context);
        
        if (decision.isAllowed()) {
            auditService.logAllowed(context, decision);
            return context.proceed();
        } else {
            auditService.logBlocked(context, decision);
            throw new SafeModeViolationException(decision);
        }
    }
    
    private Optional<Integer> extractVmId(InvocationContext context) {
        Method method = context.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = context.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            // Check for @PathParam("vmId") or parameter named vmId
            if ((param.isAnnotationPresent(jakarta.ws.rs.PathParam.class) && 
                 "vmId".equals(param.getAnnotation(jakarta.ws.rs.PathParam.class).value())) ||
                "vmId".equals(param.getName())) {
                if (args[i] instanceof Integer) {
                    return Optional.of((Integer) args[i]);
                } else if (args[i] instanceof String) {
                    try {
                        return Optional.of(Integer.parseInt((String) args[i]));
                    } catch (NumberFormatException e) {
                        LOG.warn("Could not parse vmId: " + args[i]);
                    }
                }
            }
        }
        return Optional.empty();
    }
    
    private SafetyDecision evaluateSafety(int vmId, InvocationContext context) {
        // Get VM tags
        Set<String> tags = tagService.getVMTags(vmId);
        boolean isMoxxieManaged = tags.contains(safetyConfig.tagName());
        
        // Check for manual override
        if (hasForceFlag(context) && safetyConfig.allowManualOverride()) {
            return SafetyDecision.allowed("Manual override with force flag");
        }
        
        // Apply safety rules based on mode
        SafetyConfig.Mode mode = safetyConfig.mode();
        switch (mode) {
            case STRICT:
                if (!isMoxxieManaged) {
                    return SafetyDecision.blocked("VM not tagged as Moxxie-managed");
                }
                break;
                
            case PERMISSIVE:
                if (!isMoxxieManaged && isDestructiveOperation(context)) {
                    return SafetyDecision.blocked("Destructive operation on non-Moxxie VM");
                }
                break;
                
            case AUDIT:
                // Always allow but log warnings
                if (!isMoxxieManaged) {
                    auditService.logWarning("Operating on non-Moxxie VM", context);
                }
                break;
        }
        
        return SafetyDecision.allowed("VM is Moxxie-managed");
    }
    
    private boolean hasForceFlag(InvocationContext context) {
        Object[] args = context.getParameters();
        Method method = context.getMethod();
        Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (param.isAnnotationPresent(jakarta.ws.rs.QueryParam.class) && 
                "force".equals(param.getAnnotation(jakarta.ws.rs.QueryParam.class).value())) {
                return Boolean.TRUE.equals(args[i]);
            }
        }
        return false;
    }
    
    private boolean isDestructiveOperation(InvocationContext context) {
        Method method = context.getMethod();
        SafeMode annotation = method.getAnnotation(SafeMode.class);
        if (annotation != null) {
            return annotation.operation() == SafeMode.Operation.DELETE ||
                   annotation.operation() == SafeMode.Operation.ALL;
        }
        
        // Check HTTP method annotations
        return method.isAnnotationPresent(jakarta.ws.rs.DELETE.class);
    }
}