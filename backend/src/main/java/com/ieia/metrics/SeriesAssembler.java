package com.ieia.metrics;

import com.ieia.activity.ActivityCategory;
import com.ieia.calculation.UnitConverter;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.dto.TimeSeriesPoint;
import com.ieia.record.EnvironmentalRecord;
import com.ieia.record.EnvironmentalRecordRepository;
import com.ieia.scoring.EnvironmentalScore;
import com.ieia.scoring.EnvironmentalScoreRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a user's stored data into the monthly series the ML service consumes
 * (docs/05-ml-methodology.md §1.1). One place so predictions and anomaly scans
 * agree on how each metric is derived.
 */
@Component
public class SeriesAssembler {

    private final MonthlyMetricRepository metricRepository;
    private final EnvironmentalScoreRepository scoreRepository;
    private final EnvironmentalRecordRepository recordRepository;
    private final UnitConverter unitConverter;

    public SeriesAssembler(MonthlyMetricRepository metricRepository,
                           EnvironmentalScoreRepository scoreRepository,
                           EnvironmentalRecordRepository recordRepository,
                           UnitConverter unitConverter) {
        this.metricRepository = metricRepository;
        this.scoreRepository = scoreRepository;
        this.recordRepository = recordRepository;
        this.unitConverter = unitConverter;
    }

    /** Monthly series for a forecast target: CO2E | ENERGY | WATER | SCORE. */
    @Transactional(readOnly = true)
    public List<TimeSeriesPoint> forecastSeries(UUID userId, String target) {
        if ("SCORE".equalsIgnoreCase(target)) {
            List<EnvironmentalScore> scores = scoreRepository.findByUserIdOrderByPeriodMonth(userId);
            return scores.stream()
                    .map(s -> new TimeSeriesPoint(PeriodUtil.format(s.getPeriodMonth()), s.getOverallScore()))
                    .toList();
        }
        List<MonthlyMetric> metrics = metricRepository.findByUserIdOrderByPeriodMonth(userId);
        return metrics.stream()
                .map(m -> new TimeSeriesPoint(PeriodUtil.format(m.getPeriodMonth()), metricValue(m, target)))
                .toList();
    }

    /** Per-metric series bundle for an anomaly scan over an inclusive window. */
    @Transactional(readOnly = true)
    public Map<String, List<TimeSeriesPoint>> anomalySeries(UUID userId, LocalDate from, LocalDate to) {
        List<MonthlyMetric> metrics = metricRepository
                .findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(userId, from, to);

        Map<String, List<TimeSeriesPoint>> out = new LinkedHashMap<>();
        out.put("CO2E", series(metrics, m -> m.getTotalCo2eKg()));
        out.put("ENERGY", series(metrics, m -> m.getTotalEnergyMj()));
        out.put("WATER", series(metrics, m -> m.getTotalWaterLitre()));
        out.put("WASTE", series(metrics, m -> m.getTotalWasteKg()));
        out.put("TRANSPORT", series(metrics, m -> orZero(m.getCo2eByCategory().get("TRANSPORT"))));

        // ELECTRICITY (kWh) and FUEL (litre) come from the raw records.
        Map<LocalDate, BigDecimal> electricity = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> fuel = new LinkedHashMap<>();
        for (EnvironmentalRecord r : recordRepository.findAllForUserBetween(userId, from, to)) {
            ActivityCategory cat = r.getActivityType().getCategory();
            if (cat == ActivityCategory.ELECTRICITY) {
                electricity.merge(r.getPeriodMonth(), convert(r, "kWh"), BigDecimal::add);
            } else if (cat == ActivityCategory.FUEL) {
                fuel.merge(r.getPeriodMonth(), convert(r, "litre"), BigDecimal::add);
            }
        }
        out.put("ELECTRICITY", fromMap(metrics, electricity));
        out.put("FUEL", fromMap(metrics, fuel));

        // drop series that are entirely zero / empty — nothing to scan
        out.values().removeIf(pts -> pts.isEmpty()
                || pts.stream().allMatch(p -> p.value() == null || p.value().signum() == 0));
        return out;
    }

    // ---- helpers ------------------------------------------------------

    private static BigDecimal metricValue(MonthlyMetric m, String target) {
        return switch (target.toUpperCase()) {
            case "CO2E" -> m.getTotalCo2eKg();
            case "ENERGY" -> m.getTotalEnergyMj();
            case "WATER" -> m.getTotalWaterLitre();
            case "WASTE" -> m.getTotalWasteKg();
            default -> throw com.ieia.common.exception.ApiException.validation("Unknown forecast target: " + target);
        };
    }

    private BigDecimal convert(EnvironmentalRecord r, String toUnit) {
        try {
            return unitConverter.convert(r.getAmount(), r.getUnit(), toUnit);
        } catch (RuntimeException e) {
            return r.getAmount();
        }
    }

    private List<TimeSeriesPoint> series(List<MonthlyMetric> metrics,
                                         java.util.function.Function<MonthlyMetric, BigDecimal> f) {
        List<TimeSeriesPoint> out = new ArrayList<>();
        for (MonthlyMetric m : metrics) {
            out.add(new TimeSeriesPoint(PeriodUtil.format(m.getPeriodMonth()), f.apply(m)));
        }
        return out;
    }

    private List<TimeSeriesPoint> fromMap(List<MonthlyMetric> metrics, Map<LocalDate, BigDecimal> byMonth) {
        List<TimeSeriesPoint> out = new ArrayList<>();
        for (MonthlyMetric m : metrics) {
            out.add(new TimeSeriesPoint(PeriodUtil.format(m.getPeriodMonth()),
                    byMonth.getOrDefault(m.getPeriodMonth(), BigDecimal.ZERO)));
        }
        return out;
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
