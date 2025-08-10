package com.coffeesprout.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Provider
public class ProxmoxClientLoggingFilter implements ClientRequestFilter, ClientResponseFilter {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProxmoxClientLoggingFilter.class);
    
    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        LOG.info("=== Proxmox API Request ===");
        LOG.info("Method: {} {}", requestContext.getMethod(), requestContext.getUri());
        LOG.info("Headers: {}", requestContext.getHeaders());
        
        // Log request body if present
        if (requestContext.hasEntity()) {
            Object entity = requestContext.getEntity();
            LOG.info("Request Body: {}", entity);
        }
    }
    
    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        LOG.info("=== Proxmox API Response ===");
        LOG.info("Status: {} {}", responseContext.getStatus(), responseContext.getStatusInfo());
        LOG.info("Headers: {}", responseContext.getHeaders());
        
        // Log response body
        if (responseContext.hasEntity()) {
            InputStream originalStream = responseContext.getEntityStream();
            
            // Read the stream into a byte array so we can read it twice
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = originalStream.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            byte[] responseBytes = baos.toByteArray();
            
            // Log the response body
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            if (responseContext.getStatus() >= 400) {
                LOG.error("Response Body (Error): {}", responseBody);
            } else {
                LOG.info("Response Body: {}", responseBody);
            }
            
            // Reset the stream so the client can still read it
            responseContext.setEntityStream(new ByteArrayInputStream(responseBytes));
        }
    }
}