package com.ieia.calculation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published (after commit) whenever a user's records for a given month change,
 * so downstream aggregation and scoring can rebuild exactly that
 * {@code (user, month)} slice (docs/01-architecture.md §4.1).
 *
 * <p>Stage 2 publishes it; Stage 3's {@code MetricsAggregationListener} consumes it.
 */
public record MonthChangedEvent(UUID userId, LocalDate periodMonth, String reason) {
}
