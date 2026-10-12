package com.ieia.integration.ml.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/** DTOs for POST /analytics/contributors and /analytics/explain. */
public final class MlAnalyticsDtos {

    private MlAnalyticsDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ContributorsRequest(List<ContributorInput> impacts) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ContributorInput(String category, String activityCode, double co2eKg) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ContributorsResponse(
            double totalCo2eKg,
            List<Share> byCategory,
            List<Share> byActivity,
            Share topContributor
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Share(String key, String label, double co2eKg, double sharePct) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExplainRequest(MetricBundle current, MetricBundle previous, Map<String, Double> weights) {}

    public record MetricBundle(double co2e, double energy, double water, double waste, double transport) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExplainResponse(double scoreDelta, List<Driver> drivers) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Driver(String category, String metric, double changePct, double pointsImpact, String direction) {}
}
