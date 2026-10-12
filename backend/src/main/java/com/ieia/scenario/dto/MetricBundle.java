package com.ieia.scenario.dto;

import java.math.BigDecimal;
import java.util.Map;

/** A full impact + score snapshot — baseline or projected (docs/03 §10). */
public record MetricBundle(
        BigDecimal co2eKg,
        BigDecimal energyMj,
        BigDecimal waterLitre,
        BigDecimal wasteKg,
        BigDecimal recycledFraction,
        BigDecimal overallScore,
        Map<String, BigDecimal> categoryScores,
        Map<String, BigDecimal> co2eByCategory
) {}
