package com.coffeesprout.service;

public class SafeModeViolationException extends RuntimeException {
    private final SafetyDecision decision;

    public SafeModeViolationException(SafetyDecision decision) {
        super("Safe mode violation: " + decision.getReason());
        this.decision = decision;
    }

    public SafetyDecision getDecision() {
        return decision;
    }
}
