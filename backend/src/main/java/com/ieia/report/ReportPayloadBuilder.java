package com.ieia.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieia.anomaly.AnomalyRepository;
import com.ieia.anomaly.dto.AnomalyResponse;
import com.ieia.benchmark.BenchmarkingService;
import com.ieia.benchmark.dto.BenchmarkComparison;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.ImpactQueryService;
import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.metrics.dto.ContributorsResponse;
import com.ieia.metrics.dto.ImpactResponse;
import com.ieia.metrics.dto.TimeSeriesPoint;
import com.ieia.metrics.dto.TrendsResponse;
import com.ieia.prediction.Prediction;
import com.ieia.prediction.PredictionRepository;
import com.ieia.recommendation.RecommendationService;
import com.ieia.recommendation.dto.RecommendationResponse;
import com.ieia.scenario.Scenario;
import com.ieia.scenario.ScenarioRepository;
import com.ieia.scoring.ScoreQueryService;
import com.ieia.scoring.ScoringService;
import com.ieia.scoring.dto.ScoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles the full structured environmental report (docs/03-api-specification.md §14)
 * from the domain services. Every section is reduced to flat, presentation-ready rows
 * ({@code List<Map>} of scalars, or a {@code Map} of scalars) so the PDF renderer can
 * lay it out as clean key/value and row tables. Each section is best-effort — a thin
 * data period still produces a coherent report.
 */
@Component
public class ReportPayloadBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReportPayloadBuilder.class);
    private static final List<String> ALL_SECTIONS = List.of(
            "summary", "scores", "trends", "contributors", "anomalies",
            "predictions", "recommendations", "scenarios", "benchmarks", "methodology");

    private final MonthlyMetricRepository metricRepository;
    private final ImpactQueryService impactQueryService;
    private final ScoreQueryService scoreQueryService;
    private final AnomalyRepository anomalyRepository;
    private final PredictionRepository predictionRepository;
    private final RecommendationService recommendationService;
    private final ScenarioRepository scenarioRepository;
    private final BenchmarkingService benchmarkingService;
    private final ScoringService scoringService;
    private final ObjectMapper objectMapper;

    public ReportPayloadBuilder(MonthlyMetricRepository metricRepository,
                                ImpactQueryService impactQueryService,
                                ScoreQueryService scoreQueryService,
                                AnomalyRepository anomalyRepository,
                                PredictionRepository predictionRepository,
                                RecommendationService recommendationService,
                                ScenarioRepository scenarioRepository,
                                BenchmarkingService benchmarkingService,
                                ScoringService scoringService,
                                ObjectMapper objectMapper) {
        this.metricRepository = metricRepository;
        this.impactQueryService = impactQueryService;
        this.scoreQueryService = scoreQueryService;
        this.anomalyRepository = anomalyRepository;
        this.predictionRepository = predictionRepository;
        this.recommendationService = recommendationService;
        this.scenarioRepository = scenarioRepository;
        this.benchmarkingService = benchmarkingService;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> build(UUID userId, LocalDate from, LocalDate to, List<String> include) {
        Set<String> sections = (include == null || include.isEmpty())
                ? Set.copyOf(ALL_SECTIONS) : Set.copyOf(include);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportingPeriod", Map.of("from", PeriodUtil.format(from), "to", PeriodUtil.format(to)));
        payload.put("generatedAt", Instant.now().toString());

        List<MonthlyMetric> metrics = metricRepository
                .findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(userId, from, to);
        LocalDate latest = metrics.isEmpty() ? to : metrics.get(metrics.size() - 1).getPeriodMonth();

        section(payload, sections, "summary", () -> {
            ImpactResponse impact = impactQueryService.impact(userId, from, to);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("months", impact.months());
            ImpactResponse.Totals t = impact.totals();
            if (t != null) {
                s.put("co2eKg", t.co2eKg());
                s.put("energyMj", t.energyMj());
                s.put("waterLitre", t.waterLitre());
                s.put("wasteKg", t.wasteKg());
                s.put("recordCount", t.recordCount());
            }
            s.put("recycledFraction", impact.recycledFraction());
            scoreQueryService.history(userId, from, to).stream()
                    .reduce((a, b) -> b)
                    .ifPresent(last -> {
                        s.put("latestScore", last.overall());
                        s.put("latestScoreDelta", last.delta());
                        s.put("latestScorePeriod", last.period());
                    });
            return s;
        });

        section(payload, sections, "scores", () -> scoreQueryService.history(userId, from, to).stream()
                .map(this::scoreRow)
                .toList());

        section(payload, sections, "trends", () -> trendRows(
                impactQueryService.trends(userId, from, to,
                        List.of("CO2E", "ENERGY", "WATER", "WASTE", "SCORE"))));

        section(payload, sections, "contributors", () -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("byCategory", contributorRows(impactQueryService.contributors(userId, latest, "category", 10)));
            c.put("byActivity", contributorRows(impactQueryService.contributors(userId, latest, "activity", 10)));
            return c;
        });

        section(payload, sections, "anomalies", () -> anomalyRepository.findAll().stream()
                .filter(a -> a.getUserId().equals(userId))
                .filter(a -> !a.getPeriodMonth().isBefore(from) && !a.getPeriodMonth().isAfter(to))
                .map(AnomalyResponse::summary)
                .sorted(Comparator.comparing(AnomalyResponse::period))
                .map(this::anomalyRow)
                .toList());

        section(payload, sections, "predictions", () -> {
            List<Map<String, Object>> preds = new ArrayList<>();
            for (String target : List.of("CO2E", "ENERGY", "WATER", "SCORE")) {
                predictionRepository.findFirstByUserIdAndTargetMetricOrderByCreatedAtDesc(userId, target)
                        .ifPresent(p -> preds.add(predictionRow(p)));
            }
            return preds;
        });

        section(payload, sections, "recommendations", () -> {
            RecommendationResponse r = recommendationService.get(userId, latest);
            return r == null ? List.of() : r.items().stream().map(this::recommendationRow).toList();
        });

        section(payload, sections, "scenarios", () ->
                scenarioRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10))
                        .map(this::scenarioRow).getContent());

        section(payload, sections, "benchmarks", () -> benchmarkRows(benchmarkingService.comparison(userId, latest)));

        section(payload, sections, "methodology", () -> {
            var cfg = scoringService.activeConfig();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scoringVersion", cfg.getVersion());
            m.put("weights", plainMap(cfg.getWeights()));
            m.put("scoreFormula",
                    "subScore(m) = clamp(100*(poor-m)/(poor-good),0,100); overall = Sum(weight x categoryScore)");
            m.put("mlApproach", "Interpretable model zoo (Linear/RandomForest/GradientBoosting) selected by "
                    + "expanding-window time-series cross-validation; SeasonalNaive fallback for short history. "
                    + "Anomalies via a robust-statistics + Isolation-Forest ensemble with consensus voting.");
            m.put("limitations", List.of(
                    "Emission factors are configurable defaults, not exhaustively region/year matched.",
                    "Monthly data means models see few observations; forecasts are indicative with wide intervals.",
                    "No exogenous drivers (weather, price, occupancy) are modelled.",
                    "Benchmarks are configurable reference values, not official standards."));
            return m;
        });

        return payload;
    }

    // ---- row shaping -------------------------------------------------

    private Map<String, Object> scoreRow(ScoreResponse s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", s.period());
        m.put("overall", s.overall());
        m.put("delta", s.delta());
        ScoreResponse.Categories c = s.categories();
        if (c != null) {
            m.put("carbon", c.carbon());
            m.put("energy", c.energy());
            m.put("water", c.water());
            m.put("waste", c.waste());
            m.put("transport", c.transport());
        }
        m.put("topDriver", topDriver(s.drivers()));
        return m;
    }

    private static String topDriver(List<Map<String, Object>> drivers) {
        if (drivers == null || drivers.isEmpty()) return "—";
        Map<String, Object> top = drivers.stream()
                .max(Comparator.comparingDouble(d -> Math.abs(toDouble(d.get("pointsImpact")))))
                .orElse(drivers.get(0));
        double pts = toDouble(top.get("pointsImpact"));
        Object cat = top.getOrDefault("category", top.get("metric"));
        return String.format(java.util.Locale.ROOT, "%s %+.2f pts", cat, pts);
    }

    private List<Map<String, Object>> trendRows(TrendsResponse trends) {
        Map<String, List<TimeSeriesPoint>> series = trends.series();
        Set<String> periods = new LinkedHashSet<>();
        series.values().forEach(list -> list.forEach(p -> periods.add(p.period())));
        String[] keys = {"CO2E", "ENERGY", "WATER", "WASTE", "SCORE"};
        String[] cols = {"co2eKg", "energyMj", "waterLitre", "wasteKg", "score"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String period : periods) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", period);
            for (int i = 0; i < keys.length; i++) {
                row.put(cols[i], valueAt(series.get(keys[i]), period));
            }
            rows.add(row);
        }
        return rows;
    }

    private static BigDecimal valueAt(List<TimeSeriesPoint> pts, String period) {
        if (pts == null) return null;
        return pts.stream().filter(p -> p.period().equals(period))
                .map(TimeSeriesPoint::value).findFirst().orElse(null);
    }

    private List<Map<String, Object>> contributorRows(ContributorsResponse c) {
        if (c == null || c.items() == null) return List.of();
        return c.items().stream().map(it -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", it.label() == null ? it.key() : it.label());
            m.put("co2eKg", it.co2eKg());
            m.put("sharePct", it.pct());
            return m;
        }).toList();
    }

    private Map<String, Object> anomalyRow(AnomalyResponse a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", a.period());
        m.put("metric", a.metric());
        m.put("direction", a.direction());
        m.put("severity", a.severity());
        m.put("deviationPct", a.deviationPct());
        m.put("method", a.method());
        m.put("estCo2eKg", a.estimatedCo2eImpactKg());
        m.put("note", a.explanation());
        return m;
    }

    private Map<String, Object> predictionRow(Prediction p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metric", p.getTargetMetric());
        m.put("horizonMonth", PeriodUtil.format(p.getHorizonMonth()));
        m.put("currentValue", p.getCurrentValue());
        m.put("predictedValue", p.getPredictedValue());
        m.put("lowerBound", p.getLowerBound());
        m.put("upperBound", p.getUpperBound());
        m.put("expectedChangePct", p.getExpectedChangePct());
        m.put("model", p.getModelName());
        Object eval = p.getMetrics() == null ? null : p.getMetrics().get("evaluation");
        Map<?, ?> e = eval instanceof Map<?, ?> mm ? mm : Map.of();
        m.put("r2", e.get("r2"));
        m.put("mae", e.get("mae"));
        return m;
    }

    private Map<String, Object> recommendationRow(RecommendationResponse.Item it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", it.rank());
        m.put("category", it.category());
        m.put("action", it.title());
        m.put("priority", it.confidence());
        m.put("estReductionCo2eKg", it.estimatedReductionCo2eKg());
        m.put("estReductionPct", it.estimatedReductionPct());
        m.put("rationale", it.rationale());
        return m;
    }

    private Map<String, Object> scenarioRow(Scenario s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.getName());
        m.put("basePeriod", PeriodUtil.format(s.getBasePeriodFrom()) + " to " + PeriodUtil.format(s.getBasePeriodTo()));
        Object deltas = s.getResult() == null ? null : s.getResult().get("deltas");
        if (deltas instanceof Map<?, ?> d) {
            m.put("co2eDeltaKg", d.get("co2eKg"));
            m.put("scoreDelta", d.get("score"));
        }
        return m;
    }

    private Object benchmarkRows(BenchmarkComparison b) {
        if (b == null) return Map.of("unavailable", true, "reason", "no benchmark for this period");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", b.period());
        m.put("scope", b.scope());
        m.put("segment", b.segment());
        m.put("region", b.region());
        if (b.score() != null) {
            m.put("scoreCurrent", b.score().current());
            m.put("scoreBenchmark", b.score().benchmark());
            m.put("scoreRating", b.score().rating());
        }
        if (b.categories() != null) {
            m.put("categories", b.categories().stream().map(cc -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("category", cc.category());
                r.put("value", cc.value());
                r.put("unit", cc.unit());
                r.put("benchmarkValue", cc.benchmarkValue());
                r.put("rating", cc.rating());
                return r;
            }).toList());
        }
        m.put("disclaimer", b.disclaimer());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> plainMap(Object o) {
        if (o == null) return Map.of();
        return objectMapper.convertValue(o, Map.class);
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? 0d : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    // ---- section wrapper ------------------------------------------------

    private interface SectionSupplier {
        Object get();
    }

    private void section(Map<String, Object> payload, Set<String> wanted, String key, SectionSupplier supplier) {
        if (!wanted.contains(key)) return;
        try {
            payload.put(key, supplier.get());
        } catch (RuntimeException e) {
            log.debug("Report section '{}' skipped: {}", key, e.getMessage());
            payload.put(key, Map.of("unavailable", true, "reason", String.valueOf(e.getMessage())));
        }
    }
}
