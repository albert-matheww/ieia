package com.ieia.integration.ml.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MlAnomalyResponse(
        List<Item> anomalies,
        Map<String, Object> summary,
        int seed
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(
            String period,
            String metric,
            String method,
            String direction,
            String severity,
            double observed,
            double baseline,
            double deviationAbs,
            double deviationPct,
            double score,
            int methodsAgreeing
    ) {}
}
