package com.ieia.recommendation.dto;

import com.ieia.common.util.PeriodUtil;
import com.ieia.recommendation.Recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecommendationResponse(
        String period,
        Instant generatedAt,
        String basis,
        String source,
        TopContributor topContributor,
        List<Item> items
) {
    public record TopContributor(String category, BigDecimal pctOfTotal) {}

    public record Item(
            UUID id,
            int rank,
            String category,
            String title,
            String rationale,
            String actionType,
            Map<String, Object> assumedChange,
            BigDecimal estimatedReductionCo2eKg,
            BigDecimal estimatedReductionPct,
            Map<String, Object> estimatedOther,
            String confidence,
            String status
    ) {
        public static Item from(Recommendation r) {
            return new Item(r.getId(), r.getRank(), r.getCategory(), r.getTitle(), r.getRationale(),
                    r.getActionType(), r.getAssumedChange(), r.getEstimatedReductionCo2eKg(),
                    r.getEstimatedReductionPct(), r.getEstimatedOther(), r.getConfidence(), r.getStatus());
        }
    }

    public static RecommendationResponse of(String period, Instant generatedAt, String basis, String source,
                                            TopContributor top, List<Recommendation> recs) {
        return new RecommendationResponse(period, generatedAt, basis, source, top,
                recs.stream().map(Item::from).toList());
    }
}
