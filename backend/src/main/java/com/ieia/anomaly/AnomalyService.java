package com.ieia.anomaly;

import com.ieia.activity.ActivityType;
import com.ieia.activity.ActivityTypeRepository;
import com.ieia.anomaly.dto.AnomalyResponse;
import com.ieia.anomaly.dto.ScanResult;
import com.ieia.common.dto.PageResponse;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.emissionfactor.EmissionFactor;
import com.ieia.emissionfactor.FactorResolver;
import com.ieia.emissionfactor.ImpactType;
import com.ieia.integration.ml.MlServiceClient;
import com.ieia.integration.ml.dto.MlAnomalyRequest;
import com.ieia.integration.ml.dto.MlAnomalyResponse;
import com.ieia.metrics.SeriesAssembler;
import com.ieia.metrics.dto.TimeSeriesPoint;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);
    private static final List<String> METHODS = List.of("ZSCORE", "IQR", "STL_RESIDUAL", "ISOLATION_FOREST");

    private final SeriesAssembler seriesAssembler;
    private final MlServiceClient ml;
    private final AnomalyRepository repository;
    private final FactorResolver factorResolver;
    private final ActivityTypeRepository activityTypeRepository;
    private final UserRepository userRepository;

    public AnomalyService(SeriesAssembler seriesAssembler,
                          MlServiceClient ml,
                          AnomalyRepository repository,
                          FactorResolver factorResolver,
                          ActivityTypeRepository activityTypeRepository,
                          UserRepository userRepository) {
        this.seriesAssembler = seriesAssembler;
        this.ml = ml;
        this.repository = repository;
        this.factorResolver = factorResolver;
        this.activityTypeRepository = activityTypeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ScanResult scan(UUID userId, LocalDate from, LocalDate to, String sensitivity) {
        LocalDate start = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate end = to != null ? to : PeriodUtil.currentPeriod();
        Map<String, List<TimeSeriesPoint>> series = seriesAssembler.anomalySeries(userId, start, end);
        if (series.isEmpty()) {
            return new ScanResult(0, 0, 0, List.of(), Map.of("note", "no series with enough data to scan"));
        }

        Map<String, List<MlAnomalyRequest.Point>> payload = new java.util.LinkedHashMap<>();
        series.forEach((metric, pts) -> payload.put(metric, pts.stream()
                .map(p -> new MlAnomalyRequest.Point(p.period(), p.value() == null ? 0.0 : p.value().doubleValue()))
                .toList()));

        MlAnomalyResponse res = ml.detectAnomalies(new MlAnomalyRequest(
                payload, METHODS, sensitivity == null ? "MEDIUM" : sensitivity.toUpperCase()));

        User user = userRepository.findByIdWithOrganization(userId).orElseThrow();
        String region = user.getOrganization() != null && user.getOrganization().getRegion() != null
                ? user.getOrganization().getRegion() : (user.getRegion() != null ? user.getRegion() : "GLOBAL");

        int opened = 0, updated = 0;
        List<AnomalyResponse> out = new ArrayList<>();
        for (MlAnomalyResponse.Item item : res.anomalies()) {
            LocalDate period = PeriodUtil.parse(item.period());
            var existing = repository.findByUserIdAndPeriodMonthAndMetric(userId, period, item.metric());

            Anomaly a = existing.orElseGet(Anomaly::new);
            if (existing.isPresent()
                    && List.of("RESOLVED", "DISMISSED").contains(a.getStatus())) {
                out.add(AnomalyResponse.summary(a));
                continue;   // don't reopen a reviewed anomaly
            }
            boolean isNew = a.getId() == null;

            a.setUserId(userId);
            a.setPeriodMonth(period);
            a.setMetric(item.metric());
            a.setMethod(item.method());
            a.setDirection(item.direction());
            a.setSeverity(item.severity());
            a.setObservedValue(bd(item.observed()));
            a.setBaselineValue(bd(item.baseline()));
            a.setDeviationAbs(bd(item.deviationAbs()));
            a.setDeviationPct(BigDecimal.valueOf(item.deviationPct()).setScale(2, RoundingMode.HALF_UP));
            a.setScore(BigDecimal.valueOf(item.score()).setScale(4, RoundingMode.HALF_UP));
            a.setEstimatedCo2eImpactKg(estimateCo2e(item, region, period));
            a.setExplanation(explain(item));
            if (isNew) a.setStatus("OPEN");
            repository.save(a);

            if (isNew) opened++; else updated++;
            out.add(AnomalyResponse.summary(a));
        }

        log.info("Anomaly scan user {}: {} flagged ({} new, {} updated)", userId, res.anomalies().size(), opened, updated);
        return new ScanResult(res.anomalies().size(), opened, updated, out, res.summary());
    }

    @Transactional(readOnly = true)
    public PageResponse<AnomalyResponse> list(UUID userId, String status, LocalDate from, LocalDate to,
                                              String metric, String severity, Pageable pageable) {
        Specification<Anomaly> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status.toUpperCase()));
        if (metric != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("metric"), metric.toUpperCase()));
        if (severity != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("severity"), severity.toUpperCase()));
        if (from != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("periodMonth"), from));
        if (to != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("periodMonth"), to));

        Page<Anomaly> page = repository.findAll(spec, pageable);
        return PageResponse.from(page, AnomalyResponse::summary);
    }

    @Transactional(readOnly = true)
    public AnomalyResponse get(UUID userId, UUID id) {
        Anomaly a = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Anomaly"));
        // context window: 6 months of the affected metric around the anomaly
        LocalDate winFrom = a.getPeriodMonth().minusMonths(6);
        LocalDate winTo = a.getPeriodMonth().plusMonths(1);
        Map<String, List<TimeSeriesPoint>> series = seriesAssembler.anomalySeries(userId, winFrom, winTo);
        List<TimeSeriesPoint> metricSeries = series.getOrDefault(a.getMetric(), List.of());
        var ctx = new AnomalyResponse.Context(metricSeries,
                List.of(PeriodUtil.format(winFrom), PeriodUtil.format(a.getPeriodMonth().minusMonths(1))));
        return AnomalyResponse.from(a, ctx);
    }

    @Transactional
    public AnomalyResponse review(UUID userId, UUID id, String status, String note) {
        Anomaly a = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Anomaly"));
        String s = status == null ? "" : status.toUpperCase();
        if (!List.of("ACKNOWLEDGED", "RESOLVED", "DISMISSED", "OPEN").contains(s)) {
            throw ApiException.validation("status must be ACKNOWLEDGED, RESOLVED, DISMISSED or OPEN");
        }
        a.setStatus(s);
        a.setReviewedBy(userId);
        a.setReviewedAt(Instant.now());
        if (note != null && !note.isBlank()) {
            a.setExplanation(a.getExplanation() + "\n\nReviewer note: " + note.trim());
        }
        return AnomalyResponse.summary(a);
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> openForUser(UUID userId, int limit) {
        return repository.findByUserIdAndStatusOrderByPeriodMonthDesc(userId, "OPEN").stream()
                .limit(limit)
                .map(AnomalyResponse::summary)
                .toList();
    }

    // ---- helpers ------------------------------------------------------

    private BigDecimal estimateCo2e(MlAnomalyResponse.Item item, String region, LocalDate period) {
        String m = item.metric();
        double devAbs = item.deviationAbs();
        if ("CO2E".equals(m) || "TRANSPORT".equals(m)) {
            return bd(devAbs);
        }
        String activityCode = switch (m) {
            case "ELECTRICITY" -> "electricity_grid";
            case "FUEL" -> "petrol";
            default -> null;
        };
        if (activityCode == null) return null;
        ActivityType at = activityTypeRepository.findByCode(activityCode).orElse(null);
        if (at == null) return null;
        return factorResolver.resolve(at, ImpactType.CO2E, region, period)
                .map(EmissionFactor::getValue)
                .map(f -> BigDecimal.valueOf(devAbs).multiply(f).setScale(4, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private static String explain(MlAnomalyResponse.Item item) {
        String verb = "SPIKE".equals(item.direction()) ? "increased" : "decreased";
        String impact = "";
        return String.format(
                "%s %s by %.1f%% vs the historical baseline (%.2f -> %.2f). Flagged by %s (%d method(s) agreeing).%s",
                titleCase(item.metric()), verb, Math.abs(item.deviationPct()),
                item.baseline(), item.observed(), item.method(), item.methodsAgreeing(), impact);
    }

    private static String titleCase(String s) {
        return s == null || s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
