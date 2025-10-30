package com.coffeesprout.api;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class RequestLoggingFilter implements ContainerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOG.info("=== HTTP REQUEST ===");
        LOG.info("Method: {} {}", requestContext.getMethod(), requestContext.getUriInfo().getRequestUri());
        LOG.info("Content-Type: {}", requestContext.getHeaderString("Content-Type"));

        if (requestContext.getUriInfo().getPath().contains("clusters/provision")) {
            LOG.info("CLUSTER PROVISION REQUEST DETECTED!");

            if (requestContext.hasEntity()) {
                ByteArrayInputStream bais = new ByteArrayInputStream(requestContext.getEntityStream().readAllBytes());
                String body = new BufferedReader(new InputStreamReader(bais, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
                LOG.info("Request body: {}", body);

                // Reset the stream so it can be read again
                requestContext.setEntityStream(bais);
                bais.reset();
            }
        }
    }
}
