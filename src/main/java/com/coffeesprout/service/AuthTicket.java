package com.coffeesprout.service;

import java.lang.annotation.*;

/**
 * Marks a parameter that should receive an authentication ticket from the AuthenticationInterceptor.
 * This annotation explicitly identifies which parameter should be injected with authentication,
 * eliminating the fragile position-based convention.
 * 
 * Usage:
 * <pre>
 * public VMResponse getVM(String node, int vmId, @AuthTicket String ticket) {
 *     // ticket parameter will be automatically injected
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthTicket {
}