package com.coffeesprout.service;

public class SafetyDecision {
    private final boolean allowed;
    private final String reason;
    
    private SafetyDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }
    
    public static SafetyDecision allowed(String reason) {
        return new SafetyDecision(true, reason);
    }
    
    public static SafetyDecision blocked(String reason) {
        return new SafetyDecision(false, reason);
    }
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public String getReason() {
        return reason;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyDecision that = (SafetyDecision) o;
        return allowed == that.allowed && 
               java.util.Objects.equals(reason, that.reason);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(allowed, reason);
    }
    
    @Override
    public String toString() {
        return "SafetyDecision{" +
               "allowed=" + allowed +
               ", reason='" + reason + '\'' +
               '}';
    }
}