package com.ieia.integration.ml.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MlPredictResponse(
        String target,
        String model,
        List<ForecastPoint> forecast,
        Evaluation evaluation,
        List<FeatureImportance> featureImportance,
        int nHistory,
        String note,
        List<String> limitations,
        int seed
) {
    public record ForecastPoint(String period, double value, double lower, double upper) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Evaluation(Double mae, Double rmse, Double r2, int cvFolds, int nTrain) {}

    public record FeatureImportance(String feature, double importance) {}
}
