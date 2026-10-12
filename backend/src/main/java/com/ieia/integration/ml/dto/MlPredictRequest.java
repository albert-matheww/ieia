package com.ieia.integration.ml.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MlPredictRequest(
        List<Point> series,
        String target,
        int horizon,
        int minHistory,
        int seasonality
) {
    public record Point(String period, double value) {}
}
