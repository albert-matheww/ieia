package com.ieia.scoring;

import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Reproduces the worked example in docs/06-scoring-methodology.md §4 exactly,
 * using the seeded v1 configuration.
 */
class ScoringServiceTest {

    private MonthlyMetricRepository metricRepository;
    private ScoringService service;
    private User individual;

    @BeforeEach
    void setUp() {
        metricRepository = Mockito.mock(MonthlyMetricRepository.class);
        service = new ScoringService(metricRepository,
                Mockito.mock(EnvironmentalScoreRepository.class),
                Mockito.mock(ScoringConfigRepository.class),
                Mockito.mock(UserRepository.class),
                new ScoreExplainer());
        individual = new User();
        individual.setId(UUID.randomUUID());
        individual.setRegion("GLOBAL");
        Mockito.when(metricRepository.findByUserIdOrderByPeriodMonth(Mockito.any()))
                .thenReturn(List.of(new MonthlyMetric(), new MonthlyMetric(), new MonthlyMetric()));
    }

    private ScoringConfig v1Config() {
        ScoringConfig c = new ScoringConfig();
        c.setVersion("v1");
        c.setActive(true);
        c.setWeights(Map.of("carbon", 0.35, "energy", 0.20, "water", 0.15, "waste", 0.15, "transport", 0.15));
        c.setScaleParams(Map.of("recycling_bonus_max", 8, "round_dp", 2, "insufficient_data_months", 2));
        Map<String, Object> gp = Map.of(
                "CO2E", Map.of("good", 250, "poor", 1200),
                "ENERGY", Map.of("good", 1800, "poor", 9000),
                "WATER", Map.of("good", 3000, "poor", 12000),
                "WASTE", Map.of("good", 15, "poor", 90),
                "TRANSPORT", Map.of("good", 60, "poor", 400));
        c.setBaselines(Map.of("INDIVIDUAL", Map.of("GLOBAL", gp)));
        return c;
    }

    private MonthlyMetric metric(double co2e, double energy, double water, double wasteLandfilled,
                                 double transportCo2e, double recycledFraction) {
        MonthlyMetric m = new MonthlyMetric();
        m.setUserId(individual.getId());
        m.setPeriodMonth(LocalDate.of(2026, 6, 1));
        m.setTotalCo2eKg(BigDecimal.valueOf(co2e));
        m.setTotalEnergyMj(BigDecimal.valueOf(energy));
        m.setTotalWaterLitre(BigDecimal.valueOf(water));
        m.setTotalWasteKg(BigDecimal.valueOf(wasteLandfilled));
        m.setRecycledFraction(BigDecimal.valueOf(recycledFraction));
        m.setCo2eByCategory(Map.of("TRANSPORT", BigDecimal.valueOf(transportCo2e)));
        return m;
    }

    @Test
    void carbonSubScoreMatchesDocsWorkedExample() {
        var c = service.compute(metric(890, 5040, 5700, 43.5, 236.8, 0.0), individual, v1Config());
        assertThat(c.sub().get("carbon")).isEqualTo(32.63);
    }

    @Test
    void categorySubScoresAndWeightedOverall() {
        var c = service.compute(metric(890, 5040, 5700, 43.5, 236.8, 0.0), individual, v1Config());

        assertThat(c.sub().get("carbon")).isEqualTo(32.63);
        assertThat(c.sub().get("energy")).isEqualTo(55.0);
        assertThat(c.sub().get("water")).isEqualTo(70.0);
        assertThat(c.sub().get("waste")).isEqualTo(62.0);
        assertThat(c.sub().get("transport")).isEqualTo(48.0);

        // 0.35*32.63 + 0.20*55 + 0.15*70 + 0.15*62 + 0.15*48 = 49.4205 -> 49.42
        assertThat(c.overall()).isCloseTo(49.42, within(1e-9));
    }

    @Test
    void recyclingBonusRaisesWasteSubScore() {
        var noBonus = service.compute(metric(500, 3000, 4000, 30, 100, 0.0), individual, v1Config());
        var withBonus = service.compute(metric(500, 3000, 4000, 30, 100, 0.5), individual, v1Config());
        // bonus = recycling_bonus_max(8) * 0.5 = 4 points
        assertThat(withBonus.sub().get("waste") - noBonus.sub().get("waste")).isEqualTo(4.0);
    }

    @Test
    void zeroTransportGivesAPerfectTransportSubScore() {
        MonthlyMetric m = metric(500, 3000, 4000, 20, 0, 0.0);
        m.setCo2eByCategory(Map.of()); // no transport at all
        var c = service.compute(m, individual, v1Config());
        assertThat(c.sub().get("transport")).isEqualTo(100.0);
    }

    @Test
    void breakdownRecordsEveryIntermediateValue() {
        var c = service.compute(metric(890, 5040, 5700, 43.5, 236.8, 0.0), individual, v1Config());
        @SuppressWarnings("unchecked")
        Map<String, Object> carbon = (Map<String, Object>)
                ((Map<String, Object>) c.breakdown().get("categories")).get("carbon");
        assertThat(carbon).containsKeys("score", "rawMetric", "baselineGood", "baselinePoor",
                "weight", "weightedContribution");
        assertThat(((Number) carbon.get("baselineGood")).doubleValue()).isEqualTo(250.0);
        assertThat(((Number) carbon.get("baselinePoor")).doubleValue()).isEqualTo(1200.0);
    }
}
