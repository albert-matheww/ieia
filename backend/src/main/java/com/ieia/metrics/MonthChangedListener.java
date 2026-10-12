package com.ieia.metrics;

import com.ieia.calculation.MonthChangedEvent;
import com.ieia.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The aggregation → scoring pipeline (docs/01-architecture.md §4.1).
 *
 * <p>Runs synchronously in the same transaction as the record change that
 * published the {@link MonthChangedEvent}, so a record and its derived metrics
 * and score commit together (or roll back together).
 */
@Component
public class MonthChangedListener {

    private static final Logger log = LoggerFactory.getLogger(MonthChangedListener.class);

    private final MetricsAggregationService aggregationService;
    private final ScoringService scoringService;

    public MonthChangedListener(MetricsAggregationService aggregationService, ScoringService scoringService) {
        this.aggregationService = aggregationService;
        this.scoringService = scoringService;
    }

    @EventListener
    @Order(10)
    public void onMonthChanged(MonthChangedEvent event) {
        log.debug("Pipeline: rebuilding metrics + score for user {} {} ({})",
                event.userId(), event.periodMonth(), event.reason());
        aggregationService.recomputeMonth(event.userId(), event.periodMonth());
        scoringService.recomputeMonth(event.userId(), event.periodMonth());
    }
}
