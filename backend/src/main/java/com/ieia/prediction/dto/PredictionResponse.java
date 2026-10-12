package com.ieia.prediction.dto;

import com.ieia.common.util.PeriodUtil;
import com.ieia.prediction.Prediction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** docs/03-api-specification.md §8. */
public record PredictionResponse(
        String metric,
        String generatedForMonth,
        String horizonMonth,
        BigDecimal currentValue,
        BigDecimal predictedValue,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        BigDecimal expectedChangeAbs,
        BigDecimal expectedChangePct,
        Model model,
        Map<String, Object> evaluation,
        List<Map<String, Object>> featureImportance,
        String confidenceNote,
        List<String> limitations,
        boolean cached
) {
    public record Model(String name, String trainedAt, Integer trainingRows) {}

    @SuppressWarnings("unchecked")
    public static PredictionResponse from(Prediction p, boolean cached) {
        Object limitationsRaw = p.getMetrics().get("limitations");
        List<String> limitations = limitationsRaw instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        return new PredictionResponse(
                p.getTargetMetric(),
                PeriodUtil.format(p.getGeneratedForMonth()),
                PeriodUtil.format(p.getHorizonMonth()),
                p.getCurrentValue(),
                p.getPredictedValue(),
                p.getLowerBound(),
                p.getUpperBound(),
                p.getExpectedChangeAbs(),
                p.getExpectedChangePct(),
                new Model(p.getModelName(),
                        p.getCreatedAt() == null ? null : p.getCreatedAt().toString(),
                        (Integer) p.getMetrics().getOrDefault("nTrain", null)),
                (Map<String, Object>) p.getMetrics().getOrDefault("evaluation", Map.of()),
                p.getFeatureImportance(),
                p.getConfidenceNote(),
                limitations,
                cached);
    }
}
