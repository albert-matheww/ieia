package com.ieia.metrics.dto;

import java.util.List;
import java.util.Map;

/** Monthly series keyed by metric name (docs/03-api-specification.md §6). */
public record TrendsResponse(
        String from,
        String to,
        Map<String, List<TimeSeriesPoint>> series
) {}
