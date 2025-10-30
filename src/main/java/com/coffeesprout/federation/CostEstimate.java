package com.coffeesprout.federation;

import java.util.Map;

/**
 * Represents cost estimation for resources
 */
public class CostEstimate {

    private String currency;  // e.g., "USD", "EUR"

    // Cost breakdowns
    private double cpuCostPerHour;
    private double memoryCostPerHour;
    private double storageCostPerHour;
    private double networkCostPerHour;

    private double totalCostPerHour;
    private double totalCostPerMonth;
    private double totalCostPerYear;

    // Additional costs
    private Map<String, Double> additionalCosts;  // e.g., licensing, support

    // Cost optimization suggestions
    private Map<String, String> optimizationSuggestions;

    // Discount information
    private double discountPercentage;
    private String discountReason;  // e.g., "reserved instance", "volume discount"

    // Getters and setters
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public double getCpuCostPerHour() { return cpuCostPerHour; }
    public void setCpuCostPerHour(double cpuCostPerHour) { this.cpuCostPerHour = cpuCostPerHour; }

    public double getMemoryCostPerHour() { return memoryCostPerHour; }
    public void setMemoryCostPerHour(double memoryCostPerHour) { this.memoryCostPerHour = memoryCostPerHour; }

    public double getStorageCostPerHour() { return storageCostPerHour; }
    public void setStorageCostPerHour(double storageCostPerHour) { this.storageCostPerHour = storageCostPerHour; }

    public double getNetworkCostPerHour() { return networkCostPerHour; }
    public void setNetworkCostPerHour(double networkCostPerHour) { this.networkCostPerHour = networkCostPerHour; }

    public double getTotalCostPerHour() { return totalCostPerHour; }
    public void setTotalCostPerHour(double totalCostPerHour) { this.totalCostPerHour = totalCostPerHour; }

    public double getTotalCostPerMonth() { return totalCostPerMonth; }
    public void setTotalCostPerMonth(double totalCostPerMonth) { this.totalCostPerMonth = totalCostPerMonth; }

    public double getTotalCostPerYear() { return totalCostPerYear; }
    public void setTotalCostPerYear(double totalCostPerYear) { this.totalCostPerYear = totalCostPerYear; }

    public Map<String, Double> getAdditionalCosts() { return additionalCosts; }
    public void setAdditionalCosts(Map<String, Double> additionalCosts) { this.additionalCosts = additionalCosts; }

    public Map<String, String> getOptimizationSuggestions() { return optimizationSuggestions; }
    public void setOptimizationSuggestions(Map<String, String> optimizationSuggestions) { this.optimizationSuggestions = optimizationSuggestions; }

    public double getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(double discountPercentage) { this.discountPercentage = discountPercentage; }

    public String getDiscountReason() { return discountReason; }
    public void setDiscountReason(String discountReason) { this.discountReason = discountReason; }
}
