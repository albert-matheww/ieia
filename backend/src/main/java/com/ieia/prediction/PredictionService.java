package com.ieia.prediction;

import com.ieia.common.exception.ApiException;
import com.ieia.common.util.HashUtil;
import com.ieia.common.util.PeriodUtil;
import com.ieia.integration.ml.MlServiceClient;
import com.ieia.integration.ml.dto.MlPredictRequest;
import com.ieia.integration.ml.dto.MlPredictResponse;
import com.ieia.metrics.SeriesAssembler;
import com.ieia.metrics.dto.TimeSeriesPoint;
import com.ieia.prediction.dto.PredictionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);
    private static final Set<String> TARGETS = Set.of("CO2E", "ENERGY", "WATER", "SCORE");
    private static final int MIN_MONTHS = 3;

    private final SeriesAssembler seriesAssembler;
    private final MlServiceClient ml;
    private final PredictionRepository predictionRepository;
    private final ModelRunRepository modelRunRepository;

    public PredictionService(SeriesAssembler seriesAssembler,
                             MlServiceClient ml,
                             PredictionRepository predictionRepository,
                             ModelRunRepository modelRunRepository) {
        this.seriesAssembler = seriesAssembler;
        this.ml = ml;
        this.predictionRepository = predictionRepository;
        this.modelRunRepository = modelRunRepository;
    }

    @Transactional
    public PredictionResponse getOrCreate(UUID userId, String metric, int horizon, boolean force) {
        String target = normalise(metric);
        List<TimeSeriesPoint> series = seriesAssembler.forecastSeries(userId, target);
        if (series.size() < MIN_MONTHS) {
            throw ApiException.unprocessable(
                    "Need at least " + MIN_MONTHS + " months of data to forecast " + target
                            + " (have " + series.size() + ")");
        }
        LocalDate lastActual = PeriodUtil.parse(series.get(series.size() - 1).period());
        LocalDate horizonMonth = lastActual.plusMonths(horizon);
        String seriesHash = hash(series, target, horizon);

        if (!force) {
            var cached = predictionRepository
                    .findFirstByUserIdAndTargetMetricAndHorizonMonthOrderByCreatedAtDesc(userId, target, horizonMonth);
            if (cached.isPresent() && seriesHash.equals(cached.get().getMetrics().get("seriesHash"))) {
                return PredictionResponse.from(cached.get(), true);
            }
        }

        long started = System.currentTimeMillis();
        MlPredictResponse mlRes = ml.predict(new MlPredictRequest(
                series.stream().map(p -> new MlPredictRequest.Point(
                        p.period(), p.value() == null ? 0.0 : p.value().doubleValue())).toList(),
                target, horizon, 5, 12));
        int durationMs = (int) (System.currentTimeMillis() - started);

        MlPredictResponse.ForecastPoint fp = mlRes.forecast().get(mlRes.forecast().size() - 1);
        BigDecimal current = series.get(series.size() - 1).value();
        BigDecimal predicted = bd(fp.value());
        BigDecimal changeAbs = predicted.subtract(current);
        BigDecimal changePct = current.signum() == 0 ? null
                : changeAbs.multiply(BigDecimal.valueOf(100)).divide(current, 2, RoundingMode.HALF_UP);

        ModelRun run = new ModelRun();
        run.setModelType("PREDICTION");
        run.setTargetMetric(target);
        run.setAlgorithm(mlRes.model());
        run.setParams(Map.of("horizon", horizon, "seasonality", 12, "seed", mlRes.seed()));
        run.setMetrics(evaluationMap(mlRes));
        run.setTrainingRows(mlRes.evaluation() == null ? 0 : mlRes.evaluation().nTrain());
        run.setDurationMs(durationMs);
        run = modelRunRepository.save(run);

        Prediction p = new Prediction();
        p.setUserId(userId);
        p.setModelRunId(run.getId());
        p.setTargetMetric(target);
        p.setGeneratedForMonth(lastActual);
        p.setHorizonMonth(horizonMonth);
        p.setCurrentValue(current);
        p.setPredictedValue(predicted);
        p.setLowerBound(bd(fp.lower()));
        p.setUpperBound(bd(fp.upper()));
        p.setExpectedChangeAbs(changeAbs.setScale(4, RoundingMode.HALF_UP));
        p.setExpectedChangePct(changePct);
        p.setModelName(mlRes.model());
        p.setFeatureImportance(mlRes.featureImportance() == null ? List.of()
                : mlRes.featureImportance().stream()
                    .map(fi -> Map.<String, Object>of("feature", fi.feature(), "importance", fi.importance()))
                    .toList());
        Map<String, Object> metricsBag = new LinkedHashMap<>();
        metricsBag.put("seriesHash", seriesHash);
        metricsBag.put("evaluation", evaluationMap(mlRes));
        metricsBag.put("nTrain", mlRes.evaluation() == null ? 0 : mlRes.evaluation().nTrain());
        metricsBag.put("limitations", mlRes.limitations() == null ? List.of() : mlRes.limitations());
        metricsBag.put("nHistory", mlRes.nHistory());
        p.setMetrics(metricsBag);
        p.setConfidenceNote(mlRes.note());
        p = predictionRepository.save(p);

        log.info("Prediction for user {} {}: {} -> {} ({}), model {}", userId, target,
                current, predicted, changePct == null ? "n/a" : changePct + "%", mlRes.model());
        return PredictionResponse.from(p, false);
    }

    @Transactional(readOnly = true)
    public List<PredictionResponse> history(UUID userId, String metric) {
        return predictionRepository
                .findByUserIdAndTargetMetricOrderByHorizonMonth(userId, normalise(metric)).stream()
                .map(p -> PredictionResponse.from(p, true))
                .toList();
    }

    // ---- helpers ------------------------------------------------------

    private static Map<String, Object> evaluationMap(MlPredictResponse res) {
        var e = res.evaluation();
        Map<String, Object> m = new LinkedHashMap<>();
        if (e != null) {
            m.put("mae", e.mae());
            m.put("rmse", e.rmse());
            m.put("r2", e.r2());
            m.put("cvFolds", e.cvFolds());
            m.put("nTrain", e.nTrain());
        }
        m.put("model", res.model());
        return m;
    }

    private static String normalise(String metric) {
        String t = metric == null ? "CO2E" : metric.trim().toUpperCase();
        if (!TARGETS.contains(t)) {
            throw ApiException.validation("metric must be one of " + TARGETS);
        }
        return t;
    }

    private static String hash(List<TimeSeriesPoint> series, String target, int horizon) {
        StringBuilder sb = new StringBuilder(target).append('|').append(horizon).append('|');
        for (TimeSeriesPoint p : series) {
            sb.append(p.period()).append(':')
              .append(p.value() == null ? "0" : p.value().stripTrailingZeros().toPlainString()).append(';');
        }
        return HashUtil.sha256Hex(sb.toString());
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
