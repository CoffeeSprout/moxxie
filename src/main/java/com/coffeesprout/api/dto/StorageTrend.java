package com.coffeesprout.api.dto;

import java.util.List;

public record StorageTrend(
    String period,      // "daily", "weekly", "monthly"
    List<TrendPoint> dataPoints
) {}