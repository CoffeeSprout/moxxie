package com.coffeesprout.service;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

/**
 * Interceptor binding for automatic authentication.
 * Methods annotated with this will automatically have authentication tickets injected.
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoAuthenticate {
}