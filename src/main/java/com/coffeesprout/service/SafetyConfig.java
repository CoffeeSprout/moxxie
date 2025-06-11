package com.coffeesprout.service;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "moxxie.safety")
public interface SafetyConfig {
    
    @WithDefault("false")
    boolean enabled();
    
    @WithDefault("strict")
    Mode mode();
    
    @WithDefault("moxxie")
    String tagName();
    
    @WithDefault("true")
    boolean allowUntaggedRead();
    
    @WithDefault("true")
    boolean allowManualOverride();
    
    @WithDefault("true")
    boolean auditLog();
    
    enum Mode {
        STRICT,
        PERMISSIVE,
        AUDIT
    }
}