package com.ieia.emissionfactor.dto;

import com.ieia.emissionfactor.EmissionFactor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmissionFactorDto(
        UUID id,
        UUID activityTypeId,
        String activityTypeCode,
        String impactType,
        String unitFrom,
        String unitTo,
        BigDecimal value,
        String region,
        String source,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active
) {
    public static EmissionFactorDto from(EmissionFactor f) {
        return new EmissionFactorDto(
                f.getId(), f.getActivityType().getId(), f.getActivityType().getCode(),
                f.getImpactType().name(), f.getUnitFrom(), f.getUnitTo(), f.getValue(),
                f.getRegion(), f.getSource(), f.getEffectiveFrom(), f.getEffectiveTo(), f.isActive());
    }
}
