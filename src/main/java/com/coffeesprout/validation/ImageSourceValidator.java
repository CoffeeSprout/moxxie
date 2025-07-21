package com.coffeesprout.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that imageSource is in the correct format for cloud-init VM creation.
 * Ensures users don't try to use ISO file paths which won't work.
 */
public class ImageSourceValidator implements ConstraintValidator<ValidImageSource, String> {
    
    @Override
    public void initialize(ValidImageSource constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return true; // Let @NotBlank handle empty values
        }
        
        // Check if it looks like an ISO path
        String lowerValue = value.toLowerCase();
        if (lowerValue.contains(".iso") || lowerValue.contains("/iso/") || lowerValue.contains("iso:") || lowerValue.startsWith("iso-")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Invalid imageSource format. For cloud-init VMs, use the format 'storage:vmid/base-vmid-disk-N' " +
                "(e.g., 'local-zfs:9002/base-9002-disk-0.raw'). Do not use ISO file paths."
            ).addConstraintViolation();
            return false;
        }
        
        // Check if it matches the expected format: storage:vmid/base-vmid-disk-N
        if (!value.matches("^[a-zA-Z0-9-]+:\\d+/base-\\d+-disk-\\d+(\\.\\w+)?$")) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Invalid imageSource format. Expected format: 'storage:vmid/base-vmid-disk-N' " +
                "(e.g., 'local-zfs:9002/base-9002-disk-0.raw'). This should reference a template VM's disk."
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
}