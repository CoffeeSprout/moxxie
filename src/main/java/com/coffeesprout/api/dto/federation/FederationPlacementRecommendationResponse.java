package com.coffeesprout.api.dto.federation;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for VM placement recommendation
 */
public class FederationPlacementRecommendationResponse {
    
    private String recommendedNodeId;
    private String recommendedNodeName;
    private double placementScore;
    
    private Map<String, Object> resourceFit;
    private List<String> reasons;
    private List<String> warnings;
    
    private List<Map<String, Object>> alternatives;
    
    private Map<String, Object> costEstimate;
    
    // Getters and setters
    public String getRecommendedNodeId() { return recommendedNodeId; }
    public void setRecommendedNodeId(String recommendedNodeId) { this.recommendedNodeId = recommendedNodeId; }
    
    public String getRecommendedNodeName() { return recommendedNodeName; }
    public void setRecommendedNodeName(String recommendedNodeName) { this.recommendedNodeName = recommendedNodeName; }
    
    public double getPlacementScore() { return placementScore; }
    public void setPlacementScore(double placementScore) { this.placementScore = placementScore; }
    
    public Map<String, Object> getResourceFit() { return resourceFit; }
    public void setResourceFit(Map<String, Object> resourceFit) { this.resourceFit = resourceFit; }
    
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
    
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    
    public List<Map<String, Object>> getAlternatives() { return alternatives; }
    public void setAlternatives(List<Map<String, Object>> alternatives) { this.alternatives = alternatives; }
    
    public Map<String, Object> getCostEstimate() { return costEstimate; }
    public void setCostEstimate(Map<String, Object> costEstimate) { this.costEstimate = costEstimate; }
}