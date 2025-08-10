package com.coffeesprout.service;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dependent
@Interceptor
@ReadOnly
public class ReadOnlyInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ReadOnlyInterceptor.class);

    @Inject
    @ConfigProperty(name = "moxxie.read-only", defaultValue = "false")
    boolean readOnlyMode;

    @AroundInvoke
    public Object checkReadOnly(InvocationContext context) throws Exception {
        if (readOnlyMode) {
            String methodName = context.getMethod().getName();
            
            // Block any write operations
            if (isWriteOperation(methodName)) {
                LOG.warn("BLOCKED: Write operation '{}' attempted in read-only mode", methodName);
                throw new UnsupportedOperationException(
                    "Operation '" + methodName + "' is not allowed in read-only mode"
                );
            }
        }
        
        return context.proceed();
    }

    private boolean isWriteOperation(String methodName) {
        return methodName.startsWith("create") ||
               methodName.startsWith("delete") ||
               methodName.startsWith("update") ||
               methodName.startsWith("start") ||
               methodName.startsWith("stop") ||
               methodName.startsWith("import") ||
               methodName.contains("VM") && !methodName.startsWith("list") && !methodName.startsWith("get");
    }
}