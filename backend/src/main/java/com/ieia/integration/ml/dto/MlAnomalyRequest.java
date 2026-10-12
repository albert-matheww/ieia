package com.ieia.integration.ml.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MlAnomalyRequest(
        Map<String, List<Point>> series,
        List<String> methods,
        String sensitivity
) {
    public record Point(String period, double value) {}
}
