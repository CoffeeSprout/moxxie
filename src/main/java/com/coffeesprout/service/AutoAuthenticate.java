package com.coffeesprout.service;

import java.lang.annotation.*;

import jakarta.interceptor.InterceptorBinding;

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
