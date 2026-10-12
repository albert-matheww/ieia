package com.ieia.metrics.dto;

import java.math.BigDecimal;

/** {period: "YYYY-MM", value: number} — the unit of every chart series. */
public record TimeSeriesPoint(String period, BigDecimal value) {}
