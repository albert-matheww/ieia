package com.ieia.scoring;

import com.ieia.common.exception.ApiException;
import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes and persists the transparent 0–100 {@link EnvironmentalScore} for a
 * user-month, exactly per docs/06-scoring-methodology.md. Every intermediate
 * value is stored in {@code score_breakdown} so the derivation is auditable.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);
    private static final List<String> CATEGORIES = List.of("carbon", "energy", "water", "waste", "transport");

    private final MonthlyMetricRepository metricRepository;
    private final EnvironmentalScoreRepository scoreRepository;
    private final ScoringConfigRepository configRepository;
    private final UserRepository userRepository;
    private final ScoreExplainer explainer;

    public ScoringService(MonthlyMetricRepository metricRepository,
                          EnvironmentalScoreRepository scoreRepository,
                          ScoringConfigRepository configRepository,
                          UserRepository userRepository,
                          ScoreExplainer explainer) {
        this.metricRepository = metricRepository;
        this.scoreRepository = scoreRepository;
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.explainer = explainer;
    }

    @Transactional
    public EnvironmentalScore recomputeMonth(UUID userId, LocalDate period) {
        MonthlyMetric metric = metricRepository.findByUserIdAndPeriodMonth(userId, period).orElse(null);
        if (metric == null) {
            scoreRepository.findByUserIdAndPeriodMonth(userId, period).ifPresent(scoreRepository::delete);
            return null;
        }
        ScoringConfig config = configRepository.findByActiveTrue()
                .orElseThrow(() -> new ApiException(com.ieia.common.exception.ErrorCode.INTERNAL,
                        "No active scoring configuration"));
        User user = userRepository.findByIdWithOrganization(userId).orElseThrow();

        Computation c = compute(metric, user, config);

        EnvironmentalScore score = scoreRepository.findByUserIdAndPeriodMonth(userId, period)
                .orElseGet(() -> {
                    EnvironmentalScore s = new EnvironmentalScore();
                    s.setUserId(userId);
                    s.setPeriodMonth(period);
                    return s;
                });
        score.setOrganizationId(metric.getOrganizationId());
        score.setScoringConfigId(config.getId());
        score.setOverallScore(bd(c.overall));
        score.setCarbonScore(bd(c.sub.get("carbon")));
        score.setEnergyScore(bd(c.sub.get("energy")));
        score.setWaterScore(bd(c.sub.get("water")));
        score.setWasteScore(bd(c.sub.get("waste")));
        score.setTransportScore(bd(c.sub.get("transport")));
        score.setScoreBreakdown(c.breakdown);
        score.setComputedAt(Instant.now());

        // previous month with a score → previous_score, delta, drivers
        var prevOpt = scoreRepository.findFirstByUserIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(userId, period);
        if (prevOpt.isPresent()) {
            EnvironmentalScore prev = prevOpt.get();
            score.setPreviousScore(prev.getOverallScore());
            score.setDelta(bd(c.overall - prev.getOverallScore().doubleValue()));
            score.setDrivers(explainer.drivers(
                    c.sub, subOf(prev), c.rawMetric, rawMetricOf(prev), config.getWeights()));
        } else {
            score.setPreviousScore(null);
            score.setDelta(null);
            score.setDrivers(List.of());
        }

        EnvironmentalScore saved = scoreRepository.save(score);
        log.debug("Scored user {} {}: overall={} (carbon={} energy={} water={} waste={} transport={})",
                userId, period, saved.getOverallScore(), saved.getCarbonScore(), saved.getEnergyScore(),
                saved.getWaterScore(), saved.getWasteScore(), saved.getTransportScore());
        return saved;
    }

    @Transactional
    public int recomputeRange(UUID userId, LocalDate from, LocalDate to) {
        List<MonthlyMetric> metrics = metricRepository
                .findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(userId, from, to);
        int n = 0;
        for (MonthlyMetric m : metrics) {
            recomputeMonth(userId, m.getPeriodMonth());
            n++;
        }
        return n;
    }

    @Transactional(readOnly = true)
    public ScoringConfig activeConfig() {
        return configRepository.findByActiveTrue()
                .orElseThrow(() -> new ApiException(com.ieia.common.exception.ErrorCode.INTERNAL,
                        "No active scoring configuration"));
    }

    /** Score a hypothetical metric bundle for a user using the active methodology
     *  (used by the what-if scenario engine — no persistence). */
    public record ScoredBundle(double overall, Map<String, Double> categoryScores) {}

    @Transactional(readOnly = true)
    public ScoredBundle scoreHypothetical(UUID userId, double co2e, double energy, double water,
                                          double wasteLandfilled, double transportCo2e, double recycledFraction) {
        User user = userRepository.findByIdWithOrganization(userId).orElseThrow();
        ScoringConfig config = activeConfig();
        MonthlyMetric m = new MonthlyMetric();
        m.setUserId(userId);
        m.setPeriodMonth(com.ieia.common.util.PeriodUtil.currentPeriod());
        m.setTotalCo2eKg(java.math.BigDecimal.valueOf(co2e));
        m.setTotalEnergyMj(java.math.BigDecimal.valueOf(energy));
        m.setTotalWaterLitre(java.math.BigDecimal.valueOf(water));
        m.setTotalWasteKg(java.math.BigDecimal.valueOf(wasteLandfilled));
        m.setRecycledFraction(java.math.BigDecimal.valueOf(Math.max(0, Math.min(1, recycledFraction))));
        m.setCo2eByCategory(new LinkedHashMap<>(Map.of("TRANSPORT", java.math.BigDecimal.valueOf(transportCo2e))));
        Computation c = compute(m, user, config);
        return new ScoredBundle(c.overall(), c.sub());
    }

    // ---- pure computation ------------------------------------------------

    record Computation(double overall, Map<String, Double> sub, Map<String, Double> rawMetric,
                       Map<String, Object> breakdown) {}

    Computation compute(MonthlyMetric metric, User user, ScoringConfig config) {
        boolean isOrg = user.getOrganization() != null
                && user.getOrganization().getHeadcount() != null
                && user.getOrganization().getHeadcount() > 0;
        String scope = isOrg ? "ORGANIZATION" : "INDIVIDUAL";
        String region = isOrg ? nvl(user.getOrganization().getRegion(), "GLOBAL")
                : nvl(user.getRegion(), "GLOBAL");
        double divisor = isOrg ? user.getOrganization().getHeadcount() : 1.0;

        double co2e = metric.getTotalCo2eKg().doubleValue() / divisor;
        double energy = metric.getTotalEnergyMj().doubleValue() / divisor;
        double water = metric.getTotalWaterLitre().doubleValue() / divisor;
        double wasteLandfilled = metric.getTotalWasteKg().doubleValue() / divisor;
        double transportCo2e = numberFrom(metric.getCo2eByCategory().get("TRANSPORT")) / divisor;
        double recycledFraction = metric.getRecycledFraction().doubleValue();

        Map<String, Double> raw = new LinkedHashMap<>();
        raw.put("carbon", co2e);
        raw.put("energy", energy);
        raw.put("water", water);
        raw.put("waste", wasteLandfilled);
        raw.put("transport", transportCo2e);

        int roundDp = intParam(config, "round_dp", 2);
        double recyclingBonusMax = doubleParam(config, "recycling_bonus_max", 8.0);
        int insufficientMonths = intParam(config, "insufficient_data_months", 2);

        Map<String, Double> sub = new LinkedHashMap<>();
        Map<String, Object> catBreakdown = new LinkedHashMap<>();

        Map<String, String> metricKey = Map.of("carbon", "CO2E", "energy", "ENERGY",
                "water", "WATER", "waste", "WASTE", "transport", "TRANSPORT");

        for (String cat : CATEGORIES) {
            double[] gp = baseline(config, scope, region, metricKey.get(cat));
            double m = raw.get(cat);
            double base = ScoringMath.subScore(m, gp[0], gp[1]);
            double bonus = 0.0;
            if (cat.equals("waste")) {
                bonus = ScoringMath.round(recyclingBonusMax * recycledFraction, 2);
                base = ScoringMath.clamp(base + bonus, 0, 100);
            }
            double s = ScoringMath.round(base, roundDp);
            sub.put(cat, s);

            Map<String, Object> cb = new LinkedHashMap<>();
            cb.put("score", s);
            cb.put("rawMetric", ScoringMath.round(m, 4));
            cb.put("baselineGood", gp[0]);
            cb.put("baselinePoor", gp[1]);
            cb.put("weight", config.getWeights().getOrDefault(cat, 0.0));
            cb.put("weightedContribution",
                    ScoringMath.round(config.getWeights().getOrDefault(cat, 0.0) * s, roundDp));
            if (cat.equals("waste")) cb.put("recyclingBonus", bonus);
            catBreakdown.put(cat, cb);
        }

        double overall = 0.0;
        for (String cat : CATEGORIES) {
            overall += config.getWeights().getOrDefault(cat, 0.0) * sub.get(cat);
        }
        overall = ScoringMath.round(ScoringMath.clamp(overall, 0, 100), roundDp);

        long monthsOfHistory = metricRepository.findByUserIdOrderByPeriodMonth(user.getId()).size();

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("methodologyVersion", config.getVersion());
        breakdown.put("scope", scope);
        breakdown.put("region", region);
        breakdown.put("perEmployee", isOrg);
        breakdown.put("headcount", isOrg ? user.getOrganization().getHeadcount() : null);
        breakdown.put("recycledFraction", ScoringMath.round(recycledFraction, 4));
        breakdown.put("weights", config.getWeights());
        breakdown.put("categories", catBreakdown);
        breakdown.put("rawMetrics", roundMap(raw, 4));
        breakdown.put("overall", overall);
        breakdown.put("provisional", monthsOfHistory < insufficientMonths);
        breakdown.put("monthsOfHistory", monthsOfHistory);

        return new Computation(overall, sub, raw, breakdown);
    }

    // ---- helpers ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private double[] baseline(ScoringConfig config, String scope, String region, String metric) {
        Object scoped = config.getBaselines().get(scope);
        if (!(scoped instanceof Map<?, ?> byRegion)) {
            throw configError(scope, region, metric);
        }
        Object regionNode = ((Map<String, Object>) byRegion).getOrDefault(region,
                ((Map<String, Object>) byRegion).get("GLOBAL"));
        if (!(regionNode instanceof Map<?, ?> byMetric)) {
            throw configError(scope, region, metric);
        }
        Object metricNode = ((Map<String, Object>) byMetric).get(metric);
        if (!(metricNode instanceof Map<?, ?> gp)) {
            throw configError(scope, region, metric);
        }
        double good = numberFrom(((Map<String, Object>) gp).get("good"));
        double poor = numberFrom(((Map<String, Object>) gp).get("poor"));
        return new double[]{good, poor};
    }

    private ApiException configError(String scope, String region, String metric) {
        return new ApiException(com.ieia.common.exception.ErrorCode.INTERNAL,
                "Scoring config missing baseline for " + scope + "/" + region + "/" + metric);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> subOf(EnvironmentalScore prev) {
        Object cats = prev.getScoreBreakdown().get("categories");
        Map<String, Double> out = new LinkedHashMap<>();
        if (cats instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).forEach((k, v) -> {
                if (v instanceof Map<?, ?> cb) {
                    out.put(k, numberFrom(((Map<String, Object>) cb).get("score")));
                }
            });
        }
        if (out.isEmpty()) {
            out.put("carbon", prev.getCarbonScore().doubleValue());
            out.put("energy", prev.getEnergyScore().doubleValue());
            out.put("water", prev.getWaterScore().doubleValue());
            out.put("waste", prev.getWasteScore().doubleValue());
            out.put("transport", prev.getTransportScore().doubleValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> rawMetricOf(EnvironmentalScore prev) {
        Object raw = prev.getScoreBreakdown().get("rawMetrics");
        Map<String, Double> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).forEach((k, v) -> out.put(k, numberFrom(v)));
        }
        return out;
    }

    private static Map<String, Double> roundMap(Map<String, Double> in, int dp) {
        Map<String, Double> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, ScoringMath.round(v, dp)));
        return out;
    }

    private static double numberFrom(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static int intParam(ScoringConfig config, String key, int def) {
        Object v = config.getScaleParams().get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    private static double doubleParam(ScoringConfig config, String key, double def) {
        Object v = config.getScaleParams().get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private static String nvl(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
