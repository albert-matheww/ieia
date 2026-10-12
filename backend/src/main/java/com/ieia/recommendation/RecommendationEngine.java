package com.ieia.recommendation;

import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.scenario.ScenarioEngine;
import com.ieia.scenario.dto.ScenarioRequest;
import com.ieia.scenario.dto.ScenarioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule engine (docs/05-ml-methodology.md §5.3): ranks the user's real CO₂e
 * contributors, instantiates parameterised actions for the top categories, and
 * <b>prices each action with the deterministic {@link ScenarioEngine}</b> using
 * the real emission factors. Output is ranked by estimated CO₂e reduction.
 */
@Component
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);
    private static final double MIN_REDUCTION_KG = 0.5;

    private final MonthlyMetricRepository metricRepository;
    private final ScenarioEngine scenarioEngine;

    public RecommendationEngine(MonthlyMetricRepository metricRepository, ScenarioEngine scenarioEngine) {
        this.metricRepository = metricRepository;
        this.scenarioEngine = scenarioEngine;
    }

    /** A priced candidate, before ranking/persistence. */
    public record Candidate(
            String category, String actionType, String title, String rationale,
            Map<String, Object> assumedChange, double reductionCo2eKg, double reductionPct,
            Map<String, Object> estimatedOther, String confidence) {}

    public List<Candidate> generate(UUID userId, LocalDate period) {
        MonthlyMetric metric = metricRepository.findByUserIdAndPeriodMonth(userId, period).orElse(null);
        if (metric == null || metric.getCo2eByCategory().isEmpty()) {
            return genericFallback();
        }
        double total = metric.getCo2eByCategory().values().stream()
                .mapToDouble(BigDecimal::doubleValue).sum();
        if (total <= 0) return genericFallback();

        // rank categories by CO2e share
        List<Map.Entry<String, BigDecimal>> ranked = new ArrayList<>(metric.getCo2eByCategory().entrySet());
        ranked.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // baseline window: up to 3 months ending at `period`
        LocalDate winFrom = period.minusMonths(2);

        List<Candidate> candidates = new ArrayList<>();
        int considered = 0;
        for (Map.Entry<String, BigDecimal> e : ranked) {
            if (considered++ >= 4) break;
            String category = e.getKey();
            for (ScenarioRequest.Adjustment adj : actionsFor(category)) {
                Candidate c = price(userId, winFrom, period, category, adj, total);
                if (c != null && c.reductionCo2eKg() >= MIN_REDUCTION_KG) {
                    candidates.add(c);
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(b.reductionCo2eKg(), a.reductionCo2eKg()));
        return candidates.stream().limit(6).toList();
    }

    // ---- action catalogue ------------------------------------------------

    private List<ScenarioRequest.Adjustment> actionsFor(String category) {
        return switch (category) {
            case "ELECTRICITY" -> List.of(
                    pct("CATEGORY", "ELECTRICITY", "PCT_REDUCTION", 15),
                    modalShift("electricity_grid", "electricity_renewable", 30),
                    pct("CATEGORY", "ELECTRICITY", "PCT_REDUCTION", 10));
            case "TRANSPORT" -> List.of(
                    modalShift("car_petrol", "metro_rail", 30),
                    modalShift("car_petrol", "bus", 25),
                    pct("ACTIVITY_TYPE", "car_petrol", "PCT_REDUCTION", 20));
            case "FUEL" -> List.of(
                    pct("CATEGORY", "FUEL", "PCT_REDUCTION", 20),
                    pct("CATEGORY", "FUEL", "PCT_REDUCTION", 10));
            case "WATER" -> List.of(
                    pct("CATEGORY", "WATER", "PCT_REDUCTION", 15),
                    pct("CATEGORY", "WATER", "PCT_REDUCTION", 25));
            case "WASTE" -> List.of(
                    recycling(70), recycling(85));
            case "MATERIALS" -> List.of(
                    pct("CATEGORY", "MATERIALS", "PCT_REDUCTION", 15));
            default -> List.of();
        };
    }

    private ScenarioResult.Deltas simulateSafely(UUID userId, ScenarioRequest req) {
        try {
            return scenarioEngine.simulate(userId, req).deltas();
        } catch (RuntimeException ex) {
            log.debug("recommendation candidate not applicable: {}", ex.getMessage());
            return null;
        }
    }

    private Candidate price(UUID userId, LocalDate from, LocalDate to, String category,
                            ScenarioRequest.Adjustment adj, double totalCo2e) {
        ScenarioRequest req = new ScenarioRequest(
                "rec", null, PeriodUtil.format(from), PeriodUtil.format(to), List.of(adj));
        ScenarioResult.Deltas d = simulateSafely(userId, req);
        if (d == null) return null;

        double reduction = -d.co2eKg().doubleValue();   // negative delta -> positive reduction
        if (reduction <= 0) return null;
        double reductionPct = totalCo2e > 0 ? reduction / totalCo2e * 100.0 : 0.0;

        String[] tr = titleAndRationale(category, adj, reduction, reductionPct);
        Map<String, Object> other = new java.util.LinkedHashMap<>();
        other.put("energyMj", d.energyMj());
        other.put("waterLitre", d.waterLitre());
        other.put("wasteKg", d.wasteKg());
        other.put("overallScorePoints", d.overallScore());

        String confidence = reductionPct >= 10 ? "HIGH" : reductionPct >= 3 ? "MEDIUM" : "LOW";
        return new Candidate(category, adj.change(), tr[0], tr[1],
                Map.of("target", adj.target(), "change", adj.change(), "value", adj.value(),
                        "params", adj.params() == null ? Map.of() : adj.params()),
                round(reduction, 2), round(reductionPct, 2), other, confidence);
    }

    private String[] titleAndRationale(String category, ScenarioRequest.Adjustment adj,
                                       double reduction, double reductionPct) {
        String cat = category.toLowerCase();
        String title;
        String rationale;
        switch (adj.change()) {
            case "PCT_REDUCTION" -> {
                String what = "ACTIVITY_TYPE".equals(adj.target().type())
                        ? adj.target().value().replace('_', ' ') : cat + " consumption";
                title = String.format("Reduce %s by %.0f%%", what, adj.value());
                rationale = String.format(
                        "%s is a top contributor to your footprint. Cutting %s by %.0f%% would save an "
                                + "estimated %.1f kg CO₂e per month (about %.1f%% of your total).",
                        cap(cat), what, adj.value(), reduction, reductionPct);
            }
            case "MODAL_SHIFT" -> {
                String from = adj.target().value().replace('_', ' ');
                String toCode = adj.params() == null ? "public transport" : adj.params().toActivityCode().replace('_', ' ');
                double share = adj.params() != null && adj.params().sharePct() != null
                        ? adj.params().sharePct() : adj.value();
                title = String.format("Shift %.0f%% of %s to %s", share, from, toCode);
                rationale = String.format(
                        "Moving %.0f%% of your %s activity to %s would save an estimated %.1f kg CO₂e per month "
                                + "(about %.1f%% of your total), using the emission factors for both modes.",
                        share, from, toCode, reduction, reductionPct);
            }
            case "RECYCLING_RATE" -> {
                title = String.format("Raise recycling rate to %.0f%%", adj.value());
                rationale = String.format(
                        "Diverting more waste from landfill to recycling to reach a %.0f%% rate would save an "
                                + "estimated %.1f kg CO₂e per month and cut landfilled mass.",
                        adj.value(), reduction);
            }
            default -> {
                title = "Reduce " + cat + " impact";
                rationale = String.format("Estimated saving %.1f kg CO₂e per month.", reduction);
            }
        }
        return new String[]{title, rationale};
    }

    private List<Candidate> genericFallback() {
        return List.of(
                new Candidate("ELECTRICITY", "EFFICIENCY",
                        "Switch to LED lighting and efficient appliances",
                        "Not enough recorded data to personalise this yet. LED lighting and 5-star appliances "
                                + "typically cut household electricity use by 10–20%.",
                        Map.of("basis", "generic"), 0, 0, Map.of(), "LOW"),
                new Candidate("TRANSPORT", "MODAL_SHIFT",
                        "Combine trips and use public transport where possible",
                        "Add transport records so we can quantify this against your own travel.",
                        Map.of("basis", "generic"), 0, 0, Map.of(), "LOW"),
                new Candidate("WASTE", "RECYCLING",
                        "Segregate waste and compost organic matter",
                        "Add waste records to see your landfill diversion rate and its impact.",
                        Map.of("basis", "generic"), 0, 0, Map.of(), "LOW"));
    }

    // ---- adjustment builders -----------------------------------------

    private static ScenarioRequest.Adjustment pct(String targetType, String targetValue, String change, double value) {
        return new ScenarioRequest.Adjustment(
                new ScenarioRequest.Target(targetType, targetValue), change, value, null);
    }

    private static ScenarioRequest.Adjustment modalShift(String source, String target, double sharePct) {
        return new ScenarioRequest.Adjustment(
                new ScenarioRequest.Target("ACTIVITY_TYPE", source), "MODAL_SHIFT", sharePct,
                new ScenarioRequest.Params(target, sharePct));
    }

    private static ScenarioRequest.Adjustment recycling(double targetPct) {
        return new ScenarioRequest.Adjustment(
                new ScenarioRequest.Target("CATEGORY", "WASTE"), "RECYCLING_RATE", targetPct, null);
    }

    private static double round(double v, int dp) {
        return BigDecimal.valueOf(v).setScale(dp, RoundingMode.HALF_UP).doubleValue();
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
