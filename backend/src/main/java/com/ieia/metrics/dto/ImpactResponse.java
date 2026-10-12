package com.ieia.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

/** Totals + breakdowns over a date range (docs/03-api-specification.md §6). */
public record ImpactResponse(
        String from,
        String to,
        int months,
        Totals totals,
        List<DashboardResponse.CategoryEmission> emissionsByCategory,
        List<DashboardResponse.ActivityEmission> emissionsByActivity,
        List<DashboardResponse.WasteSlice> wasteComposition,
        BigDecimal recycledFraction
) {
    public record Totals(
            BigDecimal co2eKg,
            BigDecimal energyMj,
            BigDecimal waterLitre,
            BigDecimal wasteKg,
            int recordCount
    ) {}
}
