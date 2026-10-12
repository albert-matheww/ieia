package com.ieia.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieia.anomaly.AnomalyRepository;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.ImpactAnalyticsSupport;
import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.prediction.Prediction;
import com.ieia.prediction.PredictionRepository;
import com.ieia.recommendation.Recommendation;
import com.ieia.recommendation.RecommendationRepository;
import com.ieia.scoring.EnvironmentalScore;
import com.ieia.scoring.EnvironmentalScoreRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles the compact JSON bundle of the user's <b>real</b> analytics that is
 * given to the LLM. The assistant may only phrase answers over these figures
 * (docs/03-api-specification.md §12, docs/05-ml-methodology.md §5.2).
 */
@Component
public class ConversationContextBuilder {

    private final MonthlyMetricRepository metricRepository;
    private final EnvironmentalScoreRepository scoreRepository;
    private final ImpactAnalyticsSupport analyticsSupport;
    private final AnomalyRepository anomalyRepository;
    private final PredictionRepository predictionRepository;
    private final RecommendationRepository recommendationRepository;
    private final ObjectMapper objectMapper;

    public ConversationContextBuilder(MonthlyMetricRepository metricRepository,
                                      EnvironmentalScoreRepository scoreRepository,
                                      ImpactAnalyticsSupport analyticsSupport,
                                      AnomalyRepository anomalyRepository,
                                      PredictionRepository predictionRepository,
                                      RecommendationRepository recommendationRepository,
                                      ObjectMapper objectMapper) {
        this.metricRepository = metricRepository;
        this.scoreRepository = scoreRepository;
        this.analyticsSupport = analyticsSupport;
        this.anomalyRepository = anomalyRepository;
        this.predictionRepository = predictionRepository;
        this.recommendationRepository = recommendationRepository;
        this.objectMapper = objectMapper;
    }

    public record Context(Map<String, Object> bundle, String json, boolean hasData) {}

    @Transactional(readOnly = true)
    public Context build(UUID userId) {
        List<MonthlyMetric> metrics = metricRepository.findByUserIdOrderByPeriodMonth(userId);
        Map<String, Object> ctx = new LinkedHashMap<>();

        if (metrics.isEmpty()) {
            ctx.put("hasData", false);
            ctx.put("note", "The user has no environmental records yet.");
            ctx.put("metrics12mo", List.of());
            return new Context(ctx, toJson(ctx), false);
        }

        MonthlyMetric latest = metrics.get(metrics.size() - 1);
        EnvironmentalScore score = scoreRepository.findByUserIdAndPeriodMonth(userId, latest.getPeriodMonth())
                .orElse(null);

        ctx.put("hasData", true);
        ctx.put("period", PeriodUtil.format(latest.getPeriodMonth()));
        ctx.put("dataWindow", PeriodUtil.format(metrics.get(0).getPeriodMonth())
                + ".." + PeriodUtil.format(latest.getPeriodMonth()));
        ctx.put("monthsOfHistory", metrics.size());

        ctx.put("co2eKg", latest.getTotalCo2eKg());
        ctx.put("energyMj", latest.getTotalEnergyMj());
        ctx.put("waterLitre", latest.getTotalWaterLitre());
        ctx.put("wasteKg", latest.getTotalWasteKg());
        ctx.put("recycledFraction", latest.getRecycledFraction());

        if (score != null) {
            ctx.put("score", score.getOverallScore());
            ctx.put("scoreDelta", score.getDelta());
            ctx.put("categoryScores", Map.of(
                    "carbon", score.getCarbonScore(), "energy", score.getEnergyScore(),
                    "water", score.getWaterScore(), "waste", score.getWasteScore(),
                    "transport", score.getTransportScore()));
            ctx.put("drivers", score.getDrivers());
        }

        // top contributors for the latest month
        List<Map<String, Object>> contributors = new ArrayList<>();
        for (var c : analyticsSupport.emissionsByCategory(List.of(latest))) {
            contributors.add(Map.of("category", c.category(), "co2eKg", c.kg(), "pct", c.pct()));
        }
        ctx.put("topContributors", contributors);

        // 12-month series
        List<Map<String, Object>> series = new ArrayList<>();
        int start = Math.max(0, metrics.size() - 12);
        for (int i = start; i < metrics.size(); i++) {
            MonthlyMetric m = metrics.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", PeriodUtil.format(m.getPeriodMonth()));
            row.put("co2eKg", m.getTotalCo2eKg());
            row.put("energyMj", m.getTotalEnergyMj());
            row.put("waterLitre", m.getTotalWaterLitre());
            row.put("wasteKg", m.getTotalWasteKg());
            scoreRepository.findByUserIdAndPeriodMonth(userId, m.getPeriodMonth())
                    .ifPresent(s -> row.put("score", s.getOverallScore()));
            series.add(row);
        }
        ctx.put("metrics12mo", series);

        ctx.put("openAnomalies", anomalyRepository.countByUserIdAndStatus(userId, "OPEN"));

        Prediction pred = predictionRepository
                .findFirstByUserIdAndTargetMetricOrderByCreatedAtDesc(userId, "CO2E").orElse(null);
        if (pred != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("metric", "CO2E");
            p.put("horizonMonth", PeriodUtil.format(pred.getHorizonMonth()));
            p.put("currentValue", pred.getCurrentValue());
            p.put("predictedValue", pred.getPredictedValue());
            p.put("expectedChangePct", pred.getExpectedChangePct());
            p.put("model", pred.getModelName());
            ctx.put("latestPrediction", p);
        }

        java.time.LocalDate recPeriod = recommendationRepository.latestPeriod(userId);
        if (recPeriod != null) {
            List<Map<String, Object>> recs = new ArrayList<>();
            for (Recommendation r : recommendationRepository
                    .findByUserIdAndPeriodMonthOrderByRank(userId, recPeriod)) {
                if (recs.size() >= 5) break;
                recs.add(Map.of("rank", r.getRank(), "category", r.getCategory(),
                        "title", r.getTitle(),
                        "estimatedReductionCo2eKg", r.getEstimatedReductionCo2eKg()));
            }
            ctx.put("recommendations", recs);
        }

        return new Context(ctx, toJson(ctx), true);
    }

    /** Every numeric value that legitimately appears in the context (for the fabrication guard). */
    public List<Double> numericValues(Map<String, Object> bundle) {
        List<Double> out = new ArrayList<>();
        collectNumbers(bundle, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void collectNumbers(Object node, List<Double> out) {
        if (node instanceof Number n) {
            out.add(n.doubleValue());
        } else if (node instanceof BigDecimal bd) {
            out.add(bd.doubleValue());
        } else if (node instanceof Map<?, ?> m) {
            m.values().forEach(v -> collectNumbers(v, out));
        } else if (node instanceof Iterable<?> it) {
            it.forEach(v -> collectNumbers(v, out));
        } else if (node instanceof String s) {
            var matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(s);
            while (matcher.find()) {
                try { out.add(Double.parseDouble(matcher.group())); } catch (NumberFormatException ignored) {}
            }
        }
    }

    private String toJson(Map<String, Object> ctx) {
        try {
            return objectMapper.writeValueAsString(ctx);
        } catch (Exception e) {
            return "{}";
        }
    }
}
