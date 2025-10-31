package com.coffeesprout.api.filter;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ApiKeyAuthEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "moxxie.api.auth-enabled", "true",
            "moxxie.api.key", "test-secret"
        );
    }
}
