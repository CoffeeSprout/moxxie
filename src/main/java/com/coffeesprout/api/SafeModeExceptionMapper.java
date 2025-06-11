package com.coffeesprout.api;

import com.coffeesprout.service.SafeModeViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Provider
public class SafeModeExceptionMapper implements ExceptionMapper<SafeModeViolationException> {
    
    private static final Logger log = LoggerFactory.getLogger(SafeModeExceptionMapper.class);
    
    @Override
    public Response toResponse(SafeModeViolationException exception) {
        log.warn("Safe mode violation: {}", exception.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "SAFE_MODE_VIOLATION");
        errorResponse.put("message", exception.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("reason", exception.getDecision().getReason());
        details.put("suggestion", "Use force=true to override or tag the VM with 'moxxie'");
        
        errorResponse.put("details", details);
        errorResponse.put("timestamp", Instant.now().toString());
        
        return Response.status(Response.Status.FORBIDDEN)
                .entity(errorResponse)
                .build();
    }
}