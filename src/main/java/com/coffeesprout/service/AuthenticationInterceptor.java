package com.coffeesprout.service;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor that automatically injects authentication tickets into service method calls.
 * Looks for the last String parameter and assumes it's the ticket parameter.
 */
@Dependent
@Interceptor
@AutoAuthenticate
@Priority(Interceptor.Priority.APPLICATION)
public class AuthenticationInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(AuthenticationInterceptor.class);
    
    @Inject
    TicketManager ticketManager;
    
    @AroundInvoke
    public Object injectAuthenticationTicket(InvocationContext context) throws Exception {
        Object[] parameters = context.getParameters();
        Class<?>[] parameterTypes = context.getMethod().getParameterTypes();
        
        // Find the last String parameter - this is our ticket parameter by convention
        int ticketIndex = -1;
        for (int i = parameters.length - 1; i >= 0; i--) {
            if (parameterTypes[i] == String.class) {
                ticketIndex = i;
                break;
            }
        }
        
        if (ticketIndex >= 0) {
            // Get a valid ticket from the manager
            String ticket = ticketManager.getTicket();
            parameters[ticketIndex] = ticket;
            context.setParameters(parameters);
            log.trace("Injected authentication ticket into parameter at position {} for method {}", 
                     ticketIndex, context.getMethod().getName());
        }
        
        try {
            return context.proceed();
        } catch (Exception e) {
            // If we get an authentication error, force refresh and retry once
            if (isAuthenticationError(e) && ticketIndex >= 0) {
                log.debug("Authentication error detected, refreshing ticket and retrying");
                ticketManager.forceRefresh();
                
                // Re-inject the new ticket
                parameters[ticketIndex] = ticketManager.getTicket();
                context.setParameters(parameters);
                
                // Retry the call
                return context.proceed();
            }
            throw e;
        }
    }
    
    private boolean isAuthenticationError(Exception e) {
        // Check if the exception indicates an authentication error
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        return message.contains("401") || 
               message.contains("Unauthorized") ||
               message.contains("authentication") ||
               message.contains("Authentication");
    }
}