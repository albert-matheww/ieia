package com.ieia.benchmark.dto;

import java.math.BigDecimal;
import java.util.List;

public record BenchmarkComparison(
        String period,
        String scope,
        String segment,
        String region,
        ScoreComparison score,
        List<CategoryComparison> categories,
        PctChanges periodOverPeriod,
        PctChanges yearOverYear,
        String disclaimer
) {
    public record ScoreComparison(
            BigDecimal current,
            BigDecimal previousMonth,
            BigDecimal changePoints,
            BigDecimal previousYear,
            BigDecimal changeYoY,
            BigDecimal benchmark,
            String rating
    ) {}

    public record CategoryComparison(
            String category,
            BigDecimal value,
            String unit,
            BigDecimal benchmarkValue,
            String rating
    ) {}

    public record PctChanges(
            BigDecimal co2ePct,
            BigDecimal energyPct,
            BigDecimal waterPct,
            BigDecimal wastePct
    ) {}
}
