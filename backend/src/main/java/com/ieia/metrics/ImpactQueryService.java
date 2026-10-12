package com.ieia.metrics;

import com.ieia.calculation.CalculatedImpactRepository;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.dto.ContributorsResponse;
import com.ieia.metrics.dto.ExplainResponse;
import com.ieia.metrics.dto.ImpactResponse;
import com.ieia.metrics.dto.TimeSeriesPoint;
import com.ieia.metrics.dto.TrendsResponse;
import com.ieia.scoring.EnvironmentalScore;
import com.ieia.scoring.EnvironmentalScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class ImpactQueryService {

    private final MonthlyMetricRepository metricRepository;
    private final EnvironmentalScoreRepository scoreRepository;
    private final CalculatedImpactRepository impactRepository;
    private final ImpactAnalyticsSupport support;

    public ImpactQueryService(MonthlyMetricRepository metricRepository,
                              EnvironmentalScoreRepository scoreRepository,
                              CalculatedImpactRepository impactRepository,
                              ImpactAnalyticsSupport support) {
        this.metricRepository = metricRepository;
        this.scoreRepository = scoreRepository;
        this.impactRepository = impactRepository;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public ImpactResponse impact(UUID userId, LocalDate from, LocalDate to) {
        Window w = window(userId, from, to);
        List<MonthlyMetric> metrics = metricRepository
                .findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(userId, w.from(), w.to());

        BigDecimal co2e = sum(metrics, MonthlyMetric::getTotalCo2eKg);
        BigDecimal energy = sum(metrics, MonthlyMetric::getTotalEnergyMj);
        BigDecimal water = sum(metrics, MonthlyMetric::getTotalWaterLitre);
        BigDecimal waste = sum(metrics, MonthlyMetric::getTotalWasteKg);
        int records = metrics.stream().mapToInt(MonthlyMetric::getRecordCount).sum();

        BigDecimal avgRecycled = metrics.isEmpty() ? BigDecimal.ZERO
                : metrics.stream().map(MonthlyMetric::getRecycledFraction)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(metrics.size()), 4, RoundingMode.HALF_UP);

        return new ImpactResponse(
                PeriodUtil.format(w.from()), PeriodUtil.format(w.to()), metrics.size(),
                new ImpactResponse.Totals(scale(co2e), scale(energy), scale(water), scale(waste), records),
                support.emissionsByCategory(metrics),
                support.emissionsByActivity(metrics, 20),
                support.wasteComposition(impactRepository.findDetailedForUserBetween(userId, w.from(), w.to())),
                avgRecycled);
    }

    @Transactional(readOnly = true)
    public TrendsResponse trends(UUID userId, LocalDate from, LocalDate to, List<String> metricsWanted) {
        Window w = window(userId, from, to);
        List<MonthlyMetric> metrics = metricRepository
                .findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(userId, w.from(), w.to());
        List<EnvironmentalScore> scores = scoreRepository
                .findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(userId, w.from(), w.to());

        Map<String, Function<MonthlyMetric, BigDecimal>> extractors = new LinkedHashMap<>();
        extractors.put("CO2E", MonthlyMetric::getTotalCo2eKg);
        extractors.put("ENERGY", MonthlyMetric::getTotalEnergyMj);
        extractors.put("WATER", MonthlyMetric::getTotalWaterLitre);
        extractors.put("WASTE", MonthlyMetric::getTotalWasteKg);

        List<String> keys = (metricsWanted == null || metricsWanted.isEmpty())
                ? new ArrayList<>(List.of("CO2E", "ENERGY", "WATER", "WASTE", "SCORE"))
                : metricsWanted.stream().map(String::toUpperCase).toList();

        Map<String, List<TimeSeriesPoint>> series = new LinkedHashMap<>();
        for (String key : keys) {
            if (extractors.containsKey(key)) {
                var f = extractors.get(key);
                series.put(key, metrics.stream()
                        .map(m -> new TimeSeriesPoint(PeriodUtil.format(m.getPeriodMonth()), f.apply(m)))
                        .toList());
            } else if (key.equals("SCORE")) {
                series.put("SCORE", scores.stream()
                        .map(s -> new TimeSeriesPoint(PeriodUtil.format(s.getPeriodMonth()), s.getOverallScore()))
                        .toList());
            }
        }
        return new TrendsResponse(PeriodUtil.format(w.from()), PeriodUtil.format(w.to()), series);
    }

    @Transactional(readOnly = true)
    public ContributorsResponse contributors(UUID userId, LocalDate period, String by, int limit) {
        LocalDate p = period != null ? period : latestPeriod(userId);
        MonthlyMetric metric = metricRepository.findByUserIdAndPeriodMonth(userId, p)
                .orElseThrow(() -> ApiException.notFound("No data for " + PeriodUtil.format(p)));
        boolean byCategory = "category".equalsIgnoreCase(by);
        BigDecimal total = metric.getCo2eByCategory().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ContributorsResponse.Item> items = new ArrayList<>();
        if (byCategory) {
            support.emissionsByCategory(List.of(metric)).stream().limit(limit).forEach(ce ->
                    items.add(new ContributorsResponse.Item(ce.category(), ce.category(), ce.category(),
                            ce.kg(), ce.pct())));
        } else {
            support.emissionsByActivity(List.of(metric), limit).forEach(ae ->
                    items.add(new ContributorsResponse.Item(ae.activityCode(), ae.name(), null, ae.kg(), ae.pct())));
        }
        return new ContributorsResponse(PeriodUtil.format(p), byCategory ? "category" : "activity",
                scale(total), items);
    }

    @Transactional(readOnly = true)
    public ExplainResponse explain(UUID userId, LocalDate requestedPeriod) {
        LocalDate period = requestedPeriod != null ? requestedPeriod : latestPeriod(userId);
        EnvironmentalScore score = scoreRepository.findByUserIdAndPeriodMonth(userId, period)
                .orElseThrow(() -> ApiException.notFound("No score for " + PeriodUtil.format(period)));
        var prevOpt = scoreRepository
                .findFirstByUserIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(userId, period);

        MonthlyMetric now = metricRepository.findByUserIdAndPeriodMonth(userId, period).orElse(null);
        MonthlyMetric prev = metricRepository
                .findFirstByUserIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(userId, period).orElse(null);

        List<ExplainResponse.MetricChange> changes = new ArrayList<>();
        if (now != null) {
            changes.add(metricChange("CO2E", now.getTotalCo2eKg(), prev == null ? null : prev.getTotalCo2eKg()));
            changes.add(metricChange("ENERGY", now.getTotalEnergyMj(), prev == null ? null : prev.getTotalEnergyMj()));
            changes.add(metricChange("WATER", now.getTotalWaterLitre(), prev == null ? null : prev.getTotalWaterLitre()));
            changes.add(metricChange("WASTE", now.getTotalWasteKg(), prev == null ? null : prev.getTotalWasteKg()));
        }

        String narrative = buildNarrative(score, prevOpt.map(EnvironmentalScore::getOverallScore).orElse(null));

        return new ExplainResponse(
                PeriodUtil.format(period),
                prevOpt.map(s -> PeriodUtil.format(s.getPeriodMonth())).orElse(null),
                score.getOverallScore(),
                score.getPreviousScore(),
                score.getDelta(),
                score.getDrivers(),
                changes,
                narrative);
    }

    // ---- helpers ------------------------------------------------------

    private LocalDate latestPeriod(UUID userId) {
        return metricRepository.findFirstByUserIdOrderByPeriodMonthDesc(userId)
                .map(MonthlyMetric::getPeriodMonth)
                .orElseThrow(() -> ApiException.notFound("No data yet — add some environmental records first"));
    }

    private record Window(LocalDate from, LocalDate to) {}

    private Window window(UUID userId, LocalDate from, LocalDate to) {
        if (from != null && to != null) return new Window(from, to);
        List<MonthlyMetric> all = metricRepository.findByUserIdOrderByPeriodMonth(userId);
        if (all.isEmpty()) throw ApiException.notFound("No data yet — add some environmental records first");
        return new Window(
                from != null ? from : all.get(0).getPeriodMonth(),
                to != null ? to : all.get(all.size() - 1).getPeriodMonth());
    }

    private ExplainResponse.MetricChange metricChange(String metric, BigDecimal now, BigDecimal prev) {
        return new ExplainResponse.MetricChange(metric, scale(now), prev == null ? null : scale(prev),
                ImpactAnalyticsSupport.deltaPct(now, prev));
    }

    private String buildNarrative(EnvironmentalScore score, BigDecimal prevOverall) {
        if (prevOverall == null || score.getDelta() == null) {
            return "This is the first scored month, so there is nothing to compare against yet.";
        }
        BigDecimal d = score.getDelta();
        String dir = d.signum() >= 0 ? "increased" : "decreased";
        StringBuilder sb = new StringBuilder("Your score ").append(dir).append(" by ")
                .append(d.abs().toPlainString()).append(" points because:");
        score.getDrivers().stream().limit(3).forEach(drv -> {
            Object cat = drv.get("category");
            Object pct = drv.get("changePct");
            Object direction = drv.get("direction");
            sb.append("\n• ").append(capitalize(String.valueOf(cat)))
              .append(" metric changed ").append(pct).append("% (")
              .append(String.valueOf(direction).toLowerCase()).append(")");
        });
        return sb.toString();
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal sum(List<MonthlyMetric> metrics, Function<MonthlyMetric, BigDecimal> f) {
        return metrics.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(4, RoundingMode.HALF_UP);
    }
}
