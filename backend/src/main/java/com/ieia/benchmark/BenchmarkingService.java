package com.ieia.benchmark;

import com.ieia.benchmark.dto.BenchmarkComparison;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.scoring.EnvironmentalScore;
import com.ieia.scoring.EnvironmentalScoreRepository;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class BenchmarkingService {

    private static final String DISCLAIMER =
            "Benchmarks are configurable reference values for context, not official standards.";

    private final BenchmarkRepository benchmarkRepository;
    private final MonthlyMetricRepository metricRepository;
    private final EnvironmentalScoreRepository scoreRepository;
    private final UserRepository userRepository;

    public BenchmarkingService(BenchmarkRepository benchmarkRepository,
                               MonthlyMetricRepository metricRepository,
                               EnvironmentalScoreRepository scoreRepository,
                               UserRepository userRepository) {
        this.benchmarkRepository = benchmarkRepository;
        this.metricRepository = metricRepository;
        this.scoreRepository = scoreRepository;
        this.userRepository = userRepository;
    }

    private record Scope(String scope, String segment, String region, double divisor) {}

    private Scope scopeFor(User user) {
        boolean isOrg = user.getOrganization() != null
                && user.getOrganization().getHeadcount() != null
                && user.getOrganization().getHeadcount() > 0;
        if (isOrg) {
            String region = nvl(user.getOrganization().getRegion());
            return new Scope("ORGANIZATION", "OFFICE_PER_EMPLOYEE", region,
                    user.getOrganization().getHeadcount());
        }
        String region = nvl(user.getRegion());
        String segment = "IN".equalsIgnoreCase(region) ? "IN_URBAN_HOUSEHOLD" : "GLOBAL_AVG";
        return new Scope("INDIVIDUAL", segment, region, 1.0);
    }

    @Transactional(readOnly = true)
    public List<Benchmark> activeSet(UUID userId) {
        User user = userRepository.findByIdWithOrganization(userId).orElseThrow();
        Scope s = scopeFor(user);
        return benchmarkRepository.findByScopeAndSegmentAndActiveTrue(s.scope(), s.segment());
    }

    @Transactional(readOnly = true)
    public BenchmarkComparison comparison(UUID userId, LocalDate requestedPeriod) {
        User user = userRepository.findByIdWithOrganization(userId).orElseThrow();
        Scope s = scopeFor(user);

        LocalDate period = requestedPeriod != null ? requestedPeriod
                : metricRepository.findFirstByUserIdOrderByPeriodMonthDesc(userId)
                    .map(MonthlyMetric::getPeriodMonth)
                    .orElseThrow(() -> ApiException.notFound("No data yet — add environmental records first"));

        MonthlyMetric now = metricRepository.findByUserIdAndPeriodMonth(userId, period)
                .orElseThrow(() -> ApiException.notFound("No data for " + PeriodUtil.format(period)));
        MonthlyMetric prevMonth = metricRepository
                .findByUserIdAndPeriodMonth(userId, period.minusMonths(1)).orElse(null);
        MonthlyMetric prevYear = metricRepository
                .findByUserIdAndPeriodMonth(userId, period.minusMonths(12)).orElse(null);

        EnvironmentalScore scoreNow = scoreRepository.findByUserIdAndPeriodMonth(userId, period).orElse(null);
        EnvironmentalScore scorePrevMonth = scoreRepository
                .findByUserIdAndPeriodMonth(userId, period.minusMonths(1)).orElse(null);
        EnvironmentalScore scorePrevYear = scoreRepository
                .findByUserIdAndPeriodMonth(userId, period.minusMonths(12)).orElse(null);

        // ---- score comparison ----
        BigDecimal scoreBenchmark = resolveValue(s, "SCORE");
        BenchmarkComparison.ScoreComparison score = new BenchmarkComparison.ScoreComparison(
                scoreNow == null ? null : scoreNow.getOverallScore(),
                scorePrevMonth == null ? null : scorePrevMonth.getOverallScore(),
                delta(scoreNow, scorePrevMonth),
                scorePrevYear == null ? null : scorePrevYear.getOverallScore(),
                delta(scoreNow, scorePrevYear),
                scoreBenchmark,
                scoreNow == null ? null : rate(s, "SCORE", scoreNow.getOverallScore().doubleValue()));

        // ---- category comparison ----
        List<BenchmarkComparison.CategoryComparison> categories = new ArrayList<>();
        categories.add(categoryComparison(s, "CO2E", "kgCO2e", now.getTotalCo2eKg()));
        categories.add(categoryComparison(s, "ENERGY", "MJ", now.getTotalEnergyMj()));
        categories.add(categoryComparison(s, "WATER", "litre", now.getTotalWaterLitre()));
        categories.add(categoryComparison(s, "WASTE", "kg", now.getTotalWasteKg()));

        BenchmarkComparison.PctChanges pop = pctChanges(now, prevMonth);
        BenchmarkComparison.PctChanges yoy = prevYear == null ? null : pctChanges(now, prevYear);

        return new BenchmarkComparison(
                PeriodUtil.format(period), s.scope(), s.segment(), s.region(),
                score, categories, pop, yoy, DISCLAIMER);
    }

    // ---- helpers -----------------------------------------------------

    private BenchmarkComparison.CategoryComparison categoryComparison(Scope s, String metric, String unit,
                                                                     BigDecimal total) {
        double perScope = total.doubleValue() / s.divisor();
        BigDecimal value = BigDecimal.valueOf(perScope).setScale(4, RoundingMode.HALF_UP);
        return new BenchmarkComparison.CategoryComparison(
                metric, value, unit, resolveValue(s, metric), rate(s, metric, perScope));
    }

    private BigDecimal resolveValue(Scope s, String metric) {
        List<Benchmark> found = benchmarkRepository.resolve(s.scope(), s.segment(), metric, "MONTH", s.region());
        return found.isEmpty() ? null : found.get(0).getValue();
    }

    @SuppressWarnings("unchecked")
    private String rate(Scope s, String metric, double value) {
        List<Benchmark> found = benchmarkRepository.resolve(s.scope(), s.segment(), metric, "MONTH", s.region());
        if (found.isEmpty()) return null;
        Map<String, Object> bands = found.get(0).getRatingBands();
        for (Map.Entry<String, Object> band : bands.entrySet()) {
            if (!(band.getValue() instanceof List<?> range) || range.size() < 2) continue;
            Double lo = num(range.get(0));
            Double hi = num(range.get(1));
            boolean loOk = lo == null || value >= lo;
            boolean hiOk = hi == null || value < hi;
            if (loOk && hiOk) {
                return band.getKey().toUpperCase();
            }
        }
        return null;
    }

    private BenchmarkComparison.PctChanges pctChanges(MonthlyMetric now, MonthlyMetric baseline) {
        if (baseline == null) return null;
        return new BenchmarkComparison.PctChanges(
                pct(now.getTotalCo2eKg(), baseline.getTotalCo2eKg()),
                pct(now.getTotalEnergyMj(), baseline.getTotalEnergyMj()),
                pct(now.getTotalWaterLitre(), baseline.getTotalWaterLitre()),
                pct(now.getTotalWasteKg(), baseline.getTotalWasteKg()));
    }

    private static BigDecimal pct(BigDecimal now, BigDecimal base) {
        if (base == null || base.signum() == 0) return null;
        return now.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal delta(EnvironmentalScore a, EnvironmentalScore b) {
        if (a == null || b == null) return null;
        return a.getOverallScore().subtract(b.getOverallScore()).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "GLOBAL" : s;
    }
}
