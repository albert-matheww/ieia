package com.ieia.metrics.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Why the score / metrics changed vs the previous month
 * (docs/03-api-specification.md §6, docs/06 §5). Reuses the drivers already
 * decomposed and stored on the {@code environmental_scores} row.
 */
public record ExplainResponse(
        String period,
        String comparedTo,
        BigDecimal scoreNow,
        BigDecimal scorePrevious,
        BigDecimal scoreDelta,
        List<Map<String, Object>> drivers,
        List<MetricChange> metricChanges,
        String narrative
) {
    public record MetricChange(String metric, BigDecimal now, BigDecimal previous, BigDecimal changePct) {}
}
