package com.coffeesprout.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    
    private static final Logger log = LoggerFactory.getLogger(ValidationExceptionMapper.class);
    
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        log.error("=== VALIDATION ERROR ===");
        log.error("Validation failed with {} violations", exception.getConstraintViolations().size());
        
        Map<String, Object> response = new HashMap<>();
        response.put("title", "Constraint Violation");
        response.put("status", 400);
        
        var violations = exception.getConstraintViolations().stream()
            .map(violation -> {
                log.error("Violation: {} = {}", violation.getPropertyPath(), violation.getMessage());
                Map<String, String> v = new HashMap<>();
                v.put("field", violation.getPropertyPath().toString());
                v.put("message", violation.getMessage());
                return v;
            })
            .collect(Collectors.toList());
            
        response.put("violations", violations);
        
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(response)
            .build();
    }
}