package com.ieia.scoring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoreExplainerTest {

    private final ScoreExplainer explainer = new ScoreExplainer();

    private final Map<String, Double> weights = Map.of(
            "carbon", 0.35, "energy", 0.20, "water", 0.15, "waste", 0.15, "transport", 0.15);

    @Test
    void deltaOfOverallEqualsSumOfWeightedCategoryDeltas() {
        Map<String, Double> subNow = Map.of("carbon", 30.0, "energy", 50.0, "water", 70.0,
                "waste", 60.0, "transport", 45.0);
        Map<String, Double> subPrev = Map.of("carbon", 40.0, "energy", 52.0, "water", 70.0,
                "waste", 58.0, "transport", 50.0);
        Map<String, Double> metricNow = Map.of("carbon", 1050.0, "energy", 5200.0, "water", 5000.0,
                "waste", 40.0, "transport", 260.0);
        Map<String, Double> metricPrev = Map.of("carbon", 900.0, "energy", 5000.0, "water", 5000.0,
                "waste", 39.0, "transport", 230.0);

        List<Map<String, Object>> drivers = explainer.drivers(subNow, subPrev, metricNow, metricPrev, weights);

        double overallNow = weights.entrySet().stream()
                .mapToDouble(e -> e.getValue() * subNow.get(e.getKey())).sum();
        double overallPrev = weights.entrySet().stream()
                .mapToDouble(e -> e.getValue() * subPrev.get(e.getKey())).sum();
        double sumPoints = drivers.stream()
                .mapToDouble(d -> ((Number) d.get("pointsImpact")).doubleValue()).sum();

        assertThat(sumPoints).isCloseTo(overallNow - overallPrev, within(0.05));
    }

    @Test
    void driversAreSortedByAbsolutePointsImpactDescending() {
        Map<String, Double> subNow = Map.of("carbon", 20.0, "energy", 50.0, "water", 70.0,
                "waste", 60.0, "transport", 45.0);
        Map<String, Double> subPrev = Map.of("carbon", 40.0, "energy", 51.0, "water", 70.0,
                "waste", 60.0, "transport", 47.0);
        var drivers = explainer.drivers(subNow, subPrev, subNow, subPrev, weights);

        assertThat(drivers.get(0).get("category")).isEqualTo("carbon");
        assertThat(drivers.get(0).get("direction")).isEqualTo("WORSENED");
        double first = Math.abs(((Number) drivers.get(0).get("pointsImpact")).doubleValue());
        double second = Math.abs(((Number) drivers.get(1).get("pointsImpact")).doubleValue());
        assertThat(first).isGreaterThanOrEqualTo(second);
    }

    @Test
    void noPreviousMonthYieldsNoDrivers() {
        assertThat(explainer.drivers(Map.of("carbon", 50.0), Map.of(), Map.of(), Map.of(), weights))
                .isEmpty();
    }

    @Test
    void improvementIsFlaggedImproved() {
        var drivers = explainer.drivers(
                Map.of("energy", 70.0), Map.of("energy", 50.0),
                Map.of("energy", 3000.0), Map.of("energy", 4000.0), weights);
        assertThat(drivers.get(0).get("direction")).isEqualTo("IMPROVED");
        assertThat(((Number) drivers.get(0).get("changePct")).doubleValue()).isCloseTo(-25.0, within(1e-6));
    }
}
