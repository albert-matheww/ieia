package com.ieia.record.dto;

import com.ieia.calculation.CalculatedImpact;

import java.math.BigDecimal;
import java.util.UUID;

public record ImpactDto(
        String impactType,
        BigDecimal value,
        String unit,
        UUID emissionFactorId,
        BigDecimal factorValue,
        String calculationMethod
) {
    public static ImpactDto from(CalculatedImpact ci) {
        return new ImpactDto(
                ci.getImpactType().name(),
                ci.getValue(),
                ci.getUnit(),
                ci.getEmissionFactorId(),
                ci.getFactorValue(),
                ci.getCalculationMethod());
    }
}
