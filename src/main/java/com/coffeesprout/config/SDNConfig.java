package com.coffeesprout.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "moxxie.sdn")
public interface SDNConfig {
    
    @WithDefault("false")
    boolean enabled();
    
    @WithDefault("localzone")
    String defaultZone();
    
    @WithDefault("100")
    int vlanRangeStart();
    
    @WithDefault("4000")
    int vlanRangeEnd();
    
    @WithDefault("false")
    boolean autoCreateVnets();
    
    @WithDefault("{client}-{project}")
    String vnetNamingPattern();
    
    @WithDefault("true")
    boolean applyOnChange();
}