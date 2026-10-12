package com.ieia.record.dto;

import com.ieia.calculation.CalculatedImpact;
import com.ieia.common.util.PeriodUtil;
import com.ieia.record.EnvironmentalRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecordResponse(
        UUID id,
        ActivityTypeRef activityType,
        String period,
        BigDecimal amount,
        String unit,
        String location,
        String sourceDetail,
        Map<String, Object> attributes,
        String note,
        List<ImpactDto> impacts,
        List<String> warnings,
        Instant createdAt,
        Instant updatedAt
) {
    public record ActivityTypeRef(UUID id, String code, String name, String category) {}

    public static RecordResponse from(EnvironmentalRecord r, List<CalculatedImpact> impacts, List<String> warnings) {
        var at = r.getActivityType();
        return new RecordResponse(
                r.getId(),
                new ActivityTypeRef(at.getId(), at.getCode(), at.getName(), at.getCategory().name()),
                PeriodUtil.format(r.getPeriodMonth()),
                r.getAmount(),
                r.getUnit(),
                r.getLocation(),
                r.getSourceDetail(),
                r.getAttributes(),
                r.getNote(),
                (impacts == null ? List.<CalculatedImpact>of() : impacts).stream().map(ImpactDto::from).toList(),
                warnings == null ? List.of() : warnings,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
