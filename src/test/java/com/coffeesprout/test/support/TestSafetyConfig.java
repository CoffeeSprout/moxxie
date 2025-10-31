package com.coffeesprout.test.support;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

import com.coffeesprout.service.SafetyConfig;

/**
 * Test-friendly SafetyConfig implementation that can be mutated at runtime.
 * Marked as alternative so CDI prefers it over the production config mapping.
 */
@Singleton
@Alternative
@Priority(1)
public class TestSafetyConfig implements SafetyConfig {

    private boolean enabled = true;
    private Mode mode = Mode.STRICT;
    private String tagName = "moxxie";
    private boolean allowUntaggedRead = true;
    private boolean allowManualOverride = true;
    private boolean auditLog = true;

    public void reset() {
        enabled = true;
        mode = Mode.STRICT;
        tagName = "moxxie";
        allowUntaggedRead = true;
        allowManualOverride = true;
        auditLog = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public void setAllowUntaggedRead(boolean allowUntaggedRead) {
        this.allowUntaggedRead = allowUntaggedRead;
    }

    public void setAllowManualOverride(boolean allowManualOverride) {
        this.allowManualOverride = allowManualOverride;
    }

    public void setAuditLog(boolean auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Mode mode() {
        return mode;
    }

    @Override
    public String tagName() {
        return tagName;
    }

    @Override
    public boolean allowUntaggedRead() {
        return allowUntaggedRead;
    }

    @Override
    public boolean allowManualOverride() {
        return allowManualOverride;
    }

    @Override
    public boolean auditLog() {
        return auditLog;
    }
}
