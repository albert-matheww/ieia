package com.ieia.scoring.dto;

import com.ieia.common.util.PeriodUtil;
import com.ieia.scoring.EnvironmentalScore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Full, explainable score for one month (docs/03-api-specification.md §7). */
public record ScoreResponse(
        String period,
        BigDecimal overall,
        BigDecimal previous,
        BigDecimal delta,
        Categories categories,
        List<Map<String, Object>> drivers,
        Map<String, Object> breakdown,
        String methodologyVersion,
        boolean provisional
) {
    public record CategoryScore(BigDecimal score) {}

    public record Categories(
            BigDecimal carbon,
            BigDecimal energy,
            BigDecimal water,
            BigDecimal waste,
            BigDecimal transport
    ) {}

    public static ScoreResponse from(EnvironmentalScore s) {
        Object provisional = s.getScoreBreakdown().get("provisional");
        Object version = s.getScoreBreakdown().get("methodologyVersion");
        return new ScoreResponse(
                PeriodUtil.format(s.getPeriodMonth()),
                s.getOverallScore(),
                s.getPreviousScore(),
                s.getDelta(),
                new Categories(s.getCarbonScore(), s.getEnergyScore(), s.getWaterScore(),
                        s.getWasteScore(), s.getTransportScore()),
                s.getDrivers(),
                s.getScoreBreakdown(),
                version instanceof String v ? v : null,
                Boolean.TRUE.equals(provisional));
    }
}
