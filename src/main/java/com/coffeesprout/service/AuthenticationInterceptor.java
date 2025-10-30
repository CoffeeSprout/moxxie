package com.coffeesprout.service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

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
 * Looks for parameters annotated with @AuthTicket and injects a valid authentication ticket.
 */
@Dependent
@Interceptor
@AutoAuthenticate
@Priority(Interceptor.Priority.APPLICATION)
public class AuthenticationInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationInterceptor.class);

    @Inject
    TicketManager ticketManager;

    @AroundInvoke
    public Object injectAuthenticationTicket(InvocationContext context) throws Exception {
        Object[] parameters = context.getParameters();
        Method method = context.getMethod();
        Parameter[] methodParameters = method.getParameters();

        // Find parameter annotated with @AuthTicket
        int ticketIndex = -1;
        for (int i = 0; i < methodParameters.length; i++) {
            if (methodParameters[i].isAnnotationPresent(AuthTicket.class)) {
                // Verify it's a String parameter
                if (methodParameters[i].getType() != String.class) {
                    throw new IllegalArgumentException(
                        String.format("@AuthTicket can only be applied to String parameters. " +
                                      "Method %s.%s has @AuthTicket on a %s parameter",
                                      method.getDeclaringClass().getSimpleName(),
                                      method.getName(),
                                      methodParameters[i].getType().getSimpleName()));
                }
                ticketIndex = i;
                break;
            }
        }

        // Fall back to position-based convention for backward compatibility
        if (ticketIndex == -1) {
            LOG.debug("No @AuthTicket annotation found on method {}.{}, falling back to position-based convention",
                     method.getDeclaringClass().getSimpleName(), method.getName());
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = parameters.length - 1; i >= 0; i--) {
                if (parameterTypes[i] == String.class) {
                    ticketIndex = i;
                    break;
                }
            }
        }

        if (ticketIndex >= 0) {
            // Get a valid ticket from the manager
            String ticket = ticketManager.getTicket();
            parameters[ticketIndex] = ticket;
            context.setParameters(parameters);
            LOG.trace("Injected authentication ticket into parameter at position {} for method {}.{}",
                     ticketIndex, method.getDeclaringClass().getSimpleName(), method.getName());
        }

        try {
            return context.proceed();
        } catch (Exception e) {
            // If we get an authentication error, force refresh and retry once
            if (isAuthenticationError(e) && ticketIndex >= 0) {
                LOG.debug("Authentication error detected, refreshing ticket and retrying");
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
