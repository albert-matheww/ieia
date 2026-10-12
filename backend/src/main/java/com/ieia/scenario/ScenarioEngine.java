package com.ieia.scenario;

import com.ieia.activity.ActivityCategory;
import com.ieia.activity.ActivityType;
import com.ieia.activity.ActivityTypeRepository;
import com.ieia.calculation.CalculatedImpact;
import com.ieia.calculation.CalculatedImpactRepository;
import com.ieia.calculation.UnitConverter;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.emissionfactor.EmissionFactor;
import com.ieia.emissionfactor.FactorResolver;
import com.ieia.emissionfactor.ImpactType;
import com.ieia.record.EnvironmentalRecord;
import com.ieia.record.EnvironmentalRecordRepository;
import com.ieia.scenario.dto.MetricBundle;
import com.ieia.scenario.dto.ScenarioRequest;
import com.ieia.scenario.dto.ScenarioResult;
import com.ieia.scoring.ScoringService;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic what-if maths (docs/03-api-specification.md §10). It builds a
 * per-month-averaged baseline from the user's real {@code calculated_impacts}
 * over a window, applies typed levers using the real emission factors, recomputes
 * every impact dimension and the score with the same {@code v1} methodology, and
 * emits the assumptions it made. No ML, no randomness.
 */
@Component
public class ScenarioEngine {

    private static final Set<String> DIVERTING = Set.of("waste_recycled", "waste_organic_compost");

    private final CalculatedImpactRepository impactRepository;
    private final EnvironmentalRecordRepository recordRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final FactorResolver factorResolver;
    private final UnitConverter unitConverter;
    private final UserRepository userRepository;
    private final ScoringService scoringService;

    public ScenarioEngine(CalculatedImpactRepository impactRepository,
                          EnvironmentalRecordRepository recordRepository,
                          ActivityTypeRepository activityTypeRepository,
                          FactorResolver factorResolver,
                          UnitConverter unitConverter,
                          UserRepository userRepository,
                          ScoringService scoringService) {
        this.impactRepository = impactRepository;
        this.recordRepository = recordRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.factorResolver = factorResolver;
        this.unitConverter = unitConverter;
        this.userRepository = userRepository;
        this.scoringService = scoringService;
    }

    /** Working, mutable per-month-averaged state the levers transform. */
    private static final class State {
        // impactType -> (category -> avg monthly value)
        final Map<ImpactType, Map<String, Double>> byCategory = new EnumMap<>(ImpactType.class);
        // impactType -> (activityCode -> avg monthly value)
        final Map<ImpactType, Map<String, Double>> byActivity = new EnumMap<>(ImpactType.class);
        // activityCode -> effective CO2E factor value used in the window
        final Map<String, Double> co2eFactor = new LinkedHashMap<>();
        double wasteGeneratedKg;
        double recycledFraction;
    }

    @Transactional(readOnly = true)
    public ScenarioResult simulate(UUID userId, ScenarioRequest req) {
        LocalDate from = PeriodUtil.parse(req.basePeriodFrom());
        LocalDate to = PeriodUtil.parse(req.basePeriodTo());
        if (from.isAfter(to)) {
            throw ApiException.validation("basePeriodFrom must not be after basePeriodTo");
        }

        List<CalculatedImpact> impacts = impactRepository.findDetailedForUserBetween(userId, from, to);
        if (impacts.isEmpty()) {
            throw ApiException.unprocessable(
                    "No data in " + req.basePeriodFrom() + ".." + req.basePeriodTo() + " to build a baseline");
        }
        long months = impacts.stream().map(CalculatedImpact::getPeriodMonth).distinct().count();

        User user = userRepository.findByIdWithOrganization(userId).orElseThrow();
        String region = user.getOrganization() != null && user.getOrganization().getRegion() != null
                ? user.getOrganization().getRegion() : (user.getRegion() != null ? user.getRegion() : "GLOBAL");

        Map<String, String> activityToCategory = new LinkedHashMap<>();
        for (ActivityType at : activityTypeRepository.findAll()) {
            activityToCategory.put(at.getCode(), at.getCategory().name());
        }

        State base = buildBaseline(userId, from, to, impacts, months, activityToCategory);
        MetricBundle baselineBundle = toBundle(userId, base);

        State projected = deepCopy(base);
        List<String> assumptions = new ArrayList<>();
        for (ScenarioRequest.Adjustment adj : req.adjustments()) {
            applyAdjustment(projected, adj, activityToCategory, region, assumptions);
        }
        MetricBundle projectedBundle = toBundle(userId, projected);

        List<ScenarioResult.CategoryDelta> byCat = new ArrayList<>();
        Map<String, Double> baseCat = base.byCategory.getOrDefault(ImpactType.CO2E, Map.of());
        Map<String, Double> projCat = projected.byCategory.getOrDefault(ImpactType.CO2E, Map.of());
        for (String cat : union(baseCat.keySet(), projCat.keySet())) {
            double b = baseCat.getOrDefault(cat, 0.0);
            double p = projCat.getOrDefault(cat, 0.0);
            byCat.add(new ScenarioResult.CategoryDelta(cat, r4(b), r4(p), r4(p - b)));
        }
        byCat.sort((a, b) -> Double.compare(b.deltaCo2e().abs().doubleValue(), a.deltaCo2e().abs().doubleValue()));

        ScenarioResult.Deltas deltas = new ScenarioResult.Deltas(
                sub(projectedBundle.co2eKg(), baselineBundle.co2eKg()),
                sub(projectedBundle.energyMj(), baselineBundle.energyMj()),
                sub(projectedBundle.waterLitre(), baselineBundle.waterLitre()),
                sub(projectedBundle.wasteKg(), baselineBundle.wasteKg()),
                sub(projectedBundle.overallScore(), baselineBundle.overallScore()));

        List<String> notes = List.of(
                "Baseline = average month over " + req.basePeriodFrom() + ".." + req.basePeriodTo()
                        + " (" + months + " month(s) of data).",
                "Score uses the same v1 methodology as your real score; prediction intervals are not modelled.",
                "Deterministic arithmetic over your real data and the real emission factors.");

        return new ScenarioResult(
                req.basePeriodFrom(), req.basePeriodTo(), (int) months,
                baselineBundle, projectedBundle, deltas, byCat, assumptions, notes);
    }

    // ---- baseline construction -----------------------------------------

    private State buildBaseline(UUID userId, LocalDate from, LocalDate to, List<CalculatedImpact> impacts,
                                long months, Map<String, String> activityToCategory) {
        State s = new State();
        for (ImpactType t : ImpactType.values()) {
            s.byCategory.put(t, new LinkedHashMap<>());
            s.byActivity.put(t, new LinkedHashMap<>());
        }
        for (CalculatedImpact ci : impacts) {
            String code = ci.getRecord().getActivityType().getCode();
            String category = ci.getRecord().getActivityType().getCategory().name();
            double perMonth = ci.getValue().doubleValue() / months;
            s.byCategory.get(ci.getImpactType()).merge(category, perMonth, Double::sum);
            s.byActivity.get(ci.getImpactType()).merge(code, perMonth, Double::sum);
            if (ci.getImpactType() == ImpactType.CO2E && ci.getFactorValue() != null
                    && ci.getFactorValue().signum() > 0) {
                s.co2eFactor.putIfAbsent(code, ci.getFactorValue().doubleValue());
            }
        }

        // recycled fraction from the raw waste-category records over the window
        double generated = 0, diverted = 0;
        for (EnvironmentalRecord r : recordRepository.findAllForUserBetween(userId, from, to)) {
            if (r.getActivityType().getCategory() != ActivityCategory.WASTE) continue;
            double kg;
            try {
                kg = unitConverter.convert(r.getAmount(), r.getUnit(), "kg").doubleValue();
            } catch (RuntimeException e) {
                kg = r.getAmount().doubleValue();
            }
            generated += kg;
            if (DIVERTING.contains(r.getActivityType().getCode())) diverted += kg;
        }
        s.wasteGeneratedKg = generated / months;
        s.recycledFraction = generated > 0 ? diverted / generated : 0.0;
        return s;
    }

    // ---- levers -----------------------------------------------------

    private void applyAdjustment(State s, ScenarioRequest.Adjustment adj,
                                 Map<String, String> activityToCategory, String region,
                                 List<String> assumptions) {
        String changeType = adj.change();
        String targetType = adj.target().type();
        String targetValue = adj.target().value();
        double value = adj.value();

        switch (changeType) {
            case "PCT_REDUCTION", "PCT_INCREASE" -> {
                double f = "PCT_REDUCTION".equals(changeType) ? (1 - value / 100.0) : (1 + value / 100.0);
                f = Math.max(0.0, f);
                if ("CATEGORY".equals(targetType)) {
                    scaleCategory(s, targetValue.toUpperCase(), f);
                    assumptions.add(String.format("%s of the %s category scaled every impact dimension of that category by ×%.3f.",
                            changeType.equals("PCT_REDUCTION") ? "−" + value + "%" : "+" + value + "%", targetValue, f));
                } else {
                    scaleActivity(s, targetValue, f, activityToCategory);
                    assumptions.add(String.format("%s of activity '%s' scaled its impacts by ×%.3f.",
                            changeType.equals("PCT_REDUCTION") ? "−" + value + "%" : "+" + value + "%", targetValue, f));
                }
            }
            case "MODAL_SHIFT" -> modalShift(s, adj, activityToCategory, region, assumptions);
            case "ABSOLUTE" -> absolute(s, adj, activityToCategory, region, assumptions);
            case "RECYCLING_RATE" -> recyclingRate(s, value, region, assumptions);
            default -> throw ApiException.validation("Unknown change type " + changeType);
        }
    }

    private void scaleCategory(State s, String category, double f) {
        for (ImpactType t : ImpactType.values()) {
            s.byCategory.get(t).computeIfPresent(category, (k, v) -> v * f);
        }
    }

    private void scaleActivity(State s, String activityCode, double f, Map<String, String> activityToCategory) {
        String category = activityToCategory.get(activityCode);
        for (ImpactType t : ImpactType.values()) {
            Double before = s.byActivity.get(t).get(activityCode);
            if (before == null) continue;
            double after = before * f;
            s.byActivity.get(t).put(activityCode, after);
            if (category != null) {
                s.byCategory.get(t).merge(category, after - before, Double::sum);
            }
        }
    }

    private void modalShift(State s, ScenarioRequest.Adjustment adj, Map<String, String> activityToCategory,
                            String region, List<String> assumptions) {
        if (!"ACTIVITY_TYPE".equals(adj.target().type())) {
            throw ApiException.validation("MODAL_SHIFT requires target.type = ACTIVITY_TYPE (the source activity)");
        }
        String sourceCode = adj.target().value();
        String targetCode = adj.params() == null ? null : adj.params().toActivityCode();
        double sharePct = adj.params() != null && adj.params().sharePct() != null
                ? adj.params().sharePct() : adj.value();
        if (targetCode == null) {
            throw ApiException.validation("MODAL_SHIFT requires params.toActivityCode");
        }
        double frac = Math.max(0, Math.min(1, sharePct / 100.0));

        double sourceCo2eFactor = resolveFactor(sourceCode, ImpactType.CO2E, region,
                s.co2eFactor.get(sourceCode));
        double targetCo2eFactor = resolveFactor(targetCode, ImpactType.CO2E, region, null);
        if (sourceCo2eFactor <= 0 || targetCo2eFactor <= 0) {
            throw ApiException.unprocessable("Cannot model this modal shift — missing CO2E factor for "
                    + sourceCode + " or " + targetCode);
        }

        Double sourceCo2e = s.byActivity.get(ImpactType.CO2E).get(sourceCode);
        if (sourceCo2e == null || sourceCo2e <= 0) {
            throw ApiException.unprocessable("The baseline has no '" + sourceCode + "' activity to shift");
        }
        double shiftedCo2e = sourceCo2e * frac;
        double serviceAmount = shiftedCo2e / sourceCo2eFactor;              // e.g. km
        double addedCo2e = serviceAmount * targetCo2eFactor;

        adjustActivityBy(s, ImpactType.CO2E, sourceCode, -shiftedCo2e, activityToCategory);
        adjustActivityBy(s, ImpactType.CO2E, targetCode, addedCo2e, activityToCategory);

        // energy follows the same physical shift when both activities have an ENERGY factor
        double srcEnergyFactor = resolveFactor(sourceCode, ImpactType.ENERGY, region, null);
        double tgtEnergyFactor = resolveFactor(targetCode, ImpactType.ENERGY, region, null);
        if (srcEnergyFactor > 0) {
            adjustActivityBy(s, ImpactType.ENERGY, sourceCode, -serviceAmount * srcEnergyFactor, activityToCategory);
        }
        if (tgtEnergyFactor > 0) {
            adjustActivityBy(s, ImpactType.ENERGY, targetCode, serviceAmount * tgtEnergyFactor, activityToCategory);
        }

        assumptions.add(String.format(
                "Modal shift: %.0f%% of '%s' (%.1f kgCO2e/mo) moves to '%s'. "
                        + "Equivalent activity %.1f units × %.3f kgCO2e/unit → +%.1f kgCO2e; net %.1f kgCO2e/mo.",
                frac * 100, sourceCode, shiftedCo2e, targetCode, serviceAmount, targetCo2eFactor,
                addedCo2e, addedCo2e - shiftedCo2e));
    }

    private void absolute(State s, ScenarioRequest.Adjustment adj, Map<String, String> activityToCategory,
                          String region, List<String> assumptions) {
        double newValue = adj.value();
        if ("CATEGORY".equals(adj.target().type())) {
            String category = adj.target().value().toUpperCase();
            double oldCo2e = s.byCategory.get(ImpactType.CO2E).getOrDefault(category, 0.0);
            double ratio = oldCo2e > 0 ? newValue / oldCo2e : 0.0;
            for (ImpactType t : ImpactType.values()) {
                if (t == ImpactType.CO2E) {
                    s.byCategory.get(t).put(category, newValue);
                } else {
                    s.byCategory.get(t).computeIfPresent(category, (k, v) -> v * ratio);
                }
            }
            assumptions.add(String.format("Category %s CO2e set to %.1f kgCO2e/mo (×%.3f); other dimensions scaled in proportion.",
                    category, newValue, ratio));
        } else {
            String code = adj.target().value();
            double co2eFactor = resolveFactor(code, ImpactType.CO2E, region, s.co2eFactor.get(code));
            if (co2eFactor <= 0) {
                throw ApiException.unprocessable("No CO2E factor for '" + code + "' — cannot set an absolute amount");
            }
            String category = activityToCategory.get(code);
            for (ImpactType t : ImpactType.values()) {
                double factor = resolveFactor(code, t, region, t == ImpactType.CO2E ? co2eFactor : null);
                if (factor <= 0 && t != ImpactType.CO2E) continue;
                double before = s.byActivity.get(t).getOrDefault(code, 0.0);
                double after = newValue * (t == ImpactType.CO2E ? co2eFactor : factor);
                s.byActivity.get(t).put(code, after);
                if (category != null) s.byCategory.get(t).merge(category, after - before, Double::sum);
            }
            assumptions.add(String.format("Activity '%s' set to %.2f units/mo (default unit); impacts recomputed from factors.",
                    code, newValue));
        }
    }

    private void recyclingRate(State s, double targetPct, String region, List<String> assumptions) {
        double newFraction = Math.max(0, Math.min(1, targetPct / 100.0));
        double generated = s.wasteGeneratedKg;
        if (generated <= 0) {
            assumptions.add("RECYCLING_RATE had no effect — the baseline has no waste records.");
            return;
        }
        double newLandfilled = generated * (1 - newFraction);
        double landfillF = resolveFactor("waste_mixed", ImpactType.CO2E, region, 0.45);
        double recycleF = resolveFactor("waste_recycled", ImpactType.CO2E, region, 0.021);

        double newWasteCo2e = newLandfilled * landfillF + (generated - newLandfilled) * recycleF;
        s.byCategory.get(ImpactType.CO2E).put("WASTE", newWasteCo2e);
        s.byCategory.get(ImpactType.WASTE).put("WASTE", newLandfilled);
        s.recycledFraction = newFraction;

        assumptions.add(String.format(
                "Recycling rate set to %.0f%% of %.1f kg/mo generated waste: %.1f kg landfilled "
                        + "(×%.3f) + %.1f kg diverted (×%.3f) → %.1f kgCO2e/mo for the waste category.",
                newFraction * 100, generated, newLandfilled, landfillF,
                generated - newLandfilled, recycleF, newWasteCo2e));
    }

    // ---- helpers -----------------------------------------------------

    private void adjustActivityBy(State s, ImpactType t, String activityCode, double delta,
                                  Map<String, String> activityToCategory) {
        s.byActivity.get(t).merge(activityCode, delta, Double::sum);
        String category = activityToCategory.get(activityCode);
        if (category != null) {
            s.byCategory.get(t).merge(category, delta, Double::sum);
        }
    }

    private double resolveFactor(String activityCode, ImpactType type, String region, Double fallback) {
        return activityTypeRepository.findByCode(activityCode)
                .flatMap(at -> factorResolver.resolve(at, type, region, PeriodUtil.currentPeriod()))
                .map(EmissionFactor::getValue)
                .map(BigDecimal::doubleValue)
                .orElse(fallback == null ? 0.0 : fallback);
    }

    private MetricBundle toBundle(UUID userId, State s) {
        double co2e = clampSum(s.byCategory.get(ImpactType.CO2E));
        double energy = clampSum(s.byCategory.get(ImpactType.ENERGY));
        double water = clampSum(s.byCategory.get(ImpactType.WATER));
        double waste = clampSum(s.byCategory.get(ImpactType.WASTE));
        double transportCo2e = Math.max(0.0, s.byCategory.get(ImpactType.CO2E).getOrDefault("TRANSPORT", 0.0));

        ScoringService.ScoredBundle scored = scoringService.scoreHypothetical(
                userId, co2e, energy, water, waste, transportCo2e, s.recycledFraction);

        Map<String, BigDecimal> categoryScores = new LinkedHashMap<>();
        scored.categoryScores().forEach((k, v) -> categoryScores.put(k, bd2(v)));
        Map<String, BigDecimal> co2eByCategory = new LinkedHashMap<>();
        s.byCategory.get(ImpactType.CO2E).forEach((k, v) -> co2eByCategory.put(k, r4(Math.max(0, v))));

        return new MetricBundle(
                r4(co2e), r4(energy), r4(water), r4(waste),
                bd4(s.recycledFraction), bd2(scored.overall()),
                categoryScores, co2eByCategory);
    }

    private static State deepCopy(State src) {
        State d = new State();
        for (ImpactType t : ImpactType.values()) {
            d.byCategory.put(t, new LinkedHashMap<>(src.byCategory.getOrDefault(t, Map.of())));
            d.byActivity.put(t, new LinkedHashMap<>(src.byActivity.getOrDefault(t, Map.of())));
        }
        d.co2eFactor.putAll(src.co2eFactor);
        d.wasteGeneratedKg = src.wasteGeneratedKg;
        d.recycledFraction = src.recycledFraction;
        return d;
    }

    private static double clampSum(Map<String, Double> m) {
        return m.values().stream().mapToDouble(v -> Math.max(0.0, v)).sum();
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.LinkedHashSet<String> u = new java.util.LinkedHashSet<>(a);
        u.addAll(b);
        return u;
    }

    private static BigDecimal sub(BigDecimal a, BigDecimal b) {
        return a.subtract(b).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal r4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
