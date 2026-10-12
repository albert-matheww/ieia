package com.ieia.scenario.dto;

import java.math.BigDecimal;
import java.util.List;

public record ScenarioResult(
        String basePeriodFrom,
        String basePeriodTo,
        int monthsAveraged,
        MetricBundle baseline,
        MetricBundle projected,
        Deltas deltas,
        List<CategoryDelta> byCategory,
        List<String> assumptions,
        List<String> notes
) {
    public record Deltas(
            BigDecimal co2eKg,
            BigDecimal energyMj,
            BigDecimal waterLitre,
            BigDecimal wasteKg,
            BigDecimal overallScore
    ) {}

    public record CategoryDelta(
            String category,
            BigDecimal baselineCo2e,
            BigDecimal projectedCo2e,
            BigDecimal deltaCo2e
    ) {}
}
