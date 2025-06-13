package com.coffeesprout.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket proxy endpoint for VM console connections.
 * This acts as a bridge between the client's WebSocket connection and Proxmox's WebSocket endpoint.
 * 
 * Note: This is a foundation for future UI integration. The actual proxying logic
 * would need to be implemented when the UI is added.
 */
@ServerEndpoint("/ws/console/{vmId}/{ticket}")
@ApplicationScoped
public class ConsoleWebSocketProxy {
    
    private static final Logger log = LoggerFactory.getLogger(ConsoleWebSocketProxy.class);
    
    // Map to track active sessions
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    
    @OnOpen
    public void onOpen(Session session, 
                      @PathParam("vmId") String vmId,
                      @PathParam("ticket") String ticket) {
        log.info("WebSocket connection opened for VM {} with session {}", vmId, session.getId());
        
        // Store the session
        activeSessions.put(session.getId(), session);
        
        // In a real implementation, this would:
        // 1. Validate the ticket
        // 2. Create a WebSocket client connection to Proxmox
        // 3. Bridge the data between the two connections
        
        try {
            session.getBasicRemote().sendText("Console WebSocket connection established for VM " + vmId);
        } catch (IOException e) {
            log.error("Failed to send welcome message", e);
        }
    }
    
    @OnMessage
    public void onTextMessage(String message, Session session) {
        log.debug("Received text message from session {}: {}", session.getId(), message);
        
        // In a real implementation, this would forward the message to Proxmox
        // For now, just echo it back
        try {
            session.getBasicRemote().sendText("Echo: " + message);
        } catch (IOException e) {
            log.error("Failed to send text message", e);
        }
    }
    
    @OnMessage
    public void onBinaryMessage(ByteBuffer message, Session session) {
        log.debug("Received binary message from session {}, size: {} bytes", 
                 session.getId(), message.remaining());
        
        // In a real implementation, this would forward the binary data to Proxmox
        // VNC uses binary protocol for screen updates and input events
    }
    
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        log.info("WebSocket connection closed for session {}: {}", 
                session.getId(), closeReason.getReasonPhrase());
        
        // Clean up the session
        activeSessions.remove(session.getId());
        
        // In a real implementation, this would also close the Proxmox connection
    }
    
    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("WebSocket error for session {}: {}", 
                 session.getId(), throwable.getMessage(), throwable);
        
        // Clean up on error
        activeSessions.remove(session.getId());
        
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, 
                                        "Error: " + throwable.getMessage()));
        } catch (IOException e) {
            log.error("Failed to close session after error", e);
        }
    }
    
    /**
     * Get the number of active console sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * Check if a session is active
     */
    public boolean isSessionActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}