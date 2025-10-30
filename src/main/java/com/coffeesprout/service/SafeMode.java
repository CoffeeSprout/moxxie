package com.coffeesprout.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeMode {
    boolean value() default true;
    Operation operation() default Operation.WRITE;

    enum Operation {
        READ, WRITE, DELETE, ALL
    }
}
