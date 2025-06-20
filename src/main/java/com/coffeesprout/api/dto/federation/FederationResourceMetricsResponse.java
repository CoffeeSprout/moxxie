package com.coffeesprout.api.dto.federation;

import java.util.Map;

/**
 * Response DTO for resource metrics
 */
public class FederationResourceMetricsResponse {
    
    private Map<String, Object> providerMetrics;
    private Map<String, Object> cacheStatistics;
    private Map<String, Object> performanceMetrics;
    
    // Getters and setters
    public Map<String, Object> getProviderMetrics() { return providerMetrics; }
    public void setProviderMetrics(Map<String, Object> providerMetrics) { this.providerMetrics = providerMetrics; }
    
    public Map<String, Object> getCacheStatistics() { return cacheStatistics; }
    public void setCacheStatistics(Map<String, Object> cacheStatistics) { this.cacheStatistics = cacheStatistics; }
    
    public Map<String, Object> getPerformanceMetrics() { return performanceMetrics; }
    public void setPerformanceMetrics(Map<String, Object> performanceMetrics) { this.performanceMetrics = performanceMetrics; }
}