package com.ieia.metrics;

import com.ieia.anomaly.AnomalyService;
import com.ieia.calculation.CalculatedImpactRepository;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.dto.DashboardResponse;
import com.ieia.metrics.dto.DashboardResponse.CategoryScores;
import com.ieia.metrics.dto.DashboardResponse.Charts;
import com.ieia.metrics.dto.DashboardResponse.DataQuality;
import com.ieia.metrics.dto.DashboardResponse.Delta;
import com.ieia.metrics.dto.DashboardResponse.Summary;
import com.ieia.metrics.dto.TimeSeriesPoint;
import com.ieia.prediction.Prediction;
import com.ieia.prediction.PredictionRepository;
import com.ieia.scoring.EnvironmentalScore;
import com.ieia.scoring.EnvironmentalScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class DashboardService {

    private final MonthlyMetricRepository metricRepository;
    private final EnvironmentalScoreRepository scoreRepository;
    private final CalculatedImpactRepository impactRepository;
    private final ImpactAnalyticsSupport support;
    private final AnomalyService anomalyService;
    private final PredictionRepository predictionRepository;

    public DashboardService(MonthlyMetricRepository metricRepository,
                            EnvironmentalScoreRepository scoreRepository,
                            CalculatedImpactRepository impactRepository,
                            ImpactAnalyticsSupport support,
                            AnomalyService anomalyService,
                            PredictionRepository predictionRepository) {
        this.metricRepository = metricRepository;
        this.scoreRepository = scoreRepository;
        this.impactRepository = impactRepository;
        this.support = support;
        this.anomalyService = anomalyService;
        this.predictionRepository = predictionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId, LocalDate requestedPeriod) {
        List<MonthlyMetric> allMetrics = metricRepository.findByUserIdOrderByPeriodMonth(userId);
        if (allMetrics.isEmpty()) {
            throw ApiException.notFound("Dashboard data — add some environmental records first");
        }
        LocalDate period = requestedPeriod != null ? requestedPeriod
                : allMetrics.get(allMetrics.size() - 1).getPeriodMonth();

        MonthlyMetric metric = metricRepository.findByUserIdAndPeriodMonth(userId, period)
                .orElseThrow(() -> ApiException.notFound("No data for " + PeriodUtil.format(period)));
        MonthlyMetric prev = metricRepository
                .findFirstByUserIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(userId, period)
                .orElse(null);

        EnvironmentalScore score = scoreRepository.findByUserIdAndPeriodMonth(userId, period).orElse(null);
        EnvironmentalScore prevScore = scoreRepository
                .findFirstByUserIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(userId, period).orElse(null);

        Summary summary = new Summary(
                scoreDelta(score, prevScore),
                delta(metric.getTotalCo2eKg(), prev, MonthlyMetric::getTotalCo2eKg),
                delta(metric.getTotalEnergyMj(), prev, MonthlyMetric::getTotalEnergyMj),
                delta(metric.getTotalWaterLitre(), prev, MonthlyMetric::getTotalWaterLitre),
                delta(metric.getTotalWasteKg(), prev, MonthlyMetric::getTotalWasteKg),
                metric.getRecycledFraction());

        CategoryScores categoryScores = score == null ? new CategoryScores(null, null, null, null, null)
                : new CategoryScores(score.getCarbonScore(), score.getEnergyScore(), score.getWaterScore(),
                        score.getWasteScore(), score.getTransportScore());

        List<MonthlyMetric> single = List.of(metric);
        Charts charts = new Charts(
                series(allMetrics, MonthlyMetric::getTotalCo2eKg),
                series(allMetrics, MonthlyMetric::getTotalEnergyMj),
                series(allMetrics, MonthlyMetric::getTotalWaterLitre),
                series(allMetrics, MonthlyMetric::getTotalWasteKg),
                scoreSeries(scoreRepository.findByUserIdOrderByPeriodMonth(userId)),
                support.wasteComposition(impactRepository.findDetailedForUserMonth(userId, period)),
                support.emissionsByCategory(single),
                support.emissionsByActivity(single, 12));

        DataQuality dq = new DataQuality(
                allMetrics.size(),
                metric.getRecordCount(),
                hasGaps(allMetrics));

        Prediction latestPrediction = predictionRepository
                .findFirstByUserIdAndTargetMetricOrderByCreatedAtDesc(userId, "CO2E").orElse(null);
        Map<String, Object> predictionSummary = null;
        if (latestPrediction != null) {
            predictionSummary = new LinkedHashMap<>();
            predictionSummary.put("metric", latestPrediction.getTargetMetric());
            predictionSummary.put("horizonMonth", PeriodUtil.format(latestPrediction.getHorizonMonth()));
            predictionSummary.put("currentValue", latestPrediction.getCurrentValue());
            predictionSummary.put("predictedValue", latestPrediction.getPredictedValue());
            predictionSummary.put("expectedChangePct", latestPrediction.getExpectedChangePct());
            predictionSummary.put("model", latestPrediction.getModelName());
        }

        return new DashboardResponse(
                PeriodUtil.format(period),
                summary,
                categoryScores,
                charts,
                support.topContributors(single, 5),
                List.copyOf(anomalyService.openForUser(userId, 5)),
                predictionSummary,
                dq);
    }

    // ---- helpers ------------------------------------------------------

    private Delta delta(BigDecimal now, MonthlyMetric prev, Function<MonthlyMetric, BigDecimal> f) {
        BigDecimal p = prev == null ? null : f.apply(prev);
        return new Delta(now, p,
                p == null ? null : now.subtract(p),
                ImpactAnalyticsSupport.deltaPct(now, p));
    }

    private Delta scoreDelta(EnvironmentalScore score, EnvironmentalScore prev) {
        if (score == null) return new Delta(null, null, null, null);
        BigDecimal p = prev == null ? null : prev.getOverallScore();
        return new Delta(score.getOverallScore(), p,
                p == null ? null : score.getOverallScore().subtract(p),
                ImpactAnalyticsSupport.deltaPct(score.getOverallScore(), p));
    }

    private List<TimeSeriesPoint> series(List<MonthlyMetric> metrics, Function<MonthlyMetric, BigDecimal> f) {
        return metrics.stream()
                .map(m -> new TimeSeriesPoint(PeriodUtil.format(m.getPeriodMonth()), f.apply(m)))
                .toList();
    }

    private List<TimeSeriesPoint> scoreSeries(List<EnvironmentalScore> scores) {
        return scores.stream()
                .map(s -> new TimeSeriesPoint(PeriodUtil.format(s.getPeriodMonth()), s.getOverallScore()))
                .toList();
    }

    private boolean hasGaps(List<MonthlyMetric> metrics) {
        if (metrics.size() < 2) return false;
        LocalDate first = metrics.get(0).getPeriodMonth();
        LocalDate last = metrics.get(metrics.size() - 1).getPeriodMonth();
        long span = PeriodUtil.monthsBetween(first, last) + 1;
        return span != metrics.size();
    }
}
