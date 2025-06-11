package com.coffeesprout.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class SafetyDecisionTest {

    @Test
    @DisplayName("SafetyDecision.allowed() should create allowed decision")
    void testAllowedDecision() {
        // When
        SafetyDecision decision = SafetyDecision.allowed("VM is Moxxie-managed");

        // Then
        assertTrue(decision.isAllowed());
        assertEquals("VM is Moxxie-managed", decision.getReason());
    }

    @Test
    @DisplayName("SafetyDecision.blocked() should create blocked decision")
    void testBlockedDecision() {
        // When
        SafetyDecision decision = SafetyDecision.blocked("VM not tagged as Moxxie-managed");

        // Then
        assertFalse(decision.isAllowed());
        assertEquals("VM not tagged as Moxxie-managed", decision.getReason());
    }

    @Test
    @DisplayName("Allowed decisions should not be equal to blocked decisions")
    void testEqualityBetweenAllowedAndBlocked() {
        // Given
        SafetyDecision allowed = SafetyDecision.allowed("Same reason");
        SafetyDecision blocked = SafetyDecision.blocked("Same reason");

        // Then
        assertNotEquals(allowed, blocked);
    }

    @Test
    @DisplayName("Decisions with same state and reason should be equal")
    void testEquality() {
        // Given
        SafetyDecision decision1 = SafetyDecision.allowed("Test reason");
        SafetyDecision decision2 = SafetyDecision.allowed("Test reason");

        // Then
        assertEquals(decision1, decision2);
        assertEquals(decision1.hashCode(), decision2.hashCode());
    }

    @Test
    @DisplayName("toString() should include state and reason")
    void testToString() {
        // Given
        SafetyDecision allowed = SafetyDecision.allowed("VM is managed");
        SafetyDecision blocked = SafetyDecision.blocked("VM not managed");

        // Then
        assertTrue(allowed.toString().contains("allowed=true"));
        assertTrue(allowed.toString().contains("VM is managed"));
        assertTrue(blocked.toString().contains("allowed=false"));
        assertTrue(blocked.toString().contains("VM not managed"));
    }
}