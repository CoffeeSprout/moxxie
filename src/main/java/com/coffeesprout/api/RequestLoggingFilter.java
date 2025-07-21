package com.coffeesprout.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Provider
public class RequestLoggingFilter implements ContainerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        log.info("=== HTTP REQUEST ===");
        log.info("Method: {} {}", requestContext.getMethod(), requestContext.getUriInfo().getRequestUri());
        log.info("Content-Type: {}", requestContext.getHeaderString("Content-Type"));
        
        if (requestContext.getUriInfo().getPath().contains("clusters/provision")) {
            log.info("CLUSTER PROVISION REQUEST DETECTED!");
            
            if (requestContext.hasEntity()) {
                ByteArrayInputStream bais = new ByteArrayInputStream(requestContext.getEntityStream().readAllBytes());
                String body = new BufferedReader(new InputStreamReader(bais, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
                log.info("Request body: {}", body);
                
                // Reset the stream so it can be read again
                requestContext.setEntityStream(bais);
                bais.reset();
            }
        }
    }
}