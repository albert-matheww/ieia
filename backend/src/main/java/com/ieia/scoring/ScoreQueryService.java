package com.ieia.scoring;

import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.metrics.MetricsAggregationService;
import com.ieia.metrics.MonthlyMetric;
import com.ieia.metrics.MonthlyMetricRepository;
import com.ieia.scoring.dto.ScoreResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read + recompute operations for a user's scores (docs/03-api-specification.md §7). */
@Service
public class ScoreQueryService {

    private final EnvironmentalScoreRepository scoreRepository;
    private final MonthlyMetricRepository metricRepository;
    private final MetricsAggregationService aggregationService;
    private final ScoringService scoringService;

    public ScoreQueryService(EnvironmentalScoreRepository scoreRepository,
                             MonthlyMetricRepository metricRepository,
                             MetricsAggregationService aggregationService,
                             ScoringService scoringService) {
        this.scoreRepository = scoreRepository;
        this.metricRepository = metricRepository;
        this.aggregationService = aggregationService;
        this.scoringService = scoringService;
    }

    @Transactional(readOnly = true)
    public List<ScoreResponse> history(UUID userId, LocalDate from, LocalDate to) {
        List<EnvironmentalScore> scores = (from != null && to != null)
                ? scoreRepository.findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(userId, from, to)
                : scoreRepository.findByUserIdOrderByPeriodMonth(userId);
        return scores.stream().map(ScoreResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScoreResponse forPeriod(UUID userId, LocalDate period) {
        return scoreRepository.findByUserIdAndPeriodMonth(userId, period)
                .map(ScoreResponse::from)
                .orElseThrow(() -> ApiException.notFound("Score for " + PeriodUtil.format(period)));
    }

    @Transactional(readOnly = true)
    public ScoreResponse latest(UUID userId) {
        return scoreRepository.findFirstByUserIdOrderByPeriodMonthDesc(userId)
                .map(ScoreResponse::from)
                .orElseThrow(() -> ApiException.notFound("No scores yet — add some records first"));
    }

    /** Rebuild metrics + scores for the caller's months (e.g. after a config change). */
    @Transactional
    public int recompute(UUID userId, LocalDate from, LocalDate to) {
        List<MonthlyMetric> months = metricRepository.findByUserIdOrderByPeriodMonth(userId);
        int n = 0;
        for (MonthlyMetric m : months) {
            if (from != null && m.getPeriodMonth().isBefore(from)) continue;
            if (to != null && m.getPeriodMonth().isAfter(to)) continue;
            aggregationService.recomputeMonth(userId, m.getPeriodMonth());
            scoringService.recomputeMonth(userId, m.getPeriodMonth());
            n++;
        }
        return n;
    }
}
