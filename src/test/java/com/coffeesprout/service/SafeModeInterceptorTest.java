package com.coffeesprout.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class SafeModeInterceptorTest {

    @Inject
    SafeModeInterceptor interceptor;

    @InjectMock
    SafetyConfig safetyConfig;

    @InjectMock
    TagService tagService;

    @InjectMock
    AuditService auditService;

    private InvocationContext context;

    @BeforeEach
    void setUp() {
        context = mock(InvocationContext.class);
    }

    @Test
    @DisplayName("Should allow operations when safe mode is disabled")
    void testSafeModeDisabled() throws Exception {
        // Given
        when(safetyConfig.enabled()).thenReturn(false);
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(context).proceed();
        verifyNoInteractions(tagService, auditService);
    }

    @Test
    @DisplayName("Should allow operations when no VM ID is found")
    void testNoVmIdFound() throws Exception {
        // Given
        when(safetyConfig.enabled()).thenReturn(true);
        when(context.getMethod()).thenReturn(getClass().getMethod("methodWithoutVmId"));
        when(context.getParameters()).thenReturn(new Object[]{});
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(context).proceed();
        verifyNoInteractions(tagService, auditService);
    }

    @Test
    @DisplayName("Should allow operations on Moxxie-managed VMs in strict mode")
    void testStrictModeAllowsMoxxieVMs() throws Exception {
        // Given
        setupVmIdContext(100);
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(tagService.getVMTags(100)).thenReturn(Set.of("moxxie", "production"));
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(context).proceed();
        verify(auditService).logAllowed(eq(context), any(SafetyDecision.class));
    }

    @Test
    @DisplayName("Should block operations on non-Moxxie VMs in strict mode")
    void testStrictModeBlocksNonMoxxieVMs() throws Exception {
        // Given
        setupVmIdContext(100);
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(tagService.getVMTags(100)).thenReturn(Set.of("production", "critical"));

        // When & Then
        SafeModeViolationException exception = assertThrows(
            SafeModeViolationException.class,
            () -> interceptor.checkSafeMode(context)
        );
        
        assertEquals("Safe mode violation: VM not tagged as Moxxie-managed", exception.getMessage());
        verify(auditService).logBlocked(eq(context), any(SafetyDecision.class));
        verify(context, never()).proceed();
    }

    @Test
    @DisplayName("Should allow force override when enabled")
    void testForceOverride() throws Exception {
        // Given
        setupVmIdContext(100);
        setupForceFlag(true);
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(safetyConfig.allowManualOverride()).thenReturn(true);
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(context).proceed();
        verify(auditService).logAllowed(eq(context), any(SafetyDecision.class));
    }

    @Test
    @DisplayName("Should not allow force override when disabled")
    void testForceOverrideDisabled() throws Exception {
        // Given
        setupVmIdContext(100);
        setupForceFlag(true);
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(safetyConfig.allowManualOverride()).thenReturn(false);
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));

        // When & Then
        assertThrows(
            SafeModeViolationException.class,
            () -> interceptor.checkSafeMode(context)
        );
        
        verify(context, never()).proceed();
    }

    @Test
    @DisplayName("Should block only destructive operations in permissive mode")
    void testPermissiveModeBlocksDestructive() throws Exception {
        // Given
        setupVmIdContext(100);
        setupDestructiveOperation();
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.PERMISSIVE);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));

        // When & Then
        assertThrows(
            SafeModeViolationException.class,
            () -> interceptor.checkSafeMode(context)
        );
        
        verify(auditService).logBlocked(eq(context), any(SafetyDecision.class));
    }

    @Test
    @DisplayName("Should allow non-destructive operations in permissive mode")
    void testPermissiveModeAllowsNonDestructive() throws Exception {
        // Given
        setupVmIdContext(100);
        setupNonDestructiveOperation();
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.PERMISSIVE);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(context).proceed();
    }

    @Test
    @DisplayName("Should always allow operations in audit mode but log warnings")
    void testAuditModeAllowsButLogs() throws Exception {
        // Given
        setupVmIdContext(100);
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.AUDIT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(tagService.getVMTags(100)).thenReturn(Set.of("production"));
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(context).proceed();
        verify(auditService).logWarning(eq("Operating on non-Moxxie VM"), eq(context));
    }

    @Test
    @DisplayName("Should extract VM ID from PathParam annotation")
    void testExtractVmIdFromPathParam() throws Exception {
        // Given
        Method method = TestResource.class.getMethod("deleteVM", Integer.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getParameters()).thenReturn(new Object[]{123});
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(tagService.getVMTags(123)).thenReturn(Set.of("moxxie"));
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(tagService).getVMTags(123);
    }

    @Test
    @DisplayName("Should extract VM ID from String PathParam")
    void testExtractVmIdFromStringPathParam() throws Exception {
        // Given
        Method method = TestResource.class.getMethod("getVM", String.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getParameters()).thenReturn(new Object[]{"456"});
        when(safetyConfig.enabled()).thenReturn(true);
        when(safetyConfig.mode()).thenReturn(SafetyConfig.Mode.STRICT);
        when(safetyConfig.tagName()).thenReturn("moxxie");
        when(tagService.getVMTags(456)).thenReturn(Set.of("moxxie"));
        when(context.proceed()).thenReturn("result");

        // When
        Object result = interceptor.checkSafeMode(context);

        // Then
        assertEquals("result", result);
        verify(tagService).getVMTags(456);
    }

    // Helper methods
    private void setupVmIdContext(int vmId) throws NoSuchMethodException {
        Method method = TestResource.class.getMethod("deleteVM", Integer.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getParameters()).thenReturn(new Object[]{vmId});
    }

    private void setupForceFlag(boolean force) throws NoSuchMethodException {
        Method method = TestResource.class.getMethod("deleteVMWithForce", Integer.class, Boolean.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getParameters()).thenReturn(new Object[]{100, force});
    }

    private void setupDestructiveOperation() throws NoSuchMethodException {
        Method method = TestResource.class.getMethod("deleteVM", Integer.class);
        when(context.getMethod()).thenReturn(method);
        SafeMode annotation = method.getAnnotation(SafeMode.class);
        when(context.getMethod().getAnnotation(SafeMode.class)).thenReturn(annotation);
    }

    private void setupNonDestructiveOperation() throws NoSuchMethodException {
        Method method = TestResource.class.getMethod("startVM", Integer.class);
        when(context.getMethod()).thenReturn(method);
        SafeMode annotation = method.getAnnotation(SafeMode.class);
        when(context.getMethod().getAnnotation(SafeMode.class)).thenReturn(annotation);
    }

    // Test helper methods
    public void methodWithoutVmId() {}

    // Test resource class for method references
    static class TestResource {
        @SafeMode(operation = SafeMode.Operation.DELETE)
        public void deleteVM(@PathParam("vmId") Integer vmId) {}
        
        @SafeMode(operation = SafeMode.Operation.DELETE)
        public void deleteVMWithForce(@PathParam("vmId") Integer vmId, @QueryParam("force") Boolean force) {}
        
        @SafeMode(value = false)
        public void getVM(@PathParam("vmId") String vmId) {}
        
        @SafeMode(operation = SafeMode.Operation.WRITE)
        public void startVM(@PathParam("vmId") Integer vmId) {}
    }
}