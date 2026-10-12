package com.ieia.scoring.dto;

import com.ieia.scoring.ScoringConfig;

import java.util.Map;

public record MethodologyResponse(
        String version,
        Map<String, Double> weights,
        Map<String, Object> baselines,
        Map<String, Object> scaleParams,
        String description,
        String formula
) {
    private static final String FORMULA =
            "subScore(m) = clamp(100 * (poor - m) / (poor - good), 0, 100); "
          + "categoryScore = subScore(metric) [+ recyclingBonus for waste]; "
          + "overall = Σ weight_category * categoryScore. "
          + "See docs/06-scoring-methodology.md.";

    public static MethodologyResponse from(ScoringConfig c) {
        return new MethodologyResponse(
                c.getVersion(), c.getWeights(), c.getBaselines(), c.getScaleParams(),
                c.getDescription(), FORMULA);
    }
}
