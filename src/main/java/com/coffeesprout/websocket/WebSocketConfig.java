package com.coffeesprout.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.server.ServerEndpointConfig;

import com.coffeesprout.util.UnitConverter;

/**
 * WebSocket configuration for console connections.
 * This provides a foundation for WebSocket settings that can be extended
 * when the UI is implemented.
 */
@ApplicationScoped
public class WebSocketConfig {

    // Maximum message size for WebSocket messages (10MB for screen data)
    public static final int MAX_MESSAGE_SIZE = (int) (10 * UnitConverter.Bytes.BYTES_PER_MB);

    // Idle timeout in milliseconds (10 minutes)
    public static final long IDLE_TIMEOUT = 10 * UnitConverter.Time.MILLIS_PER_MINUTE;

    // Maximum number of concurrent console sessions per user
    public static final int MAX_SESSIONS_PER_USER = 5;

    // Buffer size for binary messages
    public static final int BINARY_BUFFER_SIZE = (int) (64 * UnitConverter.Bytes.BYTES_PER_KB);

    /**
     * Configure WebSocket endpoint settings.
     * This can be extended to add custom configurators, encoders, decoders, etc.
     */
    public void configureEndpoint(ServerEndpointConfig config) {
        // Set maximum message sizes
        config.getUserProperties().put("org.apache.tomcat.websocket.textBufferSize", MAX_MESSAGE_SIZE);
        config.getUserProperties().put("org.apache.tomcat.websocket.binaryBufferSize", BINARY_BUFFER_SIZE);

        // Additional configuration can be added here when needed
    }

    /**
     * Validate if a new console session can be created based on current limits.
     */
    public boolean canCreateSession(String userId, int currentSessionCount) {
        return currentSessionCount < MAX_SESSIONS_PER_USER;
    }
}
