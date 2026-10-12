package com.ieia.anomaly.dto;

import com.ieia.anomaly.Anomaly;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.dto.TimeSeriesPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnomalyResponse(
        UUID id,
        String period,
        String metric,
        String method,
        String direction,
        String severity,
        BigDecimal observedValue,
        BigDecimal baselineValue,
        BigDecimal deviationAbs,
        BigDecimal deviationPct,
        BigDecimal score,
        BigDecimal estimatedCo2eImpactKg,
        String explanation,
        String status,
        Instant detectedAt,
        Instant reviewedAt,
        Context context
) {
    public record Context(List<TimeSeriesPoint> series, List<String> baselineWindow) {}

    public static AnomalyResponse from(Anomaly a, Context context) {
        return new AnomalyResponse(
                a.getId(), PeriodUtil.format(a.getPeriodMonth()), a.getMetric(), a.getMethod(),
                a.getDirection(), a.getSeverity(), a.getObservedValue(), a.getBaselineValue(),
                a.getDeviationAbs(), a.getDeviationPct(), a.getScore(), a.getEstimatedCo2eImpactKg(),
                a.getExplanation(), a.getStatus(), a.getDetectedAt(), a.getReviewedAt(), context);
    }

    public static AnomalyResponse summary(Anomaly a) {
        return from(a, null);
    }
}
