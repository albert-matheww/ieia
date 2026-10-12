package com.ieia.metrics.dto;

import com.ieia.anomaly.dto.AnomalyResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Everything the dashboard needs in one call (docs/03-api-specification.md §6). */
public record DashboardResponse(
        String period,
        Summary summary,
        CategoryScores categoryScores,
        Charts charts,
        List<Contributor> topContributors,
        List<AnomalyResponse> openAnomalies,
        Map<String, Object> latestPrediction,
        DataQuality dataQuality
) {
    public record Delta(BigDecimal value, BigDecimal previous, BigDecimal delta, BigDecimal deltaPct) {}

    public record Summary(
            Delta environmentalScore,
            Delta co2eKg,
            Delta energyMj,
            Delta waterLitre,
            Delta wasteKg,
            BigDecimal recycledFraction
    ) {}

    public record CategoryScores(
            BigDecimal carbon, BigDecimal energy, BigDecimal water,
            BigDecimal waste, BigDecimal transport
    ) {}

    public record WasteSlice(String method, BigDecimal kg) {}

    public record CategoryEmission(String category, BigDecimal kg, BigDecimal pct) {}

    public record ActivityEmission(String activityCode, String name, BigDecimal kg, BigDecimal pct) {}

    public record Charts(
            List<TimeSeriesPoint> co2eTrend,
            List<TimeSeriesPoint> energyTrend,
            List<TimeSeriesPoint> waterTrend,
            List<TimeSeriesPoint> wasteTrend,
            List<TimeSeriesPoint> scoreTrend,
            List<WasteSlice> wasteComposition,
            List<CategoryEmission> emissionsByCategory,
            List<ActivityEmission> emissionsByActivity
    ) {}

    public record Contributor(String category, String activityCode, String name, BigDecimal co2eKg, BigDecimal pct) {}

    public record DataQuality(int monthsOfHistory, int recordCount, boolean hasGaps) {}
}
