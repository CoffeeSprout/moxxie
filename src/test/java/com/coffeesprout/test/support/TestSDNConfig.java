package com.coffeesprout.test.support;

import com.coffeesprout.config.SDNConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

@Singleton
@Alternative
@Priority(1)
public class TestSDNConfig implements SDNConfig {

    private boolean enabled = false;
    private String defaultZone = "localzone";
    private int vlanRangeStart = 100;
    private int vlanRangeEnd = 4000;
    private boolean autoCreateVnets = true;
    private String vnetNamingPattern = "{client}-{project}";
    private boolean applyOnChange = true;

    public void reset() {
        enabled = false;
        defaultZone = "localzone";
        vlanRangeStart = 100;
        vlanRangeEnd = 4000;
        autoCreateVnets = true;
        vnetNamingPattern = "{client}-{project}";
        applyOnChange = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setDefaultZone(String defaultZone) {
        this.defaultZone = defaultZone;
    }

    public void setVlanRange(int start, int end) {
        this.vlanRangeStart = start;
        this.vlanRangeEnd = end;
    }

    public void setAutoCreateVnets(boolean autoCreateVnets) {
        this.autoCreateVnets = autoCreateVnets;
    }

    public void setVnetNamingPattern(String pattern) {
        this.vnetNamingPattern = pattern;
    }

    public void setApplyOnChange(boolean applyOnChange) {
        this.applyOnChange = applyOnChange;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String defaultZone() {
        return defaultZone;
    }

    @Override
    public int vlanRangeStart() {
        return vlanRangeStart;
    }

    @Override
    public int vlanRangeEnd() {
        return vlanRangeEnd;
    }

    @Override
    public boolean autoCreateVnets() {
        return autoCreateVnets;
    }

    @Override
    public String vnetNamingPattern() {
        return vnetNamingPattern;
    }

    @Override
    public boolean applyOnChange() {
        return applyOnChange;
    }
}
