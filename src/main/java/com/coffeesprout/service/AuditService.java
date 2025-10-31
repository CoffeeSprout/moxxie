package com.coffeesprout.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

import com.coffeesprout.util.UnitConverter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class);

    @Inject
    SafetyConfig safetyConfig;

    // In-memory storage for audit entries (in production, use a database)
    private final List<AuditEntry> auditEntries = new ArrayList<>();
    private final Map<String, AtomicLong> statistics = new ConcurrentHashMap<>();

    @Context
    SecurityContext securityContext;

    public AuditService() {
        statistics.put("totalOperations", new AtomicLong(0));
        statistics.put("blockedOperations", new AtomicLong(0));
        statistics.put("overriddenOperations", new AtomicLong(0));
    }

    public void logAllowed(InvocationContext context, SafetyDecision decision) {
        if (!safetyConfig.auditLog()) {
            return;
        }

        statistics.get("totalOperations").incrementAndGet();

        String operation = buildOperationString(context);
        String user = getCurrentUser();

        AuditEntry entry = new AuditEntry(
            Instant.now(),
            operation,
            "ALLOWED",
            decision.getReason(),
            extractVmId(context),
            user,
            getClientIp()
        );

        synchronized (auditEntries) {
            auditEntries.add(entry);
            // Keep only last UnitConverter.Time.MILLIS_PER_SECOND entries
            if (auditEntries.size() > UnitConverter.Time.MILLIS_PER_SECOND) {
                auditEntries.remove(0);
            }
        }

        LOG.info("Safety allowed: " + operation + " - " + decision.getReason());
    }

    public void logBlocked(InvocationContext context, SafetyDecision decision) {
        statistics.get("totalOperations").incrementAndGet();
        statistics.get("blockedOperations").incrementAndGet();

        String operation = buildOperationString(context);
        String user = getCurrentUser();

        AuditEntry entry = new AuditEntry(
            Instant.now(),
            operation,
            "BLOCKED",
            decision.getReason(),
            extractVmId(context),
            user,
            getClientIp()
        );

        synchronized (auditEntries) {
            auditEntries.add(entry);
            // Keep only last UnitConverter.Time.MILLIS_PER_SECOND entries
            if (auditEntries.size() > UnitConverter.Time.MILLIS_PER_SECOND) {
                auditEntries.remove(0);
            }
        }

        LOG.warn("Safety blocked: " + operation + " - " + decision.getReason());
    }

    public void logWarning(String message, InvocationContext context) {
        if (!safetyConfig.auditLog()) {
            return;
        }

        String operation = buildOperationString(context);
        LOG.warn("Safety warning: " + operation + " - " + message);
    }

    public List<AuditEntry> getAuditEntries(Instant startTime) {
        synchronized (auditEntries) {
            return auditEntries.stream()
                .filter(entry -> entry.timestamp().isAfter(startTime))
                .toList();
        }
    }

    public SafetyStatistics getStatistics() {
        Instant lastBlocked = null;
        synchronized (auditEntries) {
            for (int i = auditEntries.size() - 1; i >= 0; i--) {
                if ("BLOCKED".equals(auditEntries.get(i).decision())) {
                    lastBlocked = auditEntries.get(i).timestamp();
                    break;
                }
            }
        }

        return new SafetyStatistics(
            statistics.get("totalOperations").get(),
            statistics.get("blockedOperations").get(),
            statistics.get("overriddenOperations").get(),
            lastBlocked
        );
    }

    private String buildOperationString(InvocationContext context) {
        String methodName = context.getMethod().getName();
        String className = context.getTarget().getClass().getSimpleName();

        // Try to get HTTP method from annotations
        String httpMethod = "";
        if (context.getMethod().isAnnotationPresent(jakarta.ws.rs.GET.class)) {
            httpMethod = "GET ";
        } else if (context.getMethod().isAnnotationPresent(jakarta.ws.rs.POST.class)) {
            httpMethod = "POST ";
        } else if (context.getMethod().isAnnotationPresent(jakarta.ws.rs.PUT.class)) {
            httpMethod = "PUT ";
        } else if (context.getMethod().isAnnotationPresent(jakarta.ws.rs.DELETE.class)) {
            httpMethod = "DELETE ";
        }

        return httpMethod + className + "." + methodName;
    }

    private Integer extractVmId(InvocationContext context) {
        Object[] args = context.getParameters();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg instanceof Integer) {
                    return (Integer) arg;
                }
            }
        }
        return null;
    }

    private String getCurrentUser() {
        if (securityContext != null && securityContext.getUserPrincipal() != null) {
            return securityContext.getUserPrincipal().getName();
        }
        return "anonymous";
    }

    private String getClientIp() {
        // In a real implementation, extract from HTTP request context
        return "unknown";
    }

    public record AuditEntry(
        Instant timestamp,
        String operation,
        String decision,
        String reason,
        Integer vmId,
        String user,
        String clientIp
    ) {}

    public record SafetyStatistics(
        long totalOperations,
        long blockedOperations,
        long overriddenOperations,
        Instant lastBlocked
    ) {}
}
