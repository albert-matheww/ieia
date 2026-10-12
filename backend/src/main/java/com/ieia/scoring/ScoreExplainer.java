package com.ieia.scoring;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decomposes a month-over-month score change into per-category "drivers"
 * (docs/06-scoring-methodology.md §5):
 * <pre>Δoverall = Σ_c weight_c · (subScore_c,now − subScore_c,prev)</pre>
 * Each driver reports the underlying metric change and its points impact.
 */
@Component
public class ScoreExplainer {

    /** category key → the metric label shown to users */
    private static final Map<String, String> METRIC_LABEL = Map.of(
            "carbon", "CO2E",
            "energy", "ENERGY",
            "water", "WATER",
            "waste", "WASTE_LANDFILLED",
            "transport", "TRANSPORT_CO2E");

    public List<Map<String, Object>> drivers(Map<String, Double> subNow,
                                             Map<String, Double> subPrev,
                                             Map<String, Double> metricNow,
                                             Map<String, Double> metricPrev,
                                             Map<String, Double> weights) {
        if (subPrev == null || subPrev.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> drivers = new ArrayList<>();
        for (String category : subNow.keySet()) {
            double weight = weights.getOrDefault(category, 0.0);
            double sn = subNow.getOrDefault(category, 0.0);
            double sp = subPrev.getOrDefault(category, 0.0);
            double pointsImpact = ScoringMath.round(weight * (sn - sp), 2);

            double mn = metricNow.getOrDefault(category, 0.0);
            double mp = metricPrev.getOrDefault(category, 0.0);
            double changePct;
            if (mp != 0.0) {
                changePct = ScoringMath.round((mn - mp) / mp * 100.0, 1);
            } else {
                changePct = mn > 0 ? 100.0 : 0.0;
            }

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("category", category);
            d.put("metric", METRIC_LABEL.getOrDefault(category, category.toUpperCase()));
            d.put("changePct", changePct);
            d.put("pointsImpact", pointsImpact);
            d.put("direction", pointsImpact >= 0 ? "IMPROVED" : "WORSENED");
            drivers.add(d);
        }
        drivers.sort(Comparator.comparingDouble(
                (Map<String, Object> d) -> Math.abs(((Number) d.get("pointsImpact")).doubleValue())).reversed());
        return drivers;
    }
}
