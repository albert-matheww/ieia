package com.ieia.metrics.dto;

import java.math.BigDecimal;
import java.util.List;

public record ContributorsResponse(
        String period,
        String by,
        BigDecimal totalCo2eKg,
        List<Item> items
) {
    public record Item(String key, String label, String category, BigDecimal co2eKg, BigDecimal pct) {}
}
