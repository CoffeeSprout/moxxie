package com.coffeesprout.federation;

import java.util.List;
import java.util.Map;

/**
 * Represents a recommendation for optimal VM placement
 */
public class PlacementRecommendation {
    
    // Primary recommendation
    private String recommendedNodeId;
    private String recommendedNodeName;
    private double placementScore;  // 0-100 score
    
    // Resource fit analysis
    private ResourceFit resourceFit;
    
    // Alternative placements ranked by score
    private List<AlternativePlacement> alternatives;
    
    // Reasoning for the recommendation
    private List<String> reasons;
    private Map<String, Double> scoringFactors;
    
    // Warnings or concerns
    private List<String> warnings;
    
    // Cost implications
    private CostEstimate estimatedCost;
    
    public static class ResourceFit {
        private double cpuFitScore;      // How well CPU requirements fit
        private double memoryFitScore;   // How well memory requirements fit
        private double storageFitScore;  // How well storage requirements fit
        private double networkFitScore;  // How well network requirements fit
        
        private boolean meetsAllRequirements;
        private List<String> unmetRequirements;
        
        // Resource utilization after placement
        private double projectedCpuUtilization;
        private double projectedMemoryUtilization;
        private double projectedStorageUtilization;
        
        // Getters and setters
        public double getCpuFitScore() { return cpuFitScore; }
        public void setCpuFitScore(double cpuFitScore) { this.cpuFitScore = cpuFitScore; }
        
        public double getMemoryFitScore() { return memoryFitScore; }
        public void setMemoryFitScore(double memoryFitScore) { this.memoryFitScore = memoryFitScore; }
        
        public double getStorageFitScore() { return storageFitScore; }
        public void setStorageFitScore(double storageFitScore) { this.storageFitScore = storageFitScore; }
        
        public double getNetworkFitScore() { return networkFitScore; }
        public void setNetworkFitScore(double networkFitScore) { this.networkFitScore = networkFitScore; }
        
        public boolean isMeetsAllRequirements() { return meetsAllRequirements; }
        public void setMeetsAllRequirements(boolean meetsAllRequirements) { this.meetsAllRequirements = meetsAllRequirements; }
        
        public List<String> getUnmetRequirements() { return unmetRequirements; }
        public void setUnmetRequirements(List<String> unmetRequirements) { this.unmetRequirements = unmetRequirements; }
        
        public double getProjectedCpuUtilization() { return projectedCpuUtilization; }
        public void setProjectedCpuUtilization(double projectedCpuUtilization) { this.projectedCpuUtilization = projectedCpuUtilization; }
        
        public double getProjectedMemoryUtilization() { return projectedMemoryUtilization; }
        public void setProjectedMemoryUtilization(double projectedMemoryUtilization) { this.projectedMemoryUtilization = projectedMemoryUtilization; }
        
        public double getProjectedStorageUtilization() { return projectedStorageUtilization; }
        public void setProjectedStorageUtilization(double projectedStorageUtilization) { this.projectedStorageUtilization = projectedStorageUtilization; }
    }
    
    public static class AlternativePlacement {
        private String nodeId;
        private String nodeName;
        private double placementScore;
        private List<String> advantages;
        private List<String> disadvantages;
        
        // Getters and setters
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        
        public double getPlacementScore() { return placementScore; }
        public void setPlacementScore(double placementScore) { this.placementScore = placementScore; }
        
        public List<String> getAdvantages() { return advantages; }
        public void setAdvantages(List<String> advantages) { this.advantages = advantages; }
        
        public List<String> getDisadvantages() { return disadvantages; }
        public void setDisadvantages(List<String> disadvantages) { this.disadvantages = disadvantages; }
    }
    
    // Getters and setters
    public String getRecommendedNodeId() { return recommendedNodeId; }
    public void setRecommendedNodeId(String recommendedNodeId) { this.recommendedNodeId = recommendedNodeId; }
    
    public String getRecommendedNodeName() { return recommendedNodeName; }
    public void setRecommendedNodeName(String recommendedNodeName) { this.recommendedNodeName = recommendedNodeName; }
    
    public double getPlacementScore() { return placementScore; }
    public void setPlacementScore(double placementScore) { this.placementScore = placementScore; }
    
    public ResourceFit getResourceFit() { return resourceFit; }
    public void setResourceFit(ResourceFit resourceFit) { this.resourceFit = resourceFit; }
    
    public List<AlternativePlacement> getAlternatives() { return alternatives; }
    public void setAlternatives(List<AlternativePlacement> alternatives) { this.alternatives = alternatives; }
    
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
    
    public Map<String, Double> getScoringFactors() { return scoringFactors; }
    public void setScoringFactors(Map<String, Double> scoringFactors) { this.scoringFactors = scoringFactors; }
    
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    
    public CostEstimate getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(CostEstimate estimatedCost) { this.estimatedCost = estimatedCost; }
}