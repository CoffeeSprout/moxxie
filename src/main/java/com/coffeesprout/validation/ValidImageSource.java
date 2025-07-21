package com.coffeesprout.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that the imageSource field is in the correct format for cloud-init VM creation.
 * Ensures it references a template VM disk and not an ISO file.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ImageSourceValidator.class)
@Documented
public @interface ValidImageSource {
    String message() default "Invalid imageSource format";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}